package com.igor.fridge.data

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.Purchase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PriceRepositoryTest {

    private val dao = FakePriceRecordDao()
    private var counter = 0
    private val repository = PriceRepository(dao, { Instant.ofEpochMilli(1_774_000_000_000) }, { "p-${++counter}" })
    private val day = LocalDate.of(2026, 10, 3)

    @Test
    fun `registra gli acquisti con prezzo unitario e scarta quelli senza senso`() = runTest {
        val count = repository.record(
            purchases = listOf(
                Purchase("Pasta", 500.0, QuantityUnit.G, 95),
                Purchase("  ", 1.0, QuantityUnit.PZ, 100),
                Purchase("Omaggio", 1.0, QuantityUnit.PZ, 0),
                Purchase("Riso", 0.0, QuantityUnit.KG, 200),
            ),
            store = "  Coop ",
            date = day,
        )

        assertEquals(1, count)
        val pasta = dao.records.single()
        assertEquals("pasta", pasta.productKey)
        assertEquals(190L, pasta.unitPriceCents)
        assertEquals(QuantityUnit.KG, pasta.referenceUnit)
        assertEquals("Coop", pasta.store)
        assertEquals(day, pasta.purchasedOn)
    }

    @Test
    fun `l'ultimo prezzo si trova per nome, maiuscole e accenti a parte`() = runTest {
        repository.record(listOf(Purchase("Caffè", 1.0, QuantityUnit.PZ, 349)), null, day.minusDays(7))
        repository.record(listOf(Purchase("CAFFE", 1.0, QuantityUnit.PZ, 379)), null, day)

        assertEquals(379L, repository.latest("caffe")?.unitPriceCents)
        assertNull(repository.latest("tè"))
    }
}
