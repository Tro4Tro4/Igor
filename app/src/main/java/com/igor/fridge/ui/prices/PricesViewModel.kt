package com.igor.fridge.ui.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.openprices.CommunityPrice
import com.igor.fridge.data.openprices.OpenPricesException
import com.igor.fridge.data.prefs.OpenPricesSettings
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.domain.prices.ProductPriceSummary
import com.igor.fridge.domain.prices.StoreObservation
import com.igor.fridge.domain.prices.latestByStore
import com.igor.fridge.domain.prices.monthlySpending
import com.igor.fridge.domain.prices.summarize
import com.igor.fridge.domain.prices.toObservation
import com.igor.fridge.ui.compare.communityObservations
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class PricesUiState(
    val products: List<ProductPriceSummary> = emptyList(),
    val monthly: List<Pair<YearMonth, Long>> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && products.isEmpty() && query.isBlank()
}

/** Elenco dei prodotti con il loro prezzo e la spesa di ogni mese. */
class PricesViewModel(
    priceRepository: PriceRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * Riepiloghi e spesa mensile scorrono tutto lo storico: si ricalcolano quando cambiano
     * i prezzi, fuori dal thread dell'interfaccia, e non a ogni lettera digitata.
     */
    private val summaries = priceRepository.observeAll()
        .map { records -> summarize(records) to monthlySpending(records) }
        .flowOn(computeDispatcher)

    val uiState: StateFlow<PricesUiState> =
        combine(summaries, query) { (products, monthly), query ->
            val needle = query.trim()
            PricesUiState(
                products = products.filter {
                    needle.isEmpty() || it.productName.contains(needle, ignoreCase = true)
                },
                monthly = monthly,
                query = query,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PricesUiState(),
        )

    fun onQueryChange(value: String) = query.update { value }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PricesViewModel(igorApplication().container.priceRepository) }
        }
    }
}

data class PriceHistoryUiState(
    val summary: ProductPriceSummary? = null,
    /** Dal piu' recente. */
    val records: List<PriceRecord> = emptyList(),
    val isLoading: Boolean = true,
    val barcode: String? = null,
    val communityEnabled: Boolean = false,
    /** Prezzi della comunita' in Italia, dal piu' recente; null se non ancora scaricati. */
    val community: List<CommunityPrice>? = null,
    val isLoadingCommunity: Boolean = false,
    val message: String? = null,
) {
    // Calcolati una volta per stato, non a ogni lettura durante la ricomposizione.

    /** Gli acquisti confrontabili con l'ultimo, dal piu' vecchio: i punti del grafico. */
    val chartPoints: List<PriceRecord> =
        summary?.let { s -> records.filter { it.referenceUnit == s.referenceUnit }.reversed() }.orEmpty()

    /** L'ultimo prezzo pagato in ogni negozio, dal piu' conveniente. */
    val byStore: List<StoreObservation> = latestByStore(
        records.filter { it.referenceUnit == summary?.referenceUnit }.mapNotNull { it.toObservation() },
    )
}

/**
 * La storia dei prezzi di un prodotto, i negozi a confronto e, se attivo, Open Prices.
 */
class PriceHistoryViewModel(
    private val productKey: String,
    private val priceRepository: PriceRepository,
    openPrices: Flow<OpenPricesSettings> = flowOf(OpenPricesSettings()),
    private val fetchCommunity: suspend (String) -> List<CommunityPrice> = { emptyList() },
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val extra = MutableStateFlow(Extra())

    private data class Extra(
        val barcode: String? = null,
        val community: List<CommunityPrice>? = null,
        val loadingCommunity: Boolean = false,
        val message: String? = null,
    )

    init {
        viewModelScope.launch {
            val code = priceRepository.barcodeOf(productKey)
            extra.update { it.copy(barcode = code) }
        }
    }

    val uiState: StateFlow<PriceHistoryUiState> =
        combine(priceRepository.observeProduct(productKey), extra, openPrices) { records, extra, settings ->
            PriceHistoryUiState(
                summary = summarize(records).firstOrNull(),
                records = records,
                isLoading = false,
                barcode = extra.barcode,
                communityEnabled = settings.enabled,
                community = extra.community,
                isLoadingCommunity = extra.loadingCommunity,
                message = extra.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PriceHistoryUiState(),
        )

    /** Toglie un prezzo letto male: falserebbe minimo, media e andamento. */
    fun delete(record: PriceRecord) {
        viewModelScope.launch { priceRepository.delete(record) }
    }

    /** Associa il codice a barre letto o scritto; un codice non valido non si salva. */
    fun setBarcode(text: String) {
        viewModelScope.launch {
            val saved = priceRepository.setBarcode(productKey, text)
            extra.update {
                if (saved == null) {
                    it.copy(message = "Codice a barre non valido")
                } else {
                    it.copy(barcode = saved, community = null, message = "Codice a barre associato")
                }
            }
        }
    }

    fun clearBarcode() {
        viewModelScope.launch {
            priceRepository.clearBarcode(productKey)
            extra.update { it.copy(barcode = null, community = null) }
        }
    }

    /** Scarica i prezzi della comunita' per il codice a barre del prodotto (solo in Italia). */
    fun loadCommunityPrices() {
        val code = extra.value.barcode ?: return
        if (extra.value.loadingCommunity) return
        extra.update { it.copy(loadingCommunity = true, message = null) }
        viewModelScope.launch {
            try {
                val prices = fetchCommunity(code)
                    .filter { communityObservations(listOf(it), today()).isNotEmpty() }
                    .sortedByDescending { it.date }
                extra.update { it.copy(community = prices, loadingCommunity = false) }
            } catch (e: OpenPricesException) {
                extra.update { it.copy(loadingCommunity = false, message = e.message) }
            }
        }
    }

    fun onMessageShown() = extra.update { it.copy(message = null) }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(productKey: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                PriceHistoryViewModel(
                    productKey = productKey,
                    priceRepository = container.priceRepository,
                    openPrices = container.settingsStore.openPrices,
                    fetchCommunity = { code -> container.openPricesClient.productPrices(code) },
                )
            }
        }
    }
}
