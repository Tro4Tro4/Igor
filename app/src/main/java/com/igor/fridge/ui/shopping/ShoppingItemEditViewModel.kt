package com.igor.fridge.ui.shopping

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.formatQuantityInput
import com.igor.fridge.ui.formatPriceInput
import com.igor.fridge.ui.igorApplication
import com.igor.fridge.ui.parsePriceCents
import com.igor.fridge.ui.parseQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ShoppingItemEditUiState(
    val isLoaded: Boolean = false,
    val name: String = "",
    val quantityText: String = "1",
    val unit: QuantityUnit = QuantityUnit.PZ,
    val category: FoodCategory = FoodCategory.ALTRO,
    val brand: String = "",
    val notes: String = "",
    val purchasedText: String = "",
    val photoName: String? = null,
    val priceText: String = "",
    val store: String = "",
    /** Negozi gia' usati nella lista, da proporre. */
    val knownStores: List<String> = emptyList(),
    /** L'ultimo prezzo pagato per questo prodotto, letto dagli scontrini. */
    val lastPaid: PriceRecord? = null,
    val isImportingPhoto: Boolean = false,
    val nameError: Boolean = false,
    val quantityError: Boolean = false,
    val purchasedError: Boolean = false,
    val priceError: Boolean = false,
    val message: String? = null,
    val isDone: Boolean = false,
)

/**
 * Modifica i dettagli di una voce della lista: quanto e cosa comprare (quantita', unita',
 * marca, note, foto, categoria, prezzo, negozio) e quanto e' stato preso.
 */
