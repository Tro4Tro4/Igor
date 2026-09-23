# Igor — inventario vocale e ciclo chiuso della spesa

Data: 2026-09-23
Stato: approvata, da tradurre in piano di implementazione

## 1. Contesto

Igor è un'app Android per il monitoraggio del frigorifero. Il repository contiene un
unico commit (`2adf6ef`) con lo scaffold completo: Kotlin 2.0, Jetpack Compose, Room,
WorkManager, CameraX con ML Kit. Il 23/09/2026 il codice è stato compilato per la prima
volta su una macchina con l'Android SDK: `assembleDebug` e i 18 test JVM passano, e i due
warning di API deprecate sono stati corretti.

**L'app non è mai stata installata su un dispositivo.** Non esiste alcun database Igor in
produzione. È la circostanza che permette le decisioni della sezione 3 senza scrivere
migrazioni Room.

### Difetti rilevati nel codice esistente

1. `ShoppingRepository.addIfAbsent()` esce se trova una voce con lo stesso nome, anche
   quando è già spuntata: un prodotto consumato una seconda volta non ritorna nella lista
   della spesa.
2. `InventoryViewModel` legge `settingsStore.warningDays` una sola volta nel costruttore.
   SharedPreferences non è osservabile, quindi un cambio di soglia non raggiunge la UI.
3. `LocalDate.now()` viene valutato quando il `Flow` emette: con l'app aperta oltre la
   mezzanotte gli stati di scadenza restano fermi al giorno precedente.
4. `PeriodicWorkRequest` non garantisce l'orario di esecuzione: la notifica "delle 9" può
   arrivare più tardi.
5. `src/androidTest` non esiste, pur essendo dichiarate le dipendenze Espresso,
   `compose-ui-test-junit4` e `room-testing`. Nessun test copre DAO, ViewModel o UI.
6. `app/schemas/…/1.json` è generato ma non tracciato da git; `.kotlin/` non è ignorato.
7. Le stringhe dell'interfaccia sono scritte nel codice Kotlin, con apostrofi ASCII
   ("quantita'", "e'") visibili all'utente.
8. L'icona della notifica è `android.R.drawable.stat_notify_more`, un'icona di sistema.

## 2. Obiettivo

**Igor deve dire con precisione cosa c'è in casa, quando lo si consulta fuori casa.**

Ne discende il principio che decide i casi dubbi: *l'inventario è attendibile solo se
aggiornarlo costa poca fatica*. Ogni scelta di questo documento privilegia la riduzione
dell'attrito d'inserimento rispetto alla completezza del dato.

Le due modalità d'ingresso previste sono la **voce** ("dico cosa metto in frigo, con
quale data di scadenza") e lo **spunto della lista della spesa**.

## 3. Modello dati

Lo schema cambia sul posto e la versione del database **resta 1**: non esistendo database
installati, non serve alcuna `Migration`. `app/schemas/…/1.json` viene rigenerato e
committato. Chiunque avesse una build di prova sul telefono deve disinstallarla.

### 3.1 FoodItem

| Campo | Cambiamento |
| --- | --- |
| `uuid: String` | **Nuova chiave primaria**, generata sul dispositivo (`UUID.randomUUID()`). Sostituisce `id: Long` autogenerato |
| `updatedAt: Instant` | Aggiornato dal repository a ogni scrittura, mai dalla UI |
| `removedAt: Instant?` | Cancellazione logica: `null` se l'articolo è in casa |
| `removalReason: RemovalReason?` | `CONSUMATO`, `BUTTATO`, `ERRORE` |

`ShoppingItem` riceve `uuid` e `updatedAt` con lo stesso criterio.

**Motivazione.** Un `Long` autogenerato localmente collide fra dispositivi e una
cancellazione fisica è invisibile a una sincronizzazione: entrambe le cose rendono
impossibile aggiungere un backend senza rifare lo schema. Il motivo della rimozione è una
colonna che costa nulla oggi e abilita tre cose: annullamento di una cancellazione
sbagliata, statistiche sugli sprechi, e l'eredità degli attributi descritta in 3.3.

