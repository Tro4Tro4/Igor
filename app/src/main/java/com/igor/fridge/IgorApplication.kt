package com.igor.fridge

import android.app.Application
import com.igor.fridge.di.AppContainer
import com.igor.fridge.notification.ExpiryNotifier
import com.igor.fridge.notification.ExpiryWorkScheduler

class IgorApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Il canale va creato prima di qualsiasi notifica; e' un'operazione idempotente.
        ExpiryNotifier(this).createChannel()

        if (container.settingsStore.notificationsEnabled) {
            ExpiryWorkScheduler.schedule(this, container.settingsStore.notificationHour)
        }
    }
}
