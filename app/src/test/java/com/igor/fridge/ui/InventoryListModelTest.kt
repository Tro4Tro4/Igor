package com.igor.fridge.ui

import com.igor.fridge.data.local.*
import com.igor.fridge.ui.inventory.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InventoryListModelTest {
    private val date = LocalDate.of(2026, 10, 4)
    private val salumi = FoodItem("s", "Prosciutto", category = FoodCategory.SALUMI,
        brand = "Marca A", expiryDate = date.plusDays(1))
    private val biscotti = FoodItem("b", "Biscotti", category = FoodCategory.BISCOTTI,
        location = StorageLocation.DISPENSA)

    @Test fun `ricerca marca categoria luogo e stato si combinano senza falsare i conteggi`() {
        val c = InventoryCriteria(query = "  marca ", category = FoodCategory.SALUMI,
            location = StorageLocation.FRIGO, filter = InventoryFilter.IN_SCADENZA)
        val model = inventoryListOf(listOf(salumi, biscotti), c, date, 3)
        assertEquals(listOf("s"), model.items.map { it.uuid })
        assertEquals(2, model.totalCount)
        assertEquals(1, model.matchingCount)
        assertEquals(1, model.expiringCount)
        assertEquals(0, model.noDateCount)
        assertTrue(inventoryListOf(listOf(salumi, biscotti), c.copy(location = StorageLocation.FREEZER), date, 3).items.isEmpty())
    }
    @Test fun `una categoria vuota selezionata resta disponibile per azzerare il filtro`() {
        val model = inventoryListOf(listOf(biscotti), InventoryCriteria(category = FoodCategory.SALUMI), date, 3)
        assertTrue(model.sections.isEmpty())
        assertEquals(listOf(FoodCategory.SALUMI, FoodCategory.BISCOTTI), model.availableCategories)
    }
    @Test fun `scadenza nome e uuid ordinano le righe prima dei senza data`() {
        val food = listOf(
            salumi.copy(uuid="n", name="A", expiryDate=null),
            salumi.copy(uuid="z", name="Z", expiryDate=date),
            salumi.copy(uuid="a2", name="A", expiryDate=date),
            salumi.copy(uuid="a1", name="A", expiryDate=date),
        )
        assertEquals(listOf("a1", "a2", "z", "n"), inventoryListOf(food, InventoryCriteria(), date, 3).items.map { it.uuid })
    }
    @Test fun `solo articoli attivi compaiono una volta e Altro resta in fondo`() {
        val food = listOf(biscotti, salumi, FoodItem("x", "Altro"),
            salumi.copy(uuid="removed", removedAt=Instant.EPOCH))
        val model = inventoryListOf(food, InventoryCriteria(), date, 3)
        assertEquals(listOf(FoodCategory.SALUMI, FoodCategory.BISCOTTI, FoodCategory.ALTRO), model.sections.map { it.category })
        assertEquals(listOf("s", "b", "x"), model.items.map { it.uuid })
        assertEquals(3, model.totalCount)
    }
    @Test fun `il conteggio dello stato precede il filtro e cambia con il giorno`() {
        val food = listOf(salumi.copy(expiryDate=date), biscotti)
        val c = InventoryCriteria(filter=InventoryFilter.SCADUTI)
        val before = inventoryListOf(food, c, date, 3)
        assertTrue(before.items.isEmpty())
        assertEquals(1, before.expiringCount)
        assertEquals(1, before.noDateCount)
        assertEquals(2, before.matchingCount)
        val after = inventoryListOf(food, c, date.plusDays(1), 3)
        assertEquals(listOf("s"), after.items.map { it.uuid })
        assertEquals(1, after.expiredCount)
    }
}
