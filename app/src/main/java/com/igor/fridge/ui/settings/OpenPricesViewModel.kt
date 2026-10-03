package com.igor.fridge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.openprices.OpenPricesException
import com.igor.fridge.data.openprices.OpenPricesSession
import com.igor.fridge.data.prefs.OpenPricesSettings
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OpenPricesUiState(
    val settings: OpenPricesSettings = OpenPricesSettings(),
    val isLoggingIn: Boolean = false,
    val message: String? = null,
)

/**
 * Attiva Open Prices e gestisce l'accesso. La password passa solo dal campo al server:
 * si conserva il token che il server restituisce.
 */
class OpenPricesViewModel(
    private val settings: Flow<OpenPricesSettings>,
    private val setEnabled: suspend (Boolean) -> Unit,
    private val saveSession: suspend (String?, String?) -> Unit,
    private val authenticate: suspend (String, String) -> OpenPricesSession,
) : ViewModel() {

    private val status = MutableStateFlow(OpenPricesUiState())

    val uiState: StateFlow<OpenPricesUiState> = combine(settings, status) { settings, status ->
        status.copy(settings = settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = OpenPricesUiState(),
    )

    fun onEnabledChange(enabled: Boolean) {
        viewModelScope.launch { setEnabled(enabled) }
    }

    /** Con Open Prices spento nessuna richiesta parte, nemmeno l'accesso. */
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty() || status.value.isLoggingIn) return
        status.update { it.copy(isLoggingIn = true, message = null) }
        viewModelScope.launch {
            if (!settings.first().enabled) {
                status.update { it.copy(isLoggingIn = false) }
                return@launch
            }
            try {
                val session = authenticate(username, password)
                saveSession(session.userId, session.token)
                status.update { it.copy(isLoggingIn = false, message = "Accesso eseguito come ${session.userId}") }
            } catch (e: OpenPricesException) {
                status.update { it.copy(isLoggingIn = false, message = e.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            saveSession(null, null)
            status.update { it.copy(message = "Uscito da Open Prices") }
        }
    }

    fun onMessageShown() = status.update { it.copy(message = null) }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                val store = container.settingsStore
                OpenPricesViewModel(
                    settings = store.openPrices,
                    setEnabled = store::setOpenPricesEnabled,
                    saveSession = store::setOpenPricesSession,
                    authenticate = container.openPricesClient::login,
                )
            }
        }
    }
}
