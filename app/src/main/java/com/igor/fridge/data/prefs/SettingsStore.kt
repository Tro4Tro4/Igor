package com.igor.fridge.data.prefs

import android.content.Context

/**
 * Preferenze dell'utente. Usa SharedPreferences: i valori sono pochi e vengono letti
 * anche dal worker in background, dove un'API sincrona semplifica il codice.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Giorni di preavviso prima della scadenza. */
    var warningDays: Int
        get() = prefs.getInt(KEY_WARNING_DAYS, DEFAULT_WARNING_DAYS)
        set(value) = prefs.edit().putInt(KEY_WARNING_DAYS, value.coerceIn(0, 30)).apply()

    /** Ora del giorno (0-23) in cui viene eseguito il controllo scadenze. */
    var notificationHour: Int
        get() = prefs.getInt(KEY_NOTIFICATION_HOUR, DEFAULT_NOTIFICATION_HOUR)
        set(value) = prefs.edit().putInt(KEY_NOTIFICATION_HOUR, value.coerceIn(0, 23)).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    companion object {
        const val PREFS_NAME = "igor_settings"
        const val DEFAULT_WARNING_DAYS = 3
        const val DEFAULT_NOTIFICATION_HOUR = 9

        private const val KEY_WARNING_DAYS = "warning_days"
        private const val KEY_NOTIFICATION_HOUR = "notification_hour"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    }
}
