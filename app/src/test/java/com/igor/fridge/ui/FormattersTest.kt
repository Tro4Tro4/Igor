package com.igor.fridge.ui

import com.igor.fridge.data.local.QuantityUnit
import org.junit.Assert.assertEquals
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
}
