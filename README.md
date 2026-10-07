# Igor — monitoraggio del frigorifero

App Android per tenere sotto controllo cosa c’è in frigo, cosa sta per scadere e
cosa va ricomprato. Inventario, lista e scontrini restano sul dispositivo.
Il riconoscimento barcode con Open Food Facts e' facoltativo e attivabile nelle
Impostazioni. Il confronto prezzi funziona offline, dai tuoi acquisti.

## Funzionalità (MVP)

- **Inventario**: aggiunta, modifica ed eliminazione di alimenti con quantità, unità di
  misura, categoria, luogo di conservazione (frigo / freezer / dispensa) e note.
  La lista è raggruppata per categoria, con intestazioni durante lo scorrimento,
  filtri combinabili per categoria, luogo e stato, ricerca per nome o marca e
  conteggio degli articoli mostrati. Ricerca e filtri si ripristinano al ritorno.
- **Categorie**: 49 categorie condivise tra inventario, spesa e scontrini,
  incluse salumi, formaggi freschi e stagionati, biscotti e merendine; selettore
  con ricerca. I prodotti già salvati conservano la loro categoria, comprese
  quelle generiche: si può scegliere una categoria più precisa modificando il prodotto.
- **Scadenze**: dentro ogni categoria la lista è ordinata per urgenza; ogni articolo
  mostra lo stato (scaduto, in scadenza, fresco o senza data) e la scadenza in testo.
- **Notifiche**: un controllo giornaliero (WorkManager) invia una notifica riepilogativa
  dei prodotti scaduti o in scadenza entro la soglia di preavviso (3 giorni di default).
- **Codice a barre**: lettura EAN/UPC/Code-128 con fotocamera (CameraX + ML Kit). Se il codice
  è già stato registrato in passato, nome, categoria e unità vengono precompilati.
  Per i codici EAN/UPC sconosciuti, attivando **Riconosci con Open Food Facts** nelle
  Impostazioni, Igor cerca online nome, marca e categoria suggerita. I campi modificati
  manualmente prevalgono; quantità e scadenza restano da confermare. Errori di rete e
  prodotti assenti permettono di continuare a mano. I prodotti salvati vengono poi
  riconosciuti offline; nessun catalogo completo viene scaricato.
- **Lista della spesa**: voci aggiunte a mano, oppure generate dai prodotti consumati o in
  scadenza, raggruppate per categoria (ognuna con la sua icona) nell’ordine delle corsie.
  Per ogni voce: quantità e unità, marca, note, foto, quantità effettivamente presa. Spunta
  e "Metti in frigo" per far tornare gli articoli presi nell’inventario, con la possibilità
  di annullare lo spostamento finché lo snackbar è visibile; dopo un acquisto parziale il
  resto rimane in lista, e ciò che non si mangia (casa, igiene) esce dalla lista senza
  entrare in frigo.
  L’aggiunta rapida capisce le quantità ("2 kg mele"); ogni voce può avere prezzo e
  negozio, con totale stimato e filtro per negozio; la lista si condivide come testo e
  l’ordine delle corsie si adatta al proprio supermercato. Entrando in frigo, per i
  prodotti freschi viene chiesta la scadenza.
- **Liste salvate**: la lista attuale si salva con un nome ("Spesa settimanale") e si
  ricarica quando serve, aggiungendo solo ciò che manca.
- **Scontrino**: dalla foto di uno scontrino (OCR sul telefono, ML Kit) Igor propone i
  prodotti con la categoria, chiede quantità e scadenza dove servono, li mette in
  inventario e toglie dalla lista della spesa ciò che è stato comprato.
- **Prezzi**: ogni scontrino registra il prezzo pagato di ogni riga (sconti compresi), con
  negozio e data. Per ogni prodotto: ultimo prezzo al kg/litro/pezzo, variazione, minimo,
  massimo, media e grafico dell'andamento; per ogni mese, la spesa totale. L'ultimo prezzo
  pagato viene proposto nella lista della spesa.
- **Confronta i supermercati**: quanto costerebbe la lista in ogni negozio ai prezzi pagati,
  voce per voce. Le stime indicano la data del prezzo pagato e il numero di articoli
  coperti; gli articoli senza prezzo non entrano nel totale. Anche i suggerimenti
  di prodotti simili vengono esclusivamente dai propri scontrini. Nessun prezzo online
  o della comunità viene scaricato e gli scontrini non vengono condivisi.

## Stack tecnico

