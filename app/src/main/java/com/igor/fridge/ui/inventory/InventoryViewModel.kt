package com.igor.fridge.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
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
import com.igor.fridge.domain.currentDateFlow
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
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
    val sections: List<InventorySection> = emptyList(),
    val availableCategories: List<FoodCategory> = emptyList(),
    val category: FoodCategory? = null,
    val matchingCount: Int = 0,
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

private data class Feedback(
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val selection = MutableStateFlow(InventoryCriteria(
        query = savedStateHandle["inventory.query"] ?: "",
        filter = InventoryFilter.entries.firstOrNull {
            it.name == savedStateHandle.get<String>("inventory.filter")
        } ?: InventoryFilter.TUTTI,
        location = StorageLocation.entries.firstOrNull {
            it.name == savedStateHandle.get<String>("inventory.location")
        },
        category = FoodCategory.entries.firstOrNull {
            it.name == savedStateHandle.get<String>("inventory.category")
        },
    ))
    private val feedback = MutableStateFlow(Feedback())

    /** Come disfare l'ultima eliminazione o consumazione: vive quanto lo snackbar. */
    private var pendingUndo: (suspend () -> Unit)? = null
    private var nextMessageId = 0L

    private fun show(message: String, undo: (suspend () -> Unit)? = null) {
        pendingUndo = undo
        val id = ++nextMessageId
        feedback.update { it.copy(message = message, messageId = id, canUndo = undo != null) }
    }

    private val projected = combine(
        foodRepository.observeAll(), selection, warningDays, today,
    ) { items, criteria, warningDays, today ->
        val model = inventoryListOf(items, criteria, today, warningDays)
        InventoryUiState(
            items = model.items,
            sections = model.sections,
            availableCategories = model.availableCategories,
            category = criteria.category,
            query = criteria.query,
            filter = criteria.filter,
            location = criteria.location,
            warningDays = warningDays,
            today = today,
            totalCount = model.totalCount,
            matchingCount = model.matchingCount,
            expiringCount = model.expiringCount,
            expiredCount = model.expiredCount,
            noDateCount = model.noDateCount,
            isLoading = false,
        )
    }.flowOn(computeDispatcher)

    val uiState: StateFlow<InventoryUiState> = combine(projected, feedback) { state, feedback ->
        state.copy(message = feedback.message, messageId = feedback.messageId, canUndo = feedback.canUndo)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = InventoryUiState(
            query = selection.value.query,
            filter = selection.value.filter,
            location = selection.value.location,
            category = selection.value.category,
        ),
    )

    private fun select(value: InventoryCriteria) {
        savedStateHandle["inventory.query"] = value.query
        savedStateHandle["inventory.filter"] = value.filter.name
        savedStateHandle["inventory.location"] = value.location?.name
        savedStateHandle["inventory.category"] = value.category?.name
        selection.value = value
    }

    fun onQueryChange(value: String) = select(selection.value.copy(query = value))
    fun onFilterChange(value: InventoryFilter) = select(selection.value.copy(filter = value))
    fun onLocationChange(value: StorageLocation?) = select(selection.value.copy(location = value))
    fun onCategoryChange(value: FoodCategory?) = select(selection.value.copy(category = value))
    fun resetFilters() = select(InventoryCriteria())

    fun onMessageShown(messageId: Long = feedback.value.messageId) {
        if (messageId != feedback.value.messageId) return
        pendingUndo = null
        feedback.update { it.copy(message = null, canUndo = false) }
    }

    /** Un tocco accidentale sul cestino si annulla dallo snackbar. */
    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                show(if (e is com.igor.fridge.data.repository.UndoConflictException) e.message!!
                    else "Operazione non riuscita: riprova")
            }
        }
    }

    fun delete(item: FoodItem) {
        operation {
            val removed = withContext(NonCancellable) { transactor.run {
                val current = foodRepository.findByUuid(item.uuid)?.takeIf { it.removedAt == null }
                    ?: return@run null
                foodRepository.remove(current, RemovalReason.ERRORE)
                foodRepository.findByUuid(current.uuid)
            } } ?: return@operation
            show("${item.name} eliminato") {
                foodRepository.checkUndo(removed)
                foodRepository.restore(removed)
            }
        }
    }

    /**
     * Segna il prodotto come consumato: esce dall'inventario ed entra nella lista della
     * spesa, insieme. Uscire dalla schermata a meta' non lascia un prodotto fuori dal frigo
     * e assente dalla lista.
     */
    fun consume(item: FoodItem) {
        operation {
            val outcome = withContext(NonCancellable) { transactor.run {
                val current = foodRepository.findByUuid(item.uuid)?.takeIf { it.removedAt == null }
                    ?: return@run null
                val before = shoppingRepository.findByName(current.name)
                foodRepository.remove(current, RemovalReason.CONSUMATO)
                val changed = shoppingRepository.addIfAbsent(current.name, current.quantity,
                    current.unit, current.knownCategory(), brand = current.brand)
                val after = shoppingRepository.findByName(current.name)
                val removed = requireNotNull(foodRepository.findByUuid(current.uuid))
                suspend {
                    foodRepository.checkUndo(removed)
                    if (changed) shoppingRepository.checkUndo(before, after)
                    foodRepository.restore(removed)
                    if (changed) shoppingRepository.undoChange(before, after)
                }
            } } ?: return@operation
            show("${item.name} spostato nella lista della spesa", outcome)
        }
    }

    /** Disfa l'ultima eliminazione o consumazione. */
    fun undo(messageId: Long = feedback.value.messageId) {
        if (messageId != feedback.value.messageId) return
        val undo = pendingUndo ?: return
        pendingUndo = null
        feedback.update { it.copy(message = null, canUndo = false) }
        operation {
            try {
                withContext(NonCancellable) { transactor.run { undo() } }
                show("Annullato")
            } catch (e: CancellationException) { throw e }
            catch (e: com.igor.fridge.data.repository.UndoConflictException) { show(e.message!!) }
            catch (e: Exception) { show("Annullamento non riuscito: riprova", undo) }
        }
    }

    /** Aggiunge alla spesa tutti i prodotti scaduti o in scadenza. */
    fun addExpiringToShoppingList() {
        operation {
            val state = uiState.value
            val (candidates, added) = withContext(NonCancellable) { transactor.run {
                val candidates = foodRepository.findExpiring(state.today, state.warningDays)
                candidates to candidates.count {
                    shoppingRepository.addIfAbsent(it.name, it.quantity, it.unit, it.knownCategory())
                }
            } }
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

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                InventoryViewModel(
                    foodRepository = container.foodRepository,
                    shoppingRepository = container.shoppingRepository,
                    warningDays = container.settingsStore.warningDays,
                    today = currentDateFlow(),
                    transactor = container.transactor,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

/** "Altro" vuol dire che la categoria non e' stata scelta: la lista provi a indovinarla. */
private fun FoodItem.knownCategory(): FoodCategory? = category.takeIf { it != FoodCategory.ALTRO }
