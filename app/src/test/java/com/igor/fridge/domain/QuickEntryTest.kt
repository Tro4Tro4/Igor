package com.igor.fridge.domain

import com.igor.fridge.data.local.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickEntryTest {

    private fun assertEntry(text: String, name: String, quantity: Double?, unit: QuantityUnit?) {
        val entry = parseQuickEntry(text)
        assertEquals(text, QuickEntry(name, quantity, unit), entry)
    }

    @Test
    fun `quantita' e unita' davanti al nome`() {
        assertEntry("2 kg mele", "mele", 2.0, QuantityUnit.KG)
        assertEntry("500g farina", "farina", 500.0, QuantityUnit.G)
        assertEntry("1,5 l latte", "latte", 1.5, QuantityUnit.L)
        assertEntry("2 litri di latte", "latte", 2.0, QuantityUnit.L)
        assertEntry("3 conf pasta", "pasta", 3.0, QuantityUnit.CONF)
    }

    @Test
    fun `un numero senza unita' conta i pezzi`() {
        assertEntry("6 uova", "uova", 6.0, QuantityUnit.PZ)
        assertEntry("2 x yogurt", "yogurt", 2.0, QuantityUnit.PZ)
    }

    @Test
    fun `quantita' in fondo al nome`() {
        assertEntry("mele 2 kg", "mele", 2.0, QuantityUnit.KG)
        assertEntry("mele 2kg", "mele", 2.0, QuantityUnit.KG)
        assertEntry("latte x2", "latte", 2.0, QuantityUnit.PZ)
        assertEntry("latte x 3", "latte", 3.0, QuantityUnit.PZ)
        assertEntry("uova 6", "uova", 6.0, QuantityUnit.PZ)
        assertEntry("Coca cola 1,5 l", "Coca cola", 1.5, QuantityUnit.L)
    }

    @Test
    fun `parole al posto dei numeri e unita' convertite`() {
        assertEntry("mezzo chilo di pane", "pane", 0.5, QuantityUnit.KG)
        assertEntry("2 etti prosciutto", "prosciutto", 200.0, QuantityUnit.G)
        assertEntry("33cl birra", "birra", 330.0, QuantityUnit.ML)
    }

    @Test
    fun `cio' che non e' una quantita' resta nel nome`() {
        assertEntry("Latte", "Latte", null, null)
        assertEntry("una mela", "una mela", null, null)
        assertEntry("7up", "7up", null, null)
        assertEntry("  pane   integrale ", "pane integrale", null, null)
        assertEntry("pane 0,5", "pane 0,5", null, null)
    }
}
