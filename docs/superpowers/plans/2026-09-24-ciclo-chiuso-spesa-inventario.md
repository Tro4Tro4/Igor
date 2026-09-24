# Fase 2 — Dalla lista della spesa al frigorifero: piano di implementazione

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chiudere il ciclo: i prodotti spuntati nella lista della spesa entrano in inventario ereditando categoria e posizione dalle volte precedenti, con un annullamento immediato.

**Architecture:** L'eredità degli attributi vive in `FoodRepository`, che è già l'unico punto dove si assegnano identità e timbro temporale. L'orchestrazione dei due repository sta in `ShoppingViewModel`, come già avviene in `InventoryViewModel.consume()` per il verso opposto. L'annullamento tiene in memoria, nel ViewModel, le voci rimosse e gli articoli creati: vive quanto lo snackbar e non tocca il database.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Room 2.6.1 con KSP, DataStore 1.1.7, JUnit 4 + Robolectric + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-23-inventario-vocale-design.md` — sezione 4

## Global Constraints

- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, Java 17.
- **La versione del database Room resta `1` e non si scrivono `Migration`.** Se lo schema cambia, `app/schemas/com.igor.fridge.data.local.IgorDatabase/1.json` va rigenerato e committato, e chi ha l'app installata deve disinstallarla.
- Dependency injection manuale via `AppContainer`: niente Hilt/Dagger.
- **Testi visibili all'utente in italiano corretto**: lettere accentate (à, è, ù) e apostrofo tipografico (’), mai i sostituti ASCII. In `strings.xml` un apostrofo letterale va protetto con `\'`; il `’` non richiede protezione. I commenti nel codice seguono lo stile ASCII esistente.
- I messaggi che un ViewModel compone a runtime restano letterali Kotlin: un ViewModel non ha `Context` e non può risolvere risorse. Le etichette dei componenti stanno in `strings.xml`.
- Identità e `updatedAt` si assegnano nei repository e in nessun altro posto: un UUID non si genera mai in un ViewModel o in una schermata.
- Nessuna dipendenza di rete, nessun servizio remoto, nessun account.
- Baseline da preservare: **58 test su 12 classi**, verdi, zero warning del compilatore.
- Comandi (PowerShell, dalla radice del progetto), **da eseguire in foreground** con timeout di 600000 ms:
  ```
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
  .\gradlew.bat assembleDebug testDebugUnitTest --console=plain
  ```

### Decisioni prese prima di scrivere questo piano

**La lista della spesa continua a cancellare fisicamente.** La spec prevede tombstone (`removedAt`) anche su `shopping_items` per una futura sincronizzazione; restano rimandati. L'annullamento non ne ha bisogno, perché vive in memoria quanto lo snackbar, e introdurli ora significherebbe scrivere una migrazione di schema per un backend che non è ancora stato scelto.

**Dalla lista si eredita solo categoria e posizione.** Nome, quantità e unità arrivano dalla voce di lista, che è un dato inserito dall'utente e prevale sempre. La scadenza resta assente: la lista della spesa non può conoscerla, e inventarla sarebbe peggio che lasciarla vuota — il filtro "Senza data", già presente dalla Fase 1, serve a ritrovare i prodotti da completare. Il codice a barre non si eredita: identifica una confezione specifica, non un prodotto ricorrente.

---

## Struttura dei file

**Modificati**
| File | Responsabilità dopo la modifica |
| --- | --- |
| `data/repository/FoodRepository.kt` | Aggiunge `addFromShopping`, che crea l'articolo ereditando dagli omonimi passati |
| `data/repository/ShoppingRepository.kt` | Aggiunge `restore`, per rimettere in lista una voce rimossa |
| `ui/shopping/ShoppingViewModel.kt` | Riceve anche `FoodRepository`; orchestra lo spostamento e il suo annullamento |
| `ui/shopping/ShoppingScreen.kt` | Pulsante "Metti in frigo", snackbar con azione "Annulla" |
| `res/values/strings.xml` | Le etichette nuove |
| `README.md` | Stato aggiornato e un difetto noto da registrare |

**Creati**
| File | Responsabilità |
| --- | --- |
| `app/src/test/java/com/igor/fridge/ui/ShoppingViewModelTest.kt` | Primo test del ViewModel della lista: spostamento, eredità, annullamento |

