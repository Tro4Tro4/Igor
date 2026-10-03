package com.igor.fridge.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.Transactor
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
import kotlinx.coroutines.CancellationException
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

/** L'ultima operazione annullabile: come disfarla e cosa dire dopo. */
private class PendingUndo(val done: String, val action: suspend () -> Unit)

class ShoppingViewModel(
    private val shoppingRepository: ShoppingRepository,
    private val foodRepository: FoodRepository,
    private val photoStore: PhotoStore,
    categoryOrder: Flow<List<FoodCategory>> = flowOf(FoodCategory.entries),
    private val lastPrice: suspend (String) -> PriceRecord? = { null },
    private val transactor: Transactor = Transactor.Direct,
) : ViewModel() {

    private val storeFilter = MutableStateFlow<String?>(null)

    // Vive in memoria quanto lo snackbar: un annullamento che sopravvive alla schermata
    // non e' quello che l'utente si aspetta, e non vale una colonna in database.
    private var pendingUndo: PendingUndo? = null

    private val feedback = MutableStateFlow(Feedback())
    private var nextMessageId = 0L

    private data class Feedback(
        val message: String? = null,
        val canUndo: Boolean = false,
        val id: Long = 0,
    )

    /**
     * Un messaggio nuovo, con il suo annullamento se c'e'. L'id cambia ogni volta: due
     * spostamenti con lo stesso testo mostrano due snackbar, e "Annulla" disfa l'ultimo.
     */
    private fun show(message: String, undo: PendingUndo? = null) {
        pendingUndo = undo
        feedback.value = Feedback(message, undo != null, ++nextMessageId)
    }

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
                messageId = feedback.id,
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

    /** Un tocco accidentale sul cestino si annulla dallo snackbar. */
    fun delete(item: ShoppingItem) {
        viewModelScope.launch {
            shoppingRepository.delete(item)
            show(
                "Eliminato: ${item.name}",
                PendingUndo(done = "Ripristinato: ${item.name}") { shoppingRepository.restore(item) },
            )
        }
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
                // Le scritture sono 2N: si fanno in una sola transazione, cosi' un errore
                // o la morte del processo a meta' non lasciano articoli creati con le voci
                // ancora spuntate (che al tentativo successivo entrerebbero due volte).
                // NonCancellable porta a termine la transazione anche se la schermata se
                // ne e' gia' andata. Le voci spuntate si rileggono dal database dentro la
                // transazione: un secondo tocco ravvicinato le trova gia' tolte.
                val move = withContext(NonCancellable) {
                    transactor.run { moveChecked(expiries) }
                }
                if (move == null) show("Nessun prodotto spuntato") else show(move.message, move.undo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show("Spostamento non riuscito: riprova")
            } finally {
                moveInProgress = false
            }
        }
    }

    private class MoveOutcome(val undo: PendingUndo, val message: String)

    private suspend fun moveChecked(expiries: Map<String, LocalDate>): MoveOutcome? {
        val checked = shoppingRepository.checkedItems()
        if (checked.isEmpty()) return null
        // Entra in frigo quanto e' stato preso; cio' che non e' un alimento esce soltanto
        // dalla lista.
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
        // Dopo un acquisto parziale la voce resta, per la parte mancante. L'annullamento
        // la rimette com'era con restore(), che sovrascrive.
        var remainders = 0
        val removedNonFood = nonFood.filter { it.remainingAfterPurchase == null }
        checked.forEach { item ->
            val remaining = item.remainingAfterPurchase
            if (remaining != null) {
                shoppingRepository.keepRemainder(item, remaining)
                remainders++
            } else {
                shoppingRepository.delete(item)
            }
        }
        return MoveOutcome(
            undo = PendingUndo(done = "Spostamento annullato") {
                checked.forEach { shoppingRepository.restore(it) }
                created.forEach { foodRepository.remove(it, RemovalReason.ERRORE) }
            },
            message = moveMessage(food = food, nonFood = removedNonFood, remainders = remainders),
        )
    }

    /**
     * Disfa l'ultima operazione annullabile: uno spostamento in frigo (le voci tornano in
     * lista e gli articoli escono dall'inventario) o un'eliminazione.
     */
    fun undoLastMove() {
        val undo = pendingUndo
        // Il messaggio che offriva l'annullamento ha finito il suo turno, comunque vada:
        // consumandolo qui la schermata non deve accoppiare questa chiamata a
        // onMessageShown() in un ordine preciso.
        pendingUndo = null
        feedback.update { Feedback() }
        if (undo == null) return
        viewModelScope.launch {
            try {
                // Come lo spostamento: una transazione, portata a termine anche se si lascia
                // la schermata.
                withContext(NonCancellable) { transactor.run { undo.action() } }
                show(undo.done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show("Annullamento non riuscito")
            }
        }
    }

    /**
     * Il messaggio e' stato mostrato: con lui scade anche l'annullamento, che vive quanto
     * lo snackbar e non quanto il ViewModel.
     */
    fun onMessageShown() {
        pendingUndo = null
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
            // Solo voci comprate in parte: nulla e' entrato in frigo ne' uscito dalla lista.
            if (food.isEmpty() && nonFood.isEmpty()) {
                return if (remainders == 1) {
                    "1 voce resta in lista per la parte mancante"
                } else {
                    "$remainders voci restano in lista per la parte mancante"
                }
            }
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
                    transactor = container.transactor,
                )
            }
        }
    }
}
