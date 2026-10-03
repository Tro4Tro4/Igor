package com.igor.fridge.ui

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.FakePhotoStore
import com.igor.fridge.data.FakeReceiptReader
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
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

    private val photo = Uri.parse("content://scontrino/1")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReceiptViewModel(reader, FakePhotoStore(), foodRepository, shoppingRepository)

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
        assertEquals(129L, latte.priceCents)
        assertTrue(latte.include)
        // Cio' che non si mangia parte escluso dal frigo.
        assertEquals(FoodCategory.CASA, detersivo.category)
        assertFalse(detersivo.include)
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
        assertEquals("2 prodotti aggiunti in frigo", vm.uiState.value.summary)
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
            "1 prodotto aggiunto in frigo; 1 tolto dalla lista della spesa",
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
}
