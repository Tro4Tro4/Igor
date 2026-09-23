# Fase 1 — Fondamenta e modello dati: piano di implementazione

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Portare Igor da scaffold mai eseguito ad app installabile e provabile, con uno schema dati pronto per una sincronizzazione futura, le impostazioni osservabili e modificabili dall'utente, e una rete di test che copra dati, repository e ViewModel.

**Architecture:** Room resta la sorgente di verità. La chiave primaria diventa un UUID generato sul dispositivo, le cancellazioni diventano logiche (`removedAt` + motivo) e ogni scrittura timbra `updatedAt`: sono le tre condizioni perché un backend possa essere aggiunto in seguito senza rifare lo schema. Le preferenze passano da SharedPreferences a DataStore per diventare osservabili. I ViewModel smettono di catturare valori una volta sola e osservano `Flow`.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Room 2.6.1 con KSP, WorkManager 2.10.0, DataStore Preferences 1.1.7, JUnit 4 + Robolectric per i test dei DAO su JVM.

**Spec:** `docs/superpowers/specs/2026-09-23-inventario-vocale-design.md`

## Global Constraints

- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, Java 17.
- **La versione del database Room resta `1`.** Non si scrivono `Migration`: non esistono installazioni reali. `app/schemas/com.igor.fridge.data.local.IgorDatabase/1.json` va rigenerato e committato a ogni cambio di schema.
- Nessuna nuova dipendenza di rete, nessun servizio remoto, nessun account: questa fase resta interamente offline.
- Testi visibili all'utente in italiano con apostrofi tipografici (`'`), mai ASCII (`'`).
- Dependency injection manuale via `AppContainer`: non si introducono Hilt/Dagger.
- Comandi di build (PowerShell, dalla radice del progetto):
  ```
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
  .\gradlew.bat testDebugUnitTest --console=plain
  .\gradlew.bat assembleDebug --console=plain
  ```

### Deviazione consapevole dalla spec

La spec prevede i test dei DAO in `src/androidTest`. Quella cartella richiede un emulatore o un telefono collegato a ogni esecuzione, il che renderebbe i test dei dati — i più importanti di questa fase — difficili da eseguire di continuo. Il piano li colloca invece in `src/test` con **Robolectric**, che esegue Room su JVM con SQLite in memoria. `src/androidTest` resta previsto per il percorso Compose della Fase 2, quando un dispositivo servirà comunque.

---

## Struttura dei file

**Modificati**
| File | Responsabilità dopo la modifica |
| --- | --- |
| `data/local/Enums.kt` | Aggiunge `RemovalReason` |
| `data/local/Converters.kt` | Aggiunge `Instant` ↔ Long e `RemovalReason` ↔ String |
| `data/local/FoodItem.kt` | `uuid` come chiave primaria, `updatedAt`, `removedAt`, `removalReason` |
| `data/local/ShoppingItem.kt` | `uuid` come chiave primaria, `updatedAt` |
| `data/local/FoodItemDao.kt` | Query filtrate su `removedAt IS NULL`, `findLastByName` che attraversa i rimossi |
| `data/local/ShoppingItemDao.kt` | Chiavi testuali |
| `data/repository/FoodRepository.kt` | Genera `uuid`/`updatedAt`, espone `remove(item, reason)` |
| `data/repository/ShoppingRepository.kt` | `addIfAbsent` ripristina una voce spuntata |
| `data/prefs/SettingsStore.kt` | DataStore, tre `Flow` + una lettura bloccante per il worker |
| `di/AppContainer.kt` | Costruisce il nuovo `SettingsStore` |
| `ui/inventory/InventoryViewModel.kt` | Osserva soglia e data corrente |
| `ui/inventory/InventoryScreen.kt` | Voce Impostazioni in barra, identificatori testuali |
| `ui/edit/EditItemViewModel.kt` | Identità testuale, salvataggio via repository |
| `ui/edit/EditItemScreen.kt` | Firma con `uuid` |
| `ui/navigation/IgorNavHost.kt` | Rotta `edit/{uuid}`, rotta impostazioni |
| `notification/ExpiryCheckWorker.kt` | Legge le impostazioni dal nuovo store |
| `notification/ExpiryNotifier.kt` | Icona dedicata |
| `res/values/strings.xml` | Tutte le stringhe dell'interfaccia |

**Creati**
| File | Responsabilità |
| --- | --- |
| `domain/CurrentDate.kt` | `Flow<LocalDate>` che si risveglia a mezzanotte |
| `ui/settings/SettingsScreen.kt` | Schermata impostazioni |
| `ui/settings/SettingsViewModel.kt` | Stato e scritture delle impostazioni |
| `res/drawable/ic_notification.xml` | Icona monocromatica per la barra di stato |
| `test/.../data/FoodItemDaoTest.kt` | DAO su Room in memoria |
| `test/.../data/FoodRepositoryTest.kt` | Regole di scrittura e rimozione |
| `test/.../data/SettingsStoreTest.kt` | Persistenza e osservabilità |
| `test/.../domain/CurrentDateTest.kt` | Attesa fino a mezzanotte |
| `test/.../ui/InventoryViewModelTest.kt` | Filtri, conteggi, reazione alle impostazioni |

---

## Task 1: Infrastruttura di test e DataStore

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/igor/fridge/data/DatabaseOpensTest.kt`

**Interfaces:**
- Consuma: niente
- Produce: `testImplementation` di Robolectric, `kotlinx-coroutines-test`, `androidx.test:core`, `room-testing`; `implementation` di `androidx.datastore:datastore-preferences`. Ogni task successivo usa `runTest { }` e `@RunWith(AndroidJUnit4::class)`.

- [ ] **Step 1: Aggiungere le versioni al catalogo**

In `gradle/libs.versions.toml`, sotto `[versions]`:

```toml
datastore = "1.1.7"
coroutinesTest = "1.9.0"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
```

Sotto `[libraries]`:

```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
```

- [ ] **Step 2: Dichiarare le dipendenze nel modulo**

In `app/build.gradle.kts`, dentro il blocco `android { }`, dopo `packaging { }`:

```kotlin
    // Robolectric ha bisogno delle risorse e del manifest per far partire Room su JVM.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

Nel blocco `dependencies { }`, dopo `implementation(libs.androidx.work.runtime.ktx)`:

```kotlin
    implementation(libs.androidx.datastore.preferences)
```

e, sostituendo la riga `testImplementation(libs.junit)`:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
```

- [ ] **Step 3: Scrivere il test che verifica l'infrastruttura**

`app/src/test/java/com/igor/fridge/data/DatabaseOpensTest.kt`:

```kotlin
package com.igor.fridge.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.local.IgorDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifica che Room parta su JVM sotto Robolectric: e' il presupposto di tutti i test
 * dei DAO. `sdk = 34` evita di dipendere dal supporto di Robolectric per l'API 35.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseOpensTest {

    @Test
    fun `il database in memoria si apre e risponde`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IgorDatabase::class.java,
        ).build()

        assertTrue(db.openHelper.writableDatabase.isOpen)
        db.close()
    }
}
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DatabaseOpensTest*" --console=plain`
Atteso: PASS. Il primo avvio scarica Robolectric e le sue immagini Android: può richiedere qualche minuto.

Se fallisce con un errore sul livello di API, correggere `@Config(sdk = [34])` al valore più alto supportato dalla versione di Robolectric indicata nel messaggio d'errore.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test/java/com/igor/fridge/data/DatabaseOpensTest.kt
git commit -m "test: Room su JVM con Robolectric e dipendenze DataStore"
```

---

## Task 2: Tipi e conversioni del nuovo schema

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/data/local/Enums.kt`
- Modify: `app/src/main/java/com/igor/fridge/data/local/Converters.kt`
- Test: `app/src/test/java/com/igor/fridge/data/ConvertersTest.kt`

**Interfaces:**
- Consuma: infrastruttura di test del Task 1
- Produce: `enum class RemovalReason { CONSUMATO, BUTTATO, ERRORE }`; `Converters.toInstant(Long?): Instant?`, `Converters.fromInstant(Instant?): Long?`, `Converters.toRemovalReason(String?)`, `Converters.fromRemovalReason(RemovalReason?)`

- [ ] **Step 1: Scrivere i test che falliscono**

`app/src/test/java/com/igor/fridge/data/ConvertersTest.kt`:

