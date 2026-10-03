package com.igor.fridge.data

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.repository.SavedListRepository
import com.igor.fridge.data.repository.SavedListRepository.SaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SavedListRepositoryTest {

    private val dao = FakeSavedListDao()
    private var counter = 0
    private val repository = SavedListRepository(
        dao = dao,
        clock = { Instant.ofEpochMilli(1_774_000_000_000) },
        newUuid = { "uuid-${++counter}" },
    )

    private val latte = ShoppingItem(
        uuid = "s1",
        name = "Latte",
        quantity = 2.0,
        unit = QuantityUnit.L,
        category = FoodCategory.LATTICINI,
        brand = "Granarolo",
        notes = "senza lattosio",
        photoPath = "latte.jpg",
        isChecked = true,
        purchasedQuantity = 1.0,
    )
    private val pane = ShoppingItem(uuid = "s2", name = "Pane", category = FoodCategory.PANE)

    @Test
    fun `salva cosa comprare, non lo stato dell'acquisto`() = runTest {
        assertEquals(SaveResult.CREATED, repository.save("Settimanale", listOf(latte, pane)))

        val list = dao.lists.single()
        val items = repository.itemsOf(list.uuid)
        assertEquals(listOf("Latte", "Pane"), items.map { it.name })
        val savedLatte = items.first()
        assertEquals(2.0, savedLatte.quantity, 0.001)
        assertEquals(QuantityUnit.L, savedLatte.unit)
        assertEquals(FoodCategory.LATTICINI, savedLatte.category)
        assertEquals("Granarolo", savedLatte.brand)
        assertEquals("senza lattosio", savedLatte.notes)
        assertEquals("latte.jpg", savedLatte.photoPath)
    }

    @Test
    fun `lo stesso nome sovrascrive la lista senza sdoppiarla`() = runTest {
        repository.save("Settimanale", listOf(latte, pane))
        val firstUuid = dao.lists.single().uuid

        val result = repository.save("  settimanale ", listOf(pane))

        assertEquals(SaveResult.REPLACED, result)
        assertEquals(firstUuid, dao.lists.single().uuid)
        assertEquals(listOf("Pane"), repository.itemsOf(firstUuid).map { it.name })
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `una lista vuota o senza nome non si salva`() = runTest {
        assertEquals(SaveResult.NOTHING_TO_SAVE, repository.save("Vuota", emptyList()))
        assertEquals(SaveResult.NOTHING_TO_SAVE, repository.save("   ", listOf(pane)))
        assertTrue(dao.lists.isEmpty())
    }

    @Test
    fun `eliminare una lista ne elimina le voci`() = runTest {
        repository.save("Settimanale", listOf(latte, pane))
        repository.save("Grigliata", listOf(pane))
        val settimanale = dao.lists.first { it.name == "Settimanale" }

        repository.delete(settimanale.uuid)

        assertEquals(listOf("Grigliata"), dao.lists.map { it.name })
        assertTrue(dao.items.none { it.listUuid == settimanale.uuid })
    }

    @Test
    fun `l'elenco riporta quante voci ha ogni lista, in ordine di nome`() = runTest {
        repository.save("settimanale", listOf(latte, pane))
        repository.save("Grigliata", listOf(pane))

        val summaries = repository.observeSummaries().first()

        assertEquals(listOf("Grigliata", "settimanale"), summaries.map { it.name })
        assertEquals(listOf(1, 2), summaries.map { it.itemCount })
    }
}
