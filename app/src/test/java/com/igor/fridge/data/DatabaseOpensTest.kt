package com.igor.fridge.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.local.IgorDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifica che Room parta su JVM sotto Robolectric: e' il presupposto di tutti i test
 * dei DAO. `sdk = 34` evita di dipendere dal supporto di Robolectric per l'API 35.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseOpensTest {

    @Test
    fun `il database in memoria si apre e risponde`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IgorDatabase::class.java,
        ).build()

        assertTrue(db.openHelper.writableDatabase.isOpen)
        db.close()
    }
}