```kotlin
package com.igor.fridge.data

import com.igor.fridge.data.local.Converters
import com.igor.fridge.data.local.RemovalReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `l'istante sopravvive al viaggio di andata e ritorno`() {
        val instant = Instant.ofEpochMilli(1_774_000_000_000)
        val stored = converters.fromInstant(instant)
        assertEquals(instant, converters.toInstant(stored))
    }

    @Test
    fun `i valori nulli restano nulli`() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
        assertNull(converters.fromRemovalReason(null))
        assertNull(converters.toRemovalReason(null))
    }

    @Test
    fun `il motivo di rimozione viaggia come nome`() {
        assertEquals("BUTTATO", converters.fromRemovalReason(RemovalReason.BUTTATO))
        assertEquals(RemovalReason.BUTTATO, converters.toRemovalReason("BUTTATO"))
    }
}
```

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ConvertersTest*" --console=plain`
Atteso: FAIL in compilazione — `RemovalReason` e i metodi non esistono.

- [ ] **Step 3: Implementare**

In `Enums.kt`, in fondo:

```kotlin
/**
 * Perche' un alimento e' uscito dall'inventario. La distinzione fra consumato e buttato
 * e' l'unica informazione che rende sensata una statistica sugli sprechi; ERRORE marca
 * le rimozioni annullabili dall'utente.
 */
enum class RemovalReason {
    CONSUMATO,
    BUTTATO,
    ERRORE,
}
```

In `Converters.kt`, aggiungere l'import `java.time.Instant` e, dentro la classe:

```kotlin
    /** Gli istanti sono salvati come millisecondi dall'epoch: ordinabili in SQL. */
    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toRemovalReason(value: String?): RemovalReason? = value?.let(RemovalReason::valueOf)

    @TypeConverter
    fun fromRemovalReason(value: RemovalReason?): String? = value?.name
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ConvertersTest*" --console=plain`
Atteso: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/data/local/Enums.kt app/src/main/java/com/igor/fridge/data/local/Converters.kt app/src/test/java/com/igor/fridge/data/ConvertersTest.kt
git commit -m "feat: motivo di rimozione e conversione degli istanti"
```

---

## Task 3: Entità e DAO con identità stabile e cancellazione logica

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/data/local/FoodItem.kt`
- Modify: `app/src/main/java/com/igor/fridge/data/local/ShoppingItem.kt`
- Modify: `app/src/main/java/com/igor/fridge/data/local/FoodItemDao.kt`
- Modify: `app/src/main/java/com/igor/fridge/data/local/ShoppingItemDao.kt`
- Test: `app/src/test/java/com/igor/fridge/data/FoodItemDaoTest.kt`

**Interfaces:**
- Consuma: `RemovalReason` (Task 2)
- Produce:
  - `FoodItem(uuid: String, name: String, barcode: String?, category: FoodCategory, location: StorageLocation, quantity: Double, unit: QuantityUnit, expiryDate: LocalDate?, addedAt: LocalDate, notes: String?, updatedAt: Instant, removedAt: Instant?, removalReason: RemovalReason?)` — `uuid` è `@PrimaryKey`, senza valore di default
  - `ShoppingItem(uuid: String, name: String, quantity: Double, unit: QuantityUnit, isChecked: Boolean, createdAt: LocalDate, updatedAt: Instant)`
  - `FoodItemDao`: `observeAll(): Flow<List<FoodItem>>`, `findByUuid(uuid: String): FoodItem?`, `findLastByBarcode(barcode: String): FoodItem?`, `findLastByName(name: String): FoodItem?`, `findExpiringOnOrBefore(limitDate: LocalDate): List<FoodItem>`, `upsert(item: FoodItem)`, `delete(item: FoodItem)`
  - `ShoppingItemDao`: `observeAll()`, `findByName(name: String): ShoppingItem?`, `upsert(item: ShoppingItem)`, `delete(item: ShoppingItem)`, `deleteChecked()`

  `upsert` **non restituisce più `Long`**: con una chiave testuale il rowid non serve a nessuno.

- [ ] **Step 1: Scrivere i test che falliscono**

`app/src/test/java/com/igor/fridge/data/FoodItemDaoTest.kt`:

