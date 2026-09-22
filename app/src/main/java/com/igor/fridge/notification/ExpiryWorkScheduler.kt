package com.igor.fridge.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Pianifica il controllo scadenze una volta al giorno all'ora scelta dall'utente. */
object ExpiryWorkScheduler {

    fun schedule(context: Context, hourOfDay: Int) {
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(minutesUntilNextDailyRun(LocalDateTime.now(), hourOfDay), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            // UPDATE conserva la pianificazione esistente ma ne aggiorna i parametri:
            // cambiando l'ora nelle impostazioni non si perde il lavoro gia' accodato.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ExpiryCheckWorker.WORK_NAME)
    }
}
