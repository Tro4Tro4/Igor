# Lista della spesa: dettagli, categorie, foto e liste salvate

## Richiesta

Per ogni voce della lista: numero di unità da acquistare, unità di misura, marca, nota,
foto e quantità effettivamente acquistata. Prodotti divisi per categoria, ognuna con
un’icona. Possibilità di salvare e ricaricare la lista, valutandone l’impatto sul resto
dell’app.

## Cosa è stato fatto

| Funzione | Dove |
| --- | --- |
| Quantità da comprare (con pulsanti + e −) e unità di misura | `ShoppingItemEditScreen` |
| Marca, note, foto (scatto o galleria) | `ShoppingItemEditScreen`, `FilePhotoStore` |
| Quantità presa, con "Preso tutto" | `ShoppingItemEditScreen`; usata da "Metti in frigo" |
| Categorie con icona, lista raggruppata per corsia, sezione "Nel carrello" | `Formatters.icon()`, `ShoppingScreen` |
| Categoria proposta in automatico | storico dell’inventario, poi `domain/CategoryGuess.kt` |
| Liste salvate: salva, carica, elimina | `SavedListsScreen`, `SavedListRepository` |

Un tocco su una voce apre i dettagli; l’aggiunta rapida col solo nome resta com’era.

## Decisioni

**Le categorie restano `FoodCategory`, una sola per tutta l’app.** Una seconda
classificazione solo per la spesa avrebbe costretto a tradurre l’una nell’altra a ogni
"Metti in frigo". Le voci sono state riordinate come le corsie di un supermercato
(frutta, verdura, pane, carne, pesce, latticini, surgelati, dispensa, condimenti,
bevande, casa, igiene, altro); il database salva il nome, quindi l’ordine non tocca i dati.
Sono state aggiunte `PANE`, `CASA` e `IGIENE`.

**Icone come emoji.** Sono a colori, riconoscibili anche in piccolo e non aggiungono
dipendenze. Sono state scelte solo emoji presenti da Android 8 (minSdk 26).

**Ciò che non si mangia non entra in frigo.** `CASA` e `IGIENE` hanno `isFood = false`: con
"Metti in frigo" un detersivo esce dalla lista ma non diventa un articolo dell’inventario.
L’annullamento li rimette in lista come tutti gli altri.

**Acquisto parziale.** Se la quantità presa è minore di quella da comprare, entra in frigo
quanto è stato preso e la voce resta in lista per la differenza, senza spunta. Togliere la
spunta a mano azzera la quantità presa; indicarla nei dettagli mette la voce nel carrello.

**Le liste salvate sono modelli, non liste attive.** È l’alternativa con l’impatto minore:
la lista della spesa resta una sola, ed è quella con cui dialogano inventario
("consumato", "aggiungi i prodotti in scadenza") e "Metti in frigo". Più liste attive
avrebbero richiesto di decidere, a ogni consumo, in quale lista mettere il prodotto, e
"Metti in frigo" per lista. Caricare una lista salvata *aggiunge* le sue voci con le regole
di sempre (nessun doppione; una voce già spuntata torna da comprare) invece di sostituire
la lista attuale, che potrebbe contenere voci spuntate e non ancora messe in frigo. Salvare
con un nome già usato sovrascrive quella lista (la finestra di dialogo lo segnala).

**Foto su file, non nel database.** Ogni foto viene ridotta a 1280 px sul lato lungo,
raddrizzata secondo l’EXIF e salvata in `files/shopping_photos`; la voce ne conserva solo
il nome. Le foto non si cancellano quando la voce esce dalla lista, perché l’annullamento
e le liste salvate possono ancora usarle: all’avvio dell’app si cancellano quelle che
nessuno usa più e che hanno più di un’ora.

## Impatto sul resto dell’app

- **Database: versione 2, con una migrazione vera** (`IgorDatabase.MIGRATION_1_2`).
  Aggiunge le colonne a `shopping_items`, crea `saved_lists` e `saved_list_items`, e
  assegna alle voci già in lista la categoria che lo stesso nome ha avuto per ultimo in
  inventario. La versione andava alzata comunque: a versione invariata Room rifiuta di
  aprire un database con uno schema diverso, e `fallbackToDestructiveMigration()` in quel
  caso non interviene.
- **Inventario.** "Consumato" e "aggiungi i prodotti in scadenza" passano alla lista la
  categoria del prodotto (se non è "Altro"). "Metti in frigo" usa la categoria della voce;
  un prodotto mai visto finisce dove la sua categoria suggerisce (un surgelato in freezer,
  la dispensa in dispensa).
- **Backup.** Le regole di backup includono ora la cartella delle foto.
- **Permessi.** Nessun permesso nuovo. Scattare una foto chiede il permesso della
  fotocamera, già dichiarato per lo scanner (Android lo pretende anche per la fotocamera di
  sistema quando l’app lo dichiara). La galleria usa il selettore di foto, che non ne
  richiede.
- **Manifest.** Un `FileProvider` (`${applicationId}.photos`) consegna alla fotocamera il
  file in cui scrivere.

## Verifica

L’ambiente in cui è stato scritto questo lavoro non raggiunge `dl.google.com`: non è stato
possibile installare l’Android SDK né eseguire `./gradlew`. È stato verificato così:

- Modello, DAO (con un fake), repository, ViewModel, proposta di categoria e formattazione
  sono stati compilati ed eseguiti sulla JVM con Kotlin 2.0.21 e stub minimi di Room e
  `ViewModel`: 92 test, tutti verdi, nessun warning del compilatore. Due errori introdotti
  di proposito (resto dell’acquisto parziale, categoria nella migrazione) vengono rilevati.