**Conseguenze.**
- Le route di navigazione passano da `edit/{itemId}` (Long) a `edit/{uuid}` (String);
  la sentinella `NEW_ITEM_ID = 0L` diventa una costante testuale (`NEW_ITEM_UUID = "new"`).
- Tutte le query dell'inventario filtrano `removedAt IS NULL`.
- `Converters` acquisisce la conversione `Instant` ↔ epoch millis e `RemovalReason` ↔ `String`.
- I record rimossi restano in tabella. Per un inventario domestico la crescita è di pochi
  record al giorno: nessuna politica di pulizia in questa fase.

### 3.2 Impostazioni

`SettingsStore` passa da SharedPreferences a **DataStore Preferences**, esponendo
`warningDays`, `notificationHour` e `notificationsEnabled` come `Flow`. `InventoryViewModel`
li osserva, risolvendo il difetto 2. `ExpiryCheckWorker`, che gira ad app chiusa e ha bisogno
di una lettura sincrona, legge con una singola chiamata bloccante (`runBlocking { …first() }`).

### 3.3 Eredità degli attributi

`FoodItemDao` acquisisce:

```kotlin
@Query("""
    SELECT * FROM food_items
    WHERE name = :name COLLATE NOCASE
    ORDER BY updatedAt DESC LIMIT 1
""")
suspend fun findLastByName(name: String): FoodItem?
```

La query **non** filtra `removedAt IS NULL`: cerca di proposito anche fra gli articoli
usciti dal frigo. Quando un prodotto entra dalla lista della spesa o da una frase vocale
che nomina solo il prodotto, categoria, unità e posizione vengono ereditate dall'ultimo
omonimo. È la ragione pratica per cui la cancellazione logica ripaga subito.

## 4. Ciclo chiuso: dalla lista della spesa al frigo

Il pulsante **"Rimuovi presi (N)"** diventa **"Metti in frigo (N)"**: le voci spuntate
entrano in inventario e vengono rimosse dalla lista in un'unica operazione, con snackbar
di annullamento.

L'ingresso è legato all'azione esplicita e non allo spunto, perché al supermercato si
spunta e si toglie la spunta mentre si prende: far entrare un prodotto a ogni tocco
creerebbe record fantasma.

Ogni prodotto entra con: nome, quantità e unità presi dalla voce di lista, che sono dati
inseriti dall'utente e quindi prevalgono sempre; categoria e posizione ereditate via
`findLastByName`, o i valori di default se non esiste un omonimo; **scadenza assente**,
perché la lista della spesa non può conoscerla.

Per completare le date, `InventoryScreen` acquisisce un quarto chip di filtro accanto a
*Tutti / In scadenza / Scaduti*: **"Senza data (N)"**, che corrisponde a
`ExpiryStatus.SENZA_DATA`.

`ShoppingRepository.addIfAbsent()` viene corretto: se trova una voce esistente, la
ripristina (`isChecked = false`) e ne aggiorna quantità e unità, invece di uscire.

## 5. Inserimento vocale

### 5.1 Flusso

Un pulsante microfono in `InventoryScreen` → riconoscimento vocale → `EditItemScreen`
aperta in modalità "nuovo" con i campi precompilati → l'utente verifica e salva.

Il passo di conferma non è negoziabile in questa fase: il parser è la parte incerta del
progetto e la schermata di modifica è la rete di sicurezza che rende un errore di
interpretazione un fastidio invece che un dato sbagliato in inventario.

### 5.2 Riconoscimento

`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` con `EXTRA_LANGUAGE = "it-IT"`, avviato con
`rememberLauncherForActivityResult`.