---

## Task 1: L'inventario accoglie un prodotto dalla spesa

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/data/repository/FoodRepository.kt`
- Modify: `app/src/main/java/com/igor/fridge/data/repository/ShoppingRepository.kt`
- Test: `app/src/test/java/com/igor/fridge/data/FoodRepositoryTest.kt`

**Interfaces:**
- Consuma: `FoodItemDao.findLastByName(name: String): FoodItem?` (non filtra i rimossi, di proposito), `FoodRepository.save(item: FoodItem): FoodItem`
- Produce:
  - `suspend fun FoodRepository.addFromShopping(name: String, quantity: Double, unit: QuantityUnit): FoodItem` — crea e salva l'articolo, restituendolo come è stato salvato
  - `suspend fun ShoppingRepository.restore(item: ShoppingItem)` — rimette in lista una voce con il suo identificatore originale

- [ ] **Step 1: Scrivere i test che falliscono**

In `FoodRepositoryTest.kt`, aggiungere in fondo alla classe:

```kotlin
    @Test
    fun `un prodotto dalla spesa eredita categoria e posizione dall'ultimo omonimo`() = runTest {
        repository.save(
            FoodItem(
                uuid = "",
                name = "Latte",
                category = FoodCategory.LATTICINI,
                location = StorageLocation.FRIGO,
                expiryDate = today,
            ),
        )

        val created = repository.addFromShopping("Latte", quantity = 2.0, unit = QuantityUnit.L)

        assertEquals(FoodCategory.LATTICINI, created.category)
        assertEquals(StorageLocation.FRIGO, created.location)
        assertEquals(2.0, created.quantity, 0.001)
        assertEquals(QuantityUnit.L, created.unit)
    }

    @Test
    fun `l'eredita' attraversa anche gli articoli gia' consumati`() = runTest {
        val saved = repository.save(
            FoodItem(uuid = "", name = "Yogurt", category = FoodCategory.LATTICINI),
        )
        repository.remove(saved, RemovalReason.CONSUMATO)

        val created = repository.addFromShopping("Yogurt", quantity = 1.0, unit = QuantityUnit.PZ)

        assertEquals(FoodCategory.LATTICINI, created.category)
        assertNull(created.removedAt)
    }

    @Test
    fun `un prodotto mai visto prima riceve i valori di default`() = runTest {
        val created = repository.addFromShopping("Cavolo", quantity = 1.0, unit = QuantityUnit.PZ)

        assertEquals(FoodCategory.ALTRO, created.category)
        assertEquals(StorageLocation.FRIGO, created.location)
    }

    @Test
    fun `un prodotto dalla spesa entra senza scadenza e senza codice a barre`() = runTest {
        repository.save(
            FoodItem(
                uuid = "",
                name = "Latte",
                barcode = "8001234567890",
                expiryDate = today.plusDays(5),
            ),
        )

        val created = repository.addFromShopping("Latte", quantity = 1.0, unit = QuantityUnit.L)

        assertNull(created.expiryDate)
        assertNull(created.barcode)
    }

    @Test
    fun `il nome viene ripulito dagli spazi`() = runTest {
        val created = repository.addFromShopping("  Pane  ", quantity = 1.0, unit = QuantityUnit.PZ)

        assertEquals("Pane", created.name)
    }
```

Aggiungere gli import mancanti in cima al file: `com.igor.fridge.data.local.FoodCategory`, `com.igor.fridge.data.local.QuantityUnit`, `com.igor.fridge.data.local.StorageLocation`.

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*FoodRepositoryTest*" --console=plain`
Atteso: FAIL in compilazione — `addFromShopping` non esiste.

- [ ] **Step 3: Implementare**

In `FoodRepository.kt`, dopo `save`, aggiungere (e importare `com.igor.fridge.data.local.QuantityUnit`):

