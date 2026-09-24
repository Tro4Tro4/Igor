package com.igor.fridge.ui

import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.inventory.InventoryFilter
import com.igor.fridge.ui.inventory.InventoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private class FakeShoppingDao : ShoppingItemDao {
    val items = mutableListOf<ShoppingItem>()
    override fun observeAll(): Flow<List<ShoppingItem>> = flowOf(items.toList())
    override suspend fun findByName(name: String): ShoppingItem? =
        items.firstOrNull { it.name.equals(name, ignoreCase = true) }
    override suspend fun upsert(item: ShoppingItem) {
        items.removeAll { it.uuid == item.uuid }
        items += item
    }
    override suspend fun delete(item: ShoppingItem) { items.removeAll { it.uuid == item.uuid } }
    override suspend fun deleteChecked() { items.removeAll { it.isChecked } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 4, 1)
    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    private val foodDao = FakeFoodItemDao()
    private val shoppingDao = FakeShoppingDao()
    private var counter = 0

    private val foodRepository = FoodRepository(foodDao, { now }, { "uuid-${++counter}" })
    private val shoppingRepository = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })

    private val warningDays = MutableStateFlow(3)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = InventoryViewModel(
        foodRepository = foodRepository,
        shoppingRepository = shoppingRepository,
        warningDays = warningDays,
        today = flowOf(today),
    )

    private suspend fun seed() {
        foodRepository.save(FoodItem(uuid = "", name = "Scaduto", expiryDate = today.minusDays(1)))
        foodRepository.save(FoodItem(uuid = "", name = "In scadenza", expiryDate = today.plusDays(2)))
        foodRepository.save(FoodItem(uuid = "", name = "Fresco", expiryDate = today.plusDays(30)))
        foodRepository.save(
            FoodItem(
                uuid = "",
                name = "Senza data",
                location = StorageLocation.DISPENSA,
            ),
        )
    }

    @Test
    fun `i conteggi distinguono gli stati`() = runTest(dispatcher) {
        seed()
        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(4, state.totalCount)
        assertEquals(1, state.expiredCount)
        assertEquals(1, state.expiringCount)
        assertEquals(1, state.noDateCount)
    }

    @Test
    fun `il filtro senza data mostra solo gli articoli senza scadenza`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.onFilterChange(InventoryFilter.SENZA_DATA)

        val state = vm.uiState.first { it.filter == InventoryFilter.SENZA_DATA && !it.isLoading }

        assertEquals(listOf("Senza data"), state.items.map { it.name })
    }

    @Test
    fun `la ricerca ignora maiuscole e spazi`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.onQueryChange("  fresco ")

        val state = vm.uiState.first { it.query.isNotBlank() && !it.isLoading }

        assertEquals(listOf("Fresco"), state.items.map { it.name })
    }

    @Test
    fun `alzare la soglia sposta un articolo fra quelli in scadenza`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.uiState.first { !it.isLoading }

        warningDays.value = 40

        // Solo "Fresco" (scade fra 30 giorni) attraversa la nuova soglia: "Scaduto" resta
        // SCADUTO a prescindere da warningDays (expiryStatus lo verifica per primo), quindi
        // il conteggio in scadenza passa da 1 ("In scadenza") a 2, non a 3.
        val state = vm.uiState.first { it.warningDays == 40 }
        assertEquals(2, state.expiringCount)
    }

    @Test
    fun `consumare toglie dall'inventario e mette in lista`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }

        vm.consume(item)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Fresco"), shoppingDao.items.map { it.name })
        assertEquals(3, vm.uiState.first { it.totalCount == 3 }.totalCount)
    }
}
