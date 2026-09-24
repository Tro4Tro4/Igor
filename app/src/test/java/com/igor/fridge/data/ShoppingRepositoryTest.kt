package com.igor.fridge.data

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.repository.ShoppingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShoppingRepositoryTest {

    private val dao = FakeShoppingItemDao()
    private var counter = 0
    private val repository = ShoppingRepository(
        dao = dao,
        clock = { Instant.ofEpochMilli(1_774_000_000_000) },
        newUuid = { "uuid-${++counter}" },
    )

    @Test
    fun `aggiunge una voce nuova`() = runTest {
        assertTrue(repository.addIfAbsent("Latte"))
        assertEquals(1, dao.items.size)
        assertEquals("Latte", dao.items.single().name)
    }

    @Test
    fun `non crea un doppione per una voce gia presente e non spuntata`() = runTest {
        repository.addIfAbsent("Latte")

        assertFalse(repository.addIfAbsent("  Latte  "))
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `una voce gia spuntata torna da comprare`() = runTest {
        repository.addIfAbsent("Latte")
        repository.setChecked(dao.items.single(), checked = true)

        val changed = repository.addIfAbsent("Latte")

        assertTrue(changed)
        assertEquals(1, dao.items.size)
        assertFalse(dao.items.single().isChecked)
    }

    @Test
    fun `ripristinare una voce spuntata ne aggiorna quantita e unita`() = runTest {
        repository.addIfAbsent("Latte")
        repository.setChecked(dao.items.single(), checked = true)

        repository.addIfAbsent("Latte", quantity = 2.0, unit = QuantityUnit.L)

        assertEquals(2.0, dao.items.single().quantity, 0.001)
        assertEquals(QuantityUnit.L, dao.items.single().unit)
    }

    @Test
    fun `ignora un nome vuoto`() = runTest {
        assertFalse(repository.addIfAbsent("   "))
        assertEquals(0, dao.items.size)
    }

    @Test
    fun `rimuove solo le voci spuntate`() = runTest {
        repository.addIfAbsent("Latte")
        repository.addIfAbsent("Pane")
        repository.setChecked(dao.items.first { it.name == "Latte" }, checked = true)

        repository.deleteChecked()

        assertEquals(listOf("Pane"), dao.items.map { it.name })
    }
}
