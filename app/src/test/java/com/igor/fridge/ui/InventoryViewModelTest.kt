package com.igor.fridge.ui

import androidx.lifecycle.SavedStateHandle
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.inventory.InventoryFilter
import com.igor.fridge.ui.inventory.InventoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
class InventoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 4, 1)
    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    private val foodDao = FakeFoodItemDao()
    private val shoppingDao = FakeShoppingItemDao()
    private var counter = 0

    private val foodRepository = FoodRepository(foodDao, { now }, { "uuid-${++counter}" })
    private val shoppingRepository = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })

    private val warningDays = MutableStateFlow(3)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(), dateFlow: Flow<LocalDate> = flowOf(today)) = InventoryViewModel(
        foodRepository = foodRepository,
        shoppingRepository = shoppingRepository,
        warningDays = warningDays,
        today = dateFlow,
        savedStateHandle = handle,
        computeDispatcher = dispatcher,
    )

    @Test fun `il ripristino mantiene i criteri dal primo stato e non lo snackbar`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("inventory.query" to "prosciutto",
            "inventory.filter" to "IN_SCADENZA", "inventory.location" to "FRIGO",
            "inventory.category" to "SALUMI"))
        val vm = viewModel(handle)
        assertEquals("prosciutto", vm.uiState.value.query)
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(FoodCategory.SALUMI, state.category)
        assertEquals(StorageLocation.FRIGO, state.location)
        assertEquals(InventoryFilter.IN_SCADENZA, state.filter)
        assertNull(state.message)
        vm.resetFilters()
        val reset = vm.uiState.first { it.query.isEmpty() && it.category == null }
        assertEquals(InventoryFilter.TUTTI, reset.filter)
        assertNull(reset.location)
        assertEquals("", handle.get<String>("inventory.query"))
    }

    @Test fun `cambiare criteri li salva per una nuova istanza`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onQueryChange("marca")
        vm.onCategoryChange(FoodCategory.BISCOTTI)
        vm.onLocationChange(StorageLocation.DISPENSA)
        vm.onFilterChange(InventoryFilter.SENZA_DATA)
        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        val state = restored.uiState.first { !it.isLoading }
        assertEquals("marca", state.query)
        assertEquals(FoodCategory.BISCOTTI, state.category)
        assertEquals(StorageLocation.DISPENSA, state.location)
        assertEquals(InventoryFilter.SENZA_DATA, state.filter)
    }

    @Test fun `codici sconosciuti nello stato salvato usano i filtri predefiniti`() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("inventory.filter" to "futuro",
            "inventory.category" to "futuro", "inventory.location" to "futuro")))
        val state = vm.uiState.first { !it.isLoading }
        assertNull(state.category)
        assertNull(state.location)
        assertEquals(InventoryFilter.TUTTI, state.filter)
    }

    @Test fun `la categoria rimane selezionata dopo la rimozione e osserva nuovi dati`() = runTest(dispatcher) {
        val item = foodRepository.save(FoodItem("", "Prosciutto", category=FoodCategory.SALUMI))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onCategoryChange(FoodCategory.SALUMI)
        vm.uiState.first { !it.isLoading && it.items.size == 1 }
        vm.delete(item)
        runCurrent()
        assertEquals(FoodCategory.SALUMI, vm.uiState.value.category)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(listOf(FoodCategory.SALUMI), vm.uiState.value.availableCategories)
        foodRepository.save(FoodItem("", "Speck", category=FoodCategory.SALUMI))
        runCurrent()
        assertEquals(listOf("Speck"), vm.uiState.value.items.map { it.name })
    }

    @Test fun `il giorno e la soglia aggiornano sezioni e conteggi filtrati`() = runTest(dispatcher) {
        val dates = MutableStateFlow(today)
        foodRepository.save(FoodItem("", "Salame", category=FoodCategory.SALUMI,
            brand="Marca", expiryDate=today))
        foodRepository.save(FoodItem("", "Biscotti", category=FoodCategory.BISCOTTI))
        val vm = viewModel(dateFlow=dates)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onQueryChange("marca")
        vm.uiState.first { !it.isLoading && it.query == "marca" }
        assertEquals(1, vm.uiState.value.matchingCount)
        assertEquals(1, vm.uiState.value.expiringCount)
        assertEquals(0, vm.uiState.value.noDateCount)
        dates.value = today.plusDays(1)
        runCurrent()
        assertEquals(1, vm.uiState.value.expiredCount)
        assertEquals(0, vm.uiState.value.expiringCount)
    }

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

    @Test
    fun `eliminare conserva la riga con il motivo ERRORE`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }

        vm.delete(item)
        dispatcher.scheduler.advanceUntilIdle()

        // La riga resta in database: e' la rimozione a essere logica, e il motivo deve
        // distinguere un errore di inserimento da un consumo, altrimenti le statistiche
        // sugli sprechi contano come consumato cio' che non e' mai stato mangiato.
        val stored = foodDao.items.single { it.uuid == item.uuid }
        assertNotNull(stored.removedAt)
        assertEquals(RemovalReason.ERRORE, stored.removalReason)
        assertTrue(shoppingDao.items.isEmpty())
        assertEquals(3, vm.uiState.first { it.totalCount == 3 }.totalCount)
    }

    @Test
    fun `consumare si annulla dallo snackbar`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }

        vm.consume(item)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.canUndo)

        vm.undo()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(foodDao.items.single { it.uuid == item.uuid }.removedAt)
        assertTrue(shoppingDao.items.isEmpty())
        assertEquals("Annullato", vm.uiState.value.message)
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test
    fun `un'eliminazione si annulla dallo snackbar`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }

        vm.delete(item)
        dispatcher.scheduler.advanceUntilIdle()
        vm.undo()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(foodDao.items.single { it.uuid == item.uuid }.removedAt)
        assertEquals(4, vm.uiState.value.totalCount)
    }

    @Test fun `annullare consumo ripristina la voce gia spuntata senza perderne dettagli`() = runTest(dispatcher) {
        seed()
        shoppingRepository.addIfAbsent("Fresco", notes = "originale", brand = "Marca", quantity = 4.0)
        shoppingRepository.setChecked(shoppingDao.items.single(), true)
        val original = shoppingDao.items.single()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }
        vm.consume(item); dispatcher.scheduler.advanceUntilIdle()
        vm.undo(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(original), shoppingDao.items)
        assertNull(foodDao.items.first { it.uuid == item.uuid }.removedAt)
    }
    @Test fun `undo consumo non elimina una voce modificata dopo il consumo`() = runTest(dispatcher) {
        seed(); val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }
        vm.consume(item); dispatcher.scheduler.advanceUntilIdle()
        val edited = shoppingRepository.update(shoppingDao.items.single().copy(notes = "successiva"))
        vm.undo(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(edited), shoppingDao.items)
        assertNotNull(foodDao.items.first { it.uuid == item.uuid }.removedAt)
        assertTrue(vm.uiState.value.message!!.contains("modificat"))
    }
    @Test fun `secondo consumo della stessa copia non annulla la prima operazione`() = runTest(dispatcher) {
        seed(); val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }
        vm.consume(item); dispatcher.scheduler.advanceUntilIdle()
        vm.consume(item); dispatcher.scheduler.advanceUntilIdle()
        vm.undo(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(shoppingDao.items.isEmpty())
        assertNull(foodDao.items.first { it.uuid == item.uuid }.removedAt)
    }


    @Test fun `ritorno dopo pausa lunga trova gia il nuovo inventario senza stato obsoleto`() = runTest(dispatcher) {
        seed(); val vm = viewModel()
        val observer = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent(); assertEquals(4, vm.uiState.value.totalCount)
        observer.cancel(); dispatcher.scheduler.advanceTimeBy(6000); runCurrent()
        foodRepository.save(FoodItem("", "Nuovo")); runCurrent()
        assertEquals(5, vm.uiState.value.totalCount)
    }
    @Test fun `chiusura di vecchio snackbar non cancella ultimo undo`() = runTest(dispatcher) {
        seed(); val vm = viewModel(); backgroundScope.launch { vm.uiState.collect {} }
        val items = vm.uiState.first { !it.isLoading }.items
        vm.delete(items[0]); dispatcher.scheduler.advanceUntilIdle(); val first = vm.uiState.value.messageId
        vm.delete(items[1]); dispatcher.scheduler.advanceUntilIdle()
        vm.onMessageShown(first); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.canUndo)
        vm.undo(first); dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(foodDao.items.first { it.uuid == items[1].uuid }.removedAt)
        vm.undo(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(foodDao.items.first { it.uuid == items[1].uuid }.removedAt)
    }

}