- La migrazione è stata eseguita su SQLite e confrontata, tabella per tabella (colonne,
  tipi, `NOT NULL`, chiave, indici), con lo schema che Room genera dalle entità.

**Da verificare con `./gradlew assembleDebug testDebugUnitTest`:** le schermate Compose,
`FilePhotoStore`, l’elaborazione KSP di Room e i test Robolectric (`SavedListDaoTest`,
`MigrationTest`, `ShoppingItemEditViewModelTest`, `ReceiptViewModelTest`,
`SettingsStoreTest`) e il riconoscimento ML Kit su scontrini veri. La prima build genera
`app/schemas/com.igor.fridge.data.local.IgorDatabase/3.json`, che va committato.

## Seconda tornata: i sei suggerimenti e lo scontrino

| Funzione | Dove |
| --- | --- |
| Aggiunta rapida con quantità ("2 kg mele", "latte x2", "mezzo chilo di pane") | `domain/QuickEntry.kt` |
| Condividere la lista come testo | menu ⋮ della lista, `shareText()` in `ShoppingListModel.kt` |
| Prezzo per voce (€ per unità), totale stimato e spesa nel carrello | dettagli della voce, barra in fondo alla lista |
| Negozio per voce, con filtro | dettagli della voce, chip sopra la lista |
| Ordine delle corsie personalizzato | Impostazioni o menu ⋮ → "Ordine delle corsie" |
| Marca anche in inventario | `FoodItem.brand`; segue la voce con "Metti in frigo" |
| Scontrino fotografato → inventario | icona scontrino nell'inventario, menu ⋮ della lista |
| Scadenza chiesta all'ingresso in frigo | "Metti in frigo" e scontrino, per i prodotti freschi |

### Decisioni

**Il prezzo è per unità di misura** (€/pz, €/kg), in centesimi interi. Il totale stimato
moltiplica per la quantità da comprare; quello del carrello per la quantità presa. Le voci
senza prezzo sono contate a parte, perché una stima parziale non sembri completa.

**Il filtro per negozio mostra anche le voci senza negozio**, che si possono comprare
dovunque. Con un negozio scelto, le voci aggiunte nascono per quel negozio. "Metti in
frigo" sposta comunque tutte le voci spuntate, anche quelle nascoste dal filtro.

**L'ordine delle corsie sta nelle preferenze (DataStore)**, non nel database: è una
preferenza personale. Una categoria aggiunta in futuro si accoda da sola.

**Scontrino: riconoscimento sul telefono.** ML Kit Text Recognition con modello incluso
(circa 4 MB in più nell'APK): lo scontrino non lascia il dispositivo e funziona offline.
Il testo passa da tre funzioni pure, provate sulla JVM:
- `groupIntoRows` rimette insieme nome e prezzo, che l'OCR restituisce come blocchi
  separati;
- `parseReceipt` legge le righe dei documenti commerciali italiani, cioè nome e importo,
  aliquota e reparto ignorati, dettagli "2 x 0,89" e "0,725 kg x 2,00 €/kg" attaccati al
  prodotto il cui importo torna, formati "1L"/"500G" nel nome. Scarta sconti, pagamenti
  e tutto ciò che sta dopo il totale;
- `receiptMatches` abbina le righe alle voci della lista, anche abbreviate ("MOZZ." →
  Mozzarella).

**Categoria automatica, in quest'ordine:** quella dell'ultima volta che il prodotto è stato
in casa; quella della voce di lista abbinata; la proposta dal nome, che ora capisce anche le
abbreviazioni di cassa ("PARMIG", "PROSC") se portano a una sola categoria. Ogni conferma
salva il prodotto in inventario, quindi la volta dopo la categoria arriva dallo storico.

**Si chiede solo ciò che manca.** Nella revisione dello scontrino:
- la quantità di un prodotto venduto a peso (frutta, verdura, carne, pesce) senza peso
  stampato resta vuota ed evidenziata, e va compilata;
- la scadenza dei prodotti freschi (frutta, verdura, pane, carne, pesce, latticini) è
  evidenziata; se manca, prima di salvare si chiede conferma ("Aggiungi comunque" / "Indica
  le date");
- i prodotti non alimentari partono esclusi.

Lo stesso vale per "Metti in frigo": se fra le voci spuntate ci sono prodotti freschi, una
finestra ne chiede le scadenze, tutte facoltative. Il calendario si apre alla durata tipica
della categoria (pesce 2 giorni, carne e pane 3, verdura 5, frutta e latticini 7), ma nessuna
data entra senza essere scelta.

**Confermando lo scontrino, le voci di lista abbinate escono dalla lista.** Il nome usato in
inventario è quello della lista ("Mozzarella"), più leggibile di quello della cassa.

**Database: versione 3** (`MIGRATION_2_3`): solo colonne nuove e facoltative.

### Limiti noti dello scontrino

- Il formato degli scontrini varia fra catene: righe su due colonne non allineate, nomi
  troncati o OCR sporco producono bozze da correggere, non salvataggi sbagliati.
- Il prezzo letto dallo scontrino si mostra nella revisione ma non si salva: l'inventario
  non ha un campo prezzo.
- L'abbinamento con la lista richiede che ogni parola della voce compaia nella riga
  ("Latte di soia" non corrisponde a "LATTE PS").

## Suggerimenti per i passi successivi

1. **Inserimento vocale** (già nella spec della Fase 3): il parser di `QuickEntry` è pronto
   per essere riusato.
2. **Prezzo in inventario e storico dei prezzi**, a partire dagli scontrini: andamento del
   costo di un prodotto e della spesa mensile.
3. **Suggerimenti** dai prodotti consumati più spesso ("di solito compri…").
4. **Cancellazione logica** anche per `shopping_items`, prima di qualunque sincronizzazione.
