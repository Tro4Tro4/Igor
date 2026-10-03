package com.igor.fridge.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.remainingAfterPurchase
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.parseQuickEntry
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Cosa serve per annullare l'ultimo spostamento in frigo. */
private data class LastMove(
    val shoppingItems: List<ShoppingItem>,
    val createdFood: List<FoodItem>,
)

class ShoppingViewModel(
    private val shoppingRepository: ShoppingRepository,
    private val foodRepository: FoodRepository,
    private val photoStore: PhotoStore,
    categoryOrder: Flow<List<FoodCategory>> = flowOf(FoodCategory.entries),
    private val lastPrice: suspend (String) -> PriceRecord? = { null },
) : ViewModel() {

    private val storeFilter = MutableStateFlow<String?>(null)

    // Vive in memoria quanto lo snackbar: un annullamento che sopravvive alla schermata
    // non e' quello che l'utente si aspetta, e non vale una colonna in database.
    private var lastMove: LastMove? = null

    private val feedback = MutableStateFlow(Feedback())

    private data class Feedback(val message: String? = null, val canUndo: Boolean = false)

    // Impedisce a una seconda pressione, prima che la lista riemetta senza le voci spuntate,
    // di rileggere le stesse voci e creare un secondo articolo per lo stesso acquisto.
    private var moveInProgress = false

    val uiState: StateFlow<ShoppingUiState> =
        combine(
            shoppingRepository.observeAll(),
            feedback,
            categoryOrder,
            storeFilter,
        ) { items, feedback, order, store ->
            ShoppingUiState(
                items = items,
                isLoading = false,
                message = feedback.message,
                canUndo = feedback.canUndo,
                categoryOrder = order,
                storeFilter = store,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ShoppingUiState(),
        )

    /**
     * Aggiunge una voce dalla riga rapida: "2 kg mele" diventa mele, 2 kg. La categoria e'
     * quella che il prodotto ha avuto per ultimo in inventario, se c'e' passato e non era
     * "Altro"; altrimenti la propone il repository a partire dal nome. Con un negozio
     * scelto nel filtro, la voce nasce per quel negozio: e' li' che la si sta scrivendo.
     * Il prezzo e' l'ultimo pagato per quel prodotto, se e' espresso nella stessa unita'.
     */
    fun add(text: String) {
        val entry = parseQuickEntry(text)
        if (entry.name.isBlank()) return
        val store = uiState.value.activeStore
        viewModelScope.launch {
            val known = foodRepository.findLastByName(entry.name)?.category
                ?.takeIf { it != FoodCategory.ALTRO }
            val paid = lastPrice(entry.name)
            val unit = entry.unit ?: paid?.referenceUnit ?: QuantityUnit.PZ
            shoppingRepository.addIfAbsent(
                name = entry.name,
                quantity = entry.quantity ?: 1.0,
                unit = unit,
                category = known,
                unitPriceCents = paid?.takeIf { it.referenceUnit == unit }?.unitPriceCents,
                store = store,
            )
        }
    }

    /** Mostra solo le voci di [store] (e quelle senza negozio); null mostra tutto. */
    fun setStoreFilter(store: String?) = storeFilter.update { store }

    /** La lista da condividere, cosi' come la si vede (filtro compreso). */
    fun shareText(): String =
        uiState.value.let { com.igor.fridge.ui.shopping.shareText(it.toBuy, it.totals) }

    fun photoFile(name: String): File = photoStore.fileOf(name)

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
     *
     * [expiries] sono le scadenze indicate dall'utente per i prodotti freschi, per uuid
     * della voce; chi non ne ha una entra senza data, come prima.
     */
    fun moveCheckedToInventory(expiries: Map<String, LocalDate> = emptyMap()) {
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
                    // entrambi i posti, mai in nessuno dei due. Entra in frigo quanto e'
                    // stato preso; cio' che non e' un alimento esce soltanto dalla lista.
                    val (food, nonFood) = checked.partition { it.category.isFood }
                    val created = food.map { item ->
                        foodRepository.addFromShopping(
                            name = item.name,
                            quantity = item.purchasedQuantity ?: item.quantity,
                            unit = item.unit,
                            category = item.category,
                            brand = item.brand,
                            expiryDate = expiries[item.uuid],
                        )
                    }
                    // Dopo un acquisto parziale la voce resta, per la parte mancante.
                    // L'annullamento la rimette com'era con restore(), che sovrascrive.
                    var remainders = 0
                    checked.forEach { item ->
                        val remaining = item.remainingAfterPurchase
                        if (remaining != null) {
                            shoppingRepository.keepRemainder(item, remaining)
                            remainders++
                        } else {
                            shoppingRepository.delete(item)
                        }
                    }

                    lastMove = LastMove(shoppingItems = checked, createdFood = created)
                    val text = moveMessage(food = food, nonFood = nonFood, remainders = remainders)
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

        /** "Aggiunto in frigo: Latte", "2 prodotti messi in frigo, 1 tolto dalla lista"... */
        internal fun moveMessage(
            food: List<ShoppingItem>,
            nonFood: List<ShoppingItem>,
            remainders: Int,
        ): String {
            val main = when {
                nonFood.isEmpty() && food.size == 1 -> "Aggiunto in frigo: ${food.single().name}"
                nonFood.isEmpty() -> "${food.size} prodotti messi in frigo"
                food.isEmpty() && nonFood.size == 1 -> "Tolto dalla lista: ${nonFood.single().name}"
                food.isEmpty() -> "${nonFood.size} prodotti tolti dalla lista"
                else -> {
                    val inFridge = if (food.size == 1) {
                        "1 prodotto messo in frigo"
                    } else {
                        "${food.size} prodotti messi in frigo"
                    }
                    val removed = if (nonFood.size == 1) {
                        "1 tolto dalla lista"
                    } else {
                        "${nonFood.size} tolti dalla lista"
                    }
                    "$inFridge, $removed"
                }
            }
            return when (remainders) {
                0 -> main
                1 -> "$main; 1 resta in lista per la parte mancante"
                else -> "$main; $remainders restano in lista per la parte mancante"
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                ShoppingViewModel(
                    shoppingRepository = container.shoppingRepository,
                    foodRepository = container.foodRepository,
                    photoStore = container.photoStore,
                    categoryOrder = container.settingsStore.categoryOrder,
                    lastPrice = container.priceRepository::latest,
                )
            }
        }
    }
}