```kotlin
package com.igor.fridge.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.FoodItemDao
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FoodItemDaoTest {

    private lateinit var db: IgorDatabase
    private lateinit var dao: FoodItemDao

    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IgorDatabase::class.java,
        ).build()
        dao = db.foodItemDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(
        uuid: String,
        name: String,
        expiry: LocalDate? = null,
        removedAt: Instant? = null,
        reason: RemovalReason? = null,
        category: FoodCategory = FoodCategory.ALTRO,
        updatedAt: Instant = now,
    ) = FoodItem(
        uuid = uuid,
        name = name,
        expiryDate = expiry,
        updatedAt = updatedAt,
        removedAt = removedAt,
        removalReason = reason,
        category = category,
    )

    @Test
    fun `l'inventario esclude gli articoli rimossi`() = runTest {
        dao.upsert(item("a", "Latte"))
        dao.upsert(item("b", "Pane", removedAt = now, reason = RemovalReason.CONSUMATO))

        assertEquals(listOf("Latte"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun `gli articoli senza scadenza finiscono in fondo`() = runTest {
        dao.upsert(item("a", "Senza data"))
        dao.upsert(item("b", "Scade dopo", expiry = LocalDate.of(2026, 5, 1)))
        dao.upsert(item("c", "Scade prima", expiry = LocalDate.of(2026, 4, 1)))

        assertEquals(
            listOf("Scade prima", "Scade dopo", "Senza data"),
            dao.observeAll().first().map { it.name },
        )
    }

    @Test
    fun `findLastByName trova anche fra gli articoli usciti dal frigo`() = runTest {
        dao.upsert(
            item(
                uuid = "vecchio",
                name = "Latte",
                removedAt = now,
                reason = RemovalReason.CONSUMATO,
                category = FoodCategory.LATTICINI,
                updatedAt = now,
            ),
        )

        val found = dao.findLastByName("latte")

        assertEquals(FoodCategory.LATTICINI, found?.category)
    }

    @Test
    fun `findLastByName preferisce la versione piu recente`() = runTest {
        dao.upsert(item("vecchio", "Latte", category = FoodCategory.ALTRO, updatedAt = now))
        dao.upsert(
            item(
                uuid = "nuovo",
                name = "Latte",
                category = FoodCategory.LATTICINI,
                updatedAt = now.plusSeconds(60),
            ),
        )

        assertEquals(FoodCategory.LATTICINI, dao.findLastByName("Latte")?.category)
    }

    @Test
    fun `le scadenze entro il limite escludono i rimossi`() = runTest {
        dao.upsert(item("a", "Yogurt", expiry = LocalDate.of(2026, 4, 1)))
        dao.upsert(
            item(
                uuid = "b",
                name = "Panna",
                expiry = LocalDate.of(2026, 4, 1),
                removedAt = now,
                reason = RemovalReason.BUTTATO,
            ),
        )

        val found = dao.findExpiringOnOrBefore(LocalDate.of(2026, 4, 2))

        assertEquals(listOf("Yogurt"), found.map { it.name })
    }

    @Test
    fun `un articolo inesistente non viene trovato`() = runTest {
        assertNull(dao.findByUuid("mai-esistito"))
    }
}
```

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*FoodItemDaoTest*" --console=plain`
Atteso: FAIL in compilazione — `FoodItem` non ha `uuid`, `updatedAt`, `removedAt`.

- [ ] **Step 3: Implementare**

`FoodItem.kt` diventa:

```kotlin
package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Un alimento presente in casa.
 *
 * [uuid] e' generato sul dispositivo invece che da SQLite: identificatori autoincrementali
 * collidono fra dispositivi diversi, e l'identita' deve restare valida se un domani
 * l'inventario verra' sincronizzato.
 *
 * [removedAt] realizza la cancellazione logica. Una riga cancellata fisicamente sarebbe
 * invisibile a una sincronizzazione e cancellerebbe la storia di cosa e' stato consumato.
 *
 * [expiryDate] e' nullable perche' non tutti i prodotti riportano una scadenza: gli
 * articoli senza data restano in inventario ma non generano notifiche.
 */
@Entity(
    tableName = "food_items",
    indices = [Index("barcode"), Index("expiryDate"), Index("name"), Index("removedAt")],
)
data class FoodItem(
    @PrimaryKey
    val uuid: String,
    val name: String,
    val barcode: String? = null,
    val category: FoodCategory = FoodCategory.ALTRO,
    val location: StorageLocation = StorageLocation.FRIGO,
    val quantity: Double = 1.0,
    val unit: QuantityUnit = QuantityUnit.PZ,
    val expiryDate: LocalDate? = null,
    val addedAt: LocalDate = LocalDate.now(),
    val notes: String? = null,
    val updatedAt: Instant = Instant.now(),
    val removedAt: Instant? = null,
    val removalReason: RemovalReason? = null,
)
```

`ShoppingItem.kt`:

```kotlin
package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** Una voce della lista della spesa. */
@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey
    val uuid: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: QuantityUnit = QuantityUnit.PZ,
    val isChecked: Boolean = false,
    val createdAt: LocalDate = LocalDate.now(),
    val updatedAt: Instant = Instant.now(),
)
```

`FoodItemDao.kt`:

```kotlin
package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodItemDao {

    /** Ordina per scadenza crescente; gli articoli senza data finiscono in fondo. */
    @Query(
        """
        SELECT * FROM food_items
        WHERE removedAt IS NULL
        ORDER BY (expiryDate IS NULL), expiryDate ASC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): FoodItem?

    /** Usato dopo la scansione per riconoscere un prodotto gia' inserito in passato. */
    @Query(
        """
        SELECT * FROM food_items
        WHERE barcode = :barcode
        ORDER BY updatedAt DESC LIMIT 1
        """
    )
    suspend fun findLastByBarcode(barcode: String): FoodItem?

    /**
     * Ultimo articolo con questo nome, **compresi quelli usciti dal frigo**: serve a
     * ereditare categoria, unita' e posizione quando un prodotto rientra dalla lista
     * della spesa o viene dettato col solo nome.
     */
    @Query(
        """
        SELECT * FROM food_items
        WHERE name = :name COLLATE NOCASE
        ORDER BY updatedAt DESC LIMIT 1
        """
    )
    suspend fun findLastByName(name: String): FoodItem?

    @Query(
        """
        SELECT * FROM food_items
        WHERE removedAt IS NULL AND expiryDate IS NOT NULL AND expiryDate <= :limitDate
        ORDER BY expiryDate ASC
        """
    )
    suspend fun findExpiringOnOrBefore(limitDate: LocalDate): List<FoodItem>

    @Upsert
    suspend fun upsert(item: FoodItem)

    @Delete
    suspend fun delete(item: FoodItem)
}
```

`ShoppingItemDao.kt`: sostituire la firma `suspend fun upsert(item: ShoppingItem): Long` con `suspend fun upsert(item: ShoppingItem)`; il resto resta invariato.

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*FoodItemDaoTest*" --console=plain`
Atteso: PASS (6 test). La compilazione del resto del modulo fallirà ancora: repository e ViewModel usano `id`, ed è il Task 4.

Se il fallimento è in `:app:kspDebugKotlin`, leggere il messaggio di Room: indica la colonna o il converter mancante.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/data/local/ app/src/test/java/com/igor/fridge/data/FoodItemDaoTest.kt app/schemas/
git commit -m "feat: chiave primaria UUID e cancellazione logica nello schema"
```

---

## Task 4: Repository che timbrano le scritture

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/data/repository/FoodRepository.kt`
- Test: `app/src/test/java/com/igor/fridge/data/FoodRepositoryTest.kt`

**Interfaces:**
- Consuma: `FoodItemDao` (Task 3)
- Produce: `FoodRepository.observeAll(): Flow<List<FoodItem>>`, `findByUuid(uuid: String): FoodItem?`, `findLastByBarcode(barcode: String): FoodItem?`, `findLastByName(name: String): FoodItem?`, `save(item: FoodItem): FoodItem`, `remove(item: FoodItem, reason: RemovalReason)`, `restore(item: FoodItem)`, `findExpiring(today: LocalDate, withinDays: Int): List<FoodItem>`. Il costruttore accetta `clock: () -> Instant = Instant::now` e `newUuid: () -> String = { UUID.randomUUID().toString() }` per rendere i test deterministici.
- `save` restituisce l'articolo **come è stato salvato**, con `uuid` e `updatedAt` valorizzati: chi chiama non deve indovinarli.

- [ ] **Step 1: Scrivere i test che falliscono**

`app/src/test/java/com/igor/fridge/data/FoodRepositoryTest.kt`:

```kotlin
package com.igor.fridge.data

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.FoodItemDao
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * DAO in memoria: le regole di scrittura stanno nel repository, non nel database.
 *
 * Lo stato vive in un MutableStateFlow e non in una lista: `observeAll()` deve riemettere
 * dopo ogni scrittura, altrimenti un test che osserva l'inventario dopo una rimozione
 * resterebbe in attesa per sempre.
 */
class FakeFoodItemDao : FoodItemDao {
    private val state = MutableStateFlow<List<FoodItem>>(emptyList())

    val items: List<FoodItem> get() = state.value

    override fun observeAll(): Flow<List<FoodItem>> =
        state.map { list -> list.filter { it.removedAt == null } }

    override suspend fun findByUuid(uuid: String): FoodItem? =
        items.firstOrNull { it.uuid == uuid }

    override suspend fun findLastByBarcode(barcode: String): FoodItem? =
        items.filter { it.barcode == barcode }.maxByOrNull { it.updatedAt }

    override suspend fun findLastByName(name: String): FoodItem? =
        items.filter { it.name.equals(name, ignoreCase = true) }.maxByOrNull { it.updatedAt }

    override suspend fun findExpiringOnOrBefore(limitDate: LocalDate): List<FoodItem> =
        items.filter { it.removedAt == null && it.expiryDate?.let { d -> !d.isAfter(limitDate) } == true }

    override suspend fun upsert(item: FoodItem) {
        state.update { list -> list.filterNot { it.uuid == item.uuid } + item }
    }

    override suspend fun delete(item: FoodItem) {
        state.update { list -> list.filterNot { it.uuid == item.uuid } }
    }
}

class FoodRepositoryTest {

    private val dao = FakeFoodItemDao()
    private var now = Instant.ofEpochMilli(1_774_000_000_000)
    private var counter = 0
    private val repository = FoodRepository(
        dao = dao,
        clock = { now },
        newUuid = { "uuid-${++counter}" },
    )

    private val today = LocalDate.of(2026, 4, 1)

    @Test
    fun `un articolo nuovo riceve un identificatore e un timbro`() = runTest {
        val saved = repository.save(FoodItem(uuid = "", name = "Latte"))

        assertEquals("uuid-1", saved.uuid)
        assertEquals(now, saved.updatedAt)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `un articolo esistente conserva l'identificatore e aggiorna il timbro`() = runTest {
        val first = repository.save(FoodItem(uuid = "", name = "Latte"))
        now = now.plusSeconds(3600)

        val second = repository.save(first.copy(name = "Latte intero"))

        assertEquals(first.uuid, second.uuid)
        assertEquals(now, second.updatedAt)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `rimuovere marca il motivo invece di cancellare`() = runTest {
        val saved = repository.save(FoodItem(uuid = "", name = "Latte"))

        repository.remove(saved, RemovalReason.CONSUMATO)

        val stored = dao.findByUuid(saved.uuid)
        assertNotNull(stored)
        assertEquals(RemovalReason.CONSUMATO, stored?.removalReason)
        assertEquals(now, stored?.removedAt)
    }

    @Test
    fun `ripristinare riporta l'articolo in inventario`() = runTest {
        val saved = repository.save(FoodItem(uuid = "", name = "Latte"))
        repository.remove(saved, RemovalReason.ERRORE)

        repository.restore(dao.findByUuid(saved.uuid)!!)

        val stored = dao.findByUuid(saved.uuid)
        assertNull(stored?.removedAt)
        assertNull(stored?.removalReason)
    }

    @Test
    fun `le scadenze comprendono la soglia e gli articoli gia scaduti`() = runTest {
        repository.save(FoodItem(uuid = "", name = "Scaduto", expiryDate = today.minusDays(2)))
        repository.save(FoodItem(uuid = "", name = "Sul limite", expiryDate = today.plusDays(3)))
        repository.save(FoodItem(uuid = "", name = "Oltre", expiryDate = today.plusDays(4)))

        val found = repository.findExpiring(today, withinDays = 3)

        assertEquals(listOf("Scaduto", "Sul limite"), found.map { it.name }.sorted())
    }
}
```

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*FoodRepositoryTest*" --console=plain`
Atteso: FAIL — il costruttore di `FoodRepository` non accetta `clock` e `newUuid`, `remove` e `restore` non esistono.

- [ ] **Step 3: Implementare**

`FoodRepository.kt`:

