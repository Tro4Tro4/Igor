package com.igor.fridge.data

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.repository.FoodRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FoodRepositoryTest {

    private val dao = FakeFoodItemDao()
    private var now = Instant.ofEpochMilli(1_774_000_000_000)
    private var counter = 0
    private val repository = FoodRepository(
        dao = dao,
        clock = { now },
        newUuid = { "uuid-${++counter}" },
    )

    private val today = LocalDate.of(2026, 4, 1)

    @Test
    fun `un articolo nuovo riceve un identificatore e un timbro`() = runTest {
        val saved = repository.save(FoodItem(uuid = "", name = "Latte"))

        assertEquals("uuid-1", saved.uuid)
        assertEquals(now, saved.updatedAt)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `un articolo esistente conserva l'identificatore e aggiorna il timbro`() = runTest {
        val first = repository.save(FoodItem(uuid = "", name = "Latte"))
        now = now.plusSeconds(3600)

        val second = repository.save(first.copy(name = "Latte intero"))

        assertEquals(first.uuid, second.uuid)
        assertEquals(now, second.updatedAt)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `rimuovere marca il motivo invece di cancellare`() = runTest {
        val saved = repository.save(FoodItem(uuid = "", name = "Latte"))

        repository.remove(saved, RemovalReason.CONSUMATO)

        val stored = dao.findByUuid(saved.uuid)
        assertNotNull(stored)
        assertEquals(RemovalReason.CONSUMATO, stored?.removalReason)
        assertEquals(now, stored?.removedAt)
    }

    @Test
    fun `ripristinare riporta l'articolo in inventario`() = runTest {
        val saved = repository.save(FoodItem(uuid = "", name = "Latte"))
        repository.remove(saved, RemovalReason.ERRORE)

        repository.restore(dao.findByUuid(saved.uuid)!!)

        val stored = dao.findByUuid(saved.uuid)
        assertNull(stored?.removedAt)
        assertNull(stored?.removalReason)
    }

    @Test
    fun `le scadenze comprendono la soglia e gli articoli gia scaduti`() = runTest {
        repository.save(FoodItem(uuid = "", name = "Scaduto", expiryDate = today.minusDays(2)))
        repository.save(FoodItem(uuid = "", name = "Sul limite", expiryDate = today.plusDays(3)))
        repository.save(FoodItem(uuid = "", name = "Oltre", expiryDate = today.plusDays(4)))

        val found = repository.findExpiring(today, withinDays = 3)

        assertEquals(listOf("Scaduto", "Sul limite"), found.map { it.name }.sorted())
    }
}