```kotlin
    /**
     * Crea un articolo a partire da una voce della lista della spesa.
     *
     * Categoria e posizione sono ereditate dall'ultima volta che quel nome e' stato in
     * casa — anche se quell'articolo e' gia' stato consumato — cosi' chi spunta "Latte"
     * non deve ricatalogarlo ogni volta. Nome, quantita' e unita' arrivano invece dalla
     * lista: sono dati che l'utente ha inserito, e prevalgono.
     *
     * La scadenza resta assente perche' la lista della spesa non puo' conoscerla, e il
     * codice a barre non si eredita perche' identifica una confezione, non un prodotto.
     */
    suspend fun addFromShopping(name: String, quantity: Double, unit: QuantityUnit): FoodItem {
        val trimmed = name.trim()
        val previous = dao.findLastByName(trimmed)
        return save(
            FoodItem(
                uuid = "",
                name = trimmed,
                category = previous?.category ?: FoodCategory.ALTRO,
                location = previous?.location ?: StorageLocation.FRIGO,
                quantity = quantity,
                unit = unit,
            ),
        )
    }
```

Aggiungere anche gli import `com.igor.fridge.data.local.FoodCategory` e `com.igor.fridge.data.local.StorageLocation`.

In `ShoppingRepository.kt`, dopo `setChecked`:

```kotlin
    /**
     * Rimette in lista una voce rimossa, con il suo identificatore originale: serve
     * ad annullare uno spostamento in frigo senza che la voce cambi identita'.
     */
    suspend fun restore(item: ShoppingItem) {
        dao.upsert(item.copy(updatedAt = clock()))
    }
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*FoodRepositoryTest*" --console=plain`
Atteso: PASS (10 test: i 5 preesistenti più i 5 nuovi)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/data/repository/ app/src/test/java/com/igor/fridge/data/FoodRepositoryTest.kt
git commit -m "feat: un prodotto dalla spesa eredita categoria e posizione"
```

---

## Task 2: Spostare in frigo, e poterci ripensare

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingViewModel.kt`
- Test: `app/src/test/java/com/igor/fridge/ui/ShoppingViewModelTest.kt` (nuovo)

**Interfaces:**
- Consuma: `FoodRepository.addFromShopping(name, quantity, unit): FoodItem`, `FoodRepository.remove(item, reason)`, `ShoppingRepository.restore(item)`, `ShoppingRepository.delete(item)`, `ShoppingRepository.observeAll()`
- Produce:
  - `ShoppingViewModel(shoppingRepository: ShoppingRepository, foodRepository: FoodRepository)` — **il costruttore cambia**, la `Factory` passa entrambi dal container
  - `ShoppingUiState(items: List<ShoppingItem>, isLoading: Boolean, message: String?, canUndo: Boolean)` con `checkedCount`
  - `fun moveCheckedToInventory()`, `fun undoLastMove()`, `fun onMessageShown()`
- Il metodo `clearChecked()` **sparisce**: "Metti in frigo" lo sostituisce, e l'eliminazione di una singola voce resta disponibile riga per riga.

- [ ] **Step 1: Scrivere i test che falliscono**

`app/src/test/java/com/igor/fridge/ui/ShoppingViewModelTest.kt`:

```kotlin
package com.igor.fridge.ui

import com.igor.fridge.data.FakeFoodItemDao
import com.igor.fridge.data.FakeShoppingItemDao
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.shopping.ShoppingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import java.time.Instant

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

    private fun viewModel() = ShoppingViewModel(shoppingRepository, foodRepository)

    @Test
    fun `mettere in frigo sposta le voci spuntate e lascia le altre`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Latte", quantity = 2.0, unit = QuantityUnit.L)
        shoppingRepository.addIfAbsent("Pane")
        shoppingRepository.setChecked(shoppingDao.items.first { it.name == "Latte" }, checked = true)

        val vm = viewModel()
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Pane"), shoppingDao.items.map { it.name })
        val inFridge = foodDao.items.single()
        assertEquals("Latte", inFridge.name)
        assertEquals(2.0, inFridge.quantity, 0.001)
        assertEquals(QuantityUnit.L, inFridge.unit)
        assertNull(inFridge.expiryDate)
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
        shoppingRepository.addIfAbsent("Latte")
        shoppingRepository.setChecked(shoppingDao.items.single(), checked = true)

        val vm = viewModel()
        vm.uiState.first { !it.isLoading }
        vm.moveCheckedToInventory()
        dispatcher.scheduler.advanceUntilIdle()

        val created = foodDao.items.single { it.removedAt == null }
        assertEquals(FoodCategory.LATTICINI, created.category)
    }

    @Test
    fun `senza voci spuntate non succede nulla`() = runTest(dispatcher) {
        shoppingRepository.addIfAbsent("Pane")

        val vm = viewModel()
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
}
```

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ShoppingViewModelTest*" --console=plain`
Atteso: FAIL in compilazione — il costruttore accetta un solo repository, `moveCheckedToInventory`, `undoLastMove` e `canUndo` non esistono.

