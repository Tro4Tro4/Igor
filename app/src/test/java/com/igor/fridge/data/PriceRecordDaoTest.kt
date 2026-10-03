package com.igor.fridge.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.PriceRecordDao
import com.igor.fridge.data.local.QuantityUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PriceRecordDaoTest {

    private lateinit var db: IgorDatabase
    private lateinit var dao: PriceRecordDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IgorDatabase::class.java,
        ).build()
        dao = db.priceRecordDao()
    }

    @After
    fun tearDown() = db.close()

    private fun record(uuid: String, key: String, day: Int, cents: Long, created: Long = 0) = PriceRecord(
        uuid = uuid,
        productKey = key,
        productName = key,
        purchasedOn = LocalDate.of(2026, 10, day),
        quantity = 1.0,
        unit = QuantityUnit.PZ,
        totalCents = cents,
        unitPriceCents = cents,
        referenceUnit = QuantityUnit.PZ,
        createdAt = Instant.ofEpochMilli(created),
    )

    @Test
    fun `l'ultimo prezzo e' quello del giorno piu' recente`() = runTest {
        dao.insertAll(
            listOf(
                record("a", "latte", 1, 119),
                record("b", "latte", 3, 129, created = 1),
                record("c", "latte", 3, 139, created = 2),
                record("d", "pane", 5, 200),
            ),
        )

        assertEquals("c", dao.findLatest("latte")?.uuid)
        assertEquals(listOf("c", "b", "a"), dao.observeByProduct("latte").first().map { it.uuid })
        assertEquals(listOf("d", "c", "b", "a"), dao.observeAll().first().map { it.uuid })
    }

    @Test
    fun `un prezzo sbagliato si toglie`() = runTest {
        val wrong = record("a", "latte", 1, 9999)
        dao.insertAll(listOf(wrong, record("b", "latte", 2, 129)))

        dao.delete(wrong)

        assertEquals(listOf("b"), dao.observeAll().first().map { it.uuid })
    }

    @Test
    fun `il codice a barre di un prodotto si salva, si sostituisce e si toglie`() = runTest {
        val codes = db.productCodeDao()
        codes.upsert(com.igor.fridge.data.local.ProductCode("latte", "4006381333931"))
        codes.upsert(com.igor.fridge.data.local.ProductCode("latte", "8001234567890"))

        assertEquals("8001234567890", codes.find("latte")?.barcode)
        assertEquals(1, codes.all().size)

        codes.delete("latte")
        assertEquals(null, codes.find("latte"))
    }
}
