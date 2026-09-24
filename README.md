# Igor — monitoraggio del frigorifero

App Android per tenere sotto controllo cosa c’è in frigo, cosa sta per scadere e
cosa va ricomprato. Tutti i dati restano sul dispositivo: nessun account, nessun server.

## Funzionalità (MVP)

- **Inventario**: aggiunta, modifica ed eliminazione di alimenti con quantità, unità di
  misura, categoria, luogo di conservazione (frigo / freezer / dispensa) e note.
- **Scadenze**: la lista è ordinata per urgenza; ogni articolo mostra lo stato
  (scaduto, in scadenza, fresco) con il numero di giorni residui. Filtri rapidi e ricerca per nome.
- **Notifiche**: un controllo giornaliero (WorkManager) invia una notifica riepilogativa
  dei prodotti scaduti o in scadenza entro la soglia di preavviso (3 giorni di default).
- **Codice a barre**: lettura EAN/UPC/Code-128 con fotocamera (CameraX + ML Kit). Se il codice
  è già stato registrato in passato, nome, categoria e unità vengono precompilati.
- **Lista della spesa**: voci aggiunte a mano, oppure generate dai prodotti consumati o in
  scadenza; spunta e rimozione in blocco degli articoli presi.

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
| DI | container manuale (`AppContainer`), senza annotation processor |
| minSdk / targetSdk | 26 / 35 |

`minSdk 26` permette di usare `java.time` senza desugaring e le icone adattive senza fallback.

## Struttura del progetto

```
app/src/main/java/com/igor/fridge/
├── data/
│   ├── local/        # entità Room (UUID, cancellazione logica), DAO, database, type converter
│   ├── prefs/        # preferenze utente (DataStore, osservabili)
│   └── repository/   # FoodRepository, ShoppingRepository
├── di/               # AppContainer
├── domain/           # logica di scadenza, indipendente da Android
├── notification/     # notifica riepilogativa, worker e pianificazione
└── ui/               # tema, navigazione e schermate Compose
    ├── inventory/    # elenco, filtri, ricerca
    ├── edit/         # inserimento e modifica
    ├── scanner/      # lettura codice a barre
    ├── settings/     # impostazioni: soglia, ora della notifica, attivazione
    └── shopping/     # lista della spesa
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

Il progetto compila: `assembleDebug` e la suite di test JVM passano.

`FoodItem` e `ShoppingItem` usano come chiave primaria un UUID `String` generato sul
dispositivo, non un id autoincrementale di SQLite che collide fra dispositivi diversi, e
portano un campo `updatedAt`. `FoodItem` non viene mai cancellato fisicamente: la rimozione
è logica, tramite `removedAt` e `removalReason` (`CONSUMATO`, `BUTTATO`, `ERRORE`). Per
l’inventario alimentare questa è già una base sufficiente per un’eventuale sincronizzazione
futura senza dover rifare lo schema. La lista della spesa no: le voci vengono ancora
cancellate fisicamente, e prima di poter essere sincronizzata le servirà la stessa
cancellazione logica. Arriverà nella Fase 2, insieme alla riscrittura di "Rimuovi presi" in
"Metti in frigo", che tocca comunque quel codice.

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

I test coprono 58 casi su 12 classi, tutti sulla JVM; Room gira sotto Robolectric. Non
esiste ancora un source set `androidTest`.

**Attenzione:** lo schema di `FoodItem` e `ShoppingItem` è cambiato ma la versione del
database Room è rimasta `1`. È legittimo solo perché nessun database Igor esiste ancora su
alcun dispositivo con dati da salvare. Perché una build precedente già installata non muoia
all’apertura del database, `IgorDatabase.build` usa `fallbackToDestructiveMigration()`: il
database viene ricreato da zero. Quella riga va tolta — e sostituita da una migrazione vera
— non appena esistano dati reali, altrimenti cancellerebbe l’inventario dell’utente senza
dire niente.

## Prossimi passi

Le fasi successive sono descritte in
`docs/superpowers/specs/2026-09-23-inventario-vocale-design.md`.

- **Ciclo chiuso della lista della spesa**: il pulsante "Rimuovi presi" diventa "Metti in
  frigo" e le voci spuntate entrano in inventario in un’unica operazione, ereditando
  categoria e posizione dall’ultimo omonimo ma senza scadenza, da completare a mano (è
  per questo che esiste il filtro "Senza data").
- **Inserimento vocale**: un pulsante microfono in `InventoryScreen` avvia il
  riconoscimento vocale di Android e apre `EditItemScreen` con i campi precompilati da un
  parser testuale (`domain/voice/`); la conferma manuale resta obbligatoria prima di
  salvare, perché il parser è la parte incerta del sistema.
