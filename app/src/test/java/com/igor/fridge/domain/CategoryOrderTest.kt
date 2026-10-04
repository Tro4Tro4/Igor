package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderTest {

    @Test fun `le nuove categorie si accodano alle corsie salvate senza duplicati`() {
        val old = "BEVANDE,FRUTTA,LATTICINI,ALTRO"
        val actual = categoryOrderFrom(old)
        assertEquals(old.split(',').map(FoodCategory::valueOf), actual.take(4))
        assertEquals(FoodCategory.entries.toSet(), actual.toSet())
        assertEquals(actual.size, actual.distinct().size)
    }

    @Test
    fun `senza preferenza vale l'ordine predefinito`() {
        assertEquals(FoodCategory.entries, categoryOrderFrom(null))
        assertEquals(FoodCategory.entries, categoryOrderFrom(""))
    }

    @Test
    fun `l'ordine salvato si rilegge uguale`() {
        val order = FoodCategory.entries.reversed()
        assertEquals(order, categoryOrderFrom(order.toStoredOrder()))
    }

    @Test
    fun `nomi sconosciuti si ignorano e le categorie mancanti si accodano`() {
        val order = categoryOrderFrom("BEVANDE, INESISTENTE,FRUTTA,BEVANDE")

        assertEquals(listOf(FoodCategory.BEVANDE, FoodCategory.FRUTTA), order.take(2))
        assertEquals(FoodCategory.entries.size, order.size)
        assertEquals(FoodCategory.entries.toSet(), order.toSet())
    }

    @Test
    fun `spostare una categoria resta dentro la lista`() {
        val order = FoodCategory.entries
        assertEquals(FoodCategory.VERDURA, order.moved(FoodCategory.VERDURA, -1).first())
        assertEquals(order, order.moved(FoodCategory.FRUTTA, -1))
        assertEquals(FoodCategory.FRUTTA, order.moved(FoodCategory.FRUTTA, 100).last())
    }
}
