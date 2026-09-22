package com.igor.fridge.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val items: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    val checkedCount: Int get() = items.count { it.isChecked }
}

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    val uiState: StateFlow<ShoppingUiState> = repository.observeAll()
        .map { ShoppingUiState(items = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ShoppingUiState(),
        )

    fun add(name: String) {
        viewModelScope.launch { repository.addIfAbsent(name) }
    }

    fun setChecked(item: ShoppingItem, checked: Boolean) {
        viewModelScope.launch { repository.setChecked(item, checked) }
    }

    fun delete(item: ShoppingItem) {
        viewModelScope.launch { repository.delete(item) }
    }

    fun clearChecked() {
        viewModelScope.launch { repository.deleteChecked() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ShoppingViewModel(igorApplication().container.shoppingRepository)
            }
        }
    }
}
