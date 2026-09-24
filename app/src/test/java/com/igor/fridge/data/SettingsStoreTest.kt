package com.igor.fridge.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

// Robolectric come per il resto dei test che toccano il disco: fuori dalla sua sandbox, su
// Windows, la seconda scrittura di DataStore fallisce nel rinominare il file temporaneo
// sopra a quello esistente. E' un limite dell'ambiente, non del codice sotto test.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // Ogni test riceve un DataStore tutto suo su un file temporaneo, invece dell'unica
    // istanza per processo creata dal delegato di Context: cosi' i test non si passano
    // le scritture l'uno all'altro e non serve riportare lo stato ai default prima di
    // ognuno. Soprattutto, il file non esiste ancora, quindi i valori di default sono
    // davvero quelli mancanti e non quelli che la preparazione del test ha appena scritto.
    private fun newStore(): SettingsStore {
        val file = File(temporaryFolder.newFolder(), "igor_settings.preferences_pb")
        return SettingsStore(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun `i valori di partenza sono quelli di default`() = runTest {
        val store = newStore()

        assertEquals(SettingsStore.DEFAULT_WARNING_DAYS, store.warningDays.first())
        assertEquals(SettingsStore.DEFAULT_NOTIFICATION_HOUR, store.notificationHour.first())
        assertTrue(store.notificationsEnabled.first())
    }

    @Test
    fun `le impostazioni lette insieme partono dai default`() = runTest {
        val settings = newStore().settings.first()

        assertEquals(SettingsStore.DEFAULT_WARNING_DAYS, settings.warningDays)
        assertEquals(SettingsStore.DEFAULT_NOTIFICATION_HOUR, settings.notificationHour)
        assertTrue(settings.notificationsEnabled)
    }

    @Test
    fun `la soglia scritta viene riletta`() = runTest {
        val store = newStore()

        store.setWarningDays(7)

        assertEquals(7, store.warningDays.first())
    }

    @Test
    fun `la soglia resta entro i limiti accettabili`() = runTest {
        val store = newStore()

        store.setWarningDays(99)
        assertEquals(30, store.warningDays.first())

        store.setWarningDays(-5)
        assertEquals(0, store.warningDays.first())
    }

    @Test
    fun `l'ora resta entro la giornata`() = runTest {
        val store = newStore()

        store.setNotificationHour(30)

        assertEquals(23, store.notificationHour.first())
    }

    @Test
    fun `le impostazioni riflettono le scritture`() = runTest {
        val store = newStore()

        store.setWarningDays(5)
        store.setNotificationsEnabled(false)

        val settings = store.settings.first()

        assertEquals(5, settings.warningDays)
        assertEquals(SettingsStore.DEFAULT_NOTIFICATION_HOUR, settings.notificationHour)
        assertFalse(settings.notificationsEnabled)
    }
}
