# Inventario e categorie — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendere l'inventario leggibile tramite sezioni e filtri per categoria, ampliando la classificazione condivisa senza perdere dati.

**Architecture:** Estendere l'enum persistito mantenendo tutti i nomi esistenti. Una funzione pura costruisce sezioni, risultati e conteggi; il ViewModel osserva Room, preferenze, data e criteri ripristinabili. Compose presenta questo modello con selettori ricercabili comuni e righe Material 3.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose/Material 3, Lifecycle 2.8.7, Room 2.6.1, coroutines, JUnit 4 e Robolectric gia' presenti; Android minSdk 26, JDK 17. Nessuna dipendenza aggiuntiva prevista.

**Spec:** [Specifica approvata](../specs/2026-10-04-inventario-categorie-design.md), approvata dall'utente in chat il 4 ottobre 2026.

## Global Constraints

- «Funziona offline, nell'app Android attuale, senza dipendere dal futuro servizio prezzi.»
- «I valori esistenti non vengono rinominati o rimossi, e i record non sono riclassificati automaticamente in questo blocco.»
- «Le stringhe fisse nuove vanno nelle risorse Android.»
- «Le sezioni vuote non compaiono.» Ordine predefinito delle categorie, Altro in fondo; nessun legame con l'ordine personalizzato della spesa.
- «La scelta manuale dell'utente prevale sempre.»
- «Le screenshot del browser non valgono come verifica dell'interfaccia Android.»
- Conservare transazioni, cancellazione logica e azioni attuali; i bug gia' individuati restano nel lavoro successivo.
- Nessun cambio di schema Room: non incrementare la versione e non generare una migrazione per il solo ampliamento dell'enum.
- UI italiana, commenti Kotlin senza accenti; tema chiaro/scuro, font ingranditi, TalkBack e tocchi di almeno 48 dp.
- Prima delle modifiche UI applicare Impeccable Operate e leggere `.agents/skills/impeccable/reference/craft-floor.md` e `.agents/skills/impeccable/reference/android.md`; usare i riferimenti pertinenti gia' individuati, senza attivare un ridisegno globale.

## Review Focus

1. Categoria selezionata che perde l'ultimo articolo: il filtro resta visibile e azzerabile; test task 3 e 4.
2. Ricerca digitata rapidamente o azzerata: il testo locale e i criteri ripristinati concordano, senza lettere perse; prova task 5 e 6.
3. Ordine corsie salvato prima dell'ampliamento: resta intatto nel prefisso e si aggiungono tutte le categorie nuove una volta; test task 1.
4. Scontrino ambiguo o composito: "tonno in scatola", "latte di soia" e abbreviazioni non diventano automaticamente prodotti freschi errati; test task 2.
5. Titolo lungo, tastiera e font al 200% su schermo stretto: selettore e azioni restano raggiungibili, intestazioni non trasparenti; prova task 5 e 6.

## Confini e struttura dei file

Percorsi relativi alla radice del repository. Il prefisso `M` indica
`app/src/main/java/com/igor/fridge`, `T` indica
`app/src/test/java/com/igor/fridge`; nei blocchi Files i percorsi sono completi.

| Unita' | Responsabilita' |
| --- | --- |
| `M/data/local/Enums.kt` | Codici stabili e proprieta' merceologiche |
| `M/ui/CategoryPresentation.kt` (nuovo) | Mappa esaustiva categoria -> risorsa dell'etichetta e icona |
| `M/domain/CategoryGuess.kt` | Proposta dal nome, senza scritture sui record esistenti |
| `M/ui/inventory/InventoryListModel.kt` (nuovo) | Criteri, sezioni e proiezione pura |
| `M/ui/inventory/InventoryViewModel.kt` | Flussi, SavedStateHandle e azioni esistenti |
| `M/ui/components/CategoryPicker.kt` (nuovo) | Campo e foglio inferiore ricercabile comuni |
| `M/ui/inventory/InventoryScreen.kt` | Filtri compatti, sezioni, stati vuoti e caricamento |
| `M/ui/components/FoodItemCard.kt` | Gerarchia della riga e azioni accessibili |

I task sono sequenziali. Ogni commit deve compilare e superare i test mirati;
non aggiungere file temporanei o cambiamenti alle skill ai commit di prodotto.
Si lavora sul ramo corrente salvo modifiche concorrenti: in quel caso isolare
il lavoro senza spostare o cancellare modifiche dell'utente.

