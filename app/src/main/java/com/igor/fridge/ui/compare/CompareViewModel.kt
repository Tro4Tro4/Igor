package com.igor.fridge.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.prices.Alternative
import com.igor.fridge.domain.prices.ListComparison
import com.igor.fridge.domain.prices.cheaperAlternative
import com.igor.fridge.domain.prices.compareList
import com.igor.fridge.domain.prices.ownSimilarOffers
import com.igor.fridge.domain.prices.productKey
import com.igor.fridge.domain.prices.recordsFor
import com.igor.fridge.domain.prices.toObservation
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class CompareUiState(
    val comparison: ListComparison? = null,
    val itemCount: Int = 0,
    val alternatives: Map<String, Alternative> = emptyMap(),
    val isLoading: Boolean = true,
)

/** Stima la lista dai prezzi pagati: nessuna fonte o richiesta di rete. */
class CompareViewModel(
    shoppingRepository: ShoppingRepository,
    priceRepository: PriceRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<CompareUiState> = combine(
        shoppingRepository.observeAll(), priceRepository.observeAll(),
    ) { items, records ->
        val list = items.filterNot { it.isChecked }
        val mineByItem = list.associate { it.uuid to recordsFor(it, records) }
        val comparison = compareList(list) { item ->
            mineByItem.getValue(item.uuid).mapNotNull { it.toObservation() }
        }
        val alternatives = list.mapNotNull { item ->
            val mine = mineByItem.getValue(item.uuid)
            val baselines = mine.filter { it.referenceUnit == QuantityUnit.KG || it.referenceUnit == QuantityUnit.L }
                .map { it.unitPriceCents to it.referenceUnit }
            val own = mine.toHashSet()
            val offers = ownSimilarOffers(item.name, productKey(item.name), records.filterNot { it in own })
            cheaperAlternative(offers, baselines)?.let { item.uuid to it }
        }.toMap()
        CompareUiState(comparison = comparison, itemCount = list.size, alternatives = alternatives, isLoading = false)
    }.flowOn(computeDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                CompareViewModel(container.shoppingRepository, container.priceRepository)
            }
        }
    }
}
