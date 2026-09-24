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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Impedisce a una seconda pressione, prima che la lista riemetta senza le voci spuntate,
    // di rileggere le stesse voci e creare un secondo articolo per lo stesso acquisto.
    private var moveInProgress = false

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
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
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
        if (moveInProgress) return
        moveInProgress = true
        viewModelScope.launch {
            try {
                val checked = uiState.value.items.filter { it.isChecked }
                if (checked.isEmpty()) {
                    feedback.update { it.copy(message = "Nessun prodotto spuntato", canUndo = false) }
                    return@launch
                }

                // Le scritture sono 2N e ognuna e' un punto di cancellazione: viewModelScope
                // muore con la schermata, e uscire a meta' lascerebbe alcuni articoli creati
                // e le voci corrispondenti ancora spuntate in lista. NonCancellable porta la
                // sequenza a termine anche se la schermata se ne e' gia' andata.
                withContext(NonCancellable) {
                    // Prima il frigo, poi la lista: interrompendosi qui la spesa resta in
                    // entrambi i posti, mai in nessuno dei due.
                    val created = checked.map { item ->
                        foodRepository.addFromShopping(item.name, item.quantity, item.unit)
                    }
                    checked.forEach { shoppingRepository.delete(it) }

                    lastMove = LastMove(shoppingItems = checked, createdFood = created)
                    val text = if (checked.size == 1) {
                        "Aggiunto in frigo: ${checked.single().name}"
                    } else {
                        "${checked.size} prodotti messi in frigo"
                    }
                    feedback.update { it.copy(message = text, canUndo = true) }
                }
            } finally {
                moveInProgress = false
            }
        }
    }

    /** Rimette com'era: le voci tornano in lista e gli articoli escono dall'inventario. */
    fun undoLastMove() {
        val move = lastMove
        // Il messaggio che offriva l'annullamento ha finito il suo turno, comunque vada:
        // consumandolo qui la schermata non deve accoppiare questa chiamata a
        // onMessageShown() in un ordine preciso.
        lastMove = null
        feedback.update { Feedback() }
        if (move == null) return
        viewModelScope.launch {
            // Come nello spostamento, la sequenza va conclusa: NonCancellable la protegge
            // dalla morte di viewModelScope quando si lascia la schermata.
            withContext(NonCancellable) {
                // Prima la lista, poi il frigo, cioe' l'inverso dell'ordine in cui si legge
                // la frase: se la sequenza si spezza a meta' la spesa risulta in entrambi i
                // posti invece che in nessuno dei due. Rimettere prima le due righe "in
                // ordine di racconto" e' la tentazione da non assecondare.
                move.shoppingItems.forEach { shoppingRepository.restore(it) }
                move.createdFood.forEach { foodRepository.remove(it, RemovalReason.ERRORE) }
                feedback.update { it.copy(message = "Spostamento annullato", canUndo = false) }
            }
        }
    }

    /**
     * Il messaggio e' stato mostrato: con lui scade anche l'annullamento, che vive quanto
     * lo snackbar e non quanto il ViewModel.
     */
    fun onMessageShown() {
        lastMove = null
        feedback.update { Feedback() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

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
