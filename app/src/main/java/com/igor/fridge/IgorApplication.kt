package com.igor.fridge

import android.app.Application
import com.igor.fridge.di.AppContainer
import com.igor.fridge.notification.ExpiryNotifier
import com.igor.fridge.notification.ExpiryWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IgorApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Il canale va creato prima di qualsiasi notifica; e' un'operazione idempotente.
        ExpiryNotifier(this).createChannel()

        // Leggere le preferenze significa leggere da disco: onCreate gira sul thread
        // principale, prima del primo frame, quindi la lettura va in una coroutine.
        // La pianificazione non ha bisogno di essere pronta prima che l'app sia visibile.
        CoroutineScope(Dispatchers.Default).launch {
            val settings = container.settingsStore.settings.first()
            if (settings.notificationsEnabled) {
                ExpiryWorkScheduler.schedule(this@IgorApplication, settings.notificationHour)
            }
        }
    }
}
