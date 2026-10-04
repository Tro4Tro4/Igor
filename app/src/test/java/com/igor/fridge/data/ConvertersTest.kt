package com.igor.fridge.data

import com.igor.fridge.data.local.Converters
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.RemovalReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ConvertersTest {

    private val converters = Converters()

    @Test fun `categorie esistenti e nuove restano leggibili come nomi`() {
        val old = "FRUTTA,VERDURA,PANE,CARNE,PESCE,LATTICINI,SURGELATI,DISPENSA,CONDIMENTI,BEVANDE,CASA,IGIENE,ALTRO"
        old.split(',').forEach { code ->
            assertEquals(code, converters.fromFoodCategory(converters.toFoodCategory(code)))
        }
        FoodCategory.entries.forEach { category ->
            assertEquals(category, converters.toFoodCategory(converters.fromFoodCategory(category)))
        }
    }

    @Test
    fun `l'istante sopravvive al viaggio di andata e ritorno`() {
        val instant = Instant.ofEpochMilli(1_774_000_000_000)
        val stored = converters.fromInstant(instant)
        assertEquals(instant, converters.toInstant(stored))
    }

    @Test
    fun `i valori nulli restano nulli`() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
        assertNull(converters.fromRemovalReason(null))
        assertNull(converters.toRemovalReason(null))
    }

    @Test
    fun `il motivo di rimozione viaggia come nome`() {
        assertEquals("BUTTATO", converters.fromRemovalReason(RemovalReason.BUTTATO))
        assertEquals(RemovalReason.BUTTATO, converters.toRemovalReason("BUTTATO"))
    }
}
