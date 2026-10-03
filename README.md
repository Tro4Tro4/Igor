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
  scadenza, raggruppate per categoria (ognuna con la sua icona) nell’ordine delle corsie.
  Per ogni voce: quantità e unità, marca, note, foto, quantità effettivamente presa. Spunta
  e "Metti in frigo" per far tornare gli articoli presi nell’inventario, con la possibilità
  di annullare lo spostamento finché lo snackbar è visibile; dopo un acquisto parziale il
  resto rimane in lista, e ciò che non si mangia (casa, igiene) esce dalla lista senza
  entrare in frigo.
- **Liste salvate**: la lista attuale si salva con un nome ("Spesa settimanale") e si
  ricarica quando serve, aggiungendo solo ciò che manca.

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
│   ├── local/        # entità Room (UUID, cancellazione logica), DAO, database, migrazioni
│   ├── photos/       # foto delle voci della spesa (file privati dell’app)
│   ├── prefs/        # preferenze utente (DataStore, osservabili)
│   └── repository/   # FoodRepository, ShoppingRepository, SavedListRepository
├── di/               # AppContainer
├── domain/           # scadenze e proposta di categoria, indipendenti da Android
├── notification/     # notifica riepilogativa, worker e pianificazione
└── ui/               # tema, navigazione e schermate Compose
    ├── inventory/    # elenco, filtri, ricerca
    ├── edit/         # inserimento e modifica
    ├── scanner/      # lettura codice a barre
    ├── settings/     # impostazioni: soglia, ora della notifica, attivazione
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
lista della spesa (dettagli, categorie, foto, liste salvate; vedi
`docs/superpowers/specs/2026-10-03-lista-spesa-dettagli-design.md`) sono state scritte
senza poter eseguire `./gradlew`: la logica è stata compilata e provata sulla JVM, ma
schermate Compose, KSP di Room e test Robolectric attendono la prima build.

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

I test coprono 117 casi su 19 classi, tutti sulla JVM; Room gira sotto Robolectric. Non
esiste ancora un source set `androidTest`.

**Database: versione 2.** La lista della spesa ha cambiato schema, e da qui in poi ogni
cambio di schema alza la versione e porta la sua migrazione (`IgorDatabase.MIGRATION_1_2`
è la prima): a versione invariata Room rifiuterebbe di aprire il database. La migrazione è
provata da `MigrationTest`, che apre con `IgorDatabase.build` un database della versione 1
scritto a mano. `fallbackToDestructiveMigration()` è ancora presente e copre solo un salto
di versione senza migrazione, cancellando i dati: va tolto quando si decide come gestire
quel caso. Lo schema esportato della versione 2 (`app/schemas/.../2.json`) viene generato
dalla prima build e va committato.

### Difetti noti

**L’elenco si aggiorna con qualche secondo di ritardo.** Dopo aver aggiunto o spostato un
prodotto, la lista può impiegare qualche istante a mostrarlo: il dato è salvato
correttamente e compare da solo, senza bisogno di riaprire l’app. La causa non è ancora
stata isolata; il sospetto è il momento in cui le schermate riprendono a osservare il
database dopo essere tornate in primo piano.

## Prossimi passi

Le fasi successive sono descritte in
`docs/superpowers/specs/2026-09-23-inventario-vocale-design.md`.

- **Inserimento vocale**: un pulsante microfono in `InventoryScreen` avvia il
  riconoscimento vocale di Android e apre `EditItemScreen` con i campi precompilati da un
  parser testuale (`domain/voice/`); la conferma manuale resta obbligatoria prima di
  salvare, perché il parser è la parte incerta del sistema.
