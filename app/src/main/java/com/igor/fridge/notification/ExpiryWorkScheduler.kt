package com.igor.fridge.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Pianifica il controllo scadenze una volta al giorno all'ora scelta dall'utente. */
object ExpiryWorkScheduler {

    /**
     * [hourChanged] e' vero quando l'utente cambia ora o riaccende le notifiche: allora la
     * pianificazione riparte con il nuovo orario. All'avvio dell'app invece quella gia'
     * accodata resta com'e': ricalcolarla a ogni apertura (con UPDATE, che conserva il
     * momento dell'accodamento originale) faceva slittare l'orario.
     */
    fun schedule(context: Context, hourOfDay: Int, hourChanged: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(minutesUntilNextDailyRun(LocalDateTime.now(), hourOfDay), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            if (hourChanged) {
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            },
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ExpiryCheckWorker.WORK_NAME)
    }
}
