package com.igor.fridge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.domain.moved
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ordine delle corsie della lista della spesa, da adattare al proprio supermercato.
 * Ogni spostamento si salva subito: non c'e' un "Salva" da dimenticare.
 */
class AisleOrderViewModel(
    order: Flow<List<FoodCategory>>,
    private val save: suspend (List<FoodCategory>) -> Unit,
) : ViewModel() {

    val order: StateFlow<List<FoodCategory>> = order.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FoodCategory.entries,
    )

    fun move(category: FoodCategory, delta: Int) {
        val next = order.value.moved(category, delta)
        if (next != order.value) viewModelScope.launch { save(next) }
    }

    fun reset() {
        viewModelScope.launch { save(FoodCategory.entries) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val settings = igorApplication().container.settingsStore
                AisleOrderViewModel(
                    order = settings.categoryOrder,
                    save = settings::setCategoryOrder,
                )
            }
        }
    }
}
