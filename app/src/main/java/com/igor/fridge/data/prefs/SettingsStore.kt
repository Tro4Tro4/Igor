package com.igor.fridge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * L'unica istanza di DataStore del processo. Vive qui e non dentro [SettingsStore] perche'
 * il delegato non e' istanziabile due volte sullo stesso file: AppContainer la passa al
 * costruttore, e un test puo' costruirne una propria su un file temporaneo.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "igor_settings")

/** Valori delle impostazioni in un istante dato. */
data class Settings(
    val warningDays: Int,
    val notificationHour: Int,
    val notificationsEnabled: Boolean,
)

/**
 * Preferenze dell'utente.
 *
 * DataStore invece di SharedPreferences perche' i valori devono essere osservabili: un
 * cambio di soglia deve raggiungere la lista dell'inventario senza ricrearne il ViewModel.
 */
class SettingsStore(private val store: DataStore<Preferences>) {

    /**
     * Tutte le impostazioni insieme. I default si applicano qui e in nessun altro posto:
     * i flussi singoli derivano da questo, cosi' non esistono due copie della stessa
     * regola che possono divergere.
     */
    val settings: Flow<Settings> = store.data.map { preferences ->
        Settings(
            warningDays = preferences[KEY_WARNING_DAYS] ?: DEFAULT_WARNING_DAYS,
            notificationHour = preferences[KEY_NOTIFICATION_HOUR] ?: DEFAULT_NOTIFICATION_HOUR,
            notificationsEnabled = preferences[KEY_NOTIFICATIONS_ENABLED]
                ?: DEFAULT_NOTIFICATIONS_ENABLED,
        )
    }

    /** Giorni di preavviso prima della scadenza. */
    val warningDays: Flow<Int> = settings.map { it.warningDays }

    /** Ora del giorno (0-23) in cui viene eseguito il controllo scadenze. */
    val notificationHour: Flow<Int> = settings.map { it.notificationHour }

    val notificationsEnabled: Flow<Boolean> = settings.map { it.notificationsEnabled }

    suspend fun setWarningDays(value: Int) {
        store.edit { it[KEY_WARNING_DAYS] = value.coerceIn(0, 30) }
    }

    suspend fun setNotificationHour(value: Int) {
        store.edit { it[KEY_NOTIFICATION_HOUR] = value.coerceIn(0, 23) }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        store.edit { it[KEY_NOTIFICATIONS_ENABLED] = value }
    }

    companion object {
        const val DEFAULT_WARNING_DAYS = 3
        const val DEFAULT_NOTIFICATION_HOUR = 9
        const val DEFAULT_NOTIFICATIONS_ENABLED = true

        private val KEY_WARNING_DAYS = intPreferencesKey("warning_days")
        private val KEY_NOTIFICATION_HOUR = intPreferencesKey("notification_hour")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }
}