| Ambito | Scelta |
| --- | --- |
| Linguaggio | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Navigazione | Navigation Compose |
| Persistenza | Room (SQLite) |
| Preferenze | DataStore (Preferences), osservabili tramite `Flow` |
| Background | WorkManager |
| Fotocamera | CameraX + ML Kit Barcode Scanning |
| Scontrini | ML Kit Text Recognition (modello incluso, offline) |
| Riconoscimento prodotti | Open Food Facts (facoltativo; `HttpURLConnection` + kotlinx-serialization-json) |
| DI | container manuale (`AppContainer`), senza annotation processor |
| minSdk / targetSdk | 26 / 35 |

`minSdk 26` permette di usare `java.time` senza desugaring e le icone adattive senza fallback.

## Struttura del progetto

```
app/src/main/java/com/igor/fridge/
├── data/
│   ├── openprices/   # client di Open Prices (facoltativo)
│   ├── openfoodfacts/ # riconoscimento online dei barcode sconosciuti (facoltativo)
│   ├── local/        # entità Room (UUID, cancellazione logica), DAO, database, migrazioni
│   ├── photos/       # foto delle voci della spesa (file privati dell’app)
│   ├── prefs/        # preferenze utente (DataStore, osservabili)
│   ├── receipt/      # riconoscimento del testo degli scontrini (ML Kit)
│   └── repository/   # FoodRepository, ShoppingRepository, SavedListRepository
├── di/               # AppContainer
├── domain/           # scadenze, categorie, aggiunta rapida, lettura scontrini (senza Android)
├── notification/     # notifica riepilogativa, worker e pianificazione
└── ui/               # tema, navigazione e schermate Compose
    ├── inventory/    # elenco, filtri, ricerca
    ├── edit/         # inserimento e modifica
    ├── scanner/      # lettura codice a barre
    ├── compare/      # la lista nei vari supermercati
    ├── prices/       # storico e andamento dei prezzi
    ├── receipt/      # dallo scontrino all'inventario
    ├── settings/     # impostazioni: soglia, ora della notifica, ordine delle corsie
    └── shopping/     # lista della spesa, dettagli della voce, liste salvate
```

## Build

```bash
./gradlew assembleDebug      # APK di debug
./gradlew test               # test unitari JVM
./gradlew installDebug       # installa su un dispositivo collegato
```

Serve JDK 17+ e l’Android SDK con `platform-35` e i build-tools corrispondenti
(Android Studio li installa automaticamente aprendo il progetto).

## Stato

Fino alla Fase 2 il progetto compilava e la suite di test JVM passava. Le modifiche alla
lista della spesa e lo scontrino (vedi
`docs/superpowers/specs/2026-10-03-lista-spesa-dettagli-design.md`) sono state scritte
senza poter eseguire `./gradlew`: la logica è stata compilata e provata sulla JVM, ma
schermate Compose, KSP di Room, ML Kit e test Robolectric attendono la prima build.

`FoodItem` e `ShoppingItem` usano come chiave primaria un UUID `String` generato sul
dispositivo, non un id autoincrementale di SQLite che collide fra dispositivi diversi, e
portano un campo `updatedAt`. `FoodItem` non viene mai cancellato fisicamente: la rimozione
è logica, tramite `removedAt` e `removalReason` (`CONSUMATO`, `BUTTATO`, `ERRORE`). Per
l’inventario alimentare questa è già una base sufficiente per un’eventuale sincronizzazione
futura senza dover rifare lo schema. La lista della spesa no: le voci vengono ancora
cancellate fisicamente, e prima di poter essere sincronizzata le servirà la stessa
cancellazione logica.

Il ciclo fra spesa e inventario ora si chiude: con "Metti in frigo" le voci spuntate
entrano nell’inventario ed escono dalla lista, ereditando categoria e posizione
dall’ultima volta che quel nome è stato in casa (anche se quell’articolo era già stato
consumato), senza scadenza. Lo spostamento si può annullare finché lo snackbar che lo
conferma resta visibile: un secondo annullamento non fa nulla.

La colonna `removalReason` rende *possibili* delle statistiche sugli sprechi, ma non le
abilita: l’interfaccia offre solo "consumato" ed "eliminato", quindi `BUTTATO` non è oggi
raggiungibile e le statistiche non avrebbero ancora dati da cui partire.

Le impostazioni (giorni di preavviso, ora della notifica, notifiche attive o disattivate)
sono passate da SharedPreferences a DataStore, sono osservabili tramite `Flow`, e hanno
finalmente una schermata dedicata (**Impostazioni**) che ripianifica il worker delle
notifiche quando cambiano.

L’inventario ha un quarto filtro, "Senza data", e si aggiorna sia al cambio della soglia di
preavviso sia al cambio di giorno.

`ShoppingRepository.addIfAbsent` ripristina una voce già spuntata invece di ignorarla: un
prodotto ricomprato torna davvero nella lista della spesa.