```kotlin
package com.igor.fridge.data.repository

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.FoodItemDao
import com.igor.fridge.data.local.RemovalReason
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unico punto di accesso all'inventario alimentare.
 *
 * Identificatore e timbro temporale si assegnano qui e in nessun altro posto: se la UI
 * potesse dimenticarsene, un articolo finirebbe in database senza identita' stabile o
 * senza il dato che serve a risolvere i conflitti di una futura sincronizzazione.
 * [clock] e [newUuid] sono parametri per poter scrivere test deterministici.
 */
class FoodRepository(
    private val dao: FoodItemDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeAll(): Flow<List<FoodItem>> = dao.observeAll()

    suspend fun findByUuid(uuid: String): FoodItem? = dao.findByUuid(uuid)

    suspend fun findLastByBarcode(barcode: String): FoodItem? = dao.findLastByBarcode(barcode)

    suspend fun findLastByName(name: String): FoodItem? = dao.findLastByName(name.trim())

    /** @return l'articolo come e' stato salvato, con identificatore e timbro valorizzati. */
    suspend fun save(item: FoodItem): FoodItem {
        val stamped = item.copy(
            uuid = item.uuid.ifBlank { newUuid() },
            updatedAt = clock(),
        )
        dao.upsert(stamped)
        return stamped
    }

    /** Fa uscire l'articolo dall'inventario conservandone la storia. */
    suspend fun remove(item: FoodItem, reason: RemovalReason) {
        val now = clock()
        dao.upsert(item.copy(removedAt = now, removalReason = reason, updatedAt = now))
    }

    /** Annulla una rimozione. */
    suspend fun restore(item: FoodItem) {
        dao.upsert(item.copy(removedAt = null, removalReason = null, updatedAt = clock()))
    }

    /** Articoli scaduti oppure in scadenza entro [withinDays] giorni a partire da [today]. */
    suspend fun findExpiring(today: LocalDate, withinDays: Int): List<FoodItem> =
        dao.findExpiringOnOrBefore(today.plusDays(withinDays.toLong()))
}
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*FoodRepositoryTest*" --console=plain`
Atteso: PASS (5 test)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/data/repository/FoodRepository.kt app/src/test/java/com/igor/fridge/data/FoodRepositoryTest.kt
git commit -m "feat: il repository assegna identita' e timbro a ogni scrittura"
```

---

## Task 5: La lista della spesa riaccoglie i prodotti già spuntati

Corregge il difetto 1 della spec.

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/data/repository/ShoppingRepository.kt`
- Modify: `app/src/test/java/com/igor/fridge/data/ShoppingRepositoryTest.kt`

**Interfaces:**
- Consuma: `ShoppingItemDao` (Task 3)
- Produce: `ShoppingRepository(dao, clock, newUuid)`; `addIfAbsent(name: String, quantity: Double = 1.0, unit: QuantityUnit = QuantityUnit.PZ): Boolean` — `true` se la lista è cambiata, sia per una voce nuova sia per una voce ripristinata; `setChecked(item, checked)`, `delete(item)`, `deleteChecked()`, `observeAll()`

- [ ] **Step 1: Aggiornare il test esistente e aggiungere il caso mancante**

In `ShoppingRepositoryTest.kt`, il fake va adeguato alle firme del Task 3 e alla chiave testuale:

```kotlin
private class FakeShoppingItemDao : ShoppingItemDao {
    val items = mutableListOf<ShoppingItem>()

    override fun observeAll(): Flow<List<ShoppingItem>> = flowOf(items.toList())

    override suspend fun findByName(name: String): ShoppingItem? =
        items.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun upsert(item: ShoppingItem) {
        items.removeAll { it.uuid == item.uuid }
        items += item
    }

    override suspend fun delete(item: ShoppingItem) {
        items.removeAll { it.uuid == item.uuid }
    }

    override suspend fun deleteChecked() {
        items.removeAll { it.isChecked }
    }
}
```

La costruzione del repository nella classe di test diventa:

```kotlin
    private val dao = FakeShoppingItemDao()
    private var counter = 0
    private val repository = ShoppingRepository(
        dao = dao,
        clock = { Instant.ofEpochMilli(1_774_000_000_000) },
        newUuid = { "uuid-${++counter}" },
    )
```

Il test `non duplica una voce gia presente` va sostituito, perché descrive il comportamento sbagliato:

```kotlin
    @Test
    fun `non crea un doppione per una voce gia presente e non spuntata`() = runTest {
        repository.addIfAbsent("Latte")

        assertFalse(repository.addIfAbsent("  Latte  "))
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `una voce gia spuntata torna da comprare`() = runTest {
        repository.addIfAbsent("Latte")
        repository.setChecked(dao.items.single(), checked = true)

        val changed = repository.addIfAbsent("Latte")

        assertTrue(changed)
        assertEquals(1, dao.items.size)
        assertFalse(dao.items.single().isChecked)
    }

    @Test
    fun `ripristinare una voce spuntata ne aggiorna quantita e unita`() = runTest {
        repository.addIfAbsent("Latte")
        repository.setChecked(dao.items.single(), checked = true)

        repository.addIfAbsent("Latte", quantity = 2.0, unit = QuantityUnit.L)

        assertEquals(2.0, dao.items.single().quantity, 0.001)
        assertEquals(QuantityUnit.L, dao.items.single().unit)
    }
```

Gli altri tre test esistenti (`aggiunge una voce nuova`, `ignora un nome vuoto`, `rimuove solo le voci spuntate`) restano invariati. Aggiungere gli import `kotlinx.coroutines.test.runTest`, `java.time.Instant`, `com.igor.fridge.data.local.QuantityUnit` e sostituire `runBlocking` con `runTest`.

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ShoppingRepositoryTest*" --console=plain`
Atteso: FAIL — `una voce gia spuntata torna da comprare` fallisce perché `addIfAbsent` restituisce `false` e lascia la spunta.

- [ ] **Step 3: Implementare**

`ShoppingRepository.kt`:

```kotlin
package com.igor.fridge.data.repository

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/** Gestisce la lista della spesa. */
class ShoppingRepository(
    private val dao: ShoppingItemDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeAll(): Flow<List<ShoppingItem>> = dao.observeAll()

    /**
     * Mette il prodotto fra le cose da comprare.
     *
     * Se esiste gia' una voce con lo stesso nome ma e' spuntata, viene riportata da
     * comprare invece di essere ignorata: altrimenti un prodotto consumato una seconda
     * volta sparirebbe dall'inventario senza ricomparire in lista.
     *
     * @return true se la lista e' cambiata.
     */
    suspend fun addIfAbsent(
        name: String,
        quantity: Double = 1.0,
        unit: QuantityUnit = QuantityUnit.PZ,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false

        val existing = dao.findByName(trimmed)
        return when {
            existing == null -> {
                dao.upsert(
                    ShoppingItem(
                        uuid = newUuid(),
                        name = trimmed,
                        quantity = quantity,
                        unit = unit,
                        updatedAt = clock(),
                    ),
                )
                true
            }

            existing.isChecked -> {
                dao.upsert(
                    existing.copy(
                        isChecked = false,
                        quantity = quantity,
                        unit = unit,
                        updatedAt = clock(),
                    ),
                )
                true
            }

            else -> false
        }
    }

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) {
        dao.upsert(item.copy(isChecked = checked, updatedAt = clock()))
    }

    suspend fun delete(item: ShoppingItem) = dao.delete(item)

    suspend fun deleteChecked() = dao.deleteChecked()
}
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ShoppingRepositoryTest*" --console=plain`
Atteso: PASS (6 test)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/data/repository/ShoppingRepository.kt app/src/test/java/com/igor/fridge/data/ShoppingRepositoryTest.kt
git commit -m "fix: un prodotto gia' spuntato torna fra le cose da comprare"
```

---

## Task 6: Impostazioni osservabili con DataStore

Corregge il difetto 2 della spec.

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/data/prefs/SettingsStore.kt`
- Modify: `app/src/main/java/com/igor/fridge/di/AppContainer.kt`
- Modify: `app/src/main/java/com/igor/fridge/notification/ExpiryCheckWorker.kt`
- Modify: `app/src/main/java/com/igor/fridge/IgorApplication.kt`
- Test: `app/src/test/java/com/igor/fridge/data/SettingsStoreTest.kt`

**Interfaces:**
- Consuma: niente
- Produce: `SettingsStore(context)` con `warningDays: Flow<Int>`, `notificationHour: Flow<Int>`, `notificationsEnabled: Flow<Boolean>`; `suspend fun setWarningDays(value: Int)`, `setNotificationHour(value: Int)`, `setNotificationsEnabled(value: Boolean)`; `fun snapshot(): Settings` — lettura bloccante per worker e `Application`. `data class Settings(warningDays: Int, notificationHour: Int, notificationsEnabled: Boolean)`. Restano `DEFAULT_WARNING_DAYS = 3` e `DEFAULT_NOTIFICATION_HOUR = 9`.

- [ ] **Step 1: Scrivere i test che falliscono**

`app/src/test/java/com/igor/fridge/data/SettingsStoreTest.kt`:

