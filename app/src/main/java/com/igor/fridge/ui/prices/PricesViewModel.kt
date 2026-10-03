package com.igor.fridge.ui.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.domain.prices.ProductPriceSummary
import com.igor.fridge.domain.prices.monthlySpending
import com.igor.fridge.domain.prices.summarize
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class PricesUiState(
    val products: List<ProductPriceSummary> = emptyList(),
    val monthly: List<Pair<YearMonth, Long>> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && products.isEmpty() && query.isBlank()
}

/** Elenco dei prodotti con il loro prezzo e la spesa di ogni mese. */
class PricesViewModel(priceRepository: PriceRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<PricesUiState> =
        combine(priceRepository.observeAll(), query) { records, query ->
            val needle = query.trim()
            PricesUiState(
                products = summarize(records).filter {
                    needle.isEmpty() || it.productName.contains(needle, ignoreCase = true)
                },
                monthly = monthlySpending(records),
                query = query,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PricesUiState(),
        )

    fun onQueryChange(value: String) = query.update { value }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PricesViewModel(igorApplication().container.priceRepository) }
        }
    }
}

data class PriceHistoryUiState(
    val summary: ProductPriceSummary? = null,
    /** Dal piu' recente. */
    val records: List<PriceRecord> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** Gli acquisti confrontabili con l'ultimo, dal piu' vecchio: i punti del grafico. */
    val chartPoints: List<PriceRecord>
        get() = summary?.let { s -> records.filter { it.referenceUnit == s.referenceUnit }.reversed() }.orEmpty()
}

/** La storia dei prezzi di un prodotto. */
class PriceHistoryViewModel(
    productKey: String,
    private val priceRepository: PriceRepository,
) : ViewModel() {

    private val history: Flow<List<PriceRecord>> = priceRepository.observeProduct(productKey)

    val uiState: StateFlow<PriceHistoryUiState> = history.map { records ->
        PriceHistoryUiState(
            summary = summarize(records).firstOrNull(),
            records = records,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PriceHistoryUiState(),
    )

    /** Toglie un prezzo letto male: falserebbe minimo, media e andamento. */
    fun delete(record: PriceRecord) {
        viewModelScope.launch { priceRepository.delete(record) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(productKey: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PriceHistoryViewModel(productKey, igorApplication().container.priceRepository)
            }
        }
    }
}