## Task 1: Tassonomia e presentazione compatibili

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/data/local/Enums.kt`
- Create: `app/src/main/java/com/igor/fridge/ui/CategoryPresentation.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/Formatters.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/igor/fridge/ui/edit/EditItemScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingItemEditScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/receipt/ReceiptScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/settings/AisleOrderScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingListModel.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingViewModel.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/components/FoodItemCard.kt`
- Create: `app/src/test/java/com/igor/fridge/domain/FoodCategoryTest.kt`
- Create: `app/src/test/java/com/igor/fridge/ui/CategoryPresentationTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/data/ConvertersTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/domain/CategoryOrderTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/ui/AisleOrderViewModelTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/ui/FormattersTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/ui/ShoppingListModelTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/ui/ShoppingViewModelTest.kt`

**Interfaces:**
- Consumes: `FoodCategory`, `StorageLocation`, `Converters.toFoodCategory(String?)`, `categoryOrderFrom(String?)`.
- Produces: enum ampliato; `@StringRes fun FoodCategory.labelRes(): Int`; `fun FoodCategory.icon(): String` conserva la firma; `fun shareText(sections: List<ShoppingSection>, totals: ShoppingTotals? = null, categoryLabel: (FoodCategory) -> String): String`; `ShoppingViewModel.shareText(categoryLabel: (FoodCategory) -> String): String`.

- [x] **1. Bloccare compatibilita' e comportamento delle nuove categorie con test.**

```kotlin
@Test fun `i codici della versione precedente restano leggibili`() {
    val old = "FRUTTA,VERDURA,PANE,CARNE,PESCE,LATTICINI,SURGELATI,DISPENSA,CONDIMENTI,BEVANDE,CASA,IGIENE,ALTRO"
    val converter = Converters()
    old.split(',').forEach { code ->
        assertEquals(code, converter.fromFoodCategory(converter.toFoodCategory(code)))
    }
    FoodCategory.entries.forEach { category ->
        assertEquals(category, converter.toFoodCategory(converter.fromFoodCategory(category)))
    }
}
@Test fun `l'ordine vecchio non perde categorie e aggiunge quelle nuove`() {
    val old = "BEVANDE,FRUTTA,LATTICINI,ALTRO"
    val actual = categoryOrderFrom(old)
    assertEquals(old.split(',').map(FoodCategory::valueOf), actual.take(4))
    assertEquals(FoodCategory.entries.toSet(), actual.toSet())
    assertEquals(actual.size, actual.distinct().size)
}
@Test fun `salumi richiedono scadenza e gelati vanno in freezer`() {
    assertTrue(FoodCategory.SALUMI.isPerishable)
    assertTrue(FoodCategory.FORMAGGI_FRESCHI.isSoldByWeight)
    assertEquals(StorageLocation.FREEZER, FoodCategory.GELATI.defaultLocation)
    assertEquals(StorageLocation.DISPENSA, FoodCategory.BISCOTTI.defaultLocation)
    assertFalse(FoodCategory.BUCATO.isFood)
}
```

- [x] **2. Eseguire il primo ciclo di test.**

```powershell
.\gradlew.bat testDebugUnitTest --offline --console=plain --tests "com.igor.fridge.domain.FoodCategoryTest" --tests "com.igor.fridge.data.ConvertersTest" --tests "com.igor.fridge.domain.CategoryOrderTest"
```

Atteso: compilazione fallita sui nuovi codici ancora assenti; i casi dei vecchi
codici sono regressioni che devono continuare a passare.

- [x] **3. Aggiungere i codici e assegnare proprieta' esplicite.**

Inserire le categorie nell'ordine della tabella. Risorsa = `category_` + codice
in minuscolo. Profilo specifica posizione, deperibilita', vendita a peso e
suggerimento calendario; non e' una nuova entita' persistita.

| Codice | Etichetta | Profilo |
| --- | --- | --- |
| FRUTTA | Frutta | frutta |
| VERDURA | Verdura | verdura |
| ERBE_AROMATICHE | Erbe aromatiche | verdura |
| FRUTTA_SECCA | Frutta secca e semi | dispensa |
| CARNE | Carne | carne |
| SALUMI | Salumi e affettati | carne |
| PESCE | Pesce e frutti di mare | pesce |
| UOVA | Uova | latticini |
| LATTE_PANNA | Latte e panna | latticini |
| YOGURT | Yogurt e dessert al latte | latticini |
| BURRO_MARGARINA | Burro e margarina | latticini |
| FORMAGGI_FRESCHI | Formaggi freschi | formaggi |
| FORMAGGI_STAGIONATI | Formaggi stagionati | formaggi |
| LATTICINI | Latticini e uova — generico | latticini |
| PANE | Pane e prodotti da forno | pane |
| PIADINE_BASI | Piadine e basi per pizza | dispensa |
| CRACKERS_GALLETTE | Crackers, grissini e gallette | dispensa |
| BISCOTTI | Biscotti | dispensa |
| MERENDINE | Merendine e snack dolci | dispensa |
| CEREALI_COLAZIONE | Cereali da colazione | dispensa |
| CONFETTURE_CREME | Confetture, miele e creme spalmabili | dispensa |
| DOLCI | Dolci, caramelle e cioccolato | dispensa |
| PASTA_SECCA | Pasta secca | dispensa |
| PASTA_FRESCA | Pasta fresca e gnocchi | carne |
| RISO_CEREALI | Riso e cereali | dispensa |
| LEGUMI | Legumi | dispensa |
| CONSERVE_VEGETALI | Conserve vegetali e sottoli | dispensa |
| CONSERVE_PESCE_CARNE | Pesce e carne in conserva | dispensa |
| FARINE_DOLCI | Farine e ingredienti per dolci | dispensa |
| DISPENSA | Dispensa — generico | dispensa |
| SUGHI_PASSATE | Sughi e passate | dispensa |
| SALSE | Salse | dispensa |
| OLI_ACETI | Oli e aceti | dispensa |
| SALE_SPEZIE | Sale, spezie e insaporitori | dispensa |
| CONDIMENTI | Condimenti — generico | dispensa |
| PIATTI_PRONTI | Piatti pronti e gastronomia | carne |
| ALTERNATIVE_VEGETALI | Alternative vegetali | latticini |
| SNACK_SALATI | Snack salati | dispensa |
| SURGELATI | Surgelati | freezer |
| GELATI | Gelati | freezer |
| ACQUA | Acqua | dispensa |
| SUCCHI_BIBITE | Succhi e bibite | dispensa |
| CAFFE_INFUSI | Caffè, tè e infusi | dispensa |
| ALCOLICI | Birra, vino e alcolici | dispensa |
| BEVANDE | Bevande — generico | dispensa |
| CASA | Casa e pulizia | non alimentare |
| BUCATO | Bucato | non alimentare |
| IGIENE | Igiene personale | non alimentare |
| ALTRO | Altro | residuo |

Profili: frutta = Frigo/deperibile/peso/7 giorni; verdura =
Frigo/deperibile/peso/5; carne = Frigo/deperibile/3 con peso solo per CARNE e
SALUMI; pesce = Frigo/deperibile/peso/2; latticini = Frigo/deperibile/7 senza
peso; formaggi = Frigo/deperibile/peso/7; pane = Dispensa/deperibile/3 senza
peso; dispensa = Dispensa/non deperibile/30 senza peso; freezer =
Freezer/non deperibile/90 senza peso; non alimentare =
Dispensa/non deperibile/30 senza peso e `isFood=false`; residuo =
Frigo/non deperibile/30 senza peso. Queste sono riapplicazioni dei suggerimenti
gia' presenti, non date assegnate automaticamente. ALTERNATIVE_VEGETALI usa il
profilo fresco prudenziale per tofu e bevande vegetali; l'utente puo' scegliere
Dispensa per un prodotto a lunga conservazione. Le posizioni salvate prevalgono.

Implementare i getter con `when` esaustivi o insiemi espliciti, mantenendo il
costruttore `enum class FoodCategory(val isFood: Boolean = true)` esistente:

```kotlin
val isSoldByWeight: Boolean
    get() = this in setOf(FRUTTA, VERDURA, ERBE_AROMATICHE, CARNE, PESCE,
        SALUMI, FORMAGGI_FRESCHI, FORMAGGI_STAGIONATI)
