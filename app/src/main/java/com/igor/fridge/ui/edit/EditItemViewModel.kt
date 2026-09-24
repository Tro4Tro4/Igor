package com.igor.fridge.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
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
    val uuid: String = NEW_ITEM_UUID,
    val name: String = "",
    val barcode: String? = null,
    val category: FoodCategory = FoodCategory.ALTRO,
    val location: StorageLocation = StorageLocation.FRIGO,
    val quantityText: String = "1",
    val unit: QuantityUnit = QuantityUnit.PZ,
    val expiryDate: LocalDate? = null,
    val notes: String = "",
    val addedAt: LocalDate = LocalDate.now(),
    val nameError: Boolean = false,
    val quantityError: Boolean = false,
    val message: String? = null,
    val isSaved: Boolean = false,
) {
    val isNew: Boolean get() = uuid == NEW_ITEM_UUID
}

/** Identificatore convenzionale per un articolo non ancora salvato. */
const val NEW_ITEM_UUID: String = "new"

class EditItemViewModel(
    private val itemUuid: String,
    private val repository: FoodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditItemUiState(uuid = itemUuid))
    val uiState: StateFlow<EditItemUiState> = _uiState.asStateFlow()

    init {
        if (itemUuid != NEW_ITEM_UUID) {
            viewModelScope.launch {
                repository.findByUuid(itemUuid)?.let { item ->
                    _uiState.value = EditItemUiState(
                        uuid = item.uuid,
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

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = false) }

    fun onCategoryChange(value: FoodCategory) = _uiState.update { it.copy(category = value) }

    fun onLocationChange(value: StorageLocation) = _uiState.update { it.copy(location = value) }

    fun onQuantityChange(value: String) =
        _uiState.update { it.copy(quantityText = value, quantityError = false) }

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
                    nameError = name.isEmpty(),
                    quantityError = quantity == null || quantity <= 0.0,
                )
            }
            return
        }

        viewModelScope.launch {
            repository.save(
                FoodItem(
                    uuid = if (state.isNew) "" else state.uuid,
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
        val state = _uiState.value
        if (state.isNew) {
            _uiState.update { it.copy(isSaved = true) }
            return
        }
        viewModelScope.launch {
            repository.findByUuid(state.uuid)?.let { repository.remove(it, RemovalReason.ERRORE) }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    companion object {
        fun factory(itemUuid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditItemViewModel(
                    itemUuid = itemUuid,
                    repository = igorApplication().container.foodRepository,
                )
            }
        }

        private fun formatQuantityInput(quantity: Double): String =
            if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()
    }
}
