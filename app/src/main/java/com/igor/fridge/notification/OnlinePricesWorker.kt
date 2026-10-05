package com.igor.fridge.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.igor.fridge.IgorApplication
import com.igor.fridge.data.repository.OnlineRefreshResult
import kotlinx.coroutines.flow.first

enum class OnlineWorkOutcome { SUCCESS, RETRY, FAILURE }
suspend fun runOnlinePricesWork(enabled: suspend () -> Boolean, refresh: suspend () -> OnlineRefreshResult): OnlineWorkOutcome {
    if (!enabled()) return OnlineWorkOutcome.SUCCESS
    val result = refresh()
    return when {
        result.discarded || result.errors.isEmpty() -> OnlineWorkOutcome.SUCCESS
        result.transient -> OnlineWorkOutcome.RETRY
        else -> OnlineWorkOutcome.FAILURE
    }
}
class OnlinePricesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as IgorApplication).container
        if (!c.onlinePricesClient.configured) return Result.success()
        return when (runOnlinePricesWork({c.settingsStore.onlinePrices.first().enabled},c.onlinePricesRepository::refresh)) {
            OnlineWorkOutcome.SUCCESS -> Result.success()
            OnlineWorkOutcome.RETRY -> Result.retry()
            OnlineWorkOutcome.FAILURE -> Result.failure()
        }
    }
}
