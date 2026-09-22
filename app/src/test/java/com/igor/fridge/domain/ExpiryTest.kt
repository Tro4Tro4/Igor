package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpiryTest {

    private val today = LocalDate.of(2026, 3, 10)

    private fun item(expiry: LocalDate?) = FoodItem(name = "Latte", expiryDate = expiry)

    @Test
    fun `articolo senza data non ha giorni residui`() {
        assertNull(item(null).daysUntilExpiry(today))
        assertEquals(ExpiryStatus.SENZA_DATA, item(null).expiryStatus(today, warningDays = 3))
    }

    @Test
    fun `scadenza di oggi vale zero giorni`() {
        assertEquals(0L, item(today).daysUntilExpiry(today))
    }

    @Test
    fun `giorni residui negativi per un prodotto scaduto`() {
        assertEquals(-2L, item(today.minusDays(2)).daysUntilExpiry(today))
    }

    @Test
    fun `stato scaduto dal giorno successivo alla data`() {
        assertEquals(ExpiryStatus.SCADUTO, item(today.minusDays(1)).expiryStatus(today, 3))
    }

    @Test
    fun `stato in scadenza oggi e fino alla soglia inclusa`() {
        assertEquals(ExpiryStatus.IN_SCADENZA, item(today).expiryStatus(today, 3))
        assertEquals(ExpiryStatus.IN_SCADENZA, item(today.plusDays(3)).expiryStatus(today, 3))
    }

    @Test
    fun `stato fresco oltre la soglia`() {
        assertEquals(ExpiryStatus.FRESCO, item(today.plusDays(4)).expiryStatus(today, 3))
    }

    @Test
    fun `con soglia zero solo la scadenza odierna e in scadenza`() {
        assertEquals(ExpiryStatus.IN_SCADENZA, item(today).expiryStatus(today, 0))
        assertEquals(ExpiryStatus.FRESCO, item(today.plusDays(1)).expiryStatus(today, 0))
    }
}
