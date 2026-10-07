package com.igor.fridge.ui

import com.igor.fridge.data.FakePriceRecordDao
import com.igor.fridge.data.FakeProductCodeDao
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.Purchase
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.prices.PriceSource
import com.igor.fridge.ui.compare.CompareViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 7)
    private val now = Instant.parse("2026-10-07T08:00:00Z")
    private var counter = 0
    private val shopping = ShoppingRepository(FakeShoppingItemDao(), { now }, { "s-${++counter}" })
    private val prices = PriceRepository(FakePriceRecordDao(), FakeProductCodeDao(), { now }, { "p-${++counter}" })
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `confronta solo gli acquisti e segnala le voci senza prezzo`() = runTest(dispatcher) {
        shopping.addIfAbsent("Latte")
        shopping.addIfAbsent("Biscotti")
        prices.record(listOf(Purchase("Latte", 1.0, QuantityUnit.PZ, 129)), "Esselunga", today.minusDays(3))
        prices.record(listOf(Purchase("Latte", 1.0, QuantityUnit.PZ, 99)), "Lidl", today.minusDays(2))
        val vm = CompareViewModel(shopping, prices, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        val state = vm.uiState.first { !it.isLoading }
        val comparison = state.comparison!!
        assertEquals(2, state.itemCount)
        assertEquals(1, comparison.pricedItems)
        assertEquals(99L, comparison.splitCents)
        assertEquals(listOf("Lidl", "Esselunga"), comparison.stores.map { it.store })
        assertTrue(comparison.stores.all { it.covered == 1 && !it.isComplete })
        assertTrue(comparison.quotes.single { it.item.name == "Biscotti" }.byStore.isEmpty())
        assertTrue(comparison.quotes.flatMap { it.byStore }.all { it.first.source == PriceSource.MINE })
        assertEquals(today.minusDays(2), comparison.quotes.single { it.item.name == "Latte" }.cheapest!!.first.date)
    }

    @Test fun `usa il prezzo piu recente e aggiorna la stima dopo un nuovo scontrino`() = runTest(dispatcher) {
        shopping.addIfAbsent("Latte", 2.0, QuantityUnit.L)
        prices.record(listOf(Purchase("Latte", 1.0, QuantityUnit.L, 100)), "Coop", today.minusDays(3))
        val vm = CompareViewModel(shopping, prices, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(200L, vm.uiState.first { !it.isLoading }.comparison!!.splitCents)
        prices.record(listOf(Purchase("Latte", 1.0, QuantityUnit.L, 150)), "Coop", today)
        val updated = vm.uiState.first { it.comparison?.splitCents == 300L }.comparison!!
        assertTrue(updated.stores.single().isComplete)
        assertEquals(today, updated.quotes.single().cheapest!!.first.date)
    }

    @Test fun `propone prodotti simili soltanto dai propri scontrini`() = runTest(dispatcher) {
        shopping.addIfAbsent("Latte PS Granarolo")
        prices.record(listOf(Purchase("Latte PS Granarolo", 1.0, QuantityUnit.L, 179)), "Coop", today.minusDays(3))
        prices.record(listOf(Purchase("Latte PS Esselunga", 1.0, QuantityUnit.L, 165)), "Esselunga", today.minusDays(5))
        val vm = CompareViewModel(shopping, prices, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        val state = vm.uiState.first { !it.isLoading }
        val alternative = state.alternatives.values.single()
        assertEquals("Latte PS Esselunga", alternative.offer.productName)
        assertEquals(PriceSource.MINE, alternative.offer.source)
        assertEquals(7, alternative.savingPercent)
    }

    @Test fun `una lista senza acquisti non inventa totali o negozi`() = runTest(dispatcher) {
        shopping.addIfAbsent("Pane")
        val vm = CompareViewModel(shopping, prices, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        val comparison = vm.uiState.first { !it.isLoading }.comparison!!
        assertEquals(0, comparison.pricedItems)
        assertTrue(comparison.stores.isEmpty())
        assertTrue(comparison.quotes.single().byStore.isEmpty())
    }
}
