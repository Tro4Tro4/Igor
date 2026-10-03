package com.igor.fridge.ui

import com.igor.fridge.data.FakeSavedListDao
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.repository.SavedListRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.shopping.SavedListsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SavedListsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.ofEpochMilli(1_774_000_000_000)
    private var counter = 0

    private val shoppingDao = FakeShoppingItemDao()
    private val savedDao = FakeSavedListDao()
    private val shoppingRepository = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })
    private val savedListRepository = SavedListRepository(savedDao, { now }, { "l-${++counter}" })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SavedListsViewModel(savedListRepository, shoppingRepository)

    @Test
    fun `salvare e ricaricare riporta in lista cio' che manca`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.addIfAbsent("Pane")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.saveCurrent("Settimanale")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Lista “Settimanale” salvata", vm.uiState.value.message)

        // La spesa e' stata fatta: resta solo il pane.
        shoppingRepository.delete(shoppingDao.items.single { it.name == "Latte" })
        val list = vm.uiState.first { it.lists.isNotEmpty() }.lists.single()

        vm.load(list)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("Latte", "Pane"), shoppingDao.items.map { it.name }.toSet())
        assertEquals("1 prodotto aggiunto da “Settimanale”", vm.uiState.value.message)
    }

    @Test
    fun `una lista vuota non si salva`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.saveCurrent("Niente")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "La lista della spesa è vuota: non c’è niente da salvare",
            vm.uiState.value.message,
        )
        assertEquals(0, savedDao.lists.size)
    }

    @Test
    fun `eliminare una lista salvata non tocca la lista attuale`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.saveCurrent("Settimanale")
        dispatcher.scheduler.advanceUntilIdle()
        val list = vm.uiState.first { it.lists.isNotEmpty() }.lists.single()

        vm.delete(list)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, savedDao.lists.size)
        assertEquals(listOf("Latte"), shoppingDao.items.map { it.name })
        assertFalse(vm.uiState.value.lists.any { it.name == "Settimanale" })
    }
}
