package com.igor.fridge.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.igor.fridge.IgorApplication
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Controllo giornaliero delle scadenze. Gira anche ad app chiusa, quindi recupera
 * le dipendenze dal container dell'Application invece che da un ViewModel.
 */
class ExpiryCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IgorApplication).container
        // doWork() e' gia' una funzione sospesa: le preferenze si leggono sospendendo,
        // senza bloccare il thread del pool su cui gira il worker.
        val settings = container.settingsStore.settings.first()
        if (!settings.notificationsEnabled) return Result.success()

        val warningDays = settings.warningDays
        val today = LocalDate.now()
        val items = container.foodRepository.findExpiring(today, warningDays)

        ExpiryNotifier(applicationContext).notifyExpiring(items, today, warningDays)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "expiry_check"
    }
}
