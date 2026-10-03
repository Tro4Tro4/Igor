package com.igor.fridge.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.local.QuantityUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Un database della versione 1, scritto a mano con lo schema di `schemas/.../1.json`,
 * viene aperto da [IgorDatabase.build]: lo stesso percorso dell'app installata. Se la
 * migrazione lasciasse lo schema diverso da quello delle entita', Room rifiuterebbe di
 * aprirlo; se mancasse, il fallback distruttivo cancellerebbe i dati e il test fallirebbe
 * sulle righe scomparse.
 *
 * Il nome del file e' diverso da quello dell'app: IgorApplication, che Robolectric avvia,
 * apre il database vero in background.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: IgorDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun createVersion1() {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            V1_SCHEMA.forEach(v1::execSQL)
            v1.execSQL(
                "INSERT INTO food_items (uuid, name, category, location, quantity, unit, " +
                    "addedAt, updatedAt) VALUES ('f1', 'Latte', 'LATTICINI', 'FRIGO', 1.0, 'L', " +
                    "20000, 1000)",
            )
            v1.execSQL(
                "INSERT INTO shopping_items (uuid, name, quantity, unit, isChecked, createdAt, " +
                    "updatedAt) VALUES ('s1', 'latte', 2.0, 'L', 0, 20000, 1000)",
            )
            v1.execSQL(
                "INSERT INTO shopping_items (uuid, name, quantity, unit, isChecked, createdAt, " +
                    "updatedAt) VALUES ('s2', 'Mai visto', 1.0, 'PZ', 1, 20000, 1000)",
            )
            v1.version = 1
        }
    }

    @Test
    fun `la migrazione conserva inventario e lista e assegna le categorie`() = runTest {
        createVersion1()

        val migrated = IgorDatabase.build(context, TEST_DB).also { db = it }

        // Le migrazioni 1->2, 2->3, 3->4 e 4->5 si applicano in fila.
        val food = migrated.foodItemDao().findByUuid("f1")
        assertEquals("Latte", food?.name)
        assertNull(food?.brand)

        val shopping = migrated.shoppingItemDao().observeAll().first()
        assertEquals(2, shopping.size)
        val latte = shopping.single { it.uuid == "s1" }
        assertEquals(2.0, latte.quantity, 0.001)
        assertEquals(QuantityUnit.L, latte.unit)
        // Ereditata dall'inventario, senza distinguere le maiuscole.
        assertEquals(FoodCategory.LATTICINI, latte.category)
        assertNull(latte.brand)
        assertNull(latte.purchasedQuantity)
        assertNull(latte.unitPriceCents)
        assertNull(latte.store)
        assertEquals(FoodCategory.ALTRO, shopping.single { it.uuid == "s2" }.category)

        assertTrue(migrated.savedListDao().observeSummaries().first().isEmpty())
        assertTrue(migrated.priceRecordDao().observeAll().first().isEmpty())
        assertTrue(migrated.productCodeDao().all().isEmpty())
    }

    private companion object {
        const val TEST_DB = "igor-migration-test"

        /** createSql di `app/schemas/com.igor.fridge.data.local.IgorDatabase/1.json`. */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `food_items` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`barcode` TEXT, `category` TEXT NOT NULL, `location` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `expiryDate` INTEGER, " +
                "`addedAt` INTEGER NOT NULL, `notes` TEXT, `updatedAt` INTEGER NOT NULL, " +
                "`removedAt` INTEGER, `removalReason` TEXT, PRIMARY KEY(`uuid`))",
            "CREATE INDEX IF NOT EXISTS `index_food_items_barcode` ON `food_items` (`barcode`)",
            "CREATE INDEX IF NOT EXISTS `index_food_items_expiryDate` ON `food_items` (`expiryDate`)",
            "CREATE INDEX IF NOT EXISTS `index_food_items_name` ON `food_items` (`name`)",
            "CREATE INDEX IF NOT EXISTS `index_food_items_removedAt` ON `food_items` (`removedAt`)",
            "CREATE TABLE IF NOT EXISTS `shopping_items` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `isChecked` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uuid`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, 'f1ecb56c6fc2fb28dfd838b2589f2f1e')",
        )
    }
}