```kotlin
package com.igor.fridge.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private val store = SettingsStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `i valori di partenza sono quelli di default`() = runTest {
        assertEquals(SettingsStore.DEFAULT_WARNING_DAYS, store.warningDays.first())
        assertEquals(SettingsStore.DEFAULT_NOTIFICATION_HOUR, store.notificationHour.first())
        assertEquals(true, store.notificationsEnabled.first())
    }

    @Test
    fun `la soglia scritta viene riletta`() = runTest {
        store.setWarningDays(7)

        assertEquals(7, store.warningDays.first())
    }

    @Test
    fun `la soglia resta entro i limiti accettabili`() = runTest {
        store.setWarningDays(99)
        assertEquals(30, store.warningDays.first())

        store.setWarningDays(-5)
        assertEquals(0, store.warningDays.first())
    }

    @Test
    fun `l'ora resta entro la giornata`() = runTest {
        store.setNotificationHour(30)

        assertEquals(23, store.notificationHour.first())
    }

    @Test
    fun `lo snapshot riflette le scritture`() = runTest {
        store.setWarningDays(5)
        store.setNotificationsEnabled(false)

        val snapshot = store.snapshot()

        assertEquals(5, snapshot.warningDays)
        assertFalse(snapshot.notificationsEnabled)
    }
}
```

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SettingsStoreTest*" --console=plain`
Atteso: FAIL in compilazione — `warningDays` è oggi una proprietà `Int`, non un `Flow`.

- [ ] **Step 3: Implementare**

`SettingsStore.kt`:

```kotlin
package com.igor.fridge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "igor_settings")

/** Valori delle impostazioni in un istante dato. */
data class Settings(
    val warningDays: Int,
    val notificationHour: Int,
    val notificationsEnabled: Boolean,
)

/**
 * Preferenze dell'utente.
 *
 * DataStore invece di SharedPreferences perche' i valori devono essere osservabili: un
 * cambio di soglia deve raggiungere la lista dell'inventario senza ricrearne il ViewModel.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    /** Giorni di preavviso prima della scadenza. */
    val warningDays: Flow<Int> = store.data.map {
        it[KEY_WARNING_DAYS] ?: DEFAULT_WARNING_DAYS
    }

    /** Ora del giorno (0-23) in cui viene eseguito il controllo scadenze. */
    val notificationHour: Flow<Int> = store.data.map {
        it[KEY_NOTIFICATION_HOUR] ?: DEFAULT_NOTIFICATION_HOUR
    }

    val notificationsEnabled: Flow<Boolean> = store.data.map {
        it[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    suspend fun setWarningDays(value: Int) {
        store.edit { it[KEY_WARNING_DAYS] = value.coerceIn(0, 30) }
    }

    suspend fun setNotificationHour(value: Int) {
        store.edit { it[KEY_NOTIFICATION_HOUR] = value.coerceIn(0, 23) }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        store.edit { it[KEY_NOTIFICATIONS_ENABLED] = value }
    }

    /**
     * Lettura bloccante, per il worker delle notifiche e per l'avvio dell'Application:
     * girano fuori da una coroutine e hanno bisogno del valore subito. E' l'unico punto
     * del progetto in cui e' lecito bloccare su DataStore.
     */
    fun snapshot(): Settings = runBlocking {
        val preferences = store.data.first()
        Settings(
            warningDays = preferences[KEY_WARNING_DAYS] ?: DEFAULT_WARNING_DAYS,
            notificationHour = preferences[KEY_NOTIFICATION_HOUR] ?: DEFAULT_NOTIFICATION_HOUR,
            notificationsEnabled = preferences[KEY_NOTIFICATIONS_ENABLED] ?: true,
        )
    }

    companion object {
        const val DEFAULT_WARNING_DAYS = 3
        const val DEFAULT_NOTIFICATION_HOUR = 9

        private val KEY_WARNING_DAYS = intPreferencesKey("warning_days")
        private val KEY_NOTIFICATION_HOUR = intPreferencesKey("notification_hour")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }
}
```

In `ExpiryCheckWorker.doWork()`, sostituire le prime righe:

```kotlin
        val container = (applicationContext as IgorApplication).container
        val settings = container.settingsStore.snapshot()
        if (!settings.notificationsEnabled) return Result.success()

        val warningDays = settings.warningDays
        val today = LocalDate.now()
        val items = container.foodRepository.findExpiring(today, warningDays)
```

In `IgorApplication.onCreate()`, sostituire il blocco finale:

```kotlin
        val settings = container.settingsStore.snapshot()
        if (settings.notificationsEnabled) {
            ExpiryWorkScheduler.schedule(this, settings.notificationHour)
        }
```

`AppContainer` non cambia struttura: `SettingsStore(appContext)` ha la stessa firma.

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SettingsStoreTest*" --console=plain`
Atteso: PASS (5 test)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/data/prefs/SettingsStore.kt app/src/main/java/com/igor/fridge/notification/ExpiryCheckWorker.kt app/src/main/java/com/igor/fridge/IgorApplication.kt app/src/test/java/com/igor/fridge/data/SettingsStoreTest.kt
git commit -m "feat: impostazioni osservabili con DataStore"
```

---

## Task 7: La data corrente diventa un flusso

Corregge il difetto 3 della spec.

**Files:**
- Create: `app/src/main/java/com/igor/fridge/domain/CurrentDate.kt`
- Test: `app/src/test/java/com/igor/fridge/domain/CurrentDateTest.kt`

**Interfaces:**
- Consuma: niente
- Produce: `fun millisUntilNextMidnight(now: LocalDateTime): Long`; `fun currentDateFlow(clock: () -> LocalDateTime = LocalDateTime::now): Flow<LocalDate>` — emette subito la data corrente e poi una volta a ogni mezzanotte.

- [ ] **Step 1: Scrivere il test che fallisce**

`app/src/test/java/com/igor/fridge/domain/CurrentDateTest.kt`:

```kotlin
package com.igor.fridge.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CurrentDateTest {

    @Test
    fun `a mezzanotte in punto manca un giorno intero`() {
        val now = LocalDateTime.of(2026, 4, 1, 0, 0)

        assertEquals(24 * 60 * 60 * 1000L, millisUntilNextMidnight(now))
    }

    @Test
    fun `un minuto prima di mezzanotte manca un minuto`() {
        val now = LocalDateTime.of(2026, 4, 1, 23, 59)

        assertEquals(60 * 1000L, millisUntilNextMidnight(now))
    }

    @Test
    fun `l'attesa e' sempre positiva`() {
        val now = LocalDateTime.of(2026, 4, 1, 23, 59, 59, 999_000_000)

        assertEquals(1L, millisUntilNextMidnight(now))
    }

    @Test
    fun `il flusso emette subito la data corrente`() = runTest {
        val flow = currentDateFlow { LocalDateTime.of(2026, 4, 1, 10, 0) }

        assertEquals(LocalDate.of(2026, 4, 1), flow.first())
    }
}
```

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CurrentDateTest*" --console=plain`
Atteso: FAIL in compilazione — le funzioni non esistono.

- [ ] **Step 3: Implementare**

`app/src/main/java/com/igor/fridge/domain/CurrentDate.kt`:

```kotlin
package com.igor.fridge.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Millisecondi che mancano alla mezzanotte successiva. Vive in un file senza dipendenze
 * Android per poter essere coperto dai test JVM.
 */
fun millisUntilNextMidnight(now: LocalDateTime): Long =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()

/**
 * Data corrente come flusso: emette subito, poi una volta a ogni mezzanotte.
 *
 * Serve perche' `LocalDate.now()` letto una sola volta congela gli stati di scadenza:
 * con l'app aperta oltre la mezzanotte, "scade oggi" continuerebbe a riferirsi a ieri.
 */
fun currentDateFlow(clock: () -> LocalDateTime = LocalDateTime::now): Flow<LocalDate> = flow {
    while (true) {
        val now = clock()
        emit(now.toLocalDate())
        delay(millisUntilNextMidnight(now))
    }
}
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CurrentDateTest*" --console=plain`
Atteso: PASS (4 test)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/domain/CurrentDate.kt app/src/test/java/com/igor/fridge/domain/CurrentDateTest.kt
git commit -m "feat: la data corrente diventa osservabile"
```

---

## Task 8: L'inventario osserva impostazioni e data

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/ui/inventory/InventoryViewModel.kt`
- Test: `app/src/test/java/com/igor/fridge/ui/InventoryViewModelTest.kt`

**Interfaces:**
- Consuma: `FoodRepository` (Task 4), `ShoppingRepository` (Task 5), `SettingsStore` (Task 6), `currentDateFlow` (Task 7)
- Produce: `InventoryViewModel(foodRepository, shoppingRepository, warningDays: Flow<Int>, today: Flow<LocalDate>)`. `InventoryUiState` invariato salvo `items: List<FoodItem>` con identità testuale. Nuovo valore `InventoryFilter.SENZA_DATA` e campo `noDateCount: Int`. `delete(item)` chiama `remove(item, RemovalReason.ERRORE)`, `consume(item)` chiama `remove(item, RemovalReason.CONSUMATO)`.