Le stringhe dell’interfaccia stanno in `strings.xml`, con accenti e apostrofi tipografici
corretti; restano in Kotlin i messaggi che i ViewModel compongono a runtime (per esempio
"3 prodotti aggiunti alla lista della spesa"), dove il testo dipende dai dati.

La suite verificata il 7 ottobre comprende 374 test in 56 classi, tutti sulla JVM; Room e le foto girano sotto Robolectric. Non
esiste ancora un source set `androidTest`.

**Database: versione 7.** Ogni cambio di schema alza la versione e porta la sua migrazione
(da `MIGRATION_1_2` a `MIGRATION_5_6`, che aggiunge la colonna `nameKey` per cercare i nomi
senza distinguere maiuscole anche con le lettere accentate): a versione invariata Room rifiuterebbe di aprire il
database. Le migrazioni sono provate da `MigrationTest`, che apre con `IgorDatabase.build`
un database della versione 1 scritto a mano. Non c’è più `fallbackToDestructiveMigration()`:
con una migrazione mancante, o installando una versione più vecchia sopra una più nuova, Room
si rifiuta di aprire il database invece di ricrearlo vuoto, e i dati restano su disco. Gli
schemi esportati (`app/schemas/`) vengono generati dalla build: la CI li pubblica
nell'artifact `room-schemas`, da scaricare e committare.

**CI.** `.github/workflows/android.yml` compila l’app ed esegue tutti i test JVM, Robolectric
compreso, a ogni push.

**Privacy.** In Impostazioni › Privacy e dati si esportano tutti i dati in JSON e si cancella
tutto. Il backup di Android include database (con il file WAL), foto e impostazioni, ma nel
cloud solo se cifrato; l’accesso a Open Prices sta in un file a parte che non va mai nel
backup. Prima di condividere uno scontrino se ne vede l’anteprima e si possono ritagliare le
strisce con dati personali. ML Kit legge scontrini e codici sul telefono, ma può inviare a
Google statistiche d’uso: l’app lo dice nella stessa sezione.

### Affidabilita' e correzioni

Inventario e spesa mantengono i dati aggiornati anche durante una permanenza lunga nella
schermata di modifica: al ritorno non mostrano prima una copia obsoleta della lista.

“Annulla” ripristina la voce originale, inclusi dettagli e spunta, e non sovrascrive
modifiche intervenute dopo l'operazione. In caso di conflitto l'annullamento si interrompe
senza modificare i dati; gli errori temporanei permettono di riprovare. Le scritture su
piu' tabelle sono atomiche, con test di rollback su Room reale.

I salvataggi ripetuti non creano duplicati; gli errori conservano il modulo. Scanner e
scontrini rispettano le categorie scelte manualmente. La ricerca delle categorie ignora
gli accenti, e la classificazione riconosce anche “tonno sott’olio” e “latte bio di soia”.

Verifiche e limiti di accessibilita' e prestazioni: [rapporto delle correzioni](docs/verification/2026-10-04-correzioni-bug.md).

## Prossimi passi

Le fasi successive sono descritte in
`docs/superpowers/specs/2026-09-23-inventario-vocale-design.md`.

- **Inserimento vocale**: un pulsante microfono in `InventoryScreen` avvia il
  riconoscimento vocale di Android e apre `EditItemScreen` con i campi precompilati da un
  parser testuale (`domain/voice/`); la conferma manuale resta obbligatoria prima di
  salvare, perché il parser è la parte incerta del sistema.

## Confronto solo dai propri acquisti

Dal 7 ottobre 2026, su richiesta dell'utente, Confronta usa esclusivamente i
prezzi registrati dagli scontrini. Rimangono storico, grafico, prezzi per
kg/litro/pezzo e suggerimenti di prodotti simili gia' acquistati. Il prezzo
e' quello pagato alla data indicata, non un listino attuale del supermercato.
I totali parziali mostrano quante voci hanno un prezzo.

Rimossi dall'interfaccia catalogo delle catene, prezzi della comunita',
ricerche sui siti e condivisione a Open Prices. Il riconoscimento barcode
con Open Food Facts rimane attivo su consenso separato.

I lavori Android di aggiornamento prezzi sono cancellati e quelli gia'
accodati non eseguono richieste. Il nuovo APK non contiene URL o chiave
privata Cloudflare. I workflow GitHub Prices daily/deploy sono disabilitati
e hanno anche un blocco nel codice. Tabelle cache e dati storici restano
intatti, senza migrazione distruttiva; librerie e servizio sperimentale
rimangono in archivio, scollegati dall'app. Worker privato e D1 non sono
stati eliminati e non ricevono richieste da Igor.

Verifiche: [confronto da scontrini](docs/verification/2026-10-07-confronto-solo-scontrini.md).
Igor e' esclusivamente per uso personale e non verra' distribuita.