```

Il primo test d'ordine esistente presume FRUTTA e VERDURA adiacenti: conservarle.
In `AisleOrderViewModelTest` sostituire lo spostamento fisso `-9` con
`-FoodCategory.entries.size`, verificando che il clamp porti BEVANDE in testa.

- [x] **4. Centralizzare etichette nelle risorse e aggiornare i chiamanti.**

La mappa `labelRes()` contiene tutti i codici della tabella con ramo esaustivo,
senza `else`; eliminare solo `FoodCategory.label()` da Formatters. Quantita' e
luoghi mantengono i formatter esistenti. Esempio concreto della mappa:

```kotlin
@StringRes
fun FoodCategory.labelRes(): Int = when (this) {
    FoodCategory.FRUTTA -> R.string.category_frutta
    FoodCategory.VERDURA -> R.string.category_verdura
    FoodCategory.ERBE_AROMATICHE -> R.string.category_erbe_aromatiche
    FoodCategory.FRUTTA_SECCA -> R.string.category_frutta_secca
    FoodCategory.CARNE -> R.string.category_carne
    FoodCategory.SALUMI -> R.string.category_salumi
    FoodCategory.PESCE -> R.string.category_pesce
    FoodCategory.UOVA -> R.string.category_uova
    FoodCategory.LATTE_PANNA -> R.string.category_latte_panna
    FoodCategory.YOGURT -> R.string.category_yogurt
    FoodCategory.BURRO_MARGARINA -> R.string.category_burro_margarina
    FoodCategory.FORMAGGI_FRESCHI -> R.string.category_formaggi_freschi
    FoodCategory.FORMAGGI_STAGIONATI -> R.string.category_formaggi_stagionati
    FoodCategory.LATTICINI -> R.string.category_latticini
    FoodCategory.PANE -> R.string.category_pane
    FoodCategory.PIADINE_BASI -> R.string.category_piadine_basi
    FoodCategory.CRACKERS_GALLETTE -> R.string.category_crackers_gallette
    FoodCategory.BISCOTTI -> R.string.category_biscotti
    FoodCategory.MERENDINE -> R.string.category_merendine
    FoodCategory.CEREALI_COLAZIONE -> R.string.category_cereali_colazione
    FoodCategory.CONFETTURE_CREME -> R.string.category_confetture_creme
    FoodCategory.DOLCI -> R.string.category_dolci
    FoodCategory.PASTA_SECCA -> R.string.category_pasta_secca
    FoodCategory.PASTA_FRESCA -> R.string.category_pasta_fresca
    FoodCategory.RISO_CEREALI -> R.string.category_riso_cereali
    FoodCategory.LEGUMI -> R.string.category_legumi
    FoodCategory.CONSERVE_VEGETALI -> R.string.category_conserve_vegetali
    FoodCategory.CONSERVE_PESCE_CARNE -> R.string.category_conserve_pesce_carne
    FoodCategory.FARINE_DOLCI -> R.string.category_farine_dolci
    FoodCategory.DISPENSA -> R.string.category_dispensa
    FoodCategory.SUGHI_PASSATE -> R.string.category_sughi_passate
    FoodCategory.SALSE -> R.string.category_salse
    FoodCategory.OLI_ACETI -> R.string.category_oli_aceti
    FoodCategory.SALE_SPEZIE -> R.string.category_sale_spezie
    FoodCategory.CONDIMENTI -> R.string.category_condimenti
    FoodCategory.PIATTI_PRONTI -> R.string.category_piatti_pronti
    FoodCategory.ALTERNATIVE_VEGETALI -> R.string.category_alternative_vegetali
    FoodCategory.SNACK_SALATI -> R.string.category_snack_salati
    FoodCategory.SURGELATI -> R.string.category_surgelati
    FoodCategory.GELATI -> R.string.category_gelati
    FoodCategory.ACQUA -> R.string.category_acqua
    FoodCategory.SUCCHI_BIBITE -> R.string.category_succhi_bibite
    FoodCategory.CAFFE_INFUSI -> R.string.category_caffe_infusi
    FoodCategory.ALCOLICI -> R.string.category_alcolici
    FoodCategory.BEVANDE -> R.string.category_bevande
    FoodCategory.CASA -> R.string.category_casa
    FoodCategory.BUCATO -> R.string.category_bucato
    FoodCategory.IGIENE -> R.string.category_igiene
    FoodCategory.ALTRO -> R.string.category_altro
}
```

La tabella fornisce il testo di ciascuna risorsa. Spostare `icon()` in
CategoryPresentation; assegnare icone per famiglia
(frutta, verdura, carne, pesce, latte, formaggio, pane, dolci, dispensa,
condimenti, freezer, bevande, casa, igiene, carrello), riusando emoji compatibili
Android 8 gia' usate dove possibile. Non richiedere unicita' dell'emoji per tutte
le 49 categorie: il testo distingue le sezioni. Modificare il test delle icone
per verificarne la presenza e la copertura, senza unicita'.

Nei composable usare `stringResource(category.labelRes())`; nei callback non
composable preparare prima una mappa delle etichette con `LocalContext.current`.
Per la condivisione passare un resolver esplicito dalla schermata al ViewModel:

```kotlin
val context = LocalContext.current
val text = viewModel.shareText { context.getString(it.labelRes()) }
```

In `shareText` sostituire solo `section.category.label()` con
`categoryLabel(section.category)`. Nei test puri iniettare `{ it.name }` o
etichette italiane del fixture, mantenendo test di contenuto e ordine. Un test
Robolectric in CategoryPresentationTest risolve tutte le risorse e controlla
etichette non vuote e le stringhe "Salumi e affettati", "Formaggi freschi",
"Biscotti", "Merendine e snack dolci" tramite ApplicationProvider.

- [x] **5. Eseguire verifiche mirate, compilare e creare il commit del task.**

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain --tests "com.igor.fridge.domain.FoodCategoryTest" --tests "com.igor.fridge.domain.CategoryOrderTest" --tests "com.igor.fridge.data.ConvertersTest" --tests "com.igor.fridge.ui.CategoryPresentationTest" --tests "com.igor.fridge.ui.FormattersTest" --tests "com.igor.fridge.ui.AisleOrderViewModelTest" --tests "com.igor.fridge.ui.ShoppingListModelTest" --tests "com.igor.fridge.ui.ShoppingViewModelTest"
```

