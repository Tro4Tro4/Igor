package com.igor.fridge.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val items: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val canUndo: Boolean = false,
) {
    val checkedCount: Int get() = items.count { it.isChecked }
}

/** Cosa serve per annullare l'ultimo spostamento in frigo. */
private data class LastMove(
    val shoppingItems: List<ShoppingItem>,
    val createdFood: List<FoodItem>,
)

class ShoppingViewModel(
    private val shoppingRepository: ShoppingRepository,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    // Vive in memoria quanto lo snackbar: un annullamento che sopravvive alla schermata
    // non e' quello che l'utente si aspetta, e non vale una colonna in database.
    private var lastMove: LastMove? = null

    private val feedback = MutableStateFlow(Feedback())

    private data class Feedback(val message: String? = null, val canUndo: Boolean = false)

    // Lazily e non WhileSubscribed: moveCheckedToInventory legge uiState.value direttamente,
    // non tramite un collector. Un timeout che ferma la condivisione fra una spunta e la
    // pressione del pulsante lascerebbe .value non aggiornato con l'ultimo messaggio/canUndo.
    val uiState: StateFlow<ShoppingUiState> =
        combine(shoppingRepository.observeAll(), feedback) { items, feedback ->
            ShoppingUiState(
                items = items,
                isLoading = false,
                message = feedback.message,
                canUndo = feedback.canUndo,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = ShoppingUiState(),
        )

    fun add(name: String) {
        viewModelScope.launch { shoppingRepository.addIfAbsent(name) }
    }

    fun setChecked(item: ShoppingItem, checked: Boolean) {
        viewModelScope.launch { shoppingRepository.setChecked(item, checked) }
    }

    fun delete(item: ShoppingItem) {
        viewModelScope.launch { shoppingRepository.delete(item) }
    }

    /**
     * Le voci spuntate entrano in inventario ed escono dalla lista.
     *
     * L'operazione e' legata a un'azione esplicita e non allo spunto: al supermercato si
     * spunta e si toglie la spunta mentre si prende, e far entrare un prodotto in frigo a
     * ogni tocco creerebbe record fantasma.
     */
    fun moveCheckedToInventory() {
        viewModelScope.launch {
            val checked = uiState.value.items.filter { it.isChecked }
            if (checked.isEmpty()) {
                feedback.update { it.copy(message = "Nessun prodotto spuntato", canUndo = false) }
                return@launch
            }

            val created = checked.map { item ->
                foodRepository.addFromShopping(item.name, item.quantity, item.unit)
            }
            checked.forEach { shoppingRepository.delete(it) }

            lastMove = LastMove(shoppingItems = checked, createdFood = created)
            val text = if (checked.size == 1) {
                "${checked.single().name} messo in frigo"
            } else {
                "${checked.size} prodotti messi in frigo"
            }
            feedback.update { it.copy(message = text, canUndo = true) }
        }
    }

    /** Rimette com'era: le voci tornano in lista e gli articoli escono dall'inventario. */
    fun undoLastMove() {
        val move = lastMove ?: return
        lastMove = null
        viewModelScope.launch {
            move.createdFood.forEach { foodRepository.remove(it, RemovalReason.ERRORE) }
            move.shoppingItems.forEach { shoppingRepository.restore(it) }
            feedback.update { it.copy(message = "Spostamento annullato", canUndo = false) }
        }
    }

    fun onMessageShown() {
        feedback.update { it.copy(message = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                ShoppingViewModel(
                    shoppingRepository = container.shoppingRepository,
                    foodRepository = container.foodRepository,
                )
            }
        }
    }
}
