package com.igor.fridge.ui.receipt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.receipt.ReceiptReader
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.guessCategory
import com.igor.fridge.domain.receipt.ReceiptEntry
import com.igor.fridge.domain.receipt.groupIntoRows
import com.igor.fridge.domain.receipt.parseReceipt
import com.igor.fridge.domain.receipt.receiptMatches
import com.igor.fridge.ui.formatNumber
import com.igor.fridge.ui.igorApplication
import com.igor.fridge.ui.parseQuantity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class ReceiptPhase { CHOOSE, READING, REVIEW, SAVING, DONE }

/** Un prodotto letto dallo scontrino, come l'utente lo sta confermando. */
data class ReceiptDraft(
    val id: Int,
    val include: Boolean,
    val name: String,
    val category: FoodCategory,
    val quantityText: String,
    val unit: QuantityUnit,
    val expiryDate: LocalDate? = null,
    val priceCents: Long? = null,
    /** La quantita' non si legge dallo scontrino e va chiesta (prodotti a peso). */
    val quantityToConfirm: Boolean = false,
    /** La voce della lista della spesa che questo acquisto soddisfa. */
    val shoppingMatch: ShoppingItem? = null,
    /** Categoria e posizione dell'ultima volta che il prodotto e' stato in casa. */
    val knownCategory: FoodCategory? = null,
    val knownLocation: StorageLocation? = null,
    val quantityError: Boolean = false,
    val nameError: Boolean = false,
) {
    /** Un prodotto fresco senza scadenza: la si chiede prima di salvare. */
    val needsExpiry: Boolean get() = include && category.isFood && category.isPerishable && expiryDate == null

    /** In frigo va dove era stato messo l'ultima volta, se e' lo stesso prodotto. */
    val location: StorageLocation
        get() = knownLocation?.takeIf { category == knownCategory } ?: category.defaultLocation
}

data class ReceiptUiState(
    val phase: ReceiptPhase = ReceiptPhase.CHOOSE,
    val drafts: List<ReceiptDraft> = emptyList(),
    val message: String? = null,
    /** Quanti prodotti freschi sono ancora senza scadenza, mentre si chiede se procedere. */
    val missingExpiryPrompt: Int? = null,
    val summary: String? = null,
) {
    val includedCount: Int get() = drafts.count { it.include }
}

/**
 * Dalla foto di uno scontrino all'inventario.
 *
 * Il testo letto diventa una bozza per prodotto; nulla entra in frigo finche' l'utente non
 * conferma. La categoria si decide come altrove: quella dell'ultima volta che il prodotto e'
 * stato in casa, poi quella della voce di lista che lo scontrino soddisfa, poi la proposta
 * dal nome. I prodotti a peso senza peso stampato chiedono la quantita', quelli freschi la
 * scadenza; cio' che non si mangia parte escluso. Confermando, le voci della lista della
 * spesa soddisfatte escono dalla lista.
 */