Atteso: PASS. Commit dei soli file elencati modificati:
`feat: amplia le categorie preservando i dati salvati`.

## Task 2: Proposte di categoria precise e conservative

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/domain/CategoryGuess.kt`
- Modify: `app/src/test/java/com/igor/fridge/domain/CategoryGuessTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/domain/QuickEntryTest.kt` solo dove cambia una proposta prevista.
- Modify: `app/src/test/java/com/igor/fridge/ui/ReceiptViewModelTest.kt` solo nelle aspettative di proposte nuove.
- Modify: `app/src/test/java/com/igor/fridge/data/ShoppingRepositoryTest.kt` solo nelle aspettative di proposte nuove.

**Interfaces:**
- Consumes: enum del task 1, `nameWords(String)` e normalizzazione esistenti.
- Produces: stessa firma `guessCategory(name: String): FoodCategory`, nessuna modifica all'identita' normalizzata del prodotto.

- [x] **1. Scrivere test degli esempi approvati e delle ambiguita'.**

```kotlin
@Test fun `distingue alimenti freschi e confezionati`() {
    val cases = mapOf(
        "prosciutto cotto" to FoodCategory.SALUMI,
        "MOZZ. FIOR DI LATTE" to FoodCategory.FORMAGGI_FRESCHI,
        "PARMIG REGG" to FoodCategory.FORMAGGI_STAGIONATI,
        "biscotti al latte" to FoodCategory.BISCOTTI,
        "merendine" to FoodCategory.MERENDINE,
        "tonno in scatola" to FoodCategory.CONSERVE_PESCE_CARNE,
        "gelato al latte" to FoodCategory.GELATI,
        "latte di soia" to FoodCategory.ALTERNATIVE_VEGETALI,
        "SPINACI SURG." to FoodCategory.SURGELATI,
        "detersivo lavatrice" to FoodCategory.BUCATO,
        "olio motore" to FoodCategory.CASA,
        "acqua ossigenata" to FoodCategory.IGIENE,
        "regalo per Anna" to FoodCategory.ALTRO,
    )
    cases.forEach { (name, expected) -> assertEquals(name, expected, guessCategory(name)) }
}
```

- [x] **2. Eseguire CategoryGuessTest: gli esempi dettagliati devono fallire.**

```powershell
.\gradlew.bat testDebugUnitTest --offline --console=plain --tests "com.igor.fridge.domain.CategoryGuessTest"
```

- [x] **3. Estendere parole, espressioni e precedenze.**

Riassegnare le parole esistenti alle categorie della tabella: prosciutto/speck/
salame/mortadella/bresaola/pancetta/guanciale/affettati -> SALUMI;
mozzarella/ricotta/mascarpone/stracchino/burrata/feta/crescenza -> FORMAGGI_FRESCHI;
parmigiano/grana/pecorino/emmental/fontina/taleggio/asiago -> FORMAGGI_STAGIONATI;
"formaggio" senza dettagli -> LATTICINI. Aggiungere plurali e abbreviazioni
tramite il meccanismo esistente, senza abbreviazioni arbitrarie di due lettere.

Una regola contestuale prima dei keyword evita che "tonno" vinca su "scatola":

```kotlin
val fish = setOf("tonno", "sgombro", "sardine", "acciughe")
val preserved = setOf("scatola", "scatole", "conserva", "conserve", "sottolio")
if (words.any { it in fish } && words.any { it in preserved }) {
    return FoodCategory.CONSERVE_PESCE_CARNE
}
```

Applicarla dopo i marcatori espliciti di surgelazione; mantenere la priorita'
delle espressioni rispetto alle parole singole. Aggiungere espressioni
"latte soia", "latte avena", "latte mandorla", "burro arachidi",
"fiocchi latte", "pasta fresca", "detersivo lavatrice", "sapone piatti".
La parola gelato/gelati/ghiaccioli indica GELATI in assenza di marcatori;
"pizza" da sola conserva l'attuale proposta SURGELATI, mentre "base pizza"
e "piadina" indicano PIADINE_BASI. Non inferire il confezionamento del tonno
da una parola sola. Ampliare il dizionario per tutte le altre categorie della
tabella senza cambiare `nameWords`, `productKey` o categorie note/manuali.

- [x] **4. Aggiornare le aspettative deliberate e verificare precedenze note.**

Nei test esistenti cambiano latte, yogurt, pasta, caffe', caramelle, crema
spalmabile, salumi, formaggi, succo e bucato; conservare i test di
normalizzazione, "MINI", "GRAN", surgelati e categorie manuali. Eseguire:

```powershell
.\gradlew.bat testDebugUnitTest --offline --console=plain --tests "com.igor.fridge.domain.CategoryGuessTest" --tests "com.igor.fridge.domain.QuickEntryTest" --tests "com.igor.fridge.ui.ReceiptViewModelTest" --tests "com.igor.fridge.data.ShoppingRepositoryTest" --tests "com.igor.fridge.data.FoodRepositoryTest"
```

Atteso: PASS; categorie gia' conosciute prevalgono sul nuovo parser.

- [x] **5. Commit:** `feat: riconosce le nuove categorie da nomi e scontrini`.

## Task 3: Modello puro dell'inventario

**Files:**
- Create: `app/src/main/java/com/igor/fridge/ui/inventory/InventoryListModel.kt`
- Create: `app/src/test/java/com/igor/fridge/ui/InventoryListModelTest.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/inventory/InventoryViewModel.kt` solo per spostare l'enum InventoryFilter nel nuovo file.

**Interfaces:**
- Consumes: `FoodItem`, `FoodCategory.entries`, `StorageLocation`, `expiryStatus(LocalDate, Int)`.
- Produces: i tipi e la funzione seguenti, nello stesso package dell'inventario.

```kotlin
enum class InventoryFilter { TUTTI, IN_SCADENZA, SCADUTI, SENZA_DATA }
data class InventoryCriteria(
    val query: String = "",
    val filter: InventoryFilter = InventoryFilter.TUTTI,
    val location: StorageLocation? = null,
    val category: FoodCategory? = null,
)
data class InventorySection(val category: FoodCategory, val items: List<FoodItem>)
data class InventoryListModel(
    val sections: List<InventorySection>,
    val availableCategories: List<FoodCategory>,
    val totalCount: Int,
    val matchingCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val noDateCount: Int,
) {
    val items: List<FoodItem> get() = sections.flatMap { it.items }
}
fun inventoryListOf(
    items: List<FoodItem>, criteria: InventoryCriteria,
    today: LocalDate, warningDays: Int,
): InventoryListModel
```

- [x] **1. Scrivere test di combinazione, ordinamento e scomparsa.**

```kotlin
@Test fun `la categoria sopravvive a un risultato vuoto e i conteggi sono filtrati`() {
    val date = LocalDate.of(2026, 10, 4)
    val food = listOf(
        FoodItem("s", "Prosciutto", category = FoodCategory.SALUMI,
            brand = "Marca A", expiryDate = date.plusDays(1)),
        FoodItem("b", "Biscotti", category = FoodCategory.BISCOTTI,
            location = StorageLocation.DISPENSA),
    )
    val selected = InventoryCriteria(query = "marca", category = FoodCategory.SALUMI)
    val model = inventoryListOf(food, selected, date, 3)
    assertEquals(listOf("s"), model.items.map { it.uuid })
    assertEquals(2, model.totalCount)
    assertEquals(1, model.matchingCount)
    assertEquals(1, model.expiringCount)
    val empty = inventoryListOf(food.drop(1), selected, date, 3)
    assertTrue(empty.items.isEmpty())
    assertTrue(FoodCategory.SALUMI in empty.availableCategories)
    assertTrue(FoodCategory.BISCOTTI in empty.availableCategories)
}
@Test fun `la scadenza precede il nome e i senza data sono ultimi`() {
    val date = LocalDate.of(2026, 10, 4)
    val food = listOf(
        FoodItem("n", "A", category = FoodCategory.SALUMI),
        FoodItem("z", "Z", category = FoodCategory.SALUMI, expiryDate = date),
        FoodItem("a", "A", category = FoodCategory.SALUMI, expiryDate = date),
    )
    assertEquals(listOf("a", "z", "n"),
        inventoryListOf(food, InventoryCriteria(), date, 3).items.map { it.uuid })
}
```

Aggiungere fixture con stesso nome/data e UUID diversi, ALTRO, due categorie,
articolo rimosso e luogo incompatibile. Verificare UUID unici nel risultato,
Altro ultimo, assenza di sezioni vuote, conteggio TUTTI prima dello stato,
stato "Scade oggi" incluso e passaggio a scaduto il giorno successivo.

- [x] **2. Eseguire il test: atteso errore di compilazione sui tipi nuovi.**

```powershell
.\gradlew.bat testDebugUnitTest --offline --console=plain --tests "com.igor.fridge.ui.InventoryListModelTest"
```

- [x] **3. Implementare la proiezione pura.**

```kotlin
val active = items.filter { it.removedAt == null }
val needle = criteria.query.trim()
val base = active.filter { item ->
    (needle.isEmpty() || item.name.contains(needle, true) ||
        item.brand?.contains(needle, true) == true) &&
        (criteria.location == null || item.location == criteria.location) &&
        (criteria.category == null || item.category == criteria.category)
}
val visible = base.filter { item ->
    when (criteria.filter) {
        InventoryFilter.TUTTI -> true
        InventoryFilter.IN_SCADENZA -> item.expiryStatus(today, warningDays) == ExpiryStatus.IN_SCADENZA
        InventoryFilter.SCADUTI -> item.expiryStatus(today, warningDays) == ExpiryStatus.SCADUTO
        InventoryFilter.SENZA_DATA -> item.expiryStatus(today, warningDays) == ExpiryStatus.SENZA_DATA
    }
}
val order = FoodCategory.entries.filterNot { it == FoodCategory.ALTRO } + FoodCategory.ALTRO
val byCategory = visible.groupBy { it.category }
val comparator = compareBy<FoodItem> { it.expiryDate ?: LocalDate.MAX }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    .thenBy { it.uuid }
val sections = order.mapNotNull { category ->
    byCategory[category]?.takeIf { it.isNotEmpty() }?.let {
        InventorySection(category, it.sortedWith(comparator))
    }
}
```

Costruire `availableCategories` nello stesso ordine da tutte le categorie di
`active`, piu' `criteria.category` se non null. Costruire il risultato con
`totalCount=active.size`, `matchingCount=base.size` e conteggi di stato su base,
mai su visible o sull'intero inventario. Nessuna dipendenza da Context o Room.

- [x] **4. Eseguire InventoryListModelTest e InventoryViewModelTest: atteso PASS.**
- [x] **5. Commit:** `feat: raggruppa e filtra l'inventario con un modello testabile`.