- [ ] **Step 3: Implementare**

`ShoppingViewModel.kt` diventa:

```kotlin
package com.igor.fridge.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val items: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val canUndo: Boolean = false,
) {
    val checkedCount: Int get() = items.count { it.isChecked }
}

/** Cosa serve per annullare l'ultimo spostamento in frigo. */
private data class LastMove(
    val shoppingItems: List<ShoppingItem>,
    val createdFood: List<FoodItem>,
)

class ShoppingViewModel(
    private val shoppingRepository: ShoppingRepository,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    // Vive in memoria quanto lo snackbar: un annullamento che sopravvive alla schermata
    // non e' quello che l'utente si aspetta, e non vale una colonna in database.
    private var lastMove: LastMove? = null

    private val feedback = MutableStateFlow(Feedback())

    private data class Feedback(val message: String? = null, val canUndo: Boolean = false)

    val uiState: StateFlow<ShoppingUiState> =
        combine(shoppingRepository.observeAll(), feedback) { items, feedback ->
            ShoppingUiState(
                items = items,
                isLoading = false,
                message = feedback.message,
                canUndo = feedback.canUndo,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ShoppingUiState(),
        )

    fun add(name: String) {
        viewModelScope.launch { shoppingRepository.addIfAbsent(name) }
    }

    fun setChecked(item: ShoppingItem, checked: Boolean) {
        viewModelScope.launch { shoppingRepository.setChecked(item, checked) }
    }

    fun delete(item: ShoppingItem) {
        viewModelScope.launch { shoppingRepository.delete(item) }
    }

    /**
     * Le voci spuntate entrano in inventario ed escono dalla lista.
     *
     * L'operazione e' legata a un'azione esplicita e non allo spunto: al supermercato si
     * spunta e si toglie la spunta mentre si prende, e far entrare un prodotto in frigo a
     * ogni tocco creerebbe record fantasma.
     */
    fun moveCheckedToInventory() {
        viewModelScope.launch {
            val checked = uiState.value.items.filter { it.isChecked }
            if (checked.isEmpty()) {
                feedback.update { it.copy(message = "Nessun prodotto spuntato", canUndo = false) }
                return@launch
            }

            val created = checked.map { item ->
                foodRepository.addFromShopping(item.name, item.quantity, item.unit)
            }
            checked.forEach { shoppingRepository.delete(it) }

            lastMove = LastMove(shoppingItems = checked, createdFood = created)
            val text = if (checked.size == 1) {
                "${checked.single().name} messo in frigo"
            } else {
                "${checked.size} prodotti messi in frigo"
            }
            feedback.update { it.copy(message = text, canUndo = true) }
        }
    }

    /** Rimette com'era: le voci tornano in lista e gli articoli escono dall'inventario. */
    fun undoLastMove() {
        val move = lastMove ?: return
        lastMove = null
        viewModelScope.launch {
            move.createdFood.forEach { foodRepository.remove(it, RemovalReason.ERRORE) }
            move.shoppingItems.forEach { shoppingRepository.restore(it) }
            feedback.update { it.copy(message = "Spostamento annullato", canUndo = false) }
        }
    }

    fun onMessageShown() {
        feedback.update { it.copy(message = null) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                ShoppingViewModel(
                    shoppingRepository = container.shoppingRepository,
                    foodRepository = container.foodRepository,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ShoppingViewModelTest*" --console=plain`
Atteso: PASS (5 test)

