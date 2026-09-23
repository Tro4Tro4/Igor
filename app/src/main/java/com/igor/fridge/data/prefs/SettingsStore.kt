package com.igor.fridge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "igor_settings")

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
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    /** Giorni di preavviso prima della scadenza. */
    val warningDays: Flow<Int> = store.data.map {
        it[KEY_WARNING_DAYS] ?: DEFAULT_WARNING_DAYS
    }

    /** Ora del giorno (0-23) in cui viene eseguito il controllo scadenze. */
    val notificationHour: Flow<Int> = store.data.map {
        it[KEY_NOTIFICATION_HOUR] ?: DEFAULT_NOTIFICATION_HOUR
    }

    val notificationsEnabled: Flow<Boolean> = store.data.map {
        it[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    suspend fun setWarningDays(value: Int) {
        store.edit { it[KEY_WARNING_DAYS] = value.coerceIn(0, 30) }
    }

    suspend fun setNotificationHour(value: Int) {
        store.edit { it[KEY_NOTIFICATION_HOUR] = value.coerceIn(0, 23) }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        store.edit { it[KEY_NOTIFICATIONS_ENABLED] = value }
    }

    /**
     * Lettura bloccante, per il worker delle notifiche e per l'avvio dell'Application:
     * girano fuori da una coroutine e hanno bisogno del valore subito. E' l'unico punto
     * del progetto in cui e' lecito bloccare su DataStore.
     */
    fun snapshot(): Settings = runBlocking {
        val preferences = store.data.first()
        Settings(
            warningDays = preferences[KEY_WARNING_DAYS] ?: DEFAULT_WARNING_DAYS,
            notificationHour = preferences[KEY_NOTIFICATION_HOUR] ?: DEFAULT_NOTIFICATION_HOUR,
            notificationsEnabled = preferences[KEY_NOTIFICATIONS_ENABLED] ?: true,
        )
    }

    companion object {
        const val DEFAULT_WARNING_DAYS = 3
        const val DEFAULT_NOTIFICATION_HOUR = 9

        private val KEY_WARNING_DAYS = intPreferencesKey("warning_days")
        private val KEY_NOTIFICATION_HOUR = intPreferencesKey("notification_hour")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }
}