## Task 4: Stato osservabile e ripristino dei criteri

**Files:**
- Modify: `app/src/main/java/com/igor/fridge/ui/inventory/InventoryViewModel.kt`
- Modify: `app/src/test/java/com/igor/fridge/ui/InventoryViewModelTest.kt`

**Interfaces:**
- Consumes: `inventoryListOf`, repository e flussi esistenti.
- Produces: `InventoryUiState.sections`, `.category`, `.availableCategories`, `.matchingCount`; conserva `.items` come elenco appiattito, conteggi e messaggi esistenti. Nuovi metodi `onCategoryChange(FoodCategory?)`, `resetFilters()`; costruttore aggiunge `savedStateHandle: SavedStateHandle = SavedStateHandle()` e `computeDispatcher: CoroutineDispatcher = Dispatchers.Default` dopo i parametri esistenti.

- [x] **1. Scrivere test di ripristino, azzeramento, variazioni Room e giorno.**

Estendere l'helper del test attuale per iniettare SavedStateHandle, dispatcher e
`MutableStateFlow<LocalDate>`; conservare FakeFoodItemDao e FakeShoppingItemDao.

```kotlin
@Test fun `il ripristino mantiene i criteri ma non lo snackbar`() = runTest(dispatcher) {
    val handle = SavedStateHandle(mapOf(
        "inventory.query" to "prosciutto",
        "inventory.filter" to "IN_SCADENZA",
        "inventory.location" to "FRIGO",
        "inventory.category" to "SALUMI",
    ))
    val vm = InventoryViewModel(foodRepository, shoppingRepository, warningDays,
        flowOf(today), savedStateHandle = handle, computeDispatcher = dispatcher)
    assertEquals("prosciutto", vm.uiState.value.query)
    val state = vm.uiState.first { !it.isLoading }
    assertEquals("prosciutto", state.query)
    assertEquals(FoodCategory.SALUMI, state.category)
    assertEquals(StorageLocation.FRIGO, state.location)
    assertNull(state.message)
    vm.resetFilters()
    val reset = vm.uiState.first { it.query.isEmpty() && it.category == null }
    assertEquals(InventoryFilter.TUTTI, reset.filter)
    assertNull(reset.location)
    assertEquals("", handle.get<String>("inventory.query"))
}
```

