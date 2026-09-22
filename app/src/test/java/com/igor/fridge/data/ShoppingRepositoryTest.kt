package com.igor.fridge.data

import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import com.igor.fridge.data.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DAO in memoria: la logica anti-duplicati sta nel repository, non nel database. */
private class FakeShoppingItemDao : ShoppingItemDao {
    val items = mutableListOf<ShoppingItem>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<ShoppingItem>> = flowOf(items.toList())

    override suspend fun findByName(name: String): ShoppingItem? =
        items.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun upsert(item: ShoppingItem): Long {
        val index = items.indexOfFirst { it.id == item.id && item.id != 0L }
        return if (index >= 0) {
            items[index] = item
            item.id
        } else {
            val id = nextId++
            items += item.copy(id = id)
            id
        }
    }

    override suspend fun delete(item: ShoppingItem) {
        items.removeAll { it.id == item.id }
    }

    override suspend fun deleteChecked() {
        items.removeAll { it.isChecked }
    }
}

class ShoppingRepositoryTest {

    private val dao = FakeShoppingItemDao()
    private val repository = ShoppingRepository(dao)

    @Test
    fun `aggiunge una voce nuova`() = runBlocking {
        assertTrue(repository.addIfAbsent("Latte"))
        assertEquals(1, dao.items.size)
        assertEquals("Latte", dao.items.single().name)
    }

    @Test
    fun `non duplica una voce gia presente`() = runBlocking {
        repository.addIfAbsent("Latte")
        assertFalse(repository.addIfAbsent("  Latte  "))
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `ignora un nome vuoto`() = runBlocking {
        assertFalse(repository.addIfAbsent("   "))
        assertEquals(0, dao.items.size)
    }

    @Test
    fun `rimuove solo le voci spuntate`() = runBlocking {
        repository.addIfAbsent("Latte")
        repository.addIfAbsent("Pane")
        repository.setChecked(dao.items.first { it.name == "Latte" }, checked = true)

        repository.deleteChecked()

        assertEquals(listOf("Pane"), dao.items.map { it.name })
    }
}
