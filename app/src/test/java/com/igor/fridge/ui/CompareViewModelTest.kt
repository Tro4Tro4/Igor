package com.igor.fridge.ui

import com.igor.fridge.data.FakePriceRecordDao
import com.igor.fridge.data.FakeProductCodeDao
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.openprices.CommunityLocation
import com.igor.fridge.data.openprices.CommunityPrice
import com.igor.fridge.data.openprices.OpenPricesException
import com.igor.fridge.data.prefs.OpenPricesSettings
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.Purchase
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.prices.PriceSource
import com.igor.fridge.ui.compare.CompareViewModel
import com.igor.fridge.ui.compare.communityObservations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.ofEpochMilli(1_774_000_000_000)
    private val today = LocalDate.of(2026, 10, 3)
    private var counter = 0

    private val shoppingDao = FakeShoppingItemDao()
    private val shopping = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })
    private val prices = PriceRepository(FakePriceRecordDao(), FakeProductCodeDao(), { now }, { "p-${++counter}" })
    private val settings = MutableStateFlow(OpenPricesSettings())

    private fun communityPrice(cents: Long, store: String, country: String = "IT", date: LocalDate = today) =
        CommunityPrice(
            priceCents = cents,
            currency = "EUR",
            date = date,
            isDiscounted = false,
            location = CommunityLocation(1, "NODE", store, brand = store, countryCode = country),
            productName = null,
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun seed() {
        shopping.addIfAbsent("Latte")
        shopping.addIfAbsent("Biscotti")
        prices.record(listOf(Purchase("Latte", 1.0, QuantityUnit.PZ, 129)), "Esselunga", today.minusDays(3))
        prices.record(listOf(Purchase("Latte", 1.0, QuantityUnit.PZ, 99)), "Lidl", today.minusDays(2))
    }

    @Test
    fun `senza rete confronta i negozi dai propri scontrini`() = runTest(dispatcher) {
        seed()
        val vm = CompareViewModel(shopping, prices, settings, today = { today }, computeDispatcher = dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { !it.isLoading }
        val comparison = state.comparison!!
        assertEquals(2, state.itemCount)
        assertEquals(listOf("Lidl", "Esselunga"), comparison.stores.map { it.store })
        assertEquals(99L, comparison.splitCents)
        assertFalse(state.communityEnabled)
    }

    @Test
    fun `con Open Prices aggiunge i prezzi della comunita' delle voci con codice a barre`() = runTest(dispatcher) {
        seed()
        prices.setBarcode("biscotti", "4006381333931")
        settings.value = OpenPricesSettings(enabled = true)
        val asked = mutableListOf<String>()
        val vm = CompareViewModel(
            shopping,
            prices,
            settings,
            fetchCommunity = { code ->
                asked += code
                listOf(communityPrice(249, "Tigros"), communityPrice(199, "Eurospin", country = "FR"))
            },
            today = { today },
            computeDispatcher = dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(1, vm.uiState.first { !it.isLoading && it.itemsWithBarcode == 1 }.itemsWithBarcode)

        vm.loadCommunityPrices()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("4006381333931"), asked)
        assertTrue(state.communityLoaded)
        val biscotti = state.comparison!!.quotes.single { it.item.name == "Biscotti" }
        // Il prezzo francese resta fuori dal confronto.
        assertEquals(listOf("Tigros"), biscotti.byStore.map { it.first.store })
        assertEquals(PriceSource.COMMUNITY, biscotti.cheapest!!.first.source)
    }

    @Test
    fun `un errore di rete si dice e il confronto resta`() = runTest(dispatcher) {
        seed()
        prices.setBarcode("latte", "4006381333931")
        settings.value = OpenPricesSettings(enabled = true)
        val vm = CompareViewModel(
            shopping,
            prices,
            settings,
            fetchCommunity = { throw OpenPricesException("Open Prices non raggiungibile: controlla la connessione") },
            today = { today },
            computeDispatcher = dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }

        vm.loadCommunityPrices()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Open Prices non raggiungibile: controlla la connessione", vm.uiState.value.message)
        assertEquals(2, vm.uiState.value.comparison!!.stores.size)
    }

    @Test
    fun `se Open Prices non risponde l'attesa finisce e i prezzi arrivati restano`() = runTest(dispatcher) {
        seed()
        prices.setBarcode("latte", "4006381333931")
        prices.setBarcode("biscotti", "8000500310427")
        settings.value = OpenPricesSettings(enabled = true)
        val vm = CompareViewModel(
            shopping,
            prices,
            settings,
            fetchCommunity = { code ->
                if (code == "8000500310427") awaitCancellation()
                listOf(communityPrice(249, "Tigros"))
            },
            today = { today },
            computeDispatcher = dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }

        vm.loadCommunityPrices()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoadingCommunity)
        assertEquals("Open Prices risponde troppo lentamente: riprova più tardi", state.message)
        assertTrue(state.comparison!!.stores.any { it.store == "Tigros" })
    }

    @Test
    fun `i prezzi della comunita' troppo vecchi, esteri o senza negozio non contano`() {
        val list = listOf(
            communityPrice(100, "Lidl"),
            communityPrice(100, "Lidl", date = today.minusDays(400)),
            communityPrice(100, "Lidl", country = "DE"),
            communityPrice(100, "Lidl").copy(location = null),
            communityPrice(100, "Lidl").copy(currency = "CHF"),
        )

        assertEquals(1, communityObservations(list, today).size)
    }

    @Test
    fun `per ogni voce propone un simile che costa meno al litro`() = runTest(dispatcher) {
        shopping.addIfAbsent("Latte PS Granarolo")
        prices.record(listOf(Purchase("Latte PS Granarolo", 1.0, QuantityUnit.L, 179)), "Coop", today.minusDays(3))
        prices.record(listOf(Purchase("Latte PS Esselunga", 1.0, QuantityUnit.L, 165)), "Esselunga", today.minusDays(5))
        settings.value = OpenPricesSettings(enabled = true)
        val vm = CompareViewModel(
            shopping,
            prices,
            settings,
            findSimilar = { name, _ ->
                com.igor.fridge.data.openprices.SimilarProductSearch.Result(
                    listOf(
                        communityPrice(99, "Lidl").copy(
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
                    byCategory = name == "Latte PS Granarolo",
                )
            },
            today = { today },
            computeDispatcher = dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        val item = vm.uiState.first { !it.isLoading }.comparison!!.quotes.single().item

        // Senza rete, il simile viene dai tuoi scontrini.
        val offline = vm.uiState.value.alternatives.getValue(item.uuid)
        assertEquals("Latte PS Esselunga", offline.offer.productName)
        assertEquals(7, offline.savingPercent)

        vm.loadCommunityPrices()
        dispatcher.scheduler.advanceUntilIdle()

        val online = vm.uiState.value.alternatives.getValue(item.uuid)
        assertEquals("Latte parzialmente scremato", online.offer.productName)
        assertEquals("Lidl", online.offer.privateLabel)
        assertEquals(44, online.savingPercent)
    }
}