Aggiungere test che ricostruisce una nuova istanza dai quattro valori salvati,
codici sconosciuti ripristinati con default, categoria mantenuta dopo delete,
nuovo alimento visibile al ritorno, conteggi dopo filtro nome/marca/luogo,
passaggio della data e soglia. Nei test di flussi usare `backgroundScope`
per la raccolta continua e `runCurrent()`, evitando un advanceUntilIdle su
currentDateFlow infinito.

- [x] **2. Eseguire InventoryViewModelTest: fallimento sui campi nuovi.**
- [x] **3. Separare criteri persistenti dal feedback e collegare il modello.**

Ripristino sicuro dei nomi enum senza eccezioni e senza memorizzare prodotti,
liste o lambda di annullamento nel SavedStateHandle:

```kotlin
private val selection = MutableStateFlow(InventoryCriteria(
    query = savedStateHandle["inventory.query"] ?: "",
    filter = InventoryFilter.entries.firstOrNull {
        it.name == savedStateHandle.get<String>("inventory.filter")
    } ?: InventoryFilter.TUTTI,
    location = StorageLocation.entries.firstOrNull {
        it.name == savedStateHandle.get<String>("inventory.location")
    },
    category = FoodCategory.entries.firstOrNull {
        it.name == savedStateHandle.get<String>("inventory.category")
    },
))
private fun select(value: InventoryCriteria) {
    savedStateHandle["inventory.query"] = value.query
    savedStateHandle["inventory.filter"] = value.filter.name
    savedStateHandle["inventory.location"] = value.location?.name
    savedStateHandle["inventory.category"] = value.category?.name
    selection.value = value
}
fun onCategoryChange(value: FoodCategory?) = select(selection.value.copy(category = value))
fun resetFilters() = select(InventoryCriteria())
```

Creare un `Feedback` privato con message/messageId/canUndo, aggiornato dai metodi
show/onMessageShown/undo attuali. `combine(foodRepository.observeAll(), selection,
warningDays, today)` produce criteri, data, soglia e risultato di inventoryListOf
su computeDispatcher con flowOn; un secondo combine con Feedback produce
InventoryUiState. L'initialValue di stateIn include subito query, filter,
location e category da selection.value, con isLoading=true: il testo Compose
puo' cosi' inizializzarsi prima della prima emissione Room. Conservare totalCount
generale e usare matchingCount per il chip Tutti; `.items` deriva dalle sezioni.
Factory usa `createSavedStateHandle()`
dall'initializer. Non modificare consume, delete, undo o addExpiringToShoppingList
oltre ai riferimenti al feedback: l'azione collettiva conserva il perimetro
dell'intero inventario, come oggi.

- [x] **4. Eseguire test e build: atteso PASS.**

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain --tests "com.igor.fridge.ui.InventoryViewModelTest" --tests "com.igor.fridge.ui.InventoryListModelTest"
```

- [x] **5. Commit:** `feat: mantiene filtri e categorie al ritorno nell'inventario`.

## Task 5: Selettori e lista Compose leggibili

**Files:**
- Create: `app/src/main/java/com/igor/fridge/ui/components/CategoryPicker.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/inventory/InventoryScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/components/FoodItemCard.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/edit/EditItemScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/shopping/ShoppingItemEditScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/receipt/ReceiptScreen.kt`
- Modify: `app/src/main/java/com/igor/fridge/ui/settings/AisleOrderScreen.kt` solo se la prova di font rivela troncamenti.
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: stato del task 4 e risorse del task 1.
- Produces: `@Composable fun CategoryPicker(value: FoodCategory, onSelect: (FoodCategory) -> Unit, modifier: Modifier = Modifier)`; `@Composable fun CategoryPickerSheet(selected: FoodCategory?, options: List<FoodCategory>, allowAll: Boolean, onSelect: (FoodCategory?) -> Unit, onDismiss: () -> Unit)`.
- Inventory usa sheet con allowAll=true e availableCategories; inserimento/modifica usa tutte le categorie e allowAll=false. Nessuna categoria null nel modello persistito.

- [x] **1. Leggere craft-floor e applicare la checklist Android di Impeccable prima di editare.**

Mantenere tema, app bar, FAB e navigazione. Usare tipografia e spaziature M3,
surface per intestazioni opache, body secondario per marca/luogo, niente altezza
fissa per testi. Il riordino corsie e' gia' una LazyColumn: conservarlo, verificando
che nomi lunghi vadano a capo senza spostare le azioni fuori schermo.

- [x] **2. Implementare il selettore ricercabile comune.**

