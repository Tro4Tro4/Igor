package com.igor.fridge.ui

import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.shopping.ShoppingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    private val foodDao = FakeFoodItemDao()
    private val shoppingDao = FakeShoppingItemDao()
    private var counter = 0

    private val foodRepository = FoodRepository(foodDao, { now }, { "f-${++counter}" })
    private val shoppingRepository = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ShoppingViewModel(shoppingRepository, foodRepository)

    @Test
    fun `mettere in frigo sposta le voci spuntate e lascia le altre`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", quantity = 2.0, unit = QuantityUnit.L)
        shoppingRepository.addIfAbsent("Pane")
        shoppingRepository.setChecked(shoppingDao.items.first { it.name == "Latte" }, checked = true)

        val vm = viewModel()
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Pane"), shoppingDao.items.map { it.name })
        val inFridge = foodDao.items.single()
        assertEquals("Latte", inFridge.name)
        assertEquals(2.0, inFridge.quantity, 0.001)
        assertEquals(QuantityUnit.L, inFridge.unit)
        assertNull(inFridge.expiryDate)
    }

    @Test
    fun `mettere in frigo eredita la catalogazione precedente`() = runTest(dispatcher) {
        val previous = foodRepository.save(
            FoodItem(
                uuid = "",
                name = "Latte",
                category = FoodCategory.LATTICINI,
                location = StorageLocation.FRIGO,
            ),
        )
        foodRepository.remove(previous, RemovalReason.CONSUMATO)
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        val created = foodDao.items.single { it.removedAt == null }
        assertEquals(FoodCategory.LATTICINI, created.category)
    }

    @Test
    fun `senza voci spuntate non succede nulla`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Pane")

        val vm = viewModel()
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, shoppingDao.items.size)
        assertTrue(foodDao.items.isEmpty())
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test
    fun `annullare rimette le voci in lista e toglie gli articoli dal frigo`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", quantity = 2.0, unit = QuantityUnit.L)
        val original = shoppingDao.items.single()
        shoppingRepository.setChecked(original, checked = true)

        val vm = viewModel()
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        val restored = shoppingDao.items.single()
        assertEquals(original.uuid, restored.uuid)
        assertEquals("Latte", restored.name)
        assertTrue(restored.isChecked)
        assertTrue(foodDao.items.none { it.removedAt == null })
    }

    @Test
    fun `si puo' annullare una volta sola`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()
        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.canUndo)

        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, shoppingDao.items.size)
    }
}
