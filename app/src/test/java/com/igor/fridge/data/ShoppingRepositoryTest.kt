package com.igor.fridge.data

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.SavedListItem
import com.igor.fridge.data.repository.ShoppingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `senza categoria una voce nuova la riceve dal nome`() = runTest {
        repository.addIfAbsent("Mozzarella")

        assertEquals(FoodCategory.FORMAGGI_FRESCHI, dao.items.single().category)
    }

    @Test
    fun `una categoria indicata prevale su quella proposta dal nome`() = runTest {
        repository.addIfAbsent("Latte", category = FoodCategory.BEVANDE)

        assertEquals(FoodCategory.BEVANDE, dao.items.single().category)
    }

    @Test
    fun `ripristinare una voce spuntata ne conserva i dettagli e azzera la quantita' presa`() = runTest {
        repository.addIfAbsent("Caffè", brand = "Lavazza", notes = "in grani", photoPath = "caffe.jpg")
        val item = dao.items.single()
        repository.update(item.copy(isChecked = true, purchasedQuantity = 1.0))

        repository.addIfAbsent("Caffè")

        val restored = dao.items.single()
        assertFalse(restored.isChecked)
        assertNull(restored.purchasedQuantity)
        assertEquals("Lavazza", restored.brand)
        assertEquals("in grani", restored.notes)
        assertEquals("caffe.jpg", restored.photoPath)
        assertEquals(FoodCategory.CAFFE_INFUSI, restored.category)
    }

    @Test
    fun `togliere la spunta dimentica la quantita' presa`() = runTest {
        repository.addIfAbsent("Uova", quantity = 12.0)
        repository.update(dao.items.single().copy(isChecked = true, purchasedQuantity = 6.0))

        repository.setChecked(dao.items.single(), checked = false)

        assertNull(dao.items.single().purchasedQuantity)
    }

    @Test
    fun `dopo un acquisto parziale resta da comprare la differenza`() = runTest {
        repository.addIfAbsent("Uova", quantity = 12.0)
        val bought = dao.items.single().copy(isChecked = true, purchasedQuantity = 6.0)

        repository.keepRemainder(bought, remaining = 6.0)

        val left = dao.items.single()
        assertEquals(6.0, left.quantity, 0.001)
        assertFalse(left.isChecked)
        assertNull(left.purchasedQuantity)
    }

    @Test
    fun `caricare una lista salvata aggiunge solo cio' che manca`() = runTest {
        repository.addIfAbsent("Latte")
        val saved = listOf(
            SavedListItem(uuid = "a", listUuid = "l", position = 0, name = "Latte"),
            SavedListItem(
                uuid = "b",
                listUuid = "l",
                position = 1,
                name = "Pasta",
                quantity = 2.0,
                unit = QuantityUnit.CONF,
                category = FoodCategory.DISPENSA,
                brand = "De Cecco",
            ),
        )

        val added = repository.addAll(saved)

        assertEquals(1, added)
        assertEquals(2, dao.items.size)
        val pasta = dao.items.single { it.name == "Pasta" }
        assertEquals(2.0, pasta.quantity, 0.001)
        assertEquals(QuantityUnit.CONF, pasta.unit)
        assertEquals("De Cecco", pasta.brand)
    }

    @Test
    fun `le foto in uso sono quelle delle voci in lista`() = runTest {
        repository.addIfAbsent("Latte", photoPath = "latte.jpg")
        repository.addIfAbsent("Pane")

        assertEquals(setOf("latte.jpg"), repository.photoNames())
    }
}
