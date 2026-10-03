package com.igor.fridge.ui

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.FakePriceRecordDao
import com.igor.fridge.data.FakeProductCodeDao
import com.igor.fridge.data.FakePhotoStore
import com.igor.fridge.data.FakeReceiptReader
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.receipt.ReceiptPhase
import com.igor.fridge.ui.receipt.ReceiptViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Robolectric solo per costruire degli Uri veri: il resto non tocca Android. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReceiptViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.ofEpochMilli(1_774_000_000_000)
    private var counter = 0

    private val reader = FakeReceiptReader()
    private val foodDao = FakeFoodItemDao()
    private val shoppingDao = FakeShoppingItemDao()
    private val foodRepository = FoodRepository(foodDao, { now }, { "f-${++counter}" })
    private val shoppingRepository = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })
    private val priceDao = FakePriceRecordDao()
    private val codeDao = FakeProductCodeDao()
    private val priceRepository = PriceRepository(priceDao, codeDao, { now }, { "p-${++counter}" })
    private val today = LocalDate.of(2026, 10, 3)

    private val photo = Uri.parse("content://scontrino/1")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ReceiptViewModel(reader, FakePhotoStore(), foodRepository, shoppingRepository, priceRepository, today = { today })

    private fun ReceiptViewModel.read(vararg lines: String) {
        reader.lines = lines.toList()
        onImageChosen(photo)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `lo scontrino diventa una bozza per prodotto con la categoria proposta`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("LATTE PS 1L  4%  1,29", "DETERSIVO PIATTI  22%  2,49", "TOTALE  3,78")

        val state = vm.uiState.value
        assertEquals(ReceiptPhase.REVIEW, state.phase)
        val (latte, detersivo) = state.drafts
        assertEquals(FoodCategory.LATTICINI, latte.category)
        assertEquals("1", latte.quantityText)
        assertEquals(QuantityUnit.L, latte.unit)
        assertEquals("1,29", latte.priceText)
        assertTrue(latte.goesToFridge)
        // Cio' che non si mangia si tiene (per il prezzo) ma non va in frigo.
        assertEquals(FoodCategory.CASA, detersivo.category)
        assertTrue(detersivo.include)
        assertFalse(detersivo.goesToFridge)
    }

    @Test
    fun `la categoria e la posizione dell'ultima volta prevalgono`() = runTest(dispatcher) {
        val previous = foodRepository.save(
            FoodItem(uuid = "", name = "Panna", category = FoodCategory.CONDIMENTI, location = StorageLocation.DISPENSA),
        )
        foodRepository.remove(previous, RemovalReason.CONSUMATO)
        val vm = viewModel()
        vm.read("PANNA  1,10")

        val draft = vm.uiState.value.drafts.single()
        assertEquals(FoodCategory.CONDIMENTI, draft.category)
        assertEquals(StorageLocation.DISPENSA, draft.location)
    }

    @Test
    fun `un prodotto a peso senza peso chiede la quantita'`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("BANANE  1,45", "MELE  2,00", "1,000 kg x 2,00")

        val (banane, mele) = vm.uiState.value.drafts
        assertTrue(banane.quantityToConfirm)
        assertEquals("", banane.quantityText)
        assertEquals(QuantityUnit.KG, banane.unit)
        assertFalse(mele.quantityToConfirm)
        assertEquals("1", mele.quantityText)

        vm.confirm()
        assertTrue(vm.uiState.value.drafts.first().quantityError)
        assertEquals(ReceiptPhase.REVIEW, vm.uiState.value.phase)
    }

    @Test
    fun `i prodotti freschi senza scadenza la chiedono prima di salvare`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("YOGURT  0,99", "PASTA 500G  1,15")

        vm.confirm()
        assertEquals(1, vm.uiState.value.missingExpiryPrompt)
        assertTrue(foodDao.items.isEmpty())

        val yogurt = vm.uiState.value.drafts.first()
        val expiry = LocalDate.of(2026, 10, 20)
        vm.onExpiryChange(yogurt.id, expiry)
        vm.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReceiptPhase.DONE, vm.uiState.value.phase)
        assertEquals(expiry, foodDao.items.single { it.name == "Yogurt" }.expiryDate)
        assertNull(foodDao.items.single { it.name == "Pasta" }.expiryDate)
        assertEquals("2 prodotti aggiunti in frigo; 2 prezzi registrati", vm.uiState.value.summary)
    }

    @Test
    fun `si puo' salvare anche senza scadenza, se lo si conferma`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("YOGURT  0,99")

        vm.confirm()
        vm.confirm(skipExpiryCheck = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(foodDao.items.single().expiryDate)
    }

    @Test
    fun `lo scontrino spunta la lista della spesa`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Mozzarella")
        shoppingRepository.addIfAbsent("Pane")
        val vm = viewModel()
        vm.read("MOZZ. FIOR DI LATTE  0,99")

        val draft = vm.uiState.value.drafts.single()
        assertEquals("Mozzarella", draft.name)
        assertEquals("Mozzarella", draft.shoppingMatch?.name)

        vm.onExpiryChange(draft.id, LocalDate.of(2026, 10, 8))
        vm.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Pane"), shoppingDao.items.map { it.name })
        assertEquals("Mozzarella", foodDao.items.single().name)
        assertEquals(
            "1 prodotto aggiunto in frigo; 1 prezzo registrato; 1 tolto dalla lista della spesa",
            vm.uiState.value.summary,
        )
    }

    @Test
    fun `le bozze escluse non entrano in frigo`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("PASTA  1,00", "RISO  2,00")
        vm.onIncludeChange(vm.uiState.value.drafts.last().id, false)

        vm.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Pasta"), foodDao.items.map { it.name })
        assertEquals(listOf("Pasta"), priceDao.records.map { it.productName })
    }

    @Test
    fun `una foto illeggibile o senza prodotti torna alla scelta`() = runTest(dispatcher) {
        val vm = viewModel()
        reader.lines = null
        vm.onImageChosen(photo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ReceiptPhase.CHOOSE, vm.uiState.value.phase)
        assertEquals("Impossibile leggere la foto", vm.uiState.value.message)

        vm.read("GRAZIE E ARRIVEDERCI")
        assertEquals(ReceiptPhase.CHOOSE, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.message!!.startsWith("Nessun prodotto riconosciuto"))
    }

    @Test
    fun `una riga persa si aggiunge a mano e il nome e' obbligatorio`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("PASTA  1,00")
        vm.addDraft()

        vm.confirm()
        assertTrue(vm.uiState.value.drafts.last().nameError)

        vm.onNameChange(vm.uiState.value.drafts.last().id, "Riso")
        vm.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("Pasta", "Riso"), foodDao.items.map { it.name }.toSet())
    }

    @Test
    fun `i prezzi si registrano con negozio e data dello scontrino, anche per cio' che non va in frigo`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.read(
                "SUPERMERCATO ESEMPIO",
                "PASTA 500G  1,15",
                "SCONTO PASTA  -0,20",
                "DETERSIVO PIATTI  2,49",
                "TOTALE  3,44",
                "28/09/2026 18:22",
            )

            assertEquals("Supermercato Esempio", vm.uiState.value.store)
            assertEquals(LocalDate.of(2026, 9, 28), vm.uiState.value.purchaseDate)

            vm.confirm()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("Pasta"), foodDao.items.map { it.name })
            val pasta = priceDao.records.single { it.productName == "Pasta" }
            assertEquals(95L, pasta.totalCents)
            // 0,95 € per 500 g fanno 1,90 €/kg.
            assertEquals(190L, pasta.unitPriceCents)
            assertEquals(QuantityUnit.KG, pasta.referenceUnit)
            assertEquals("Supermercato Esempio", pasta.store)
            assertEquals(LocalDate.of(2026, 9, 28), pasta.purchasedOn)
            assertEquals(249L, priceDao.records.single { it.productName == "Detersivo piatti" }.totalCents)
            assertEquals(
                "1 prodotto aggiunto in frigo; 2 prezzi registrati",
                vm.uiState.value.summary,
            )
        }

    @Test
    fun `negozio, data e prezzo si correggono prima di confermare`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("RISO  2,00")
        val riso = vm.uiState.value.drafts.single()

        vm.onStoreChange("Mercato")
        vm.onDateChange(LocalDate.of(2026, 10, 1))
        vm.onPriceChange(riso.id, "1,80")
        vm.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        val record = priceDao.records.single()
        assertEquals(180L, record.totalCents)
        assertEquals("Mercato", record.store)
        assertEquals(LocalDate.of(2026, 10, 1), record.purchasedOn)
    }

    @Test
    fun `senza data sullo scontrino vale oggi, e un prezzo illeggibile blocca`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("RISO  2,00")
        assertEquals(today, vm.uiState.value.purchaseDate)

        vm.onPriceChange(vm.uiState.value.drafts.single().id, "due euro")
        vm.confirm()

        assertTrue(vm.uiState.value.drafts.single().priceError)
        assertTrue(priceDao.records.isEmpty())
    }

    @Test
    fun `una riga senza prezzo entra in frigo ma non nello storico`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("RISO  2,00")
        vm.onPriceChange(vm.uiState.value.drafts.single().id, "")

        vm.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, foodDao.items.size)
        assertTrue(priceDao.records.isEmpty())
    }

    @Test
    fun `il prezzo da condividere e' per pezzo, con il numero di pezzi`() {
        val draft = com.igor.fridge.ui.receipt.ReceiptDraft(
            id = 1,
            include = true,
            name = "Yogurt",
            category = FoodCategory.LATTICINI,
            quantityText = "4",
            unit = QuantityUnit.PZ,
            priceText = "1,80",
        )

        val item = ReceiptViewModel.contributionItem(draft, "4006381333931")!!
        assertEquals(45L, item.unitPriceCents)
        assertEquals(4, item.quantity)

        val pasta = draft.copy(name = "Pasta", quantityText = "500", unit = QuantityUnit.G, priceText = "0,95")
        assertEquals(1, ReceiptViewModel.contributionItem(pasta, "4006381333931")!!.quantity)
        assertEquals(null, ReceiptViewModel.contributionItem(draft, "123"))
        assertEquals(null, ReceiptViewModel.contributionItem(draft.copy(priceText = ""), "4006381333931"))
    }

    @Test
    fun `dopo la conferma si condividono su Open Prices i prezzi con codice a barre`() = runTest(dispatcher) {
        val transport = com.igor.fridge.data.openprices.FakeTransport().apply {
            respond(
                "GET",
                "/locations",
                com.igor.fridge.data.openprices.HttpResponse(
                    200,
                    """{"items":[{"osm_id":42,"osm_type":"NODE","osm_name":"Esselunga","osm_address_city":"Milano"}]}""",
                ),
            )
            respond("POST", "/proofs/upload", com.igor.fridge.data.openprices.HttpResponse(201, """{"id":5}"""))
            respond("POST", "/prices", com.igor.fridge.data.openprices.HttpResponse(201, """{"id":6}"""))
        }
        priceRepository.setBarcode("latte", "4006381333931")
        val vm = ReceiptViewModel(
            reader,
            FakePhotoStore(),
            foodRepository,
            shoppingRepository,
            priceRepository,
            today = { today },
            openPrices = kotlinx.coroutines.flow.flowOf(
                com.igor.fridge.data.prefs.OpenPricesSettings(enabled = true, userId = "mario", token = "tok"),
            ),
            openPricesClient = com.igor.fridge.data.openprices.OpenPricesClient(transport, boundary = { "B" }),
        )
        vm.read("ESSELUNGA SPA", "LATTE  1,29", "RISO  2,00", "TOTALE  3,29", "01/10/2026")
        vm.confirm(skipExpiryCheck = true)
        dispatcher.scheduler.advanceUntilIdle()

        val done = vm.uiState.value
        assertTrue(done.canContribute)
        assertEquals(listOf("Latte"), done.contributable.map { it.name })

        vm.openContribution()
        assertEquals("Esselunga", vm.uiState.value.contribution?.query)
        vm.searchStores()
        dispatcher.scheduler.advanceUntilIdle()
        // Un solo negozio trovato: e' gia' scelto.
        assertEquals(42L, vm.uiState.value.contribution?.location?.osmId)

        vm.sendContribution()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("1 prezzo condiviso su Open Prices. Grazie!", vm.uiState.value.contribution?.result)
        val price = transport.requests.last()
        assertTrue(price.bodyText!!.contains("\"date\":\"2026-10-01\""))
        assertTrue(price.bodyText!!.contains("\"location_osm_id\":42"))
    }

    @Test
    fun `senza account Open Prices non si propone di condividere`() = runTest(dispatcher) {
        priceRepository.setBarcode("latte", "4006381333931")
        val vm = viewModel()
        vm.read("LATTE  1,29")
        vm.confirm(skipExpiryCheck = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.contributable.size)
        assertFalse(vm.uiState.value.canContribute)
    }
}
