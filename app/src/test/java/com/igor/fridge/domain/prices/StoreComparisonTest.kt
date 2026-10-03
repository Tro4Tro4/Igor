package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StoreComparisonTest {

    private fun record(
        name: String,
        store: String?,
        total: Long,
        quantity: Double = 1.0,
        unit: QuantityUnit = QuantityUnit.PZ,
        day: Int = 1,
    ) = PriceRecord(
        uuid = "$name-$store-$day-$total",
        productKey = productKey(name),
        productName = name,
        purchasedOn = LocalDate.of(2026, 9, day),
        store = store,
        quantity = quantity,
        unit = unit,
        totalCents = total,
        unitPriceCents = unitPriceCents(total, quantity, unit)!!,
        referenceUnit = toReference(quantity, unit).second,
        createdAt = Instant.EPOCH,
    )

    private fun item(name: String, quantity: Double = 1.0, unit: QuantityUnit = QuantityUnit.PZ) =
        ShoppingItem(uuid = name, name = name, quantity = quantity, unit = unit)

    private val records = listOf(
        record("Latte", "ESSELUNGA SPA", 129, day = 1),
        record("Latte", "Esselunga", 139, day = 20),
        record("Latte", "Lidl Italia", 99),
        record("Mele", "Esselunga", 299, 1.0, QuantityUnit.KG),
        record("Mele", "Lidl", 199, 1.0, QuantityUnit.KG),
        record("Pasta", "Tigros", 95, 500.0, QuantityUnit.G),
        record("Pane", null, 200),
    )

    private fun compare(items: List<ShoppingItem>) =
        compareList(items) { recordsFor(it, records).mapNotNull { r -> r.toObservation() } }

    @Test
    fun `per ogni negozio vale l'ultimo prezzo, raggruppando i nomi della stessa catena`() {
        val latte = latestByStore(records.filter { it.productName == "Latte" }.mapNotNull { it.toObservation() })

        assertEquals(listOf("Lidl" to 99L, "Esselunga" to 139L), latte.map { it.store to it.unitPriceCents })
    }

    @Test
    fun `la quantita' in lista moltiplica il prezzo unitario`() {
        val comparison = compare(listOf(item("Mele", 2.0, QuantityUnit.KG), item("Pasta", 1.0, QuantityUnit.KG)))

        assertEquals(listOf("Lidl" to 398L, "Esselunga" to 598L), comparison.quotes[0].byStore.map { it.first.store to it.second })
        assertEquals(190L, comparison.quotes[1].cheapest?.second)
    }

    @Test
    fun `con unita' diverse si stima con l'ultimo formato comprato`() {
        // 2 pacchi di pasta, comprata l'ultima volta da 500 g a 0,95.
        val comparison = compare(listOf(item("Pasta", 2.0, QuantityUnit.PZ)))

        assertEquals(190L, comparison.quotes.single().cheapest?.second)
    }

    @Test
    fun `i negozi si ordinano per voci coperte, poi per totale, e c'e' la spesa divisa`() {
        val comparison = compare(listOf(item("Latte"), item("Mele", 1.0, QuantityUnit.KG), item("Pasta"), item("Sale")))

        assertEquals(
            listOf(
                StoreEstimate("Lidl", 298, 2, 4),
                StoreEstimate("Esselunga", 438, 2, 4),
                StoreEstimate("Tigros", 95, 1, 4),
            ),
            comparison.stores,
        )
        // Latte e mele da Lidl, pasta da Tigros; il sale non ha prezzi.
        assertEquals(99L + 199L + 95L, comparison.splitCents)
        assertEquals(3, comparison.pricedItems)
        assertNull(comparison.quotes.last().cheapest)
    }

    @Test
    fun `una voce trova i prezzi anche dal nome di cassa`() {
        val cassa = listOf(record("Latte ps granarolo", "Coop", 135))

        assertEquals(1, recordsFor(item("Latte"), cassa).size)
        assertEquals(0, recordsFor(item("Latte di soia"), cassa).size)
    }

    @Test
    fun `un prezzo senza negozio non si confronta`() {
        assertNull(records.last().toObservation())
    }

    @Test
    fun `a pezzi si stima il costo di una confezione, non dell'acquisto intero`() {
        val pesate = listOf(record("Mele", "Lidl", 298, 1.5, QuantityUnit.KG))
        // Mele a pezzi contro un acquisto a peso: non si sa quanto pesa una mela.
        val mele = compareList(listOf(item("Mele", 3.0, QuantityUnit.PZ))) { pesate.mapNotNull { r -> r.toObservation() } }
        assertNull(mele.quotes.single().cheapest)

        val pacchi = listOf(record("Pasta", "Lidl", 178, 2.0, QuantityUnit.PZ))
        val unPacco = compareList(listOf(item("Pasta", 500.0, QuantityUnit.G))) { pacchi.mapNotNull { r -> r.toObservation() } }
        assertEquals(89L, unPacco.quotes.single().cheapest?.second)
        val trePacchi = compareList(listOf(item("Pasta", 3.0, QuantityUnit.CONF))) { pacchi.mapNotNull { r -> r.toObservation() } }
        assertEquals(267L, trePacchi.quotes.single().cheapest?.second)
    }

    @Test
    fun `a parita' di data e fonte vince sempre lo stesso prezzo`() {
        val a = StoreObservation("Lidl", 120, QuantityUnit.PZ, LocalDate.of(2026, 9, 1), PriceSource.MINE)
        val b = a.copy(unitPriceCents = 99)

        assertEquals(listOf(99L), latestByStore(listOf(a, b)).map { it.unitPriceCents })
        assertEquals(listOf(99L), latestByStore(listOf(b, a)).map { it.unitPriceCents })
    }
}