Il ViewModel riceve dei `Flow` invece del `SettingsStore`: non gli serve sapere da dove vengono, e i test possono iniettarne di finti senza toccare DataStore.

- [ ] **Step 1: Scrivere i test che falliscono**

`app/src/test/java/com/igor/fridge/ui/InventoryViewModelTest.kt`:

```kotlin
package com.igor.fridge.ui

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.ui.inventory.InventoryFilter
import com.igor.fridge.ui.inventory.InventoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private class FakeShoppingDao : ShoppingItemDao {
    val items = mutableListOf<ShoppingItem>()
    override fun observeAll(): Flow<List<ShoppingItem>> = flowOf(items.toList())
    override suspend fun findByName(name: String): ShoppingItem? =
        items.firstOrNull { it.name.equals(name, ignoreCase = true) }
    override suspend fun upsert(item: ShoppingItem) {
        items.removeAll { it.uuid == item.uuid }
        items += item
    }
    override suspend fun delete(item: ShoppingItem) { items.removeAll { it.uuid == item.uuid } }
    override suspend fun deleteChecked() { items.removeAll { it.isChecked } }
}

class InventoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 4, 1)
    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    private val foodDao = FakeFoodItemDao()
    private val shoppingDao = FakeShoppingDao()
    private var counter = 0

    private val foodRepository = FoodRepository(foodDao, { now }, { "uuid-${++counter}" })
    private val shoppingRepository = ShoppingRepository(shoppingDao, { now }, { "s-${++counter}" })

    private val warningDays = MutableStateFlow(3)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = InventoryViewModel(
        foodRepository = foodRepository,
        shoppingRepository = shoppingRepository,
        warningDays = warningDays,
        today = flowOf(today),
    )

    private suspend fun seed() {
        foodRepository.save(FoodItem(uuid = "", name = "Scaduto", expiryDate = today.minusDays(1)))
        foodRepository.save(FoodItem(uuid = "", name = "In scadenza", expiryDate = today.plusDays(2)))
        foodRepository.save(FoodItem(uuid = "", name = "Fresco", expiryDate = today.plusDays(30)))
        foodRepository.save(
            FoodItem(
                uuid = "",
                name = "Senza data",
                location = StorageLocation.DISPENSA,
            ),
        )
    }

    @Test
    fun `i conteggi distinguono gli stati`() = runTest(dispatcher) {
        seed()
        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(4, state.totalCount)
        assertEquals(1, state.expiredCount)
        assertEquals(1, state.expiringCount)
        assertEquals(1, state.noDateCount)
    }

    @Test
    fun `il filtro senza data mostra solo gli articoli senza scadenza`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.onFilterChange(InventoryFilter.SENZA_DATA)

        val state = vm.uiState.first { it.filter == InventoryFilter.SENZA_DATA && !it.isLoading }

        assertEquals(listOf("Senza data"), state.items.map { it.name })
    }

    @Test
    fun `la ricerca ignora maiuscole e spazi`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.onQueryChange("  fresco ")

        val state = vm.uiState.first { it.query.isNotBlank() && !it.isLoading }

        assertEquals(listOf("Fresco"), state.items.map { it.name })
    }

    @Test
    fun `alzare la soglia sposta un articolo fra quelli in scadenza`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.uiState.first { !it.isLoading }

        warningDays.value = 40

        val state = vm.uiState.first { it.warningDays == 40 }
        assertEquals(3, state.expiringCount)
    }

    @Test
    fun `consumare toglie dall'inventario e mette in lista`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        val item = vm.uiState.first { !it.isLoading }.items.first { it.name == "Fresco" }

        vm.consume(item)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Fresco"), shoppingDao.items.map { it.name })
        assertEquals(3, vm.uiState.first { it.totalCount == 3 }.totalCount)
    }
}
```

`FakeFoodItemDao` è quello del Task 4: spostarlo da `FoodRepositoryTest.kt` a un file condiviso `app/src/test/java/com/igor/fridge/data/FakeFoodItemDao.kt` rimuovendo `private` dalla dichiarazione, e importarlo in entrambi i test.

- [ ] **Step 2: Eseguire e verificare il fallimento**

Run: `.\gradlew.bat testDebugUnitTest --tests "*InventoryViewModelTest*" --console=plain`
Atteso: FAIL in compilazione — il costruttore non accetta `Flow`, `InventoryFilter.SENZA_DATA` e `noDateCount` non esistono.

- [ ] **Step 3: Implementare**

In `InventoryViewModel.kt`: aggiungere `SENZA_DATA` all'enum, `noDateCount` allo stato, e sostituire costruttore e `uiState`:

```kotlin
enum class InventoryFilter { TUTTI, IN_SCADENZA, SCADUTI, SENZA_DATA }

class InventoryViewModel(
    private val foodRepository: FoodRepository,
    private val shoppingRepository: ShoppingRepository,
    warningDays: Flow<Int>,
    today: Flow<LocalDate>,
) : ViewModel() {

    private val criteria = MutableStateFlow(Criteria())

    val uiState: StateFlow<InventoryUiState> =
        combine(
            foodRepository.observeAll(),
            criteria,
            warningDays,
            today,
        ) { items, criteria, warningDays, today ->
            val statuses = items.associateBy({ it.uuid }, { it.expiryStatus(today, warningDays) })
            val visible = items.filter { item ->
                val matchesQuery = criteria.query.isBlank() ||
                    item.name.contains(criteria.query.trim(), ignoreCase = true)
                val matchesLocation = criteria.location == null || item.location == criteria.location
                val matchesFilter = when (criteria.filter) {
                    InventoryFilter.TUTTI -> true
                    InventoryFilter.IN_SCADENZA -> statuses[item.uuid] == ExpiryStatus.IN_SCADENZA
                    InventoryFilter.SCADUTI -> statuses[item.uuid] == ExpiryStatus.SCADUTO
                    InventoryFilter.SENZA_DATA -> statuses[item.uuid] == ExpiryStatus.SENZA_DATA
                }
                matchesQuery && matchesLocation && matchesFilter
            }
            InventoryUiState(
                items = visible,
                query = criteria.query,
                filter = criteria.filter,
                location = criteria.location,
                warningDays = warningDays,
                today = today,
                totalCount = items.size,
                expiringCount = statuses.values.count { it == ExpiryStatus.IN_SCADENZA },
                expiredCount = statuses.values.count { it == ExpiryStatus.SCADUTO },
                noDateCount = statuses.values.count { it == ExpiryStatus.SENZA_DATA },
                isLoading = false,
                message = criteria.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = InventoryUiState(),
        )
```

`delete` e `consume` passano per la rimozione logica:

```kotlin
    fun delete(item: FoodItem) {
        viewModelScope.launch {
            foodRepository.remove(item, RemovalReason.ERRORE)
            criteria.update { it.copy(message = "${item.name} eliminato") }
        }
    }

    /** Segna il prodotto come consumato: esce dall'inventario ed entra nella lista della spesa. */
    fun consume(item: FoodItem) {
        viewModelScope.launch {
            foodRepository.remove(item, RemovalReason.CONSUMATO)
            shoppingRepository.addIfAbsent(item.name, item.quantity, item.unit)
            criteria.update { it.copy(message = "${item.name} spostato nella lista della spesa") }
        }
    }
```

`addExpiringToShoppingList` legge la data e la soglia dallo stato corrente invece che da variabili catturate:

```kotlin
    fun addExpiringToShoppingList() {
        viewModelScope.launch {
            val state = uiState.value
            val candidates = foodRepository.findExpiring(state.today, state.warningDays)
            val added = candidates.count { shoppingRepository.addIfAbsent(it.name, it.quantity, it.unit) }
            val text = when {
                candidates.isEmpty() -> "Nessun prodotto in scadenza"
                added == 0 -> "Già presenti nella lista della spesa"
                added == 1 -> "1 prodotto aggiunto alla lista della spesa"
                else -> "$added prodotti aggiunti alla lista della spesa"
            }
            criteria.update { it.copy(message = text) }
        }
    }
```

La `Factory` costruisce i flussi dal container:

```kotlin
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                InventoryViewModel(
                    foodRepository = container.foodRepository,
                    shoppingRepository = container.shoppingRepository,
                    warningDays = container.settingsStore.warningDays,
                    today = currentDateFlow(),
                )
            }
        }
