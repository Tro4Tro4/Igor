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
`MigrationTest`, `ShoppingItemEditViewModelTest`). La prima build genera
`app/schemas/com.igor.fridge.data.local.IgorDatabase/2.json`, che va committato.

## Suggerimenti per i passi successivi

1. **Aggiunta rapida con quantità**: interpretare "2 kg mele" o "6 uova" nel campo di
   aggiunta. Il parser è lo stesso che servirà all’inserimento vocale.
2. **Condividere la lista** come testo (WhatsApp, SMS) dal menu della lista.
3. **Prezzo** per voce e totale stimato della spesa; con la quantità presa diventa anche
   la spesa reale.
4. **Negozio** per voce, con filtro: chi fa la spesa in due posti vede solo cosa comprare lì.
5. **Riordinare le corsie** secondo il proprio supermercato.
6. **Marca verso l’inventario**: oggi la marca resta nella lista; aggiungerla a `FoodItem`
   la conserverebbe anche dopo "Metti in frigo".
7. **Suggerimenti** dai prodotti consumati più spesso ("di solito compri…").
8. **Cancellazione logica** anche per `shopping_items`, come per l’inventario, prima di
   qualunque sincronizzazione.