**Non richiede `RECORD_AUDIO` nel manifest di Igor**: il microfono è gestito dall'app di
riconoscimento. Quando `queryIntentActivities` non trova alcun gestore, il pulsante
microfono non viene mostrato.

L'audio può transitare dai servizi Google, esattamente come la dettatura della tastiera:
scelta accettata esplicitamente. Nessun server proprio, nessun account, nessun costo.
`SpeechRecognizer` diretto — che darebbe la trascrizione parziale durante il parlato — è
rimandato a dopo la prova sul campo, perché costa il permesso microfono e la sua gestione.

### 5.3 Il parser

Funzione pura in `domain/voice/`, senza dipendenze Android:

```kotlin
data class ParsedItem(
    val name: String,
    val quantity: Double?,
    val unit: QuantityUnit?,
    val expiryDate: LocalDate?,
    val location: StorageLocation?,
    val rawText: String,
)

fun parseSpokenItem(text: String, today: LocalDate): ParsedItem
```

Catena di estrattori, ciascuno dei quali consuma la porzione di frase che riconosce e
passa il resto al successivo:

| Ordine | Estrattore | Riconosce |
| --- | --- | --- |
| 1 | Data | `scade il 5 marzo`, `scadenza 12/4`, `fra tre giorni`, `domani`, `dopodomani`, `fra una settimana`, `fine mese` |
| 2 | Posizione | `in freezer`, `nel congelatore`, `in dispensa`, `in frigo` |
| 3 | Quantità e unità | `due litri`, `mezzo chilo`, `500 grammi`, `tre confezioni`, numeri in cifre e in lettere |
| 4 | Nome | tutto ciò che resta, ripulito di preposizioni e articoli residui |

Il nome è il residuo, non un campo riconosciuto: una parola imprevista finisce nel nome —
il campo che l'utente rilegge comunque — invece di mandare in errore l'analisi.

Regole di completamento: quantità assente → `1.0 PZ`; nome vuoto dopo l'estrazione →
`rawText` integrale; nome presente in inventario (anche rimosso) → categoria, posizione e
unità ereditate da `findLastByName`, senza sovrascrivere ciò che la frase dichiara.

File: `SpokenItemParser.kt` (orchestrazione), `NumberWords.kt` (numeri in lettere,
`mezzo`/`mezza`), `DatePhrases.kt`, `QuantityPhrases.kt`.

### 5.4 Corpus di riferimento

Frasi confermate dall'utente come rappresentative del proprio modo di parlare; diventano i
primi casi di test:

| Frase | Risultato atteso |
| --- | --- |
| `due litri di latte scadenza 5 marzo` | latte · 2 L · 5 marzo |
| `mezzo chilo di carne macinata scade il 12 aprile` | carne macinata · 0,5 kg · 12 aprile |
| `500 grammi di parmigiano` | parmigiano · 500 g |
| `tre confezioni di yogurt fra tre giorni` | yogurt · 3 conf. · oggi+3 |
| `pane domani` | pane · 1 pz · oggi+1 |
| `piselli in freezer scadenza fine mese` | piselli · freezer · ultimo giorno del mese |
| `latte` | latte · attributi ereditati |

Le date espresse solo come giorno e mese si riferiscono alla prossima occorrenza futura:
`5 marzo` detto in aprile significa il 5 marzo dell'anno seguente.

### 5.5 Bozza in attesa

`IgorApp` conserva oggi `scannedBarcode: String?` per consegnare alla schermata di modifica
il codice letto dallo scanner. La voce ha bisogno dello stesso meccanismo per più campi:
`scannedBarcode` viene generalizzato in `pendingDraft: ItemDraft?`, dove il barcode è un
caso particolare. Una sola strada da mantenere invece di due che fanno la stessa cosa.

## 6. Correzioni e manutenzione