```

- [ ] **Step 4: Eseguire e verificare che passi**

Run: `.\gradlew.bat testDebugUnitTest --tests "*InventoryViewModelTest*" --console=plain`
Atteso: PASS (5 test)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/ui/inventory/InventoryViewModel.kt app/src/test/java/com/igor/fridge/ui/InventoryViewModelTest.kt app/src/test/java/com/igor/fridge/data/
git commit -m "feat: l'inventario reagisce a soglia e cambio di giorno"
```

---

## Task 9: Identità testuale in navigazione e schermate

Questo task non introduce comportamenti nuovi: porta il resto dell'app al nuovo modello. Il suo criterio di riuscita è che **tutto compili e i test precedenti continuino a passare**.

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/ui/edit/EditItemViewModel.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/edit/EditItemScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/navigation/IgorNavHost.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/inventory/InventoryScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/components/FoodItemCard.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingScreen.kt`

**Interfaces:**
- Consuma: `FoodRepository` (Task 4), `InventoryViewModel` (Task 8)
- Produce: `const val NEW_ITEM_UUID = "new"`; `EditItemViewModel.factory(uuid: String)`; `EditItemScreen(uuid: String, scannedBarcode: String?, …)`; `Routes.EDIT = "edit/{uuid}"`, `Routes.edit(uuid: String)`

- [ ] **Step 1: Sostituire l'identità nel ViewModel di modifica**

In `EditItemViewModel.kt`:

```kotlin
/** Identificatore convenzionale per un articolo non ancora salvato. */
const val NEW_ITEM_UUID: String = "new"
```

`EditItemUiState.id: Long` diventa `uuid: String = NEW_ITEM_UUID` e `isNew` diventa `get() = uuid == NEW_ITEM_UUID`. Il costruttore prende `itemUuid: String`. Il blocco `init` usa `repository.findByUuid(itemUuid)`. In `save()`, la costruzione dell'articolo usa:

```kotlin
            repository.save(
                FoodItem(
                    uuid = if (state.isNew) "" else state.uuid,
                    name = name,
                    barcode = state.barcode,
                    category = state.category,
                    location = state.location,
                    quantity = quantity,
                    unit = state.unit,
                    expiryDate = state.expiryDate,
                    addedAt = state.addedAt,
                    notes = state.notes.trim().ifEmpty { null },
                ),
            )
```

Un `uuid` vuoto è il segnale con cui `FoodRepository.save` capisce che deve generarne uno nuovo.

`delete()` diventa:

```kotlin
    fun delete() {
        val state = _uiState.value
        if (state.isNew) {
            _uiState.update { it.copy(isSaved = true) }
            return
        }
        viewModelScope.launch {
            repository.findByUuid(state.uuid)?.let { repository.remove(it, RemovalReason.ERRORE) }
            _uiState.update { it.copy(isSaved = true) }
        }
    }
```

e `factory(itemUuid: String)` sostituisce `factory(itemId: Long)`.

- [ ] **Step 2: Aggiornare navigazione e schermate**

In `IgorNavHost.kt`:

```kotlin
object Routes {
    const val INVENTORY = "inventory"
    const val EDIT = "edit/{uuid}"
    const val SCANNER = "scanner"
    const val SHOPPING = "shopping"
    const val SETTINGS = "settings"

    fun edit(uuid: String): String = "edit/$uuid"
}
```

La destinazione `EDIT` usa `NavType.StringType` con `defaultValue = NEW_ITEM_UUID`, legge `backStackEntry.arguments?.getString("uuid") ?: NEW_ITEM_UUID` e lo passa come `uuid` a `EditItemScreen`. `InventoryScreen` riceve `onEditItem: (String) -> Unit` e `onAddItem` naviga a `Routes.edit(NEW_ITEM_UUID)`.

In `InventoryScreen.kt`, la `LazyColumn` usa `key = { it.uuid }`; in `ShoppingScreen.kt` lo stesso. `FoodItemCard` non cambia firma: riceve già l'oggetto intero.

In `FilterRow`, dopo il chip "Scaduti", va aggiunto il quarto filtro che rende raggiungibile il nuovo stato introdotto nel Task 8:

```kotlin
        FilterChip(
            selected = state.filter == InventoryFilter.SENZA_DATA,
            onClick = { onFilterChange(InventoryFilter.SENZA_DATA) },
            label = { Text("Senza data (${state.noDateCount})") },
        )
