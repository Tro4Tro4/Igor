package com.igor.fridge.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.prefs.SettingsStore
import com.igor.fridge.notification.ExpiryWorkScheduler
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SettingsUiState(
    val warningDays: Int = SettingsStore.DEFAULT_WARNING_DAYS,
    val notificationHour: Int = SettingsStore.DEFAULT_NOTIFICATION_HOUR,
    val notificationsEnabled: Boolean = SettingsStore.DEFAULT_NOTIFICATIONS_ENABLED,
    val isWorking: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val onScheduleChanged: (enabled: Boolean, hour: Int) -> Unit,
    private val exportData: suspend () -> String = { "" },
    private val writeExport: suspend (Uri, String) -> Unit = { _, _ -> },
    private val deleteAllData: suspend () -> Unit = {},
) : ViewModel() {

    private val status = MutableStateFlow(Status())

    private data class Status(val working: Boolean = false, val message: String? = null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.warningDays,
        settingsStore.notificationHour,
        settingsStore.notificationsEnabled,
        status,
    ) { days, hour, enabled, status ->
        SettingsUiState(days, hour, enabled, status.working, status.message)
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

    /** Scrive l'esportazione nel file scelto dall'utente. */
    fun exportTo(target: Uri) = runOperation(
        done = "Dati esportati",
        failed = "Esportazione non riuscita",
    ) {
        writeExport(target, exportData())
    }

    /** Cancella tutto e riporta le notifiche alle impostazioni iniziali. */
    fun deleteEverything() = runOperation(
        done = "Tutti i dati sono stati eliminati",
        failed = "Eliminazione non riuscita",
    ) {
        withContext(NonCancellable) { deleteAllData() }
        onScheduleChanged(SettingsStore.DEFAULT_NOTIFICATIONS_ENABLED, SettingsStore.DEFAULT_NOTIFICATION_HOUR)
    }

    fun onMessageShown() = status.update { it.copy(message = null) }

    private fun runOperation(done: String, failed: String, block: suspend () -> Unit) {
        if (status.value.working) return
        status.update { Status(working = true) }
        viewModelScope.launch {
            val ok = try {
                block()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            status.update { Status(working = false, message = if (ok) done else failed) }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = igorApplication()
                val container = application.container
                SettingsViewModel(
                    settingsStore = container.settingsStore,
                    onScheduleChanged = { enabled, hour ->
                        if (enabled) {
                            ExpiryWorkScheduler.schedule(application, hour, hourChanged = true)
                        } else {
                            ExpiryWorkScheduler.cancel(application)
                        }
                    },
                    exportData = container::exportData,
                    writeExport = { uri, text ->
                        withContext(Dispatchers.IO) {
                            val stream = application.contentResolver.openOutputStream(uri, "wt")
                                ?: throw IOException("File non scrivibile")
                            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        }
                    },
                    deleteAllData = container::deleteAllData,
                )
            }
        }
    }
}
