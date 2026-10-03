package com.igor.fridge.ui

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormattersTest {

    @Test
    fun `le quantita intere non mostrano decimali`() {
        assertEquals("1 pz", formatQuantity(1.0, QuantityUnit.PZ))
        assertEquals("500 g", formatQuantity(500.0, QuantityUnit.G))
    }

    @Test
    fun `le quantita decimali usano la virgola`() {
        assertEquals("1,5 l", formatQuantity(1.5, QuantityUnit.L))
        assertEquals("0,25 kg", formatQuantity(0.25, QuantityUnit.KG))
    }

    @Test
    fun `le date usano il formato italiano`() {
        assertEquals("09/03/2026", LocalDate.of(2026, 3, 9).formatShort())
    }

    @Test
    fun `l etichetta di scadenza copre passato presente e futuro`() {
        assertEquals("Senza scadenza", expiryLabel(null))
        assertEquals("Scaduto da 3 giorni", expiryLabel(-3))
        assertEquals("Scaduto ieri", expiryLabel(-1))
        assertEquals("Scade oggi", expiryLabel(0))
        assertEquals("Scade domani", expiryLabel(1))
        assertEquals("Scade fra 5 giorni", expiryLabel(5))
    }

    @Test
    fun `una quantita' scritta dall'utente accetta virgola e punto`() {
        assertEquals(1.5, parseQuantity(" 1,5 ")!!, 0.001)
        assertEquals(2.25, parseQuantity("2.25")!!, 0.001)
    }

    @Test
    fun `una quantita' nulla, negativa o non numerica non vale`() {
        assertNull(parseQuantity(""))
        assertNull(parseQuantity("0"))
        assertNull(parseQuantity("-1"))
        assertNull(parseQuantity("due"))
        assertNull(parseQuantity("NaN"))
    }

    @Test
    fun `ogni categoria ha un'icona sua`() {
        val icons = FoodCategory.entries.map { it.icon() }
        assertEquals(icons.size, icons.toSet().size)
    }

    @Test
    fun `i prezzi si leggono in centesimi e si scrivono in euro`() {
        assertEquals(129L, parsePriceCents("1,29"))
        assertEquals(129L, parsePriceCents(" 1.29 € "))
        assertEquals(200L, parsePriceCents("2"))
        assertEquals(0L, parsePriceCents("0"))
        assertNull(parsePriceCents("-1"))
        assertNull(parsePriceCents("gratis"))
        assertEquals("12,90 €", formatEuro(1290))
        assertEquals("0,05 €", formatEuro(5))
        assertEquals("1,29", formatPriceInput(129))
    }

    @Test
    fun `prezzo unitario, mese e variazione`() {
        assertEquals("1,90 €/kg", formatUnitPrice(190, QuantityUnit.KG))
        assertEquals("ottobre 2026", java.time.YearMonth.of(2026, 10).formatMonth())
        assertEquals("+4,8%", formatChange(4.76))
        assertEquals("-3%", formatChange(-3.0))
        assertEquals("0%", formatChange(0.01))
    }

    @Test
    fun `la quantita' nei campi tiene fino a tre decimali`() {
        assertEquals("0,125", formatQuantityInput(0.125))
        assertEquals("1,234", formatQuantityInput(1.234))
        assertEquals("2", formatQuantityInput(2.0))
        assertEquals("0,5", formatQuantityInput(0.5))
    }

    @Test
    fun `gli importi negativi tengono il segno`() {
        assertEquals("-0,50 €", formatEuro(-50))
        assertEquals("-1,50 €", formatEuro(-150))
        assertEquals("-0,50", formatPriceInput(-50))
        assertEquals("-1,50", formatPriceInput(-150))
    }

    @Test
    fun `la variazione si arrotonda allo stesso modo nei due sensi`() {
        assertEquals("+0,1%", formatChange(0.05))
        assertEquals("-0,1%", formatChange(-0.05))
        assertEquals("0%", formatChange(-0.01))
        assertEquals("-4,8%", formatChange(-4.76))
    }

    @Test
    fun `un prezzo si legge solo se e' scritto come un prezzo`() {
        assertNull(parsePriceCents("1e3"))
        assertNull(parsePriceCents("1,234"))
        assertNull(parsePriceCents("1,2,3"))
        assertEquals(123456L, parsePriceCents("1.234,56"))
        assertEquals(150L, parsePriceCents("1,5"))
        assertEquals(1050L, parsePriceCents("10.50"))
        assertEquals(123400L, parsePriceCents("1.234"))
    }
}