class ShoppingItemEditViewModel(
    private val itemUuid: String,
    private val repository: ShoppingRepository,
    private val photoStore: PhotoStore,
    private val lastPrice: suspend (String) -> PriceRecord? = { null },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingItemEditUiState())
    val uiState: StateFlow<ShoppingItemEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val item = repository.findByUuid(itemUuid)
            if (item == null) {
                // La voce e' uscita dalla lista mentre si apriva la schermata.
                _uiState.update { it.copy(isDone = true) }
                return@launch
            }
            _uiState.value = ShoppingItemEditUiState(
                isLoaded = true,
                name = item.name,
                quantityText = formatQuantityInput(item.quantity),
                unit = item.unit,
                category = item.category,
                brand = item.brand.orEmpty(),
                notes = item.notes.orEmpty(),
                purchasedText = item.purchasedQuantity?.let(::formatQuantityInput).orEmpty(),
                photoName = item.photoPath,
                priceText = item.unitPriceCents?.let(::formatPriceInput).orEmpty(),
                store = item.store.orEmpty(),
                knownStores = repository.currentItems()
                    .mapNotNull { it.store?.trim()?.takeIf(String::isNotEmpty) }
                    .distinctBy { it.lowercase() }
                    .sortedWith(String.CASE_INSENSITIVE_ORDER),
                lastPaid = lastPrice(item.name),
            )
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = false) }

    fun onQuantityChange(value: String) =
        _uiState.update { it.copy(quantityText = value, quantityError = false) }

    /** I pulsanti + e - cambiano di un'unita', senza scendere sotto 1. */
    fun stepQuantity(delta: Int) = _uiState.update { state ->
        val current = parseQuantity(state.quantityText) ?: 1.0
        val next = (current + delta).coerceAtLeast(1.0)
        state.copy(quantityText = formatQuantityInput(next), quantityError = false)
    }

    fun onUnitChange(value: QuantityUnit) = _uiState.update { it.copy(unit = value) }

    fun onCategoryChange(value: FoodCategory) = _uiState.update { it.copy(category = value) }

    fun onBrandChange(value: String) = _uiState.update { it.copy(brand = value) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onPriceChange(value: String) = _uiState.update { it.copy(priceText = value, priceError = false) }

    /**
     * Usa l'ultimo prezzo pagato: anche l'unita' passa a quella del prezzo (€/kg, €/l),
     * altrimenti il numero non vorrebbe dire nulla.
     */
    fun useLastPaid() = _uiState.update { state ->
        val paid = state.lastPaid ?: return@update state
        // 500 g diventano 0,5 kg: cambiare l'unita' senza convertire la quantita' farebbe
        // stimare 500 kg.
        val quantity = parseQuantity(state.quantityText)
        val converted = when {
            quantity == null -> null
            state.unit == QuantityUnit.G && paid.referenceUnit == QuantityUnit.KG -> quantity / 1000.0
            state.unit == QuantityUnit.ML && paid.referenceUnit == QuantityUnit.L -> quantity / 1000.0
            else -> quantity
        }
        state.copy(
            priceText = formatPriceInput(paid.unitPriceCents),
            unit = paid.referenceUnit,
            quantityText = converted?.let(::formatQuantityInput) ?: state.quantityText,
            priceError = false,
        )
    }

    fun onStoreChange(value: String) = _uiState.update { it.copy(store = value) }

    fun onPurchasedChange(value: String) =
        _uiState.update { it.copy(purchasedText = value, purchasedError = false) }

    /** "Preso tutto": la quantita' presa diventa quella da comprare. */
    fun purchaseAll() = _uiState.update {
        it.copy(purchasedText = it.quantityText, purchasedError = false)
    }

    fun onCameraUnavailable() = _uiState.update {
        it.copy(message = "Nessuna app fotocamera disponibile: scegli la foto dalla galleria")
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun photoFile(name: String): File = photoStore.fileOf(name)

    fun newCaptureUri(): Uri = photoStore.newCaptureUri()

    /** Importa la foto scattata o scelta dalla galleria; la voce cambia solo al salvataggio. */
    fun onPhotoChosen(source: Uri) {
        _uiState.update { it.copy(isImportingPhoto = true) }
        viewModelScope.launch {
            val name = photoStore.import(source)
            _uiState.update {
                if (name == null) {
                    it.copy(isImportingPhoto = false, message = "Impossibile leggere la foto")
                } else {
                    it.copy(isImportingPhoto = false, photoName = name)
                }
            }
        }
    }

    /**
     * Toglie la foto dalla voce. Il file resta su disco finche' la pulizia all'avvio non
     * verifica che nessuno lo usi piu': una lista salvata potrebbe ancora volerlo.
     */
    fun removePhoto() = _uiState.update { it.copy(photoName = null) }

    fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        val quantity = parseQuantity(state.quantityText)
        val purchasedBlank = state.purchasedText.isBlank()
        val purchased = if (purchasedBlank) null else parseQuantity(state.purchasedText)
        val purchasedInvalid = !purchasedBlank && purchased == null
        val priceBlank = state.priceText.isBlank()
        val price = if (priceBlank) null else parsePriceCents(state.priceText)
        val priceInvalid = !priceBlank && price == null

        if (name.isEmpty() || quantity == null || purchasedInvalid || priceInvalid) {
            _uiState.update {
                it.copy(
                    nameError = name.isEmpty(),
                    quantityError = quantity == null,
                    purchasedError = purchasedInvalid,
                    priceError = priceInvalid,
                )
            }
            return
        }

        viewModelScope.launch {
            // Si parte dalla riga in database, come per l'inventario: cio' che la schermata
            // non mostra (spunta, data di creazione) non deve tornare al default.
            val base = repository.findByUuid(itemUuid)
            if (base != null) {
                repository.update(
                    base.copy(
                        name = name,
                        quantity = quantity,
                        unit = state.unit,
                        category = state.category,
                        brand = state.brand.trim().ifEmpty { null },
                        notes = state.notes.trim().ifEmpty { null },
                        photoPath = state.photoName,
                        purchasedQuantity = purchased,
                        unitPriceCents = price,
                        store = state.store.trim().ifEmpty { null },
                        // Indicare quanto si e' preso vuol dire che il prodotto e' nel carrello.
                        isChecked = base.isChecked || purchased != null,
                    ),
                )
            }
            _uiState.update { it.copy(isDone = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.findByUuid(itemUuid)?.let { repository.delete(it) }
            _uiState.update { it.copy(isDone = true) }
        }
    }

    companion object {
        fun factory(itemUuid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                ShoppingItemEditViewModel(
                    itemUuid = itemUuid,
                    repository = container.shoppingRepository,
                    photoStore = container.photoStore,
                    lastPrice = container.priceRepository::latest,
                )
            }
        }


    }
}
