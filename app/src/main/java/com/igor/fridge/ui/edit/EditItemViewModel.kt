package com.igor.fridge.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.Transactor
import com.igor.fridge.ui.parseQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
import com.igor.fridge.data.openfoodfacts.BarcodeProduct
import com.igor.fridge.data.openfoodfacts.ProductLookupException
import com.igor.fridge.domain.prices.normalizeGtin
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

enum class BarcodeLookupStatus { IDLE, LOADING, FOUND, INCOMPLETE, NOT_FOUND, ERROR, RATE_LIMITED, DISABLED, UNSUPPORTED }

data class EditItemUiState(
    val uuid: String = NEW_ITEM_UUID,
    val name: String = "",
    val brand: String = "",
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
    val isSaving: Boolean = false,
    val isLoaded: Boolean = true,
    val barcodeLookup: BarcodeLookupStatus = BarcodeLookupStatus.IDLE,
) {
    val isNew: Boolean get() = uuid == NEW_ITEM_UUID
}

/** Identificatore convenzionale per un articolo non ancora salvato. */
const val NEW_ITEM_UUID: String = "new"

class EditItemViewModel(
    private val itemUuid: String,
    private val repository: FoodRepository,
    private val transactor: Transactor = Transactor.Direct,
    private val onlineEnabled: suspend () -> Boolean = { false },
    private val fetchProduct: suspend (String) -> BarcodeProduct? = { null },
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditItemUiState(uuid = itemUuid, isLoaded = itemUuid == NEW_ITEM_UUID))
    val uiState: StateFlow<EditItemUiState> = _uiState.asStateFlow()

    private var categoryChosen = itemUuid != NEW_ITEM_UUID
    private var unitChosen = itemUuid != NEW_ITEM_UUID
    private var locationChosen = itemUuid != NEW_ITEM_UUID
    private var loaded = itemUuid == NEW_ITEM_UUID
    private var scanId = 0L
    private var scanJob: Job? = null
    private var nameChosen = itemUuid != NEW_ITEM_UUID
    private var brandChosen = itemUuid != NEW_ITEM_UUID

    init {
        if (itemUuid != NEW_ITEM_UUID) {
            viewModelScope.launch {
                repository.findByUuid(itemUuid)?.let { item ->
                    _uiState.value = EditItemUiState(
                        uuid = item.uuid,
                        name = item.name,
                        brand = item.brand.orEmpty(),
                        barcode = item.barcode,
                        category = item.category,
                        location = item.location,
                        quantityText = formatQuantityInput(item.quantity),
                        unit = item.unit,
                        expiryDate = item.expiryDate,
                        notes = item.notes.orEmpty(),
                        addedAt = item.addedAt,
                    )
                    loaded = true
                }
            }
        }
    }

    fun onNameChange(value: String) {
        nameChosen = true
        _uiState.update { it.copy(name = value, nameError = false) }
    }

    fun onBrandChange(value: String) {
        brandChosen = true
        _uiState.update { it.copy(brand = value) }
    }

    fun onCategoryChange(value: FoodCategory) {
        categoryChosen = true; _uiState.update { it.copy(category = value) }
    }

    fun onLocationChange(value: StorageLocation) {
        locationChosen = true; _uiState.update { it.copy(location = value) }
    }

    fun onQuantityChange(value: String) =
        _uiState.update { it.copy(quantityText = value, quantityError = false) }

    fun onUnitChange(value: QuantityUnit) {
        unitChosen = true; _uiState.update { it.copy(unit = value) }
    }

    fun onExpiryDateChange(value: LocalDate?) = _uiState.update { it.copy(expiryDate = value) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /**
     * Applica il codice letto dallo scanner. Se lo stesso barcode e' gia' stato inserito
     * in passato, riusa nome, categoria e unita' per evitare di ridigitarli.
     */
    fun onBarcodeScanned(barcode: String) {
        if (_uiState.value.isSaving || _uiState.value.isSaved || !loaded) return
        val id = ++scanId
        scanJob?.cancel()
        _uiState.update { it.copy(barcode = barcode, barcodeLookup = BarcodeLookupStatus.IDLE) }
        scanJob = viewModelScope.launch {
            try {
                val known = repository.findLastByBarcode(barcode)
                if (id != scanId) return@launch
                _uiState.update { state ->
                    if (known == null) {
                        state.copy(barcode = barcode, message = null)
                    } else {
                        state.copy(
                            barcode = barcode,
                            name = if (nameChosen) state.name else known.name,
                            brand = if (brandChosen) state.brand else known.brand.orEmpty(),
                            category = if (categoryChosen) state.category else known.category,
                            unit = if (unitChosen) state.unit else known.unit,
                            location = if (locationChosen) state.location else known.location,
                            message = "Riconosciuto: ${known.name}",
                        )
                    }
                }
                if (known != null) return@launch
                if (!onlineEnabled()) {
                    _uiState.update { it.copy(barcodeLookup = BarcodeLookupStatus.DISABLED) }
                    return@launch
                }
                if (normalizeGtin(barcode) == null) {
                    _uiState.update { it.copy(barcodeLookup = BarcodeLookupStatus.UNSUPPORTED) }
                    return@launch
                }
                _uiState.update { it.copy(barcodeLookup = BarcodeLookupStatus.LOADING, message = null) }
                val product = withTimeout(10_000) { fetchProduct(barcode) }
                if (id != scanId || _uiState.value.isSaving || _uiState.value.isSaved) return@launch
                // Il consenso puo' essere cambiato mentre la richiesta era in corso.
                if (!onlineEnabled()) {
                    _uiState.update { it.copy(barcodeLookup = BarcodeLookupStatus.DISABLED) }
                    return@launch
                }
                _uiState.update { state ->
                    if (product == null) state.copy(barcodeLookup = BarcodeLookupStatus.NOT_FOUND)
                    else state.copy(
                        name = if (nameChosen) state.name else product.name,
                        brand = if (brandChosen) state.brand else product.brand,
                        category = if (categoryChosen || product.category == FoodCategory.ALTRO) state.category else product.category,
                        nameError = if (!nameChosen && product.name.isNotBlank()) false else state.nameError,
                        barcodeLookup = if (product.name.isBlank()) BarcodeLookupStatus.INCOMPLETE else BarcodeLookupStatus.FOUND,
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (id == scanId) _uiState.update { it.copy(barcodeLookup = BarcodeLookupStatus.ERROR) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (id == scanId) _uiState.update { it.copy(barcodeLookup =
                    if (e is ProductLookupException && e.rateLimited) BarcodeLookupStatus.RATE_LIMITED else BarcodeLookupStatus.ERROR) }
            }
        }
    }

    fun retryBarcodeLookup() { _uiState.value.barcode?.let(::onBarcodeScanned) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isSaved || !loaded) return
        val name = state.name.trim()
        val quantity = parseQuantity(state.quantityText)

        if (name.isEmpty() || quantity == null || quantity <= 0.0) {
            _uiState.update {
                it.copy(
                    nameError = name.isEmpty(),
                    quantityError = quantity == null || quantity <= 0.0,
                )
            }
            return
        }

        ++scanId
        scanJob?.cancel()
        _uiState.update { it.copy(isSaving = true, barcodeLookup = BarcodeLookupStatus.IDLE) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { transactor.run {
                    // Si parte dalla riga in database e se ne copiano i campi modificati, invece di
                    // ricostruire un FoodItem da zero: cio' che non passa dallo stato della schermata
                    // (removedAt, removalReason, e ogni colonna che verra' aggiunta in futuro)
                    // altrimenti tornerebbe al proprio default a ogni salvataggio, riportando in
                    // inventario un articolo rimosso nel frattempo e cancellandone la storia.
                    val base = if (state.isNew) {
                        FoodItem(uuid = "", name = name)
                    } else {
                        repository.findByUuid(state.uuid) ?: error("Articolo non piu' disponibile")
                    }
                    repository.save(
                        base.copy(
                            name = name,
                            brand = state.brand.trim().ifEmpty { null },
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
                } }
                _uiState.update { it.copy(isSaved = true, isSaving = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(isSaving = false,
                message = "Salvataggio non riuscito: riprova") } }
        }
    }

    fun delete() {
        val state = _uiState.value
        if (state.isSaving || state.isSaved || !loaded) return
        ++scanId
        scanJob?.cancel()
        if (state.isNew) {
            _uiState.update { it.copy(isSaved = true, barcodeLookup = BarcodeLookupStatus.IDLE) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { transactor.run {
                    repository.findByUuid(state.uuid)?.let { repository.remove(it, RemovalReason.ERRORE) }
                } }
                    _uiState.update { it.copy(isSaved = true, isSaving = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(isSaving = false,
                message = "Eliminazione non riuscita: riprova") } }
        }
    }

    companion object {
        fun factory(itemUuid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditItemViewModel(
                    itemUuid = itemUuid,
                    repository = igorApplication().container.foodRepository,
                    transactor = igorApplication().container.transactor,
                    onlineEnabled = { igorApplication().container.settingsStore.openFoodFactsEnabled.first() },
                    fetchProduct = igorApplication().container.openFoodFactsClient::product,
                )
            }
        }

        private fun formatQuantityInput(quantity: Double): String =
            if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()
    }
}
