package com.igor.fridge.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.SavedListSummary
import com.igor.fridge.data.repository.SavedListRepository
import com.igor.fridge.data.repository.SavedListRepository.SaveResult
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedListsUiState(
    val lists: List<SavedListSummary> = emptyList(),
    val currentItemCount: Int = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
)

/**
 * Salva la lista attuale con un nome e ricarica quelle salvate.
 *
 * Caricare *aggiunge* alla lista attuale, con le regole di sempre (niente doppioni, una
 * voce gia' presa torna da comprare), invece di sostituirla: le voci attuali possono
 * essere spuntate e in attesa di "Metti in frigo", e cancellarle perderebbe la spesa.
 */
class SavedListsViewModel(
    private val savedListRepository: SavedListRepository,
    private val shoppingRepository: ShoppingRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SavedListsUiState> =
        combine(
            savedListRepository.observeSummaries(),
            shoppingRepository.observeAll(),
            message,
        ) { lists, current, message ->
            SavedListsUiState(
                lists = lists,
                currentItemCount = current.size,
                isLoading = false,
                message = message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SavedListsUiState(),
        )

    fun saveCurrent(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            val result = savedListRepository.save(trimmed, shoppingRepository.currentItems())
            message.value = when (result) {
                SaveResult.CREATED -> "Lista “$trimmed” salvata"
                SaveResult.REPLACED -> "Lista “$trimmed” aggiornata"
                SaveResult.NOTHING_TO_SAVE -> "La lista della spesa è vuota: non c’è niente da salvare"
            }
        }
    }

    fun load(list: SavedListSummary) {
        viewModelScope.launch {
            val added = shoppingRepository.addAll(savedListRepository.itemsOf(list.uuid))
            message.value = when (added) {
                0 -> "I prodotti di “${list.name}” sono già tutti in lista"
                1 -> "1 prodotto aggiunto da “${list.name}”"
                else -> "$added prodotti aggiunti da “${list.name}”"
            }
        }
    }

    fun delete(list: SavedListSummary) {
        viewModelScope.launch {
            savedListRepository.delete(list.uuid)
            message.value = "Lista “${list.name}” eliminata"
        }
    }

    fun onMessageShown() = message.update { null }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                SavedListsViewModel(
                    savedListRepository = container.savedListRepository,
                    shoppingRepository = container.shoppingRepository,
                )
            }
        }
    }
}
