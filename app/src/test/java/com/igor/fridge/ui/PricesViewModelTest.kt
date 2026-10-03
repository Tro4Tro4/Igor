package com.igor.fridge.ui

import com.igor.fridge.data.FakePriceRecordDao
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.Purchase
import com.igor.fridge.ui.prices.PriceHistoryViewModel
import com.igor.fridge.ui.prices.PricesViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PricesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakePriceRecordDao()
    private var counter = 0
    private val repository = PriceRepository(dao, { Instant.ofEpochMilli(1_774_000_000_000) }, { "p-${++counter}" })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun seed() {
        repository.record(listOf(Purchase("Latte", 1.0, QuantityUnit.L, 119)), "Coop", LocalDate.of(2026, 9, 1))
        repository.record(
            listOf(Purchase("Latte", 1.0, QuantityUnit.L, 129), Purchase("Pane", 1.0, QuantityUnit.PZ, 200)),
            "Coop",
            LocalDate.of(2026, 10, 1),
        )
    }

    @Test
    fun `elenca i prodotti, filtra per nome e somma la spesa per mese`() = runTest(dispatcher) {
        seed()
        val vm = PricesViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(setOf("Latte", "Pane"), state.products.map { it.productName }.toSet())
        assertEquals(listOf(329L, 119L), state.monthly.map { it.second })

        vm.onQueryChange("lat")
        assertEquals(listOf("Latte"), vm.uiState.first { it.query == "lat" }.products.map { it.productName })
    }

    @Test
    fun `senza scontrini lo dice`() = runTest(dispatcher) {
        val vm = PricesViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        assertTrue(vm.uiState.first { !it.isLoading }.isEmpty)
    }

    @Test
    fun `la storia di un prodotto ha i punti in ordine di tempo e si puo' correggere`() = runTest(dispatcher) {
        seed()
        val vm = PriceHistoryViewModel("latte", repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(129L, state.summary?.lastCents)
        assertEquals(listOf(119L, 129L), state.chartPoints.map { it.unitPriceCents })

        vm.delete(state.records.first())
        dispatcher.scheduler.advanceUntilIdle()

        val after = vm.uiState.value
        assertEquals(119L, after.summary?.lastCents)
        assertNull(after.summary?.previousCents)
    }
}