```kotlin
var query by rememberSaveable { mutableStateOf("") }
val labels = options.associateWith { stringResource(it.labelRes()) }
val visible = options.filter { labels.getValue(it).contains(query.trim(), true) }
ModalBottomSheet(onDismissRequest = onDismiss) {
    OutlinedTextField(value = query, onValueChange = { query = it },
        label = { Text(stringResource(R.string.category_search)) },
        singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        if (allowAll) item(key = "all") {
            ListItem(headlineContent = { Text(stringResource(R.string.category_all)) },
                modifier = Modifier.clickable { onSelect(null); onDismiss() })
        }
        items(visible, key = { it.name }) { category ->
            ListItem(
                headlineContent = { Text(labels.getValue(category)) },
                leadingContent = { Text(category.icon(), Modifier.clearAndSetSemantics {}) },
                trailingContent = { RadioButton(selected = selected == category, onClick = null) },
                modifier = Modifier.selectable(selected == category, role = Role.RadioButton) {
                    onSelect(category); onDismiss()
                },
            )
        }
    }
}
```

Adattare il max del foglio allo spazio disponibile tramite BoxWithConstraints,
non imporre 480 dp su uno schermo piu' corto. Aggiungere imePadding e padding
navigationBars; vuoto di ricerca con testo esplicito. "Tutte le categorie" resta
raggiungibile anche durante la ricerca. CategoryPicker apre il foglio con campo
readOnly e nome corrente, callback non nullo protetto da `category?.let(onSelect)`.
Sostituire solo i tre EnumDropdown di categoria (alimento, spesa, scontrino),
conservando gli EnumDropdown per unita' e luogo.

- [x] **3. Separare filtri di stato dai controlli Categoria e Luogo.**

Prima riga: quattro chip orizzontali; Tutti usa matchingCount. Seconda riga:
due controlli compatti con nome corrente, uno apre CategoryPickerSheet, l'altro
un menu per Tutti/Frigo/Freezer/Dispensa. Su larghezze ridotte e font grandi
usare FlowRow per andare a capo. Sotto, testo con visibili/totale generale.
Nuove risorse concrete:

```xml
<string name="category_search">Cerca una categoria</string>
<string name="category_all">Tutte le categorie</string>
<string name="category_no_results">Nessuna categoria trovata</string>
<string name="inventory_shown_count">%1$d di %2$d articoli</string>
<string name="inventory_reset_filters">Azzera ricerca e filtri</string>
<string name="inventory_loading">Caricamento inventario</string>
<string name="inventory_category_filter">Categoria: %1$s</string>
<string name="inventory_location_filter">Luogo: %1$s</string>
```

Preservare il testo locale sincrono della ricerca attuale; inizializzarlo dai
criteri ripristinati e aggiornarlo direttamente nel callback. Il reset imposta
prima `query=""`, poi viewModel.resetFilters(); non copiare ogni emissione
asincrona dello stato nel testo locale mentre l'utente digita.

- [x] **4. Rendere la lista per sezioni con sticky header e chiavi stabili.**

```kotlin
state.sections.forEach { section ->
    stickyHeader(key = "category:${section.category.name}") {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(section.category.icon(), Modifier.clearAndSetSemantics {})
                Text(stringResource(section.category.labelRes()),
                    Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall)
                Text(section.items.size.toString())
            }
        }
    }
    items(section.items, key = { "food:${it.uuid}" }) { item ->
        FoodItemCard(item, state.today, state.warningDays,
            onClick = { onEditItem(item.uuid) },
            onConsume = { viewModel.consume(item) },
            onDelete = { viewModel.delete(item) })
    }
}
```

OptIn ExperimentalFoundationApi quando richiesto dalla versione installata;
non aggiornare Compose per ottenere una API nuova. Mantenere padding inferiore
96 dp per il FAB. Stato loading esplicito con indicatore e testo; vuoto generale
con azione onAddItem; vuoto filtrato con reset. Se esistono criteri non
predefiniti, mostrare il reset anche quando totalCount diventa zero dopo
l'eliminazione dell'ultimo articolo. Separare i tre stati prima di costruire
la LazyColumn.

- [x] **5. Semplificare FoodItemCard senza perdere informazioni o azioni.**

Eliminare la categoria ripetuta dal dettaglio della riga e dalla relativa
contentDescription. Nome titleMedium su piu' righe, quantita' bodyMedium,
scadenza bodyMedium con testo e colore attuali; marca/luogo bodySmall distinti.
Conservare onClick/onConsume/onDelete e le loro etichette. Su font grandi lasciare
le due IconButton su una riga di azioni dedicata se il testo non ha spazio;
non imporre larghezze/altezza di riga, non nascondere nomi con maxLines=1.

```kotlin
Text(item.name, style = MaterialTheme.typography.titleMedium)
Text(formatQuantity(item.quantity, item.unit), style = MaterialTheme.typography.bodyMedium)
Text(expiryLabel(item.daysUntilExpiry(today)),
    style = MaterialTheme.typography.bodyMedium, color = statusColor)
Text(listOfNotNull(item.brand?.takeIf { it.isNotBlank() }, item.location.label())
    .joinToString(" · "), style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
```

- [x] **6. Compilare, eseguire test mirati e provare l'interazione Android.**

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain --tests "com.igor.fridge.ui.InventoryViewModelTest" --tests "com.igor.fridge.ui.InventoryListModelTest" --tests "com.igor.fridge.ui.EditItemViewModelTest" --tests "com.igor.fridge.ui.ShoppingItemEditViewModelTest" --tests "com.igor.fridge.ui.ReceiptViewModelTest"
```

Su device/emulatore debug: selezionare SALUMI, cercare per marca, applicare Frigo,
eliminare l'ultimo risultato, azzerare; riaprire tutti e tre i selettori, cercare
"formaggi", selezionare e salvare. Digitare velocemente "prosciutto" e azzerare:
testo e lista devono concordare. TalkBack annuncia categoria e selezione una sola
volta. Le righe offrono consumo/eliminazione distinti dall'apertura.

- [x] **7. Commit:** `feat: rende leggibile l'inventario e ricercabili le categorie`.

## Task 6: Compatibilita' reale, verifica complessiva e documentazione

