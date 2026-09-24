package com.igor.fridge.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.igor.fridge.MainActivity
import com.igor.fridge.R
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.domain.ExpiryStatus
import com.igor.fridge.domain.expiryStatus
import java.time.LocalDate

/** Costruisce e mostra la notifica riepilogativa delle scadenze. */
class ExpiryNotifier(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.expiry_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.expiry_notification_channel_description)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * Mostra una sola notifica riepilogativa: un avviso per articolo sarebbe invasivo
     * e verrebbe ripetuto a ogni esecuzione giornaliera del worker.
     *
     * @return true se la notifica e' stata mostrata.
     */
    @SuppressLint("MissingPermission")
    fun notifyExpiring(items: List<FoodItem>, today: LocalDate, warningDays: Int): Boolean {
        if (items.isEmpty() || !hasPermission()) return false

        val expired = items.count { it.expiryStatus(today, warningDays) == ExpiryStatus.SCADUTO }
        val expiring = items.size - expired

        val title = when {
            expired > 0 && expiring > 0 -> "$expired scaduti, $expiring in scadenza"
            expired > 0 -> if (expired == 1) "1 prodotto scaduto" else "$expired prodotti scaduti"
            expiring == 1 -> "1 prodotto in scadenza"
            else -> "$expiring prodotti in scadenza"
        }
        val body = items.take(MAX_ITEMS_IN_BODY).joinToString(separator = ", ") { it.name }
        val text = if (items.size > MAX_ITEMS_IN_BODY) {
            "$body e altri ${items.size - MAX_ITEMS_IN_BODY}"
        } else {
            body
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    /** Da Android 13 la notifica richiede un permesso runtime concesso dall'utente. */
    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "expiry_alerts"
        const val NOTIFICATION_ID = 1001
        private const val MAX_ITEMS_IN_BODY = 5
    }
}