La compilazione di `ShoppingScreen.kt` fallirà, perché chiama ancora `clearChecked()`: è il Task 3.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/ui/shopping/ShoppingViewModel.kt app/src/test/java/com/igor/fridge/ui/ShoppingViewModelTest.kt
git commit -m "feat: le voci spuntate entrano in inventario, con annullamento"
```

---

## Task 3: Il pulsante, l'annulla e la documentazione

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `README.md`

**Interfaces:**
- Consuma: `ShoppingViewModel.moveCheckedToInventory()`, `undoLastMove()`, `onMessageShown()`, `ShoppingUiState.canUndo`, `ShoppingUiState.message`, `ShoppingUiState.checkedCount`
- Produce: nessuna firma pubblica nuova

- [ ] **Step 1: Aggiungere le stringhe**

In `res/values/strings.xml`, sostituire la riga `shopping_clear_checked` con:

```xml
    <string name="shopping_move_to_fridge">Metti in frigo (%1$d)</string>
    <string name="action_undo">Annulla</string>
```

La stringa `shopping_clear_checked` non ha più usi e va rimossa.

- [ ] **Step 2: Sostituire il pulsante e aggiungere lo snackbar**

In `ShoppingScreen.kt`:

1. L'azione in barra passa da `clearChecked()` a `moveCheckedToInventory()` e cambia etichetta:

```kotlin
                actions = {
                    if (state.checkedCount > 0) {
                        TextButton(onClick = { viewModel.moveCheckedToInventory() }) {
                            Text(stringResource(R.string.shopping_move_to_fridge, state.checkedCount))
                        }
                    }
                },
```

2. Lo `Scaffold` acquisisce un `SnackbarHost` — oggi la schermata non ne ha uno. Dichiarare sopra lo `Scaffold`:

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
```

e passarlo allo `Scaffold`:

```kotlin
        snackbarHost = { SnackbarHost(snackbarHostState) },
```

3. Mostrare il messaggio, con l'azione di annullamento quando è disponibile:

```kotlin
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (state.canUndo) undoLabel else null,
            withDismissAction = false,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastMove()
        viewModel.onMessageShown()
    }
```

Import da aggiungere: `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState`, `androidx.compose.material3.SnackbarResult`, `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 3: Compilare e verificare l'intero progetto**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest --console=plain`
Atteso: BUILD SUCCESSFUL, nessun warning, **68 test su 13 classi** (58 di partenza, più 5 in `FoodRepositoryTest` e 5 nel nuovo `ShoppingViewModelTest`).

- [ ] **Step 4: Aggiornare il README**

Nella sezione "Stato", sostituire la descrizione del ciclo aperto: la lista della spesa ora restituisce i prodotti all'inventario con "Metti in frigo", ereditando categoria e posizione dall'ultima volta che quel nome è stato in casa, e lo spostamento si può annullare finché lo snackbar è visibile.

Aggiungere in fondo alla sezione "Stato" un capoverso **"Difetti noti"** con:

> **L'elenco si aggiorna con qualche secondo di ritardo.** Dopo aver aggiunto o spostato un prodotto, la lista può impiegare qualche istante a mostrarlo: il dato è salvato correttamente e compare da solo, senza bisogno di riaprire l'app. La causa non è ancora stata isolata; il sospetto è il momento in cui le schermate riprendono a osservare il database dopo essere tornate in primo piano.

Aggiornare il conteggio dei test se è citato altrove nel README.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/ui/shopping/ShoppingScreen.kt app/src/main/res/values/strings.xml README.md
git commit -m "feat: metti in frigo dalla lista della spesa"
```

---

## Prova sul dispositivo

```
.\gradlew.bat installDebug --console=plain
```

Da verificare a mano, nell'ordine:

1. Aggiungi "Latte" all'inventario, categoria Latticini, posizione Frigo. Consumalo: finisce nella lista della spesa.
2. Nella lista, spunta "Latte" e premi **"Metti in frigo (1)"**. Il latte deve sparire dalla lista e ricomparire in inventario **già catalogato come Latticini, in Frigo**, senza scadenza.
3. Ripeti e premi **"Annulla"** nello snackbar: il latte deve tornare nella lista, ancora spuntato, e sparire dall'inventario.
4. Il chip **"Senza data"** nell'inventario deve contare i prodotti entrati dalla spesa.
5. Spunta due prodotti insieme e mettili in frigo: il messaggio deve dire "2 prodotti messi in frigo".
