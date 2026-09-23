package com.igor.fridge.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private val store = SettingsStore(ApplicationProvider.getApplicationContext())

    // preferencesDataStore usa un'unica istanza per l'intera JVM: sotto Robolectric,
    // dove tutti i test girano nello stesso processo, i test condividerebbero altrimenti
    // le scritture l'uno dell'altro. Si riporta lo stato ai default prima di ogni test.
    @Before
    fun resetStore() = runBlocking {
        store.setWarningDays(SettingsStore.DEFAULT_WARNING_DAYS)
        store.setNotificationHour(SettingsStore.DEFAULT_NOTIFICATION_HOUR)
        store.setNotificationsEnabled(true)
    }

    @Test
    fun `i valori di partenza sono quelli di default`() = runTest {
        assertEquals(SettingsStore.DEFAULT_WARNING_DAYS, store.warningDays.first())
        assertEquals(SettingsStore.DEFAULT_NOTIFICATION_HOUR, store.notificationHour.first())
        assertEquals(true, store.notificationsEnabled.first())
    }

    @Test
    fun `la soglia scritta viene riletta`() = runTest {
        store.setWarningDays(7)

        assertEquals(7, store.warningDays.first())
    }

    @Test
    fun `la soglia resta entro i limiti accettabili`() = runTest {
        store.setWarningDays(99)
        assertEquals(30, store.warningDays.first())

        store.setWarningDays(-5)
        assertEquals(0, store.warningDays.first())
    }

    @Test
    fun `l'ora resta entro la giornata`() = runTest {
        store.setNotificationHour(30)

        assertEquals(23, store.notificationHour.first())
    }

    @Test
    fun `lo snapshot riflette le scritture`() = runTest {
        store.setWarningDays(5)
        store.setNotificationsEnabled(false)

        val snapshot = store.snapshot()

        assertEquals(5, snapshot.warningDays)
        assertFalse(snapshot.notificationsEnabled)
    }
}
