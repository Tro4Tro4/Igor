package com.igor.fridge.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditItemUiState(
    val id: Long = NEW_ITEM_ID,
    val name: String = "",
    val barcode: String? = null,
    val category: FoodCategory = FoodCategory.ALTRO,
    val location: StorageLocation = StorageLocation.FRIGO,
    val quantityText: String = "1",
    val unit: QuantityUnit = QuantityUnit.PZ,
    val expiryDate: LocalDate? = null,
    val notes: String = "",
    val addedAt: LocalDate = LocalDate.now(),
    val nameError: String? = null,
    val quantityError: String? = null,
    val message: String? = null,
    val isSaved: Boolean = false,
) {
    val isNew: Boolean get() = id == NEW_ITEM_ID
}

/** Id convenzionale per un articolo non ancora salvato. */
const val NEW_ITEM_ID: Long = 0L

class EditItemViewModel(
    private val itemId: Long,
    private val repository: FoodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditItemUiState(id = itemId))
    val uiState: StateFlow<EditItemUiState> = _uiState.asStateFlow()

    init {
        if (itemId != NEW_ITEM_ID) {
            viewModelScope.launch {
                repository.findById(itemId)?.let { item ->
                    _uiState.value = EditItemUiState(
                        id = item.id,
                        name = item.name,
                        barcode = item.barcode,
                        category = item.category,
                        location = item.location,
                        quantityText = formatQuantityInput(item.quantity),
                        unit = item.unit,
                        expiryDate = item.expiryDate,
                        notes = item.notes.orEmpty(),
                        addedAt = item.addedAt,
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null) }

    fun onCategoryChange(value: FoodCategory) = _uiState.update { it.copy(category = value) }

    fun onLocationChange(value: StorageLocation) = _uiState.update { it.copy(location = value) }

    fun onQuantityChange(value: String) =
        _uiState.update { it.copy(quantityText = value, quantityError = null) }

    fun onUnitChange(value: QuantityUnit) = _uiState.update { it.copy(unit = value) }

    fun onExpiryDateChange(value: LocalDate?) = _uiState.update { it.copy(expiryDate = value) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /**
     * Applica il codice letto dallo scanner. Se lo stesso barcode e' gia' stato inserito
     * in passato, riusa nome, categoria e unita' per evitare di ridigitarli.
     */
    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val known = repository.findLastByBarcode(barcode)
            _uiState.update { state ->
                if (known == null) {
                    state.copy(barcode = barcode, message = "Codice $barcode: prodotto nuovo")
                } else {
                    state.copy(
                        barcode = barcode,
                        name = state.name.ifBlank { known.name },
                        category = known.category,
                        unit = known.unit,
                        location = known.location,
                        message = "Riconosciuto: ${known.name}",
                    )
                }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        val quantity = state.quantityText.replace(',', '.').trim().toDoubleOrNull()

        if (name.isEmpty() || quantity == null || quantity <= 0.0) {
            _uiState.update {
                it.copy(
                    nameError = if (name.isEmpty()) "Il nome e' obbligatorio" else null,
                    quantityError = if (quantity == null || quantity <= 0.0) {
                        "Inserisci una quantita' maggiore di zero"
                    } else {
                        null
                    },
                )
            }
            return
        }

        viewModelScope.launch {
            repository.save(
                FoodItem(
                    id = state.id,
                    name = name,
                    barcode = state.barcode,
                    category = state.category,
                    location = state.location,
                    quantity = quantity,
                    unit = state.unit,
                    expiryDate = state.expiryDate,
                    addedAt = state.addedAt,
                    notes = state.notes.trim().ifEmpty { null },
                ),
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun delete() {
        val id = _uiState.value.id
        if (id == NEW_ITEM_ID) {
            _uiState.update { it.copy(isSaved = true) }
            return
        }
        viewModelScope.launch {
            repository.deleteById(id)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    companion object {
        fun factory(itemId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditItemViewModel(
                    itemId = itemId,
                    repository = igorApplication().container.foodRepository,
                )
            }
        }

        private fun formatQuantityInput(quantity: Double): String =
            if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()
    }
}
