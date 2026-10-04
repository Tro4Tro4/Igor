package com.igor.fridge.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.Transactor
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.prefs.SettingsStore
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.ExpiryStatus
import com.igor.fridge.domain.currentDateFlow
import com.igor.fridge.domain.expiryStatus
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class InventoryUiState(
    val items: List<FoodItem> = emptyList(),
    val query: String = "",
    val filter: InventoryFilter = InventoryFilter.TUTTI,
    val location: StorageLocation? = null,
    val warningDays: Int = SettingsStore.DEFAULT_WARNING_DAYS,
    val today: LocalDate = LocalDate.now(),
    val totalCount: Int = 0,
    val expiringCount: Int = 0,
    val expiredCount: Int = 0,
    val noDateCount: Int = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
    /** Cambia a ogni messaggio: due messaggi uguali di seguito si mostrano entrambi. */
    val messageId: Long = 0,
    val canUndo: Boolean = false,
)

private data class Criteria(
    val query: String = "",
    val filter: InventoryFilter = InventoryFilter.TUTTI,
    val location: StorageLocation? = null,
    val message: String? = null,
    val messageId: Long = 0,
    val canUndo: Boolean = false,
)

class InventoryViewModel(
    private val foodRepository: FoodRepository,
    private val shoppingRepository: ShoppingRepository,
    warningDays: Flow<Int>,
    today: Flow<LocalDate>,
    private val transactor: Transactor = Transactor.Direct,
) : ViewModel() {

    private val criteria = MutableStateFlow(Criteria())

    /** Come disfare l'ultima eliminazione o consumazione: vive quanto lo snackbar. */
    private var pendingUndo: (suspend () -> Unit)? = null
    private var nextMessageId = 0L

    private fun show(message: String, undo: (suspend () -> Unit)? = null) {
        pendingUndo = undo
        val id = ++nextMessageId
        criteria.update { it.copy(message = message, messageId = id, canUndo = undo != null) }
    }

    val uiState: StateFlow<InventoryUiState> =
        combine(
            foodRepository.observeAll(),
            criteria,
            warningDays,
            today,
        ) { items, criteria, warningDays, today ->
            val statuses = items.associateBy({ it.uuid }, { it.expiryStatus(today, warningDays) })
            val visible = items.filter { item ->
                val needle = criteria.query.trim()
                val matchesQuery = needle.isEmpty() ||
                    item.name.contains(needle, ignoreCase = true) ||
                    item.brand?.contains(needle, ignoreCase = true) == true
                val matchesLocation = criteria.location == null || item.location == criteria.location
                val matchesFilter = when (criteria.filter) {
                    InventoryFilter.TUTTI -> true
                    InventoryFilter.IN_SCADENZA -> statuses[item.uuid] == ExpiryStatus.IN_SCADENZA
                    InventoryFilter.SCADUTI -> statuses[item.uuid] == ExpiryStatus.SCADUTO
                    InventoryFilter.SENZA_DATA -> statuses[item.uuid] == ExpiryStatus.SENZA_DATA
                }
                matchesQuery && matchesLocation && matchesFilter
            }
            InventoryUiState(
                items = visible,
                query = criteria.query,
                filter = criteria.filter,
                location = criteria.location,
                warningDays = warningDays,
                today = today,
                totalCount = items.size,
                expiringCount = statuses.values.count { it == ExpiryStatus.IN_SCADENZA },
                expiredCount = statuses.values.count { it == ExpiryStatus.SCADUTO },
                noDateCount = statuses.values.count { it == ExpiryStatus.SENZA_DATA },
                isLoading = false,
                message = criteria.message,
                messageId = criteria.messageId,
                canUndo = criteria.canUndo,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = InventoryUiState(),
        )

    fun onQueryChange(value: String) = criteria.update { it.copy(query = value) }

    fun onFilterChange(value: InventoryFilter) = criteria.update { it.copy(filter = value) }

    fun onLocationChange(value: StorageLocation?) = criteria.update { it.copy(location = value) }

    fun onMessageShown() {
        pendingUndo = null
        criteria.update { it.copy(message = null, canUndo = false) }
    }

    /** Un tocco accidentale sul cestino si annulla dallo snackbar. */
    fun delete(item: FoodItem) {
        viewModelScope.launch {
            foodRepository.remove(item, RemovalReason.ERRORE)
            show("${item.name} eliminato") { foodRepository.restore(item) }
        }
    }

    /**
     * Segna il prodotto come consumato: esce dall'inventario ed entra nella lista della
     * spesa, insieme. Uscire dalla schermata a meta' non lascia un prodotto fuori dal frigo
     * e assente dalla lista.
     */
    fun consume(item: FoodItem) {
        viewModelScope.launch {
            val added = withContext(NonCancellable) {
                transactor.run {
                    foodRepository.remove(item, RemovalReason.CONSUMATO)
                    shoppingRepository.addIfAbsent(
                        item.name,
                        item.quantity,
                        item.unit,
                        item.knownCategory(),
                    )
                }
            }
            show("${item.name} spostato nella lista della spesa") {
                foodRepository.restore(item)
                // La voce si toglie solo se l'ha creata questa consumazione.
                if (added) shoppingRepository.removeUnchecked(item.name)
            }
        }
    }

    /** Disfa l'ultima eliminazione o consumazione. */
    fun undo() {
        val undo = pendingUndo ?: return
        pendingUndo = null
        criteria.update { it.copy(message = null, canUndo = false) }
        viewModelScope.launch {
            withContext(NonCancellable) { transactor.run { undo() } }
            show("Annullato")
        }
    }

    /** Aggiunge alla spesa tutti i prodotti scaduti o in scadenza. */
    fun addExpiringToShoppingList() {
        viewModelScope.launch {
            val state = uiState.value
            val candidates = foodRepository.findExpiring(state.today, state.warningDays)
            val added = candidates.count {
                shoppingRepository.addIfAbsent(it.name, it.quantity, it.unit, it.knownCategory())
            }
            val text = when {
                candidates.isEmpty() -> "Nessun prodotto in scadenza"
                added == 0 -> "Già presenti nella lista della spesa"
                added == 1 -> "1 prodotto aggiunto alla lista della spesa"
                else -> "$added prodotti aggiunti alla lista della spesa"
            }
            show(text)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                InventoryViewModel(
                    foodRepository = container.foodRepository,
                    shoppingRepository = container.shoppingRepository,
                    warningDays = container.settingsStore.warningDays,
                    today = currentDateFlow(),
                    transactor = container.transactor,
                )
            }
        }
    }
}

/** "Altro" vuol dire che la categoria non e' stata scelta: la lista provi a indovinarla. */
private fun FoodItem.knownCategory(): FoodCategory? = category.takeIf { it != FoodCategory.ALTRO }
