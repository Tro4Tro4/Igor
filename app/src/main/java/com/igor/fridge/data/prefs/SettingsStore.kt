package com.igor.fridge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.domain.categoryOrderFrom
import com.igor.fridge.domain.toStoredOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * L'unica istanza di DataStore del processo. Vive qui e non dentro [SettingsStore] perche'
 * il delegato non e' istanziabile due volte sullo stesso file: AppContainer la passa al
 * costruttore, e un test puo' costruirne una propria su un file temporaneo.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "igor_settings",
        // Un file illeggibile riparte dai default invece di far chiudere l'app a ogni avvio.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

/**
 * L'accesso a Open Prices sta in un file a parte, escluso dal backup: le impostazioni
 * tornano con un ripristino, il token no. Un token non deve finire nel cloud, e su un
 * altro telefono si rientra con la password.
 */
internal val Context.sessionDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "igor_session",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

/** Valori delle impostazioni in un istante dato. */
data class Settings(
    val warningDays: Int,
    val notificationHour: Int,
    val notificationsEnabled: Boolean,
)

/** Stato di Open Prices: attivo o no, e con quale account (null se non si e' entrati). */
data class OpenPricesSettings(
    val enabled: Boolean = false,
    val userId: String? = null,
    val token: String? = null,
) {
    val isLoggedIn: Boolean get() = userId != null && token != null
}

/**
 * Preferenze dell'utente.
 *
 * DataStore invece di SharedPreferences perche' i valori devono essere osservabili: un
 * cambio di soglia deve raggiungere la lista dell'inventario senza ricrearne il ViewModel.
 */
class SettingsStore(
    private val store: DataStore<Preferences>,
    private val session: DataStore<Preferences> = store,
) {

    /** Un errore di lettura da disco vale come "nessuna preferenza": restano i default. */
    private val preferences: Flow<Preferences> = store.data.safe()
    private val sessionPreferences: Flow<Preferences> = session.data.safe()

    /**
     * Tutte le impostazioni insieme. I default si applicano qui e in nessun altro posto:
     * i flussi singoli derivano da questo, cosi' non esistono due copie della stessa
     * regola che possono divergere.
     */
    val settings: Flow<Settings> = preferences.map { preferences ->
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

    val onlinePrices: Flow<com.igor.fridge.data.onlineprices.OnlinePricesSettings> = preferences.map {
        com.igor.fridge.data.onlineprices.OnlinePricesSettings(it[KEY_ONLINE_PRICES_ENABLED] ?: false,it[KEY_ONLINE_POSTCODE] ?: "20125")
    }
    suspend fun setOnlinePricesEnabled(enabled: Boolean) { store.edit { it[KEY_ONLINE_PRICES_ENABLED] = enabled } }
    suspend fun setOnlinePostcode(postcode: String) {
        require(postcode.matches(Regex("[0-9]{5}")))
        store.edit { it[KEY_ONLINE_POSTCODE] = postcode }
    }

    /**
     * Ordine delle corsie della lista della spesa. Sta fuori da [Settings], che raccoglie
     * cio' che serve alle notifiche.
     */
    val categoryOrder: Flow<List<FoodCategory>> =
        preferences.map { categoryOrderFrom(it[KEY_CATEGORY_ORDER]) }

    suspend fun setCategoryOrder(order: List<FoodCategory>) {
        store.edit { it[KEY_CATEGORY_ORDER] = order.toStoredOrder() }
    }

    /**
     * Open Prices: prezzi della comunita' e invio dei propri scontrini. Spento finche'
     * l'utente non lo attiva, perche' e' l'unica funzione che usa Internet.
     *
     * Le versioni precedenti tenevano il token nel file delle impostazioni: lo si legge
     * ancora da li', finche' il prossimo accesso o uscita non lo sposta.
     */
    val openPrices: Flow<OpenPricesSettings> =
        combine(preferences, sessionPreferences) { prefs, sess ->
            val fromSession = sess[KEY_OPEN_PRICES_TOKEN] != null
            OpenPricesSettings(
                enabled = prefs[KEY_OPEN_PRICES_ENABLED] ?: false,
                userId = if (fromSession) sess[KEY_OPEN_PRICES_USER] else prefs[KEY_OPEN_PRICES_USER],
                token = if (fromSession) sess[KEY_OPEN_PRICES_TOKEN] else prefs[KEY_OPEN_PRICES_TOKEN],
            )
        }

    suspend fun setOpenPricesEnabled(enabled: Boolean) {
        store.edit { it[KEY_OPEN_PRICES_ENABLED] = enabled }
    }

    /**
     * Ricorda l'accesso a Open Prices. Si salva il token restituito dal server, mai la
     * password, nel file della sessione che resta fuori dal backup.
     */
    suspend fun setOpenPricesSession(userId: String?, token: String?) {
        session.edit {
            if (userId == null || token == null) {
                it.remove(KEY_OPEN_PRICES_USER)
                it.remove(KEY_OPEN_PRICES_TOKEN)
            } else {
                it[KEY_OPEN_PRICES_USER] = userId
                it[KEY_OPEN_PRICES_TOKEN] = token
            }
        }
        // Una copia rimasta nel file delle impostazioni andrebbe nel backup.
        if (session !== store) {
            store.edit {
                it.remove(KEY_OPEN_PRICES_USER)
                it.remove(KEY_OPEN_PRICES_TOKEN)
            }
        }
    }

    /** Per "Elimina i miei dati": impostazioni ai valori iniziali e uscita da Open Prices. */
    suspend fun clearAll() {
        store.edit { it.clear() }
        session.edit { it.clear() }
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

    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    companion object {
        const val DEFAULT_WARNING_DAYS = 3
        const val DEFAULT_NOTIFICATION_HOUR = 9
        const val DEFAULT_NOTIFICATIONS_ENABLED = true

        private val KEY_WARNING_DAYS = intPreferencesKey("warning_days")
        private val KEY_ONLINE_PRICES_ENABLED = booleanPreferencesKey("online_prices_enabled")
        private val KEY_ONLINE_POSTCODE = stringPreferencesKey("online_postcode")
        private val KEY_NOTIFICATION_HOUR = intPreferencesKey("notification_hour")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_CATEGORY_ORDER = stringPreferencesKey("category_order")
        private val KEY_OPEN_PRICES_ENABLED = booleanPreferencesKey("open_prices_enabled")
        private val KEY_OPEN_PRICES_USER = stringPreferencesKey("open_prices_user")
        private val KEY_OPEN_PRICES_TOKEN = stringPreferencesKey("open_prices_token")
    }
}