| Difetto | Intervento |
| --- | --- |
| 1 — voce spuntata | `addIfAbsent` ripristina e aggiorna la voce esistente (sezione 4) |
| 2 — soglia congelata | DataStore con `Flow` osservato dal ViewModel (sezione 3.2) |
| 3 — data ferma a mezzanotte | Un `Flow` che emette la data corrente e si risveglia alla mezzanotte successiva entra nel `combine` di `InventoryViewModel`: `today` smette di essere una variabile catturata e diventa un ingresso osservato |
| 4 — ora notifica | Nessun codice: le impostazioni dichiarano un orario indicativo |
| 5 — test assenti | Sezione 7 |
| 6 — schema e gitignore | `app/schemas/` committato, `.kotlin/` ignorato |
| 7 — stringhe | Spostate in `strings.xml`, apostrofi tipografici |
| 8 — icona notifica | Icona monocromatica dedicata in `res/drawable` |

**Schermata Impostazioni**, oggi assente pur essendo i valori già persistiti: giorni di
preavviso, ora della notifica, attivazione delle notifiche. Raggiungibile dalla barra
superiore dell'inventario. Il testo sull'ora dichiara che l'esecuzione è indicativa.

## 7. Strategia di test

**JVM (`src/test`)** — il grosso della copertura:
- `SpokenItemParser`: l'intero corpus 5.4 più i casi limite (frase vuota, solo numeri,
  unità sconosciuta, data passata, `29 febbraio` in anno non bisestile).
- `NumberWords`, `DatePhrases`, `QuantityPhrases` separatamente.
- `InventoryViewModel` e `ShoppingViewModel` con repository finti: filtri, ricerca,
  conteggi, "metti in frigo", eredità degli attributi.
- `EditItemViewModel`: validazione, salvataggio, applicazione della bozza.

**Strumentali (`src/androidTest`, da creare)**:
- DAO su database Room in memoria: ordinamenti, `findLastByName` che attraversa i record
  rimossi, filtro `removedAt IS NULL`.
- Un percorso Compose essenziale: aggiunta di un alimento e sua comparsa in lista.

Il parser viene scritto in test-driven development: i casi del corpus esistono prima
dell'implementazione.

## 8. Fuori scope

- **Account e sincronizzazione remota**: solo predisposizione dello schema. La scelta del
  fornitore (Firebase in vantaggio per la persistenza offline integrata; Supabase come
  alternativa senza lock-in) è rimandata.
- **Open Food Facts**: declassato. Serviva a evitare di digitare i nomi, problema che la
  voce risolve; resta utile solo per la scansione.
- **Statistiche sugli sprechi**: abilitate dal modello dati, non costruite.
- **Dettatura a raffica** (più prodotti in una sessione continua): rimandata alla prova sul
  campo del parser.
- Backup su file, widget, condivisione della lista.

## 9. Rischi

| Rischio | Mitigazione |
| --- | --- |
| Riconoscimento vocale assente sul dispositivo | Pulsante nascosto quando nessuna app lo gestisce; inserimento manuale invariato |
| Il parser sbaglia l'interpretazione | Schermata di conferma obbligatoria; la frase grezza non va mai perduta |
| Il cambio di chiave primaria tocca navigazione e ViewModel | Fatto per primo, su un'app senza dati installati, con i test dei DAO a copertura |
| DataStore letto in modo bloccante nel worker | Chiamata isolata in un unico punto del `SettingsStore` |

## 10. Ordine di lavoro

1. **Fondamenta e modello dati** — schema (uuid, updatedAt, removedAt, removalReason),
   DataStore, difetti 1-4, stringhe, icona, schermata Impostazioni, rete di test.
   Al termine l'app è installabile e provabile in cucina.
2. **Ciclo chiuso** — "Metti in frigo", eredità degli attributi, chip "Senza data".
3. **Voce** — parser in TDD, poi riconoscimento e aggancio alla schermata di modifica.

La voce viene per ultima perché dipende da entrambi i punti precedenti: ha bisogno dello
schema definitivo e dell'eredità degli attributi per interpretare una frase che nomina
soltanto il prodotto.
