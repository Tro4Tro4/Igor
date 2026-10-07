package com.igor.fridge.ui

import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.ui.edit.EditItemViewModel
import com.igor.fridge.ui.edit.NEW_ITEM_UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
class EditItemViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    private val dao = FakeFoodItemDao()
    private var counter = 0
    private val repository = FoodRepository(dao, { now }, { "uuid-${++counter}" })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(uuid: String) = EditItemViewModel(uuid, repository)

    private val code = "3017620422003"
    private val onlineProduct = com.igor.fridge.data.openfoodfacts.BarcodeProduct("Latte", "Marca", FoodCategory.LATTE_PANNA)

    @Test fun `codice sconosciuto precompila online senza inventare quantita o scadenza`() = runTest(dispatcher) {
        val vm = EditItemViewModel(NEW_ITEM_UUID, repository, onlineEnabled = { true }, fetchProduct = { onlineProduct })
        vm.onBarcodeScanned(code); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Latte", vm.uiState.value.name)
        assertEquals("Marca", vm.uiState.value.brand)
        assertEquals(FoodCategory.LATTE_PANNA, vm.uiState.value.category)
        assertEquals("1", vm.uiState.value.quantityText); assertNull(vm.uiState.value.expiryDate)
        vm.save(); dispatcher.scheduler.advanceUntilIdle()
        val offline = EditItemViewModel(NEW_ITEM_UUID, repository, fetchProduct = { error("Rete vietata") })
        offline.onBarcodeScanned(code); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Latte", offline.uiState.value.name)
    }

    @Test fun `consenso spento e prodotto locale non interrogano il servizio`() = runTest(dispatcher) {
        val vm = EditItemViewModel(NEW_ITEM_UUID, repository, fetchProduct = { error("Rete vietata") })
        vm.onBarcodeScanned(code); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(com.igor.fridge.ui.edit.BarcodeLookupStatus.DISABLED, vm.uiState.value.barcodeLookup)
        repository.save(FoodItem(uuid = "", name = "Locale", barcode = code))
        val local = EditItemViewModel(NEW_ITEM_UUID, repository, onlineEnabled = { true }, fetchProduct = { error("Rete vietata") })
        local.onBarcodeScanned(code); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Locale", local.uiState.value.name)
    }

    @Test fun `modifiche durante attesa prevalgono sulla risposta online`() = runTest(dispatcher) {
        val pending = kotlinx.coroutines.CompletableDeferred<com.igor.fridge.data.openfoodfacts.BarcodeProduct?>()
        val vm = EditItemViewModel(NEW_ITEM_UUID, repository, onlineEnabled = { true }, fetchProduct = { pending.await() })
        vm.onBarcodeScanned(code); dispatcher.scheduler.runCurrent()
        vm.onNameChange("Mio latte"); vm.onBrandChange(""); vm.onCategoryChange(FoodCategory.ALTERNATIVE_VEGETALI)
        pending.complete(onlineProduct); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Mio latte", vm.uiState.value.name); assertEquals("", vm.uiState.value.brand)
        assertEquals(FoodCategory.ALTERNATIVE_VEGETALI, vm.uiState.value.category)
    }

    @Test fun `seconda scansione annulla la precedente`() = runTest(dispatcher) {
        val pending = kotlinx.coroutines.CompletableDeferred<com.igor.fridge.data.openfoodfacts.BarcodeProduct?>()
        val vm = EditItemViewModel(NEW_ITEM_UUID, repository, onlineEnabled = { true }, fetchProduct = {
            if (it == code) pending.await() else onlineProduct.copy(name = "Pane")
        })
        vm.onBarcodeScanned(code); dispatcher.scheduler.runCurrent()
        vm.onBarcodeScanned("4006381333931"); dispatcher.scheduler.runCurrent()
        pending.complete(onlineProduct); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Pane", vm.uiState.value.name); assertEquals("4006381333931", vm.uiState.value.barcode)
    }

    @Test fun `assenza errore e timeout conservano il modulo`() = runTest(dispatcher) {
        val status = com.igor.fridge.ui.edit.BarcodeLookupStatus.entries
        for (expected in listOf(status.first { it.name == "NOT_FOUND" }, status.first { it.name == "ERROR" })) {
            val vm = EditItemViewModel(NEW_ITEM_UUID, repository, onlineEnabled = { true }, fetchProduct = {
                if (expected.name == "ERROR") throw java.io.IOException() else null
            })
            vm.onNameChange("Manuale"); vm.onBarcodeScanned(code); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(expected, vm.uiState.value.barcodeLookup); assertEquals("Manuale", vm.uiState.value.name)
        }
        val vm = EditItemViewModel(NEW_ITEM_UUID, repository, onlineEnabled = { true }, fetchProduct = {
            kotlinx.coroutines.delay(20_000); onlineProduct
        })
        vm.onBarcodeScanned(code); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(com.igor.fridge.ui.edit.BarcodeLookupStatus.ERROR, vm.uiState.value.barcodeLookup)
    }

    private suspend fun seedLatte(): FoodItem = repository.save(
        FoodItem(
            uuid = "",
            name = "Latte",
            category = FoodCategory.LATTICINI,
            expiryDate = LocalDate.of(2026, 4, 10),
            notes = "scaffale in alto",
        ),
    )

    @Test
    fun `salvare un articolo esistente ne conserva l'identificatore`() = runTest(dispatcher) {
        val saved = seedLatte()
        val vm = viewModel(saved.uuid)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onNameChange("Latte intero")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val stored = dao.items.single()
        assertEquals(saved.uuid, stored.uuid)
        assertEquals("Latte intero", stored.name)
        assertEquals(FoodCategory.LATTICINI, stored.category)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `salvare non riporta in inventario un articolo rimosso`() = runTest(dispatcher) {
        val saved = seedLatte()
        val vm = viewModel(saved.uuid)
        dispatcher.scheduler.advanceUntilIdle()

        // L'articolo viene consumato mentre la schermata di modifica e' aperta.
        repository.remove(saved, RemovalReason.CONSUMATO)

        vm.onNameChange("Latte intero")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        // Ricostruire il FoodItem da zero invece di copiarlo riazzererebbe removedAt e
        // removalReason: l'articolo tornerebbe in inventario e la sua storia sparirebbe.
        val stored = dao.items.single()
        assertEquals("Latte intero", stored.name)
        assertNotNull(stored.removedAt)
        assertEquals(RemovalReason.CONSUMATO, stored.removalReason)
    }

    @Test
    fun `salvare un articolo nuovo lo inserisce con un identificatore generato`() =
        runTest(dispatcher) {
            val vm = viewModel(NEW_ITEM_UUID)

            vm.onNameChange("Pane")
            vm.onQuantityChange("2")
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = dao.items.single()
            assertEquals("uuid-1", stored.uuid)
            assertEquals("Pane", stored.name)
            assertEquals(2.0, stored.quantity, 0.001)
            assertNull(stored.removedAt)
        }

    @Test
    fun `eliminare dalla modifica segna il motivo ERRORE`() = runTest(dispatcher) {
        val saved = seedLatte()
        val vm = viewModel(saved.uuid)
        dispatcher.scheduler.advanceUntilIdle()

        vm.delete()
        dispatcher.scheduler.advanceUntilIdle()

        val stored = dao.items.single()
        assertNotNull(stored.removedAt)
        assertEquals(RemovalReason.ERRORE, stored.removalReason)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `un nome vuoto segnala l'errore e non salva`() = runTest(dispatcher) {
        val vm = viewModel(NEW_ITEM_UUID)

        vm.onNameChange("   ")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.isSaved)
        assertTrue(dao.items.isEmpty())
    }

    @Test
    fun `una quantita non positiva segnala l'errore e non salva`() = runTest(dispatcher) {
        val vm = viewModel(NEW_ITEM_UUID)

        vm.onNameChange("Pane")
        vm.onQuantityChange("0")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.quantityError)
        assertFalse(vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.isSaved)
        assertTrue(dao.items.isEmpty())
    }

    @Test
    fun `la marca si salva e si rilegge`() = runTest(dispatcher) {
        val vm = viewModel(NEW_ITEM_UUID)
        vm.onNameChange("Latte")
        vm.onBrandChange("  Granarolo ")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val stored = dao.items.single()
        assertEquals("Granarolo", stored.brand)

        val reopened = viewModel(stored.uuid)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Granarolo", reopened.uiState.value.brand)
    }

    @Test fun `due salvataggi ravvicinati creano un solo articolo`() = runTest(dispatcher) {
        val vm = viewModel(NEW_ITEM_UUID); vm.onNameChange("Pane")
        vm.save(); vm.save(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.items.size)
        vm.save(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.items.size)
    }
    @Test fun `quantita non finita viene rifiutata`() = runTest(dispatcher) {
        val vm = viewModel(NEW_ITEM_UUID); vm.onNameChange("Pane"); vm.onQuantityChange("NaN")
        vm.save(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.quantityError); assertTrue(dao.items.isEmpty())
    }
    @Test fun `scanner rispetta categoria unita e posizione scelte manualmente`() = runTest(dispatcher) {
        repository.save(FoodItem(uuid = "", name = "Latte", barcode = "123", category = FoodCategory.LATTICINI))
        val vm = viewModel(NEW_ITEM_UUID)
        vm.onCategoryChange(FoodCategory.ALTERNATIVE_VEGETALI)
        vm.onUnitChange(com.igor.fridge.data.local.QuantityUnit.L)
        vm.onLocationChange(com.igor.fridge.data.local.StorageLocation.DISPENSA)
        vm.onBarcodeScanned("123"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FoodCategory.ALTERNATIVE_VEGETALI, vm.uiState.value.category)
        assertEquals(com.igor.fridge.data.local.QuantityUnit.L, vm.uiState.value.unit)
        assertEquals(com.igor.fridge.data.local.StorageLocation.DISPENSA, vm.uiState.value.location)
    }


    @Test fun `errore salvataggio nuovo conserva il modulo e non duplica al retry`() = runTest(dispatcher) {
        var fail = true
        val failing = object : com.igor.fridge.data.local.FoodItemDao by dao {
            override suspend fun upsert(item: FoodItem) {
                if (fail) error("disco") else dao.upsert(item)
            }
        }
        val vm = EditItemViewModel(NEW_ITEM_UUID, FoodRepository(failing))
        vm.onNameChange("Pane"); vm.save(); dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaved); assertTrue(vm.uiState.value.message!!.contains("riprova"))
        fail = false; vm.save(); vm.save(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.items.size)
    }

}
