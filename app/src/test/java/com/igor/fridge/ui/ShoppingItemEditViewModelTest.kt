package com.igor.fridge.ui

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.FakePhotoStore
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.shopping.ShoppingItemEditViewModel
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

/** Robolectric solo per costruire degli Uri veri: il resto non tocca Android. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShoppingItemEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeShoppingItemDao()
    private val photos = FakePhotoStore()
    private var counter = 0
    private val repository = ShoppingRepository(
        dao = dao,
        clock = { Instant.ofEpochMilli(1_774_000_000_000) },
        newUuid = { "s-${++counter}" },
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun viewModelFor(name: String): ShoppingItemEditViewModel {
        repository.addIfAbsent(name)
        val vm = ShoppingItemEditViewModel(dao.items.single { it.name == name }.uuid, repository, photos)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `salva tutti i dettagli della voce`() = runTest(dispatcher) {
        val vm = viewModelFor("Latte")

        vm.onQuantityChange("2")
        vm.onUnitChange(QuantityUnit.L)
        vm.onBrandChange("  Granarolo ")
        vm.onNotesChange("senza lattosio")
        vm.onCategoryChange(FoodCategory.BEVANDE)
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = dao.items.single()
        assertEquals(2.0, saved.quantity, 0.001)
        assertEquals(QuantityUnit.L, saved.unit)
        assertEquals("Granarolo", saved.brand)
        assertEquals("senza lattosio", saved.notes)
        assertEquals(FoodCategory.BEVANDE, saved.category)
        assertFalse(saved.isChecked)
        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun `indicare la quantita' presa mette la voce nel carrello`() = runTest(dispatcher) {
        val vm = viewModelFor("Uova")

        vm.onQuantityChange("12")
        vm.onPurchasedChange("6")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = dao.items.single()
        assertEquals(6.0, saved.purchasedQuantity!!, 0.001)
        assertTrue(saved.isChecked)
    }

    @Test
    fun `preso tutto copia la quantita' da comprare`() = runTest(dispatcher) {
        val vm = viewModelFor("Uova")

        vm.onQuantityChange("1,5")
        vm.purchaseAll()

        assertEquals("1,5", vm.uiState.value.purchasedText)
    }

    @Test
    fun `una quantita' presa non valida blocca il salvataggio`() = runTest(dispatcher) {
        val vm = viewModelFor("Uova")

        vm.onPurchasedChange("tante")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.purchasedError)
        assertFalse(vm.uiState.value.isDone)
        assertNull(dao.items.single().purchasedQuantity)
    }

    @Test
    fun `i pulsanti cambiano la quantita' senza scendere sotto uno`() = runTest(dispatcher) {
        val vm = viewModelFor("Uova")

        vm.stepQuantity(1)
        vm.stepQuantity(1)
        assertEquals("3", vm.uiState.value.quantityText)

        repeat(5) { vm.stepQuantity(-1) }
        assertEquals("1", vm.uiState.value.quantityText)
    }

    @Test
    fun `la foto scelta entra nella voce solo al salvataggio`() = runTest(dispatcher) {
        val vm = viewModelFor("Caffè")
        photos.nextImportName = "caffe.jpg"

        vm.onPhotoChosen(Uri.parse("content://galleria/1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("caffe.jpg", vm.uiState.value.photoName)
        assertNull(dao.items.single().photoPath)

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("caffe.jpg", dao.items.single().photoPath)
    }

    @Test
    fun `una foto illeggibile lo dice e non cambia nulla`() = runTest(dispatcher) {
        val vm = viewModelFor("Caffè")
        photos.nextImportName = null

        vm.onPhotoChosen(Uri.parse("content://galleria/rotta"))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.photoName)
        assertEquals("Impossibile leggere la foto", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isImportingPhoto)
    }

    @Test
    fun `una voce sparita chiude la schermata`() = runTest(dispatcher) {
        val vm = ShoppingItemEditViewModel("inesistente", repository, photos)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun `prezzo e negozio si salvano, e i negozi gia' usati si propongono`() = runTest(dispatcher) {
        repository.addIfAbsent("Pane", store = "Forno Rossi")
        val vm = viewModelFor("Latte")

        assertEquals(listOf("Forno Rossi"), vm.uiState.value.knownStores)
        vm.onPriceChange("1,29")
        vm.onStoreChange(" Esselunga ")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = dao.items.single { it.name == "Latte" }
        assertEquals(129L, saved.unitPriceCents)
        assertEquals("Esselunga", saved.store)
    }

    @Test
    fun `un prezzo non valido blocca il salvataggio`() = runTest(dispatcher) {
        val vm = viewModelFor("Latte")

        vm.onPriceChange("caro")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.priceError)
        assertFalse(vm.uiState.value.isDone)
    }

    @Test
    fun `l'ultimo prezzo pagato si mostra e si usa con la sua unita'`() = runTest(dispatcher) {
        repository.addIfAbsent("Mele")
        val paid = com.igor.fridge.data.local.PriceRecord(
            uuid = "p",
            productKey = "mele",
            productName = "Mele",
            purchasedOn = java.time.LocalDate.of(2026, 10, 1),
            store = "Mercato",
            quantity = 1.5,
            unit = QuantityUnit.KG,
            totalCents = 300,
            unitPriceCents = 200,
            referenceUnit = QuantityUnit.KG,
        )
        val vm = ShoppingItemEditViewModel(dao.items.single().uuid, repository, photos) { paid }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(paid, vm.uiState.value.lastPaid)
        vm.useLastPaid()

        assertEquals("2,00", vm.uiState.value.priceText)
        assertEquals(QuantityUnit.KG, vm.uiState.value.unit)
    }
}
