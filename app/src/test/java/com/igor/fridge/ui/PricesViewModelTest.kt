package com.igor.fridge.ui

import com.igor.fridge.data.FakePriceRecordDao
import com.igor.fridge.data.FakeProductCodeDao
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
    private val repository = PriceRepository(
        dao,
        FakeProductCodeDao(),
        { Instant.ofEpochMilli(1_774_000_000_000) },
        { "p-${++counter}" },
    )

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
        val vm = PricesViewModel(repository, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(setOf("Latte", "Pane"), state.products.map { it.productName }.toSet())
        assertEquals(listOf(329L, 119L), state.monthly.map { it.second })

        vm.onQueryChange("lat")
        assertEquals(listOf("Latte"), vm.uiState.first { it.query == "lat" }.products.map { it.productName })
    }

    @Test
    fun `senza scontrini lo dice`() = runTest(dispatcher) {
        val vm = PricesViewModel(repository, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }

        assertTrue(vm.uiState.first { !it.isLoading }.isEmpty)
    }

    @Test
    fun `la storia di un prodotto ha i punti in ordine di tempo e si puo' correggere`() = runTest(dispatcher) {
        seed()
        val vm = PriceHistoryViewModel("latte", repository, computeDispatcher = dispatcher)
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

    @Test
    fun `la scheda mostra i negozi dal piu' conveniente e gestisce il codice a barre`() = runTest(dispatcher) {
        repository.record(listOf(Purchase("Pane", 1.0, QuantityUnit.PZ, 220)), "TIGROS SPA", LocalDate.of(2026, 9, 1))
        repository.record(listOf(Purchase("Pane", 1.0, QuantityUnit.PZ, 180)), "Lidl", LocalDate.of(2026, 9, 2))
        val vm = PriceHistoryViewModel("pane", repository, computeDispatcher = dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(listOf("Lidl", "Tigros"), state.byStore.map { it.store })

        vm.setBarcode("123")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Codice a barre non valido", vm.uiState.value.message)

        vm.setBarcode("4006381333931")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("4006381333931", vm.uiState.value.barcode)
        assertEquals("4006381333931", repository.barcodeOf("pane"))
    }

    @Test
    fun `i prezzi della comunita' si scaricano per il codice a barre`() = runTest(dispatcher) {
        repository.record(listOf(Purchase("Pane", 1.0, QuantityUnit.PZ, 220)), "Tigros", LocalDate.of(2026, 9, 1))
        repository.setBarcode("pane", "4006381333931")
        val asked = mutableListOf<String>()
        val vm = PriceHistoryViewModel(
            productKey = "pane",
            priceRepository = repository,
            openPrices = kotlinx.coroutines.flow.flowOf(com.igor.fridge.data.prefs.OpenPricesSettings(enabled = true)),
            fetchCommunity = { code ->
                asked += code
                listOf(
                    com.igor.fridge.data.openprices.CommunityPrice(
                        priceCents = 199,
                        currency = "EUR",
                        date = LocalDate.of(2026, 9, 20),
                        isDiscounted = false,
                        location = com.igor.fridge.data.openprices.CommunityLocation(1, "NODE", "Esselunga", countryCode = "IT"),
                        productName = "Pane",
                    ),
                )
            },
            today = { LocalDate.of(2026, 10, 3) },
            computeDispatcher = dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it.barcode != null }

        vm.loadCommunityPrices()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("4006381333931"), asked)
        assertEquals(listOf(199L), vm.uiState.value.community?.map { it.priceCents })
    }

    @Test
    fun `la scheda propone i prodotti simili, dai tuoi scontrini e dalla comunita'`() = runTest(dispatcher) {
        repository.record(listOf(Purchase("Latte PS Granarolo", 1.0, QuantityUnit.L, 179)), "Coop", LocalDate.of(2026, 9, 1))
        repository.record(listOf(Purchase("Latte PS Esselunga", 1.0, QuantityUnit.L, 125)), "Esselunga", LocalDate.of(2026, 9, 2))
        repository.setBarcode("latte ps granarolo", "8000500310427")
        val asked = mutableListOf<Pair<String, String?>>()
        val vm = PriceHistoryViewModel(
            productKey = "latte ps granarolo",
            priceRepository = repository,
            openPrices = kotlinx.coroutines.flow.flowOf(com.igor.fridge.data.prefs.OpenPricesSettings(enabled = true)),
            findSimilar = { name, code ->
                asked += name to code
                com.igor.fridge.data.openprices.SimilarProductSearch.Result(
                    listOf(
                        com.igor.fridge.data.openprices.CommunityPrice(
                            priceCents = 99,
                            currency = "EUR",
                            date = LocalDate.of(2026, 9, 20),
                            isDiscounted = false,
                            location = com.igor.fridge.data.openprices.CommunityLocation(1, "NODE", "Lidl", countryCode = "IT"),
                            productName = "Latte parzialmente scremato",
                            product = com.igor.fridge.data.openprices.CommunityProduct(
                                code = "4056489",
                                name = "Latte parzialmente scremato",
                                brands = "Milbona",
                                quantity = 1000.0,
                                quantityUnit = QuantityUnit.ML,
                            ),
                        ),
                    ),
                    byCategory = true,
                )
            },
            today = { LocalDate.of(2026, 10, 3) },
            computeDispatcher = dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        val ready = vm.uiState.first { it.summary != null && it.barcode != null }
        assertEquals(listOf("Latte PS Esselunga"), ready.similarMine.map { it.productName })
        assertNull(ready.similarCommunity)

        vm.loadSimilarProducts()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Latte PS Granarolo" to "8000500310427"), asked)
        val community = vm.uiState.value.similarCommunity!!.single()
        assertEquals("Lidl", community.privateLabel)
        assertEquals(99L, community.unitPriceCents)
    }
}
