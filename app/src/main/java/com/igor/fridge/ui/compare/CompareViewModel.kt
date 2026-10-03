package com.igor.fridge.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.openprices.CommunityPrice
import com.igor.fridge.data.openprices.OpenPricesException
import com.igor.fridge.data.prefs.OpenPricesSettings
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.prices.ListComparison
import com.igor.fridge.domain.prices.compareList
import com.igor.fridge.domain.prices.productKey
import com.igor.fridge.domain.prices.recordsFor
import com.igor.fridge.domain.prices.toObservation
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

data class CompareUiState(
    val comparison: ListComparison? = null,
    val itemCount: Int = 0,
    val communityEnabled: Boolean = false,
    /** Voci della lista che hanno un codice a barre: solo quelle si confrontano con la comunita'. */
    val itemsWithBarcode: Int = 0,
    val communityLoaded: Boolean = false,
    val isLoadingCommunity: Boolean = false,
    val message: String? = null,
    val isLoading: Boolean = true,
)

/**
 * Quanto costerebbe la lista della spesa nei vari negozi.
 *
 * I prezzi vengono dai tuoi scontrini e, se Open Prices e' attivo, dalla comunita' per le
 * voci con un codice a barre. I prezzi della comunita' si scaricano solo quando lo si
 * chiede: il confronto funziona anche senza rete.
 */
class CompareViewModel(
    shoppingRepository: ShoppingRepository,
    private val priceRepository: PriceRepository,
    openPrices: Flow<OpenPricesSettings> = flowOf(OpenPricesSettings()),
    private val fetchCommunity: suspend (String) -> List<CommunityPrice> = { emptyList() },
    private val today: () -> LocalDate = LocalDate::now,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val barcodes = MutableStateFlow<Map<String, String>>(emptyMap())
    private val community = MutableStateFlow<Map<String, List<CommunityPrice>>>(emptyMap())
    private val status = MutableStateFlow(Status())

    private data class Status(
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val message: String? = null,
    )

    init {
        viewModelScope.launch { barcodes.value = priceRepository.allBarcodes() }
    }

    private val toBuy: Flow<List<ShoppingItem>> = shoppingRepository.observeAll()

    /**
     * Il confronto confronta ogni voce con tutto lo storico: con molti scontrini e' un
     * calcolo pesante, che gira fuori dal thread dell'interfaccia e non riparte quando
     * cambia solo lo stato del caricamento.
     */
    private val comparison: Flow<CompareUiState> = combine(
        toBuy,
        priceRepository.observeAll(),
        combine(barcodes, community, ::Pair),
        openPrices,
    ) { items, records, (codes, prices), settings ->
        val list = items.filterNot { it.isChecked }
        val comparison = compareList(list) { item ->
            val mine = recordsFor(item, records).mapNotNull { it.toObservation() }
            val code = codes[productKey(item.name)]
            val theirs = if (settings.enabled && code != null) {
                communityObservations(prices[code].orEmpty(), today())
            } else {
                emptyList()
            }
            mine + theirs
        }
        CompareUiState(
            comparison = comparison,
            itemCount = list.size,
            communityEnabled = settings.enabled,
            itemsWithBarcode = list.count { codes.containsKey(productKey(it.name)) },
            isLoading = false,
        )
    }.flowOn(computeDispatcher)

    val uiState: StateFlow<CompareUiState> = combine(comparison, status) { state, status ->
        state.copy(
            communityLoaded = status.loaded,
            isLoadingCommunity = status.loading,
            message = status.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CompareUiState(),
    )

    /**
     * Scarica da Open Prices i prezzi delle voci con codice a barre, uno per prodotto.
     * Qualche richiesta alla volta e un tempo massimo per tutte: con una rete lenta e
     * venti prodotti l'attesa non deve durare minuti. Cio' che arriva in tempo si tiene.
     */
    fun loadCommunityPrices() {
        if (status.value.loading) return
        status.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            val codes = priceRepository.allBarcodes().also { barcodes.value = it }
            val items = uiState.first { !it.isLoading }.comparison?.quotes?.map { it.item }.orEmpty()
            val wanted = items.mapNotNull { codes[productKey(it.name)] }.distinct()
            val fetched = ConcurrentHashMap<String, List<CommunityPrice>>()
            val errors = ConcurrentLinkedQueue<String>()
            val gate = Semaphore(PARALLEL_REQUESTS)
            val completed = withTimeoutOrNull(COMMUNITY_TIMEOUT_MS) {
                coroutineScope {
                    wanted.forEach { code ->
                        launch {
                            gate.withPermit {
                                try {
                                    fetched[code] = fetchCommunity(code)
                                } catch (e: OpenPricesException) {
                                    errors += e.message ?: "Open Prices non disponibile"
                                }
                            }
                        }
                    }
                }
            } != null
            community.update { it + fetched }
            val error = errors.peek()
                ?: if (completed) null else "Open Prices risponde troppo lentamente: riprova più tardi"
            status.update {
                Status(
                    loading = false,
                    loaded = error == null,
                    message = error ?: when {
                        wanted.isEmpty() -> "Nessuna voce ha un codice a barre: associalo dalla scheda prezzi del prodotto"
                        else -> null
                    },
                )
            }
        }
    }

    fun onMessageShown() = status.update { it.copy(message = null) }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val PARALLEL_REQUESTS = 4
        private const val COMMUNITY_TIMEOUT_MS = 45_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                CompareViewModel(
                    shoppingRepository = container.shoppingRepository,
                    priceRepository = container.priceRepository,
                    openPrices = container.settingsStore.openPrices,
                    fetchCommunity = { code -> container.openPricesClient.productPrices(code) },
                )
            }
        }
    }
}
