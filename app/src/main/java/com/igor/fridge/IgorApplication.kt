package com.igor.fridge

import android.app.Application
import com.igor.fridge.di.AppContainer
import com.igor.fridge.notification.ExpiryNotifier
import com.igor.fridge.notification.ExpiryWorkScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IgorApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * I lavori di avvio sono un di piu': un errore (preferenze illeggibili, disco pieno)
     * li salta fino al prossimo avvio invece di chiudere l'app ogni volta che si apre.
     */
    private val startupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> },
    )

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        startupScope.launch { container.onlinePricesWorkScheduler.sync(false) }

        // Il canale va creato prima di qualsiasi notifica; e' un'operazione idempotente.
        ExpiryNotifier(this).createChannel()

        // Leggere le preferenze significa leggere da disco: onCreate gira sul thread
        // principale, prima del primo frame, quindi la lettura va in una coroutine.
        // La pianificazione non ha bisogno di essere pronta prima che l'app sia visibile.
        startupScope.launch {
            val settings = container.settingsStore.settings.first()
            if (settings.notificationsEnabled) {
                ExpiryWorkScheduler.schedule(this@IgorApplication, settings.notificationHour)
            }
        }

        // Fare spazio e' un di piu': se fallisce, le foto aspettano il prossimo avvio.
        startupScope.launch(Dispatchers.IO) { container.deleteOrphanPhotos() }
    }
}
