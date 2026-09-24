package com.igor.fridge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.prefs.SettingsStore
import com.igor.fridge.notification.ExpiryWorkScheduler
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val warningDays: Int = SettingsStore.DEFAULT_WARNING_DAYS,
    val notificationHour: Int = SettingsStore.DEFAULT_NOTIFICATION_HOUR,
    val notificationsEnabled: Boolean = true,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val onScheduleChanged: (enabled: Boolean, hour: Int) -> Unit,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.warningDays,
        settingsStore.notificationHour,
        settingsStore.notificationsEnabled,
    ) { days, hour, enabled ->
        SettingsUiState(days, hour, enabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    fun onWarningDaysChange(value: Int) {
        viewModelScope.launch { settingsStore.setWarningDays(value) }
    }

    /** Cambiare l'ora riprogramma il controllo: il valore salvato da solo non basta. */
    fun onHourChange(value: Int) {
        viewModelScope.launch {
            settingsStore.setNotificationHour(value)
            val enabled = settingsStore.notificationsEnabled.first()
            val hour = settingsStore.notificationHour.first()
            onScheduleChanged(enabled, hour)
        }
    }

    fun onNotificationsToggle(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setNotificationsEnabled(enabled)
            val hour = settingsStore.notificationHour.first()
            onScheduleChanged(enabled, hour)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = igorApplication()
                SettingsViewModel(
                    settingsStore = application.container.settingsStore,
                    onScheduleChanged = { enabled, hour ->
                        if (enabled) {
                            ExpiryWorkScheduler.schedule(application, hour)
                        } else {
                            ExpiryWorkScheduler.cancel(application)
                        }
                    },
                )
            }
        }
    }
}