class ReceiptViewModel(
    private val reader: ReceiptReader,
    private val photoStore: PhotoStore,
    private val foodRepository: FoodRepository,
    private val shoppingRepository: ShoppingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptUiState())
    val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    private var nextId = 0

    fun newCaptureUri(): Uri = photoStore.newCaptureUri()

    fun onImageChosen(source: Uri) {
        _uiState.update { it.copy(phase = ReceiptPhase.READING, message = null) }
        viewModelScope.launch {
            val fragments = reader.read(source)
            if (fragments == null) {
                _uiState.update {
                    it.copy(phase = ReceiptPhase.CHOOSE, message = "Impossibile leggere la foto")
                }
                return@launch
            }
            val entries = parseReceipt(groupIntoRows(fragments))
            if (entries.isEmpty()) {
                _uiState.update {
                    it.copy(
                        phase = ReceiptPhase.CHOOSE,
                        message = "Nessun prodotto riconosciuto: prova con una foto più nitida e dritta",
                    )
                }
                return@launch
            }
            val drafts = buildDrafts(entries)
            _uiState.update { it.copy(phase = ReceiptPhase.REVIEW, drafts = drafts) }
        }
    }

    private suspend fun buildDrafts(entries: List<ReceiptEntry>): List<ReceiptDraft> {
        val shopping = shoppingRepository.currentItems()
        val matched = mutableSetOf<String>()
        return entries.map { entry ->
            val match = shopping.firstOrNull { it.uuid !in matched && receiptMatches(it.name, entry.name) }
            match?.let { matched += it.uuid }
            // Il nome scelto dall'utente per la lista e' piu' leggibile di quello della cassa.
            val name = match?.name ?: entry.name
            val known = foodRepository.findLastByName(name)
            val category = known?.category?.takeIf { it != FoodCategory.ALTRO }
                ?: match?.category?.takeIf { it != FoodCategory.ALTRO }
                ?: guessCategory(entry.name)
            val toConfirm = entry.quantity == null && category.isSoldByWeight
            ReceiptDraft(
                id = nextId++,
                include = category.isFood,
                name = name,
                category = category,
                quantityText = when {
                    entry.quantity != null -> formatNumber(entry.quantity)
                    toConfirm -> ""
                    else -> "1"
                },
                unit = entry.unit ?: if (toConfirm) QuantityUnit.KG else QuantityUnit.PZ,
                priceCents = entry.price?.let { Math.round(it * 100) },
                quantityToConfirm = toConfirm,
                shoppingMatch = match,
                knownCategory = known?.category,
                knownLocation = known?.location,
            )
        }
    }

    fun onIncludeChange(id: Int, include: Boolean) = updateDraft(id) { it.copy(include = include) }

    fun onNameChange(id: Int, name: String) = updateDraft(id) { it.copy(name = name, nameError = false) }

    fun onCategoryChange(id: Int, category: FoodCategory) = updateDraft(id) { it.copy(category = category) }

    fun onQuantityChange(id: Int, text: String) =
        updateDraft(id) { it.copy(quantityText = text, quantityError = false, quantityToConfirm = false) }

    fun onUnitChange(id: Int, unit: QuantityUnit) = updateDraft(id) { it.copy(unit = unit) }

    fun onExpiryChange(id: Int, date: LocalDate?) = updateDraft(id) { it.copy(expiryDate = date) }

    /** Una riga che il riconoscimento ha perso si aggiunge a mano. */
    fun addDraft() = _uiState.update { state ->
        state.copy(
            drafts = state.drafts + ReceiptDraft(
                id = nextId++,
                include = true,
                name = "",
                category = FoodCategory.ALTRO,
                quantityText = "1",
                unit = QuantityUnit.PZ,
            ),
        )
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun dismissMissingExpiry() = _uiState.update { it.copy(missingExpiryPrompt = null) }

    /** Riparte da capo con un'altra foto. */
    fun restart() = _uiState.update { ReceiptUiState() }

    /**
     * Mette in inventario le bozze incluse. Se dei prodotti freschi non hanno scadenza lo
     * chiede prima ([ReceiptUiState.missingExpiryPrompt]); [skipExpiryCheck] e' la risposta
     * "aggiungi comunque".
     */
    fun confirm(skipExpiryCheck: Boolean = false) {
        val state = _uiState.value
        if (state.phase != ReceiptPhase.REVIEW) return
        val checked = state.drafts.map { draft ->
            if (!draft.include) {
                draft
            } else {
                draft.copy(
                    nameError = draft.name.isBlank(),
                    quantityError = parseQuantity(draft.quantityText) == null,
                )
            }
        }
        if (checked.any { it.nameError || it.quantityError }) {
            _uiState.update {
                it.copy(drafts = checked, message = "Completa i nomi e le quantità evidenziati")
            }
            return
        }
        val included = checked.filter { it.include }
        if (included.isEmpty()) {
            _uiState.update { it.copy(message = "Nessun prodotto selezionato") }
            return
        }
        val missing = included.count { it.needsExpiry }
        if (missing > 0 && !skipExpiryCheck) {
            _uiState.update { it.copy(drafts = checked, missingExpiryPrompt = missing) }
            return
        }

        _uiState.update { it.copy(phase = ReceiptPhase.SAVING, missingExpiryPrompt = null) }
        viewModelScope.launch {
            // Come per "Metti in frigo": la sequenza va conclusa anche se si lascia la
            // schermata, altrimenti meta' scontrino resterebbe fuori dal frigo.
            withContext(NonCancellable) {
                included.forEach { draft ->
                    foodRepository.save(
                        FoodItem(
                            uuid = "",
                            name = draft.name.trim(),
                            category = draft.category,
                            location = draft.location,
                            quantity = parseQuantity(draft.quantityText) ?: 1.0,
                            unit = draft.unit,
                            expiryDate = draft.expiryDate,
                        ),
                    )
                }
                val fromList = included.mapNotNull { it.shoppingMatch }
                fromList.forEach { shoppingRepository.delete(it) }

                _uiState.update {
                    it.copy(phase = ReceiptPhase.DONE, summary = summaryOf(included.size, fromList.size))
                }
            }
        }
    }

    private fun updateDraft(id: Int, change: (ReceiptDraft) -> ReceiptDraft) = _uiState.update { state ->
        state.copy(drafts = state.drafts.map { if (it.id == id) change(it) else it })
    }

    companion object {
        internal fun summaryOf(added: Int, fromList: Int): String {
            val first = if (added == 1) "1 prodotto aggiunto in frigo" else "$added prodotti aggiunti in frigo"
            return when (fromList) {
                0 -> first
                1 -> "$first; 1 tolto dalla lista della spesa"
                else -> "$first; $fromList tolti dalla lista della spesa"
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                ReceiptViewModel(
                    reader = container.receiptReader,
                    photoStore = container.photoStore,
                    foodRepository = container.foodRepository,
                    shoppingRepository = container.shoppingRepository,
                )
            }
        }
    }
}
