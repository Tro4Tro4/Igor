package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SimilarProductsTest {

    private val today = LocalDate.of(2026, 10, 3)

    private fun record(
        name: String,
        store: String,
        unitCents: Long,
        unit: QuantityUnit = QuantityUnit.L,
        date: LocalDate = today,
    ) = PriceRecord(
        uuid = "$name-$store-$date",
        productKey = productKey(name),
        productName = name,
        purchasedOn = date,
        store = store,
        quantity = 1.0,
        unit = unit,
        totalCents = unitCents,
        unitPriceCents = unitCents,
        referenceUnit = unit,
    )

    private fun offer(name: String, unitCents: Long, unit: QuantityUnit = QuantityUnit.L, similarity: Double = 0.5) =
        SimilarOffer(
            productName = name,
            brands = null,
            privateLabel = null,
            store = "Esselunga",
            priceCents = unitCents,
            unitPriceCents = unitCents,
            referenceUnit = unit,
            date = today,
            source = PriceSource.COMMUNITY,
            similarity = similarity,
        )

    @Test
    fun `la descrizione toglie parole corte, formati e catene`() {
        assertEquals(listOf("latte", "granarolo"), descriptionWords("LATTE PS GRANAROLO 1L"))
        assertEquals(listOf("latte", "intero"), descriptionWords("Latte intero Esselunga"))
        assertEquals(listOf("yogurt", "greco"), descriptionWords("Milbona yogurt greco 500g"))
    }

    @Test
    fun `due nomi sono simili se il prodotto e' lo stesso`() {
        val milk = descriptionWords("Latte PS Granarolo")

        assertEquals(1.0, nameSimilarity(milk, descriptionWords("LATTE GRANAROLO")), 0.001)
        assertTrue(nameSimilarity(milk, descriptionWords("Latte parzialmente scremato Esselunga")) > 0.0)
        assertEquals(0.0, nameSimilarity(milk, descriptionWords("Caffe latte")), 0.001)
        assertEquals(0.0, nameSimilarity(milk, emptyList()), 0.001)
        // L'abbreviazione della cassa vale.
        assertTrue(nameSimilarity(descriptionWords("Mozzarella"), descriptionWords("MOZZAR SANTA LUCIA")) > 0.0)
    }

    @Test
    fun `i simili dei tuoi scontrini escludono il prodotto stesso e partono dal piu' conveniente`() {
        val records = listOf(
            record("Latte PS Granarolo", "Coop", 179),
            record("Latte PS Esselunga", "Esselunga", 125),
            record("Latte PS Esselunga", "Esselunga", 135, date = today.minusDays(30)),
            record("Latte di soia", "Lidl", 149),
            record("Biscotti", "Coop", 300),
        )

        val offers = ownSimilarOffers("Latte PS Granarolo", productKey("Latte PS Granarolo"), records)

        assertEquals(listOf("Latte PS Esselunga", "Latte di soia"), offers.map { it.productName })
        // Per ogni prodotto e negozio conta l'ultimo prezzo.
        assertEquals(125L, offers.first().unitPriceCents)
        assertEquals("Esselunga", offers.first().privateLabel)
        assertNull(offers[1].privateLabel)
    }

    @Test
    fun `l'alternativa deve costare meno al kg o al litro, non a confezione`() {
        val offers = listOf(
            offer("Latte Esselunga", 120),
            offer("Latte piccolo", 60, unit = QuantityUnit.PZ),
            offer("Latte caro", 200),
        )

        assertEquals("Latte Esselunga", bestAlternative(offers, 150, QuantityUnit.L)?.productName)
        assertNull(bestAlternative(offers, 110, QuantityUnit.L))
        assertNull(bestAlternative(offers, 150, QuantityUnit.KG))
    }

    @Test
    fun `l'alternativa si propone solo se fa risparmiare almeno il 5 per cento`() {
        val offers = listOf(offer("Latte Esselunga", 120))

        val alternative = cheaperAlternative(offers, listOf(150L to QuantityUnit.L, 160L to QuantityUnit.L))
        assertEquals(20, alternative?.savingPercent)
        assertNull(cheaperAlternative(offers, listOf(124L to QuantityUnit.L)))
        assertNull(cheaperAlternative(offers, emptyList()))
    }
}
