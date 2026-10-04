package com.igor.fridge.ui

import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.FakePhotoStore
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.shopping.ShoppingViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    private val foodDao = FakeFoodItemDao()
    private val shoppingDao = FakeShoppingItemDao()
    private var counter = 0

    private val foodRepository = FoodRepository(foodDao, { now }, { "f-${++counter}" })
    private val shoppingRepository = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ShoppingViewModel(shoppingRepository, foodRepository, FakePhotoStore())

    @Test
    fun `mettere in frigo sposta le voci spuntate e lascia le altre`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", quantity = 2.0, unit = QuantityUnit.L)
        shoppingRepository.addIfAbsent("Pane")
        shoppingRepository.setChecked(shoppingDao.items.first { it.name == "Latte" }, checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Pane"), shoppingDao.items.map { it.name })
        val inFridge = foodDao.items.single()
        assertEquals("Latte", inFridge.name)
        assertEquals(2.0, inFridge.quantity, 0.001)
        assertEquals(QuantityUnit.L, inFridge.unit)
        assertNull(inFridge.expiryDate)
        assertEquals("Aggiunto in frigo: Latte", vm.uiState.value.message)
        assertTrue(vm.uiState.value.canUndo)
    }

    @Test
    fun `mettere in frigo eredita la catalogazione precedente`() = runTest(dispatcher) {
        val previous = foodRepository.save(
            FoodItem(
                uuid = "",
                name = "Latte",
                category = FoodCategory.LATTICINI,
                location = StorageLocation.FRIGO,
            ),
        )
        foodRepository.remove(previous, RemovalReason.CONSUMATO)
        val vm = viewModel()
        vm.add("Latte")
        dispatcher.scheduler.advanceUntilIdle()
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        val created = foodDao.items.single { it.removedAt == null }
        assertEquals(FoodCategory.LATTICINI, created.category)
        assertEquals(StorageLocation.FRIGO, created.location)
    }

    @Test
    fun `senza voci spuntate non succede nulla`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Pane")

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, shoppingDao.items.size)
        assertTrue(foodDao.items.isEmpty())
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test
    fun `annullare rimette le voci in lista e toglie gli articoli dal frigo`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", quantity = 2.0, unit = QuantityUnit.L)
        val original = shoppingDao.items.single()
        shoppingRepository.setChecked(original, checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        val restored = shoppingDao.items.single()
        assertEquals(original.uuid, restored.uuid)
        assertEquals("Latte", restored.name)
        assertTrue(restored.isChecked)
        assertTrue(foodDao.items.none { it.removedAt == null })
    }

    @Test
    fun `si puo' annullare una volta sola`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()
        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.canUndo)

        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, shoppingDao.items.size)
    }

    @Test
    fun `due pressioni ravvicinate creano un solo articolo`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }

        // Due tocchi prima che la lista riemetta senza "Latte": senza la guardia, il
        // secondo rilegge la stessa voce ancora spuntata e crea un secondo articolo.
        vm.moveCheckedToInventory()
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, foodDao.items.count { it.removedAt == null })
    }

    @Test
    fun `mettere in frigo con piu' voci spuntate le sposta tutte e usa il messaggio al plurale`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.addIfAbsent("Pane")
        shoppingRepository.setChecked(shoppingDao.items.first { it.name == "Latte" }, checked = true)
        shoppingRepository.setChecked(shoppingDao.items.first { it.name == "Pane" }, checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(shoppingDao.items.isEmpty())
        assertEquals(2, foodDao.items.count { it.removedAt == null })
        assertEquals("2 prodotti messi in frigo", vm.uiState.value.message)
    }

    @Test
    fun `annullare lo annuncia e chiude la porta`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Spostamento annullato", vm.uiState.value.message)
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test
    fun `un'eliminazione si annulla e la voce torna com'era`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", 2.0, store = "Coop")
        val item = shoppingDao.items.single()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }

        vm.delete(item)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(shoppingDao.items.isEmpty())
        assertEquals("Eliminato: Latte", vm.uiState.value.message)
        assertTrue(vm.uiState.value.canUndo)

        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(item.uuid, shoppingDao.items.single().uuid)
        assertEquals("Coop", shoppingDao.items.single().store)
        assertEquals("Ripristinato: Latte", vm.uiState.value.message)
    }

    @Test
    fun `due messaggi uguali di seguito hanno id diversi`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }

        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()
        val first = vm.uiState.value.messageId
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Nessun prodotto spuntato", vm.uiState.value.message)
        assertNotEquals(first, vm.uiState.value.messageId)
    }

    @Test
    fun `quando il messaggio e' stato mostrato scade anche l'annullamento`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.canUndo)

        vm.onMessageShown()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.canUndo)

        // Lo snackbar e' sparito: l'annullamento e' scaduto con lui e non tocca piu' nulla.
        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(shoppingDao.items.isEmpty())
        assertEquals(1, foodDao.items.count { it.removedAt == null })
    }

    @Test
    fun `entra in frigo la quantita' presa e il resto rimane in lista`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Uova", quantity = 12.0)
        val original = shoppingDao.items.single()
        shoppingRepository.update(original.copy(isChecked = true, purchasedQuantity = 6.0))

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(6.0, foodDao.items.single().quantity, 0.001)
        val left = shoppingDao.items.single()
        assertEquals(original.uuid, left.uuid)
        assertEquals(6.0, left.quantity, 0.001)
        assertFalse(left.isChecked)
        assertEquals(
            "Aggiunto in frigo: Uova; 1 resta in lista per la parte mancante",
            vm.uiState.value.message,
        )
    }

    @Test
    fun `annullare un acquisto parziale rimette la voce com'era`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Uova", quantity = 12.0)
        shoppingRepository.update(
            shoppingDao.items.single().copy(isChecked = true, purchasedQuantity = 6.0),
        )

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()
        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        val restored = shoppingDao.items.single()
        assertEquals(12.0, restored.quantity, 0.001)
        assertEquals(6.0, restored.purchasedQuantity!!, 0.001)
        assertTrue(restored.isChecked)
        assertTrue(foodDao.items.none { it.removedAt == null })
    }

    @Test
    fun `cio' che non e' un alimento esce dalla lista ma non entra in frigo`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Detersivo")
        shoppingRepository.addIfAbsent("Latte")
        shoppingDao.items.forEach { shoppingRepository.setChecked(it, checked = true) }

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(shoppingDao.items.isEmpty())
        assertEquals(listOf("Latte"), foodDao.items.map { it.name })
        assertEquals("1 prodotto messo in frigo, 1 tolto dalla lista", vm.uiState.value.message)

        vm.undoLastMove()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("Detersivo", "Latte"), shoppingDao.items.map { it.name }.toSet())
    }

    @Test
    fun `la categoria della voce arriva in frigo`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Piselli", category = FoodCategory.SURGELATI)
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        val created = foodDao.items.single()
        assertEquals(FoodCategory.SURGELATI, created.category)
        assertEquals(StorageLocation.FREEZER, created.location)
    }

    @Test
    fun `aggiungere un prodotto gia' passato dal frigo ne riprende la categoria`() = runTest(dispatcher) {
        val previous = foodRepository.save(
            FoodItem(uuid = "", name = "Tofu", category = FoodCategory.LATTICINI),
        )
        foodRepository.remove(previous, RemovalReason.CONSUMATO)

        val vm = viewModel()
        vm.add("tofu")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FoodCategory.LATTICINI, shoppingDao.items.single().category)
    }

    @Test
    fun `le voci da comprare sono raggruppate nell'ordine delle corsie`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.addIfAbsent("Mele")
        shoppingRepository.addIfAbsent("Yogurt")
        shoppingRepository.addIfAbsent("Pane")
        shoppingRepository.setChecked(shoppingDao.items.first { it.name == "Pane" }, checked = true)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(
            listOf(FoodCategory.FRUTTA, FoodCategory.LATTE_PANNA, FoodCategory.YOGURT),
            state.toBuy.map { it.category },
        )
        assertEquals(
            setOf("Latte"),
            state.toBuy.single { it.category == FoodCategory.LATTE_PANNA }.items.map { it.name }.toSet(),
        )
        assertEquals(listOf("Pane"), state.inCart.map { it.name })
    }

    @Test
    fun `il messaggio dello spostamento distingue frigo, lista e resti`() {
        val latte = ShoppingItem(uuid = "1", name = "Latte")
        val pane = ShoppingItem(uuid = "2", name = "Pane")
        val sapone = ShoppingItem(uuid = "3", name = "Sapone")
        val spugne = ShoppingItem(uuid = "4", name = "Spugne")

        assertEquals("Tolto dalla lista: Sapone", ShoppingViewModel.moveMessage(emptyList(), listOf(sapone), 0))
        assertEquals(
            "2 prodotti tolti dalla lista",
            ShoppingViewModel.moveMessage(emptyList(), listOf(sapone, spugne), 0),
        )
        assertEquals(
            "2 prodotti messi in frigo, 2 tolti dalla lista; 2 restano in lista per la parte mancante",
            ShoppingViewModel.moveMessage(listOf(latte, pane), listOf(sapone, spugne), 2),
        )
        // Un non alimentare preso in parte non e' "tolto dalla lista".
        assertEquals(
            "1 voce resta in lista per la parte mancante",
            ShoppingViewModel.moveMessage(emptyList(), emptyList(), 1),
        )
    }

    @Test
    fun `l'aggiunta rapida capisce quantita' e unita'`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.add("2 kg mele")
        dispatcher.scheduler.advanceUntilIdle()

        val mele = shoppingDao.items.single()
        assertEquals("mele", mele.name)
        assertEquals(2.0, mele.quantity, 0.001)
        assertEquals(QuantityUnit.KG, mele.unit)
        assertEquals(FoodCategory.FRUTTA, mele.category)
    }

    @Test
    fun `con un negozio scelto la voce nuova nasce per quel negozio`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", store = "Esselunga")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }

        vm.setStoreFilter("Esselunga")
        vm.uiState.first { it.activeStore == "Esselunga" }
        vm.add("Pane")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Esselunga", shoppingDao.items.single { it.name == "Pane" }.store)
    }

    @Test
    fun `scadenza e marca arrivano in frigo`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", brand = "Granarolo")
        val latte = shoppingDao.items.single()
        shoppingRepository.setChecked(latte, checked = true)
        val expiry = LocalDate.of(2026, 10, 10)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory(mapOf(latte.uuid to expiry))
        dispatcher.scheduler.advanceUntilIdle()

        val created = foodDao.items.single()
        assertEquals(expiry, created.expiryDate)
        assertEquals("Granarolo", created.brand)
    }

    @Test
    fun `l'ordine delle corsie arriva dalle impostazioni`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.addIfAbsent("Mele")
        val order = listOf(FoodCategory.LATTE_PANNA) + FoodCategory.entries.filterNot { it == FoodCategory.LATTE_PANNA }
        val vm = ShoppingViewModel(shoppingRepository, foodRepository, FakePhotoStore(), kotlinx.coroutines.flow.flowOf(order))

        backgroundScope.launch { vm.uiState.collect {} }
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(listOf(FoodCategory.LATTE_PANNA, FoodCategory.FRUTTA), state.toBuy.map { it.category })
        assertTrue(vm.shareText { it.name }.indexOf("Latte") < vm.shareText { it.name }.indexOf("Mele"))
    }

    @Test
    fun `una voce nuova riceve l'ultimo prezzo pagato se l'unita' torna`() = runTest(dispatcher) {
        val paid = com.igor.fridge.data.local.PriceRecord(
            uuid = "p",
            productKey = "mele",
            productName = "Mele",
            purchasedOn = LocalDate.of(2026, 10, 1),
            quantity = 1.0,
            unit = QuantityUnit.KG,
            totalCents = 199,
            unitPriceCents = 199,
            referenceUnit = QuantityUnit.KG,
        )
        val vm = ShoppingViewModel(
            shoppingRepository,
            foodRepository,
            FakePhotoStore(),
            lastPrice = { name -> paid.takeIf { name.equals("mele", ignoreCase = true) } },
        )

        vm.add("Mele")
        vm.add("6 mele")
        vm.add("Pane")
        dispatcher.scheduler.advanceUntilIdle()

        val mele = shoppingDao.items.single { it.name == "Mele" }
        assertEquals(QuantityUnit.KG, mele.unit)
        assertEquals(199L, mele.unitPriceCents)
        assertNull(shoppingDao.items.single { it.name == "Pane" }.unitPriceCents)
    }

    @Test
    fun `con un'unita' diversa il prezzo non si propone`() = runTest(dispatcher) {
        val paid = com.igor.fridge.data.local.PriceRecord(
            uuid = "p",
            productKey = "uova",
            productName = "Uova",
            purchasedOn = LocalDate.of(2026, 10, 1),
            quantity = 1.0,
            unit = QuantityUnit.CONF,
            totalCents = 250,
            unitPriceCents = 250,
            referenceUnit = QuantityUnit.CONF,
        )
        val vm = ShoppingViewModel(shoppingRepository, foodRepository, FakePhotoStore(), lastPrice = { paid })

        vm.add("6 uova")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(shoppingDao.items.single().unitPriceCents)
    }

    @Test
    fun `lo spostamento in frigo avviene in una transazione e un errore non chiude l'app`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)
        var transactions = 0
        val failingFood = FoodRepository(
            object : com.igor.fridge.data.local.FoodItemDao by foodDao {
                override suspend fun upsert(item: FoodItem) = throw IllegalStateException("disco pieno")
            },
            { now },
            { "f-${++counter}" },
        )
        val vm = ShoppingViewModel(
            shoppingRepository,
            failingFood,
            FakePhotoStore(),
            transactor = object : com.igor.fridge.data.Transactor {
                override suspend fun <T> run(block: suspend () -> T): T {
                    transactions++
                    return block()
                }
            },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }

        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, transactions)
        assertEquals("Spostamento non riuscito: riprova", vm.uiState.value.message)
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test fun `undo acquisto parziale non sovrascrive modifiche successive`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", quantity = 3.0)
        shoppingRepository.update(shoppingDao.items.single().copy(isChecked = true, purchasedQuantity = 1.0))
        val vm = viewModel(); backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory(); dispatcher.scheduler.advanceUntilIdle()
        val edited = shoppingRepository.update(shoppingDao.items.single().copy(notes = "successiva"))
        vm.undoLastMove(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(edited), shoppingDao.items)
        assertNull(foodDao.items.single().removedAt)
        assertTrue(vm.uiState.value.message!!.contains("modificat"))
    }


    @Test fun `lista resta aggiornata anche mentre si modificano i dettagli a lungo`() = runTest(dispatcher) {
        val vm = viewModel(); val observer = backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.runCurrent(); observer.cancel()
        dispatcher.scheduler.advanceTimeBy(6000); dispatcher.scheduler.runCurrent()
        shoppingRepository.addIfAbsent("Latte"); dispatcher.scheduler.runCurrent()
        assertEquals(listOf("Latte"), vm.uiState.value.items.map { it.name })
    }
    @Test fun `errore aggiunta e recuperabile senza terminare la schermata`() = runTest(dispatcher) {
        var fail = true
        val failing = object : com.igor.fridge.data.local.ShoppingItemDao by shoppingDao {
            override suspend fun upsert(item: ShoppingItem) {
                if (fail) error("disco non disponibile") else shoppingDao.upsert(item)
            }
        }
        val vm = ShoppingViewModel(ShoppingRepository(failing), foodRepository, FakePhotoStore())
        backgroundScope.launch { vm.uiState.collect {} }; vm.uiState.first { !it.isLoading }
        vm.add("Latte"); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.message!!.contains("riprova")); assertTrue(shoppingDao.items.isEmpty())
        fail = false; vm.add("Latte"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, shoppingDao.items.size)
    }


    @Test fun `errore temporaneo di undo mantiene la possibilita di riprovare`() = runTest(dispatcher) {
        var fail = false
        val tx = object : com.igor.fridge.data.Transactor {
            override suspend fun <T> run(block: suspend () -> T): T {
                if (fail) error("disco")
                return block()
            }
        }
        shoppingRepository.addIfAbsent("Latte"); shoppingRepository.setChecked(shoppingDao.items.single(), true)
        val vm = ShoppingViewModel(shoppingRepository, foodRepository, FakePhotoStore(), transactor = tx)
        backgroundScope.launch { vm.uiState.collect {} }; vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory(); dispatcher.scheduler.advanceUntilIdle()
        fail = true; vm.undoLastMove(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.canUndo)
        fail = false; vm.undoLastMove(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, shoppingDao.items.size)
        assertEquals(RemovalReason.ERRORE, foodDao.items.single().removalReason)
    }
    @Test fun `undo cestino ripristina i dati correnti non la copia vecchia della riga`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte"); val old = shoppingDao.items.single()
        val current = shoppingRepository.update(old.copy(notes = "aggiornata"))
        val vm = viewModel(); backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }; vm.delete(old); dispatcher.scheduler.advanceUntilIdle()
        vm.undoLastMove(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(current), shoppingDao.items)
    }

    @Test fun `annullamento viene offerto solo dopo il commit completato`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte")
        lateinit var vm: ShoppingViewModel
        var offeredBeforeCommit = false
        val tx = object : com.igor.fridge.data.Transactor {
            override suspend fun <T> run(block: suspend () -> T): T {
                val result = block()
                kotlinx.coroutines.delay(1)
                offeredBeforeCommit = vm.uiState.value.canUndo
                return result
            }
        }
        vm = ShoppingViewModel(shoppingRepository, foodRepository, FakePhotoStore(), transactor = tx)
        backgroundScope.launch { vm.uiState.collect {} }; vm.uiState.first { !it.isLoading }
        vm.delete(shoppingDao.items.single()); dispatcher.scheduler.advanceUntilIdle()
        assertFalse(offeredBeforeCommit); assertTrue(vm.uiState.value.canUndo)
    }
}