**Files:**
- Modify: `app/src/test/java/com/igor/fridge/data/FoodItemDaoTest.kt`
- Modify: `app/src/test/java/com/igor/fridge/data/SavedListDaoTest.kt`
- Modify: `README.md`
- Create: `docs/verification/2026-10-04-inventario-categorie.md`
- Modify: questo piano, marcando i passi eseguiti e gli esiti reali.

**Interfaces:**
- Consumes: Room e DAO gia' presenti, implementazione dei task 1–5.
- Produces: evidenza dei dati vecchi e nuovi leggibili, esiti di build/test e verifica UI nativa.

- [x] **1. Aggiungere test Room sulle categorie e i record conservati.**

Dentro il fixture Room reale di FoodItemDaoTest usare `upsert` con vecchie
LATTICINI/DISPENSA e nuove SALUMI/BISCOTTI, includendo una riga removedAt.
Rileggere tramite findByUuid e controllare category, location, quantity,
expiryDate e removedAt, senza chiamare il parser. In SavedListDaoTest:

```kotlin
@Test fun `categorie vecchie e nuove convivono nelle liste salvate`() = runTest {
    val list = SavedList("compat", "Compatibilita'", now, now)
    val old = item("old", "compat", 0, "Latte").copy(category = FoodCategory.LATTICINI)
    val fresh = item("new", "compat", 1, "Biscotti").copy(category = FoodCategory.BISCOTTI)
    dao.replace(list, listOf(old, fresh))
    val restored = dao.itemsOf("compat")
    assertEquals(listOf(FoodCategory.LATTICINI, FoodCategory.BISCOTTI), restored.map { it.category })
    assertEquals(listOf(old.photoPath, fresh.photoPath), restored.map { it.photoPath })
}
```

Usare parametri nominati per SavedList secondo il costruttore reale. I test
esistenti di migrazione verificano l'apertura delle versioni precedenti;
eseguirli senza inventare una nuova migrazione. Controllare che la build non
abbia prodotto variazioni allo schema esportato.

- [x] **2. Eseguire build e suite completa una volta dopo l'ultimo cambio.**

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain
git -c safe.directory=C:/Temp/Claude/Igor diff --check
git -c safe.directory=C:/Temp/Claude/Igor diff -- app/schemas
```

Atteso: BUILD SUCCESSFUL, zero test falliti, nessuna differenza negli schemi.
Se il sandbox blocca cache Gradle esterne al progetto, richiedere escalation
del comando gia' autorizzato anziche' cambiare SDK o installare dipendenze.
La baseline verificata era 304 test in 45 classi; riportare il nuovo numero
reale dai risultati XML, senza presumere che sia identico.

- [x] **3. Verificare la matrice Android e conservare screenshot ed esiti.**

Device/emulatore debug, dati di prova separati dai dati personali; non cancellare
il database dell'utente e non aggiungere un menu di seeding al prodotto.
Creare i campioni tramite fixture/test o l'interfaccia debug.

| Scenario | Risultato atteso |
| --- | --- |
| 1 articolo | Una sezione, nessun riempitivo o duplicato |
| 30 articoli misti | Sezioni leggibili, nome/scadenza/quantita' riconoscibili |
| 200 articoli | Scorrimento fluido, header della sezione corrente, chiavi stabili |
| Chiaro e scuro | Testo e stato leggibili, superfici degli header opache |
| Font 200%, schermo stretto | Nomi multilinea, controlli raggiungibili, nessuna azione fuori schermo |
| Tastiera nel selettore | Risultati e selezione raggiungibili sopra tastiera e barre |
| Nessun dato / nessun risultato | Invito ad aggiungere / azzeramento, distinti dal loading |
| Rotazione e ritorno dai dettagli | Criteri mantenuti, articolo modificato osservato |
| Processo ricreato | Ricerca, categoria, luogo e stato ripristinati; snackbar non riproposto |
| Cambio giorno/soglia | Scadenze e conteggi aggiornati |
| TalkBack | Header e selezioni comprensibili, emoji decorative ignorate, azioni distinte |

Per ricreare il processo usare background + terminazione del processo debug,
poi ripristinare l'attivita' da recenti; non usare force-stop come prova di
SavedStateHandle. Se nessun emulatore/device e' disponibile, documentare
precisamente le righe non verificate e dichiararle nella consegna: una build
JVM riuscita non dimostra questa matrice.

- [x] **4. Aggiornare README e registrare il risultato reale.**

Descrivere raggruppamento, nuovi filtri, categorie specifiche e permanenza delle
vecchie categorie generiche. Conservare i difetti ancora aperti e il servizio
prezzi come lavoro futuro. Il rapporto di verifica deve contenere commit testato,
comandi, conteggio test, dispositivo/API, screenshot e scenari eseguiti o non
eseguiti, senza dichiarazioni di verifica non effettuata.

- [x] **5. Commit:** `test: verifica compatibilita e documenta il nuovo inventario`.

## Consegna ed esecuzione

Prima dell'implementazione: revisione dell'utente di questo piano e scelta del
metodo, come richiesto da brainstorming/writing-plans.

Consigliata esecuzione **Native**: i sei task condividono enum, etichette e stato,
e il lavoro e' reversibile e coperto da test JVM. L'agente principale esegue i
task nella sessione, seguendo executing-plans, poi un revisore indipendente
controlla il cambiamento complessivo. Alternativa **Subagent-driven**: agente e
revisore dedicati a ciascun task, piu' revisione finale, con maggior costo di
contesto. Nessun agente aggiuntivo viene avviato durante la sola pianificazione.

La consegna finale deve indicare comportamento ottenuto, verifiche reali,
limiti della verifica Android e commit. Poi si passa alla verifica della prima
fonte prezzi prevista dal disegno separato, senza presentare queste modifiche
come implementazione del servizio prezzi.

## Esito esecuzione Native

I sei task sono implementati. Build e 322 test in 48 classi passano; schema Room invariato. La matrice Android e i suoi limiti effettivi sono riportati nel [rapporto di verifica](../../verification/2026-10-04-inventario-categorie.md): la spunta indica esecuzione del passo con esiti documentati, non certificazione delle prove manuali non svolte (in particolare TalkBack vocale). Revisione indipendente completata: due Important corretti con regressioni RED->GREEN, minori e limiti nel rapporto.