```

(il testo passerà a `stringResource(R.string.filter_no_date, state.noDateCount)` nel Task 11, insieme a tutti gli altri)

- [ ] **Step 3: Compilare**

Run: `.\gradlew.bat assembleDebug --console=plain`
Atteso: BUILD SUCCESSFUL. Gli errori residui sono riferimenti a `item.id` rimasti: correggerli in `uuid`.

- [ ] **Step 4: Eseguire tutti i test**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Atteso: PASS, tutte le classi di test comprese quelle originali (`ExpiryTest`, `FormattersTest`, `NextDailyRunTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/ui/
git commit -m "refactor: identita' testuale in navigazione e schermate"
```

---

## Task 10: Schermata Impostazioni

**Files:**
- Create: `app/src/main/java/com/igor/fridge/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/igor/fridge/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/navigation/IgorNavHost.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/inventory/InventoryScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/di/AppContainer.kt`

**Interfaces:**
- Consuma: `SettingsStore` (Task 6), `ExpiryWorkScheduler` (esistente)
- Produce: `SettingsViewModel(settingsStore, onScheduleChanged: (enabled: Boolean, hour: Int) -> Unit)` con `uiState: StateFlow<SettingsUiState>` e `onWarningDaysChange(Int)`, `onHourChange(Int)`, `onNotificationsToggle(Boolean)`; `SettingsScreen(onBack: () -> Unit)`
- `data class SettingsUiState(warningDays: Int, notificationHour: Int, notificationsEnabled: Boolean)`

- [ ] **Step 1: Scrivere il ViewModel**

`app/src/main/java/com/igor/fridge/ui/settings/SettingsViewModel.kt`:

```kotlin
package com.igor.fridge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.prefs.SettingsStore
import com.igor.fridge.notification.ExpiryWorkScheduler
import com.igor.fridge.ui.igorApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val warningDays: Int = SettingsStore.DEFAULT_WARNING_DAYS,
    val notificationHour: Int = SettingsStore.DEFAULT_NOTIFICATION_HOUR,
    val notificationsEnabled: Boolean = true,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val onScheduleChanged: (enabled: Boolean, hour: Int) -> Unit,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.warningDays,
        settingsStore.notificationHour,
        settingsStore.notificationsEnabled,
    ) { days, hour, enabled ->
        SettingsUiState(days, hour, enabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    fun onWarningDaysChange(value: Int) {
        viewModelScope.launch { settingsStore.setWarningDays(value) }
    }

    /** Cambiare l'ora riprogramma il controllo: il valore salvato da solo non basta. */
    fun onHourChange(value: Int) {
        viewModelScope.launch {
            settingsStore.setNotificationHour(value)
            val state = settingsStore.snapshot()
            onScheduleChanged(state.notificationsEnabled, state.notificationHour)
        }
    }

    fun onNotificationsToggle(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setNotificationsEnabled(enabled)
            onScheduleChanged(enabled, settingsStore.snapshot().notificationHour)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = igorApplication()
                SettingsViewModel(
                    settingsStore = application.container.settingsStore,
                    onScheduleChanged = { enabled, hour ->
                        if (enabled) {
                            ExpiryWorkScheduler.schedule(application, hour)
                        } else {
                            ExpiryWorkScheduler.cancel(application)
                        }
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Scrivere la schermata**

`app/src/main/java/com/igor/fridge/ui/settings/SettingsScreen.kt`:

```kotlin
package com.igor.fridge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_warning_days, state.warningDays),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_warning_days_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.warningDays.toFloat(),
                onValueChange = { viewModel.onWarningDaysChange(it.toInt()) },
                valueRange = 0f..14f,
                steps = 13,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_notifications),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = state.notificationsEnabled,
                    onCheckedChange = viewModel::onNotificationsToggle,
                )
            }

            Text(
                text = stringResource(R.string.settings_hour, state.notificationHour),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = state.notificationHour.toFloat(),
                onValueChange = { viewModel.onHourChange(it.toInt()) },
                valueRange = 0f..23f,
                steps = 22,
                enabled = state.notificationsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_hour_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 3: Agganciare la schermata**

In `IgorNavHost.kt`, dentro il `NavHost`:

```kotlin
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
```

`InventoryScreen` riceve un parametro `onOpenSettings: () -> Unit` e mostra in barra un `IconButton` con `Icons.Filled.Settings` e `contentDescription = stringResource(R.string.action_settings)`; in `IgorNavHost` viene collegato a `navController.navigate(Routes.SETTINGS)`.

- [ ] **Step 4: Compilare e provare**

Run: `.\gradlew.bat assembleDebug --console=plain`
Atteso: BUILD SUCCESSFUL. Le stringhe `R.string.settings_*` non esistono ancora: vanno aggiunte ora a `res/values/strings.xml` (il Task 11 sposterà le altre):

```xml
    <string name="settings_title">Impostazioni</string>
    <string name="settings_warning_days">Preavviso: %1$d giorni</string>
    <string name="settings_warning_days_help">Un prodotto è considerato in scadenza entro questo numero di giorni.</string>
    <string name="settings_notifications">Notifiche</string>
    <string name="settings_hour">Ora del controllo: %1$d</string>
    <string name="settings_hour_help">L\'orario è indicativo: il sistema può rimandare il controllo di qualche ora per risparmiare batteria.</string>
    <string name="action_back">Indietro</string>
    <string name="action_settings">Impostazioni</string>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/igor/fridge/ui/ app/src/main/res/values/strings.xml
git commit -m "feat: schermata impostazioni"
```

---

## Task 11: Stringhe in risorse e apostrofi corretti

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: tutte le schermate in `app/src/main/java/com/igor/fridge/ui/`
- Modify: `app/src/main/java/com/igor/fridge/ui/Formatters.kt`
- Modify: `app/src/main/java/com/igor/fridge/notification/ExpiryNotifier.kt`

**Interfaces:**
- Consuma: niente
- Produce: nessuna nuova firma pubblica. `Formatters.kt` mantiene le firme attuali (`expiryLabel(days: Long?): String` e le `label()`), ma le stringhe letterali vengono corrette negli apostrofi: quelle funzioni non hanno accesso alle risorse e restano in Kotlin.

- [ ] **Step 1: Spostare le stringhe delle schermate**

In `strings.xml`, aggiungere (oltre a quelle del Task 10):

```xml
    <string name="inventory_title">Igor · Frigorifero</string>
    <string name="inventory_search">Cerca</string>
    <string name="inventory_empty">Il frigo è vuoto.\nTocca + per aggiungere il primo alimento.</string>
    <string name="inventory_empty_filtered">Nessun prodotto corrisponde ai filtri</string>
    <string name="filter_all">Tutti (%1$d)</string>
    <string name="filter_expiring">In scadenza (%1$d)</string>
    <string name="filter_expired">Scaduti (%1$d)</string>
    <string name="filter_no_date">Senza data (%1$d)</string>
    <string name="filter_anywhere">Ovunque</string>
    <string name="action_add_item">Aggiungi un alimento</string>
    <string name="action_add_expiring">Aggiungi i prodotti in scadenza alla lista della spesa</string>
    <string name="action_open_shopping">Apri la lista della spesa</string>
    <string name="action_consume">Segna come consumato e aggiungi alla spesa</string>
    <string name="action_delete_item">Elimina %1$s</string>
    <string name="shopping_title">Lista della spesa</string>
    <string name="shopping_add">Aggiungi alla lista</string>
    <string name="shopping_empty">La lista è vuota.</string>
    <string name="shopping_clear_checked">Rimuovi presi (%1$d)</string>
    <string name="edit_title_new">Nuovo alimento</string>
    <string name="edit_title_existing">Modifica alimento</string>
    <string name="edit_name">Nome *</string>
    <string name="edit_barcode">Codice a barre</string>
    <string name="edit_quantity">Quantità</string>
    <string name="edit_unit">Unità</string>
    <string name="edit_category">Categoria</string>
    <string name="edit_location">Conservazione</string>
    <string name="edit_expiry">Scadenza</string>
    <string name="edit_expiry_none">Nessuna data</string>
    <string name="edit_expiry_set">Imposta data</string>
    <string name="edit_expiry_change">Cambia data</string>
    <string name="edit_expiry_clear">Rimuovi data</string>
    <string name="edit_notes">Note</string>
    <string name="edit_save">Salva</string>
    <string name="error_name_required">Il nome è obbligatorio</string>
    <string name="error_quantity_invalid">Inserisci una quantità maggiore di zero</string>
    <string name="scanner_title">Scansiona il codice</string>
    <string name="scanner_permission_needed">Serve il permesso di usare la fotocamera.</string>
    <string name="scanner_permission_denied">Senza accesso alla fotocamera non è possibile leggere i codici a barre. Puoi concederlo dalle impostazioni di sistema.</string>
    <string name="scanner_allow">Consenti</string>
    <string name="action_scan">Scansiona il codice a barre</string>
    <string name="action_ok">OK</string>
    <string name="action_cancel">Annulla</string>
```

Nei file Compose, sostituire ogni letterale con `stringResource(R.string.…)`, aggiungendo gli import `androidx.compose.ui.res.stringResource` e `com.igor.fridge.R`.

I messaggi generati nei ViewModel (`"$added prodotti aggiunti…"`, `"${item.name} eliminato"`) **restano in Kotlin**: i ViewModel non hanno un `Context` e passarglielo per costruire stringhe sarebbe peggio del problema che risolve. Correggere però gli apostrofi: `"Già presenti nella lista della spesa"`.

- [ ] **Step 2: Correggere gli apostrofi nei commenti e nelle stringhe rimaste**

In `Formatters.kt`, `expiryLabel` e le `label()` restano invariate nelle firme; nessuna contiene apostrofi. In `EditItemViewModel` i due messaggi di errore diventano `stringResource` lato schermata: lo stato espone `nameError: Boolean` e `quantityError: Boolean`, e `EditItemScreen` decide il testo. Aggiornare di conseguenza i campi di `EditItemUiState`.

- [ ] **Step 3: Compilare**

Run: `.\gradlew.bat assembleDebug --console=plain`
Atteso: BUILD SUCCESSFUL, nessun warning

- [ ] **Step 4: Eseguire tutti i test**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Atteso: PASS. Se `EditItemViewModel` ha test che verificano il testo degli errori, adeguarli ai booleani.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/igor/fridge/ui/
git commit -m "refactor: testi dell'interfaccia in strings.xml"
```

---

## Task 12: Icona della notifica e verifica finale

**Files:**
- Create: `app/src/main/res/drawable/ic_notification.xml`
- Modify: `app/src/main/java/com/igor/fridge/notification/ExpiryNotifier.kt`
- Modify: `README.md`

**Interfaces:**
- Consuma: niente
- Produce: `R.drawable.ic_notification`

- [ ] **Step 1: Creare l'icona**

`app/src/main/res/drawable/ic_notification.xml` — sagoma piena su fondo trasparente, come richiesto dalle icone di stato (il sistema le colora di bianco e ne usa solo l'alfa):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M7,2h10c1.1,0 2,0.9 2,2v16c0,1.1 -0.9,2 -2,2H7c-1.1,0 -2,-0.9 -2,-2V4c0,-1.1 0.9,-2 2,-2zM7,4v6h10V4H7zM7,12v8h10v-8H7zM8.5,5.5h1.5v3h-1.5zM8.5,13.5h1.5v3h-1.5z" />
</vector>
```

- [ ] **Step 2: Usarla nella notifica**

In `ExpiryNotifier.kt`, sostituire `.setSmallIcon(android.R.drawable.stat_notify_more)` con `.setSmallIcon(R.drawable.ic_notification)`. L'import di `com.igor.fridge.R` è già presente.

- [ ] **Step 3: Compilare e verificare l'intero progetto**

Run: `.\gradlew.bat clean assembleDebug testDebugUnitTest --console=plain`
Atteso: BUILD SUCCESSFUL, nessun warning, tutti i test verdi. L'APK è in `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Aggiornare il README**

Sostituire la sezione "Stato" con la situazione reale (schema a UUID con cancellazione logica, impostazioni con DataStore, test su JVM con Robolectric) e la sezione "Prossimi passi" con le Fasi 2 e 3 della spec: ciclo chiuso della lista della spesa, inserimento vocale. Segnalare che una build precedente installata va disinstallata, perché lo schema è cambiato mantenendo `version = 1`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_notification.xml app/src/main/java/com/igor/fridge/notification/ExpiryNotifier.kt README.md
git commit -m "feat: icona dedicata per la notifica delle scadenze"
```

---

## Prova sul dispositivo

Conclusi i dodici task, l'app va installata e provata: è il primo contatto reale con Igor e nessun test lo sostituisce.

```
.\gradlew.bat installDebug --console=plain
```

Da verificare a mano: aggiunta di un alimento con e senza scadenza; ordinamento della lista; i quattro filtri compreso "Senza data"; consumo di un prodotto e sua comparsa nella lista della spesa; consumo dello stesso prodotto dopo averlo spuntato (è il difetto 1, deve tornare da comprare); modifica della soglia nelle impostazioni e aggiornamento immediato dei conteggi; scansione di un codice a barre; permesso notifiche al primo avvio.
