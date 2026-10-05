package com.igor.fridge.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.*
import com.igor.fridge.data.onlineprices.*
import com.igor.fridge.data.repository.OnlinePricesRepository
import com.igor.fridge.domain.prices.*
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

data class OnlineCompareUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val settings: OnlinePricesSettings = OnlinePricesSettings(),
    val comparison: OnlineComparison = OnlineComparison(),
    val candidates: Map<String,List<OnlineOffer>> = emptyMap(),
    val sources: List<OnlineSource> = emptyList(),
    val message: String? = null,
    val items: List<ShoppingItem> = emptyList(),
    val offers: List<OnlineOffer> = emptyList(),
    val bindings: List<OnlineBinding> = emptyList(),
    val configured: Boolean = false,
    val searching: Set<String> = emptySet(),
) {
    val hasWinner: Boolean get() = comparison.commonItemUuids.isNotEmpty() && comparison.estimates.count { it.commonTotalCents != null } >= 2
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineCompareViewModel(
    private val repository: OnlinePricesRepository,
    private val shopping: Flow<List<ShoppingItem>>,
    private val settings: Flow<OnlinePricesSettings>,
    private val configured: Boolean,
    private val now: () -> Instant = Instant::now,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private data class Status(val refreshing: Boolean = false, val candidates: Map<String,List<OnlineOffer>> = emptyMap(),val searching: Set<String> = emptySet(),val message: String? = null,val fingerprints: Map<String,Pair<String,String>> = emptyMap())
    private data class Inputs(val settings: OnlinePricesSettings,val items: List<ShoppingItem>,val bindings: List<OnlineBinding>,val sources: List<OnlineSource>,val status: Status)
    private val status = MutableStateFlow(Status())
    private val searches = mutableMapOf<String,Job>()
    private val ticks = flow { while (true) { emit(now()); delay(60_000) } }
    val uiState: StateFlow<OnlineCompareUiState> = combine(settings,shopping,repository.observeBindings(),repository.observeSources(),status) { s,i,b,src,st -> Inputs(s,i,b,src,st) }
        .combine(settings.flatMapLatest { repository.observeOffers(it.postcode) }) { input,offers -> input to offers }
        .combine(ticks) { (input,offers),time ->
            val active = input.sources.filter { it.status == "active" }.map { it.source }.toSet()
            val comparison = compareOnline(input.items,input.bindings,offers.filter { it.source in active },time)
            OnlineCompareUiState(false,input.status.refreshing,input.settings,comparison,
                input.status.candidates.filter { (uuid,_) -> input.items.firstOrNull { it.uuid == uuid }?.let { input.status.fingerprints[uuid] == (it.nameKey to nameKeyOf(it.brand.orEmpty())) } == true }
                    .mapValues { (_,list) -> list.filter { it.requestedPostcode == input.settings.postcode && it.source in active } },input.sources,input.status.message,input.items.filterNot { it.isChecked },offers,input.bindings,configured,input.status.searching)
        }.flowOn(computeDispatcher).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),OnlineCompareUiState(configured=configured))

    fun refresh() {
        if (status.value.refreshing || !configured) return
        status.update { it.copy(refreshing=true,message=null) }
        viewModelScope.launch {
            try {
                val result = repository.refresh()
                status.update { it.copy(message=result.errors.firstOrNull()) }
            } finally { status.update { it.copy(refreshing=false) } }
        }
    }
    fun search(uuid: String) {
        searches.remove(uuid)?.cancel()
        searches[uuid] = viewModelScope.launch {
            status.update { it.copy(searching=it.searching+uuid,message=null) }
            try {
                val item = shopping.first().firstOrNull { it.uuid == uuid } ?: return@launch
                val candidates = repository.searchCandidates(item)
                status.update { it.copy(candidates=it.candidates+(uuid to candidates),fingerprints=it.fingerprints+(uuid to (item.nameKey to nameKeyOf(item.brand.orEmpty())))) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status.update { it.copy(message=e.message ?: "Ricerca prezzi non riuscita") } }
            finally { status.update { it.copy(searching=it.searching-uuid) } }
        }
    }
    fun select(uuid: String, offerId: String) = action { repository.select(uuid,offerId); status.update { it.copy(candidates=it.candidates-uuid) } }
    fun unselect(uuid: String, source: String) = action { repository.unselect(uuid,source) }
    fun onMessageShown() { status.update { it.copy(message=null) } }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { status.update { it.copy(message=e.message) } }
    } }
    companion object {
        val Factory = viewModelFactory { initializer {
            val c = igorApplication().container
            OnlineCompareViewModel(c.onlinePricesRepository,c.shoppingRepository.observeAll(),c.settingsStore.onlinePrices,c.onlinePricesClient.configured)
        } }
    }
}
