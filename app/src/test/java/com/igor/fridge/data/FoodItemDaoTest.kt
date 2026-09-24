package com.igor.fridge.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.FoodItemDao
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FoodItemDaoTest {

    private lateinit var db: IgorDatabase
    private lateinit var dao: FoodItemDao

    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IgorDatabase::class.java,
        ).build()
        dao = db.foodItemDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(
        uuid: String,
        name: String,
        expiry: LocalDate? = null,
        removedAt: Instant? = null,
        reason: RemovalReason? = null,
        category: FoodCategory = FoodCategory.ALTRO,
        updatedAt: Instant = now,
    ) = FoodItem(
        uuid = uuid,
        name = name,
        expiryDate = expiry,
        updatedAt = updatedAt,
        removedAt = removedAt,
        removalReason = reason,
        category = category,
    )

    @Test
    fun `l'inventario esclude gli articoli rimossi`() = runTest {
        dao.upsert(item("a", "Latte"))
        dao.upsert(item("b", "Pane", removedAt = now, reason = RemovalReason.CONSUMATO))

        assertEquals(listOf("Latte"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun `gli articoli senza scadenza finiscono in fondo`() = runTest {
        dao.upsert(item("a", "Senza data"))
        dao.upsert(item("b", "Scade dopo", expiry = LocalDate.of(2026, 5, 1)))
        dao.upsert(item("c", "Scade prima", expiry = LocalDate.of(2026, 4, 1)))

        assertEquals(
            listOf("Scade prima", "Scade dopo", "Senza data"),
            dao.observeAll().first().map { it.name },
        )
    }

    @Test
    fun `findLastByName trova anche fra gli articoli usciti dal frigo`() = runTest {
        dao.upsert(
            item(
                uuid = "vecchio",
                name = "Latte",
                removedAt = now,
                reason = RemovalReason.CONSUMATO,
                category = FoodCategory.LATTICINI,
                updatedAt = now,
            ),
        )

        val found = dao.findLastByName("latte")

        assertEquals(FoodCategory.LATTICINI, found?.category)
    }

    @Test
    fun `findLastByName preferisce la versione piu recente`() = runTest {
        dao.upsert(item("vecchio", "Latte", category = FoodCategory.ALTRO, updatedAt = now))
        dao.upsert(
            item(
                uuid = "nuovo",
                name = "Latte",
                category = FoodCategory.LATTICINI,
                updatedAt = now.plusSeconds(60),
            ),
        )

        assertEquals(FoodCategory.LATTICINI, dao.findLastByName("Latte")?.category)
    }

    @Test
    fun `le scadenze entro il limite escludono i rimossi`() = runTest {
        dao.upsert(item("a", "Yogurt", expiry = LocalDate.of(2026, 4, 1)))
        dao.upsert(
            item(
                uuid = "b",
                name = "Panna",
                expiry = LocalDate.of(2026, 4, 1),
                removedAt = now,
                reason = RemovalReason.BUTTATO,
            ),
        )

        val found = dao.findExpiringOnOrBefore(LocalDate.of(2026, 4, 2))

        assertEquals(listOf("Yogurt"), found.map { it.name })
    }

    @Test
    fun `un articolo inesistente non viene trovato`() = runTest {
        assertNull(dao.findByUuid("mai-esistito"))
    }
}
