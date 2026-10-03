package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class PriceStatsTest {

    private fun record(
        name: String,
        date: LocalDate,
        total: Long,
        quantity: Double = 1.0,
        unit: QuantityUnit = QuantityUnit.PZ,
        store: String? = null,
    ): PriceRecord {
        val (_, reference) = toReference(quantity, unit)
        return PriceRecord(
            uuid = "$name-$date-$total",
            productKey = productKey(name),
            productName = name,
            purchasedOn = date,
            store = store,
            quantity = quantity,
            unit = unit,
            totalCents = total,
            unitPriceCents = unitPriceCents(total, quantity, unit)!!,
            referenceUnit = reference,
            createdAt = Instant.EPOCH,
        )
    }

    @Test
    fun `la chiave ignora maiuscole, accenti e punteggiatura`() {
        assertEquals("caffe macinato", productKey("Caffè, macinato!"))
        assertEquals(productKey("CAFFE MACINATO"), productKey("Caffè macinato"))
    }

    @Test
    fun `grammi e millilitri si confrontano al kg e al litro`() {
        assertEquals(190L, unitPriceCents(95, 500.0, QuantityUnit.G))
        assertEquals(240L, unitPriceCents(180, 750.0, QuantityUnit.ML))
        assertEquals(50L, unitPriceCents(300, 6.0, QuantityUnit.PZ))
        assertNull(unitPriceCents(100, 0.0, QuantityUnit.KG))
    }

    @Test
    fun `il riepilogo dice ultimo, precedente, minimo, massimo e media`() {
        val records = listOf(
            record("Pasta", LocalDate.of(2026, 8, 1), 100, 500.0, QuantityUnit.G),
            record("Pasta", LocalDate.of(2026, 9, 1), 210, 1.0, QuantityUnit.KG),
            record("Pasta", LocalDate.of(2026, 10, 1), 110, 500.0, QuantityUnit.G, store = "Coop"),
        )

        val pasta = summarize(records).single()

        assertEquals(QuantityUnit.KG, pasta.referenceUnit)
        assertEquals(220L, pasta.lastCents)
        assertEquals(210L, pasta.previousCents)
        assertEquals(200L, pasta.minCents)
        assertEquals(220L, pasta.maxCents)
        assertEquals(210L, pasta.averageCents)
        assertEquals(3, pasta.purchases)
        assertEquals(420L, pasta.totalSpentCents)
        assertEquals("Coop", pasta.lastStore)
        assertEquals(4.76, pasta.changePercent!!, 0.01)
    }

    @Test
    fun `al primo acquisto non c'e' variazione`() {
        val summary = summarize(listOf(record("Latte", LocalDate.of(2026, 10, 1), 129))).single()

        assertNull(summary.previousCents)
        assertNull(summary.changePercent)
    }

    @Test
    fun `acquisti in unita' diverse non si confrontano`() {
        val records = listOf(
            record("Mele", LocalDate.of(2026, 9, 1), 50, 1.0, QuantityUnit.PZ),
            record("Mele", LocalDate.of(2026, 10, 1), 199, 1.0, QuantityUnit.KG),
        )

        val mele = summarize(records).single()

        assertEquals(QuantityUnit.KG, mele.referenceUnit)
        assertEquals(1, mele.purchases)
        assertNull(mele.previousCents)
        assertEquals(249L, mele.totalSpentCents)
    }

    @Test
    fun `i prodotti piu' recenti vengono prima, la spesa si somma per mese`() {
        val records = listOf(
            record("Latte", LocalDate.of(2026, 9, 10), 129),
            record("Pane", LocalDate.of(2026, 10, 2), 200),
            record("Uova", LocalDate.of(2026, 10, 1), 300),
        )

        assertEquals(listOf("Pane", "Uova", "Latte"), summarize(records).map { it.productName })
        assertEquals(
            listOf(YearMonth.of(2026, 10) to 500L, YearMonth.of(2026, 9) to 129L),
            monthlySpending(records),
        )
    }
}
