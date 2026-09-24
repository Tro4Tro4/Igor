package com.igor.fridge.ui

import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.ui.edit.EditItemViewModel
import com.igor.fridge.ui.edit.NEW_ITEM_UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EditItemViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    private val dao = FakeFoodItemDao()
    private var counter = 0
    private val repository = FoodRepository(dao, { now }, { "uuid-${++counter}" })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(uuid: String) = EditItemViewModel(uuid, repository)

    private suspend fun seedLatte(): FoodItem = repository.save(
        FoodItem(
            uuid = "",
            name = "Latte",
            category = FoodCategory.LATTICINI,
            expiryDate = LocalDate.of(2026, 4, 10),
            notes = "scaffale in alto",
        ),
    )

    @Test
    fun `salvare un articolo esistente ne conserva l'identificatore`() = runTest(dispatcher) {
        val saved = seedLatte()
        val vm = viewModel(saved.uuid)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onNameChange("Latte intero")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val stored = dao.items.single()
        assertEquals(saved.uuid, stored.uuid)
        assertEquals("Latte intero", stored.name)
        assertEquals(FoodCategory.LATTICINI, stored.category)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `salvare non riporta in inventario un articolo rimosso`() = runTest(dispatcher) {
        val saved = seedLatte()
        val vm = viewModel(saved.uuid)
        dispatcher.scheduler.advanceUntilIdle()

        // L'articolo viene consumato mentre la schermata di modifica e' aperta.
        repository.remove(saved, RemovalReason.CONSUMATO)

        vm.onNameChange("Latte intero")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        // Ricostruire il FoodItem da zero invece di copiarlo riazzererebbe removedAt e
        // removalReason: l'articolo tornerebbe in inventario e la sua storia sparirebbe.
        val stored = dao.items.single()
        assertEquals("Latte intero", stored.name)
        assertNotNull(stored.removedAt)
        assertEquals(RemovalReason.CONSUMATO, stored.removalReason)
    }

    @Test
    fun `salvare un articolo nuovo lo inserisce con un identificatore generato`() =
        runTest(dispatcher) {
            val vm = viewModel(NEW_ITEM_UUID)

            vm.onNameChange("Pane")
            vm.onQuantityChange("2")
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = dao.items.single()
            assertEquals("uuid-1", stored.uuid)
            assertEquals("Pane", stored.name)
            assertEquals(2.0, stored.quantity, 0.001)
            assertNull(stored.removedAt)
        }

    @Test
    fun `eliminare dalla modifica segna il motivo ERRORE`() = runTest(dispatcher) {
        val saved = seedLatte()
        val vm = viewModel(saved.uuid)
        dispatcher.scheduler.advanceUntilIdle()

        vm.delete()
        dispatcher.scheduler.advanceUntilIdle()

        val stored = dao.items.single()
        assertNotNull(stored.removedAt)
        assertEquals(RemovalReason.ERRORE, stored.removalReason)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `un nome vuoto segnala l'errore e non salva`() = runTest(dispatcher) {
        val vm = viewModel(NEW_ITEM_UUID)

        vm.onNameChange("   ")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.isSaved)
        assertTrue(dao.items.isEmpty())
    }

    @Test
    fun `una quantita non positiva segnala l'errore e non salva`() = runTest(dispatcher) {
        val vm = viewModel(NEW_ITEM_UUID)

        vm.onNameChange("Pane")
        vm.onQuantityChange("0")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.quantityError)
        assertFalse(vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.isSaved)
        assertTrue(dao.items.isEmpty())
    }
}
