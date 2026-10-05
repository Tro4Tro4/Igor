package com.igor.fridge.notification

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class OnlinePricesWorkScheduler(private val context: Context) {
    fun sync(enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<OnlinePricesWorker>(24,TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.KEEP,request)
    }
    companion object { private const val NAME = "igor-online-prices" }
}
