# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Igor è un'app Android (Kotlin, Jetpack Compose, Room) per inventario del frigo, scadenze, lista della spesa, scontrini e prezzi. Tutti i dati restano sul dispositivo; l'unico accesso a Internet è Open Prices, facoltativo e spento di default. Il `README.md` descrive funzionalità, stato e difetti noti: tenerlo aggiornato quando cambia qualcosa di rilevante.

## Vincolo d'uso concordato

Il 6 ottobre 2026 l'utente ha confermato che Igor e' esclusivamente per
uso personale e non verra' distribuita. Applicare questo vincolo anche al
servizio prezzi e alle richieste di accesso alle fonti. Non confondere
l'uso personale con una verifica delle condizioni di acquisizione dei dati.

## Comandi

```bash
./gradlew testDebugUnitTest                  # tutti i test (JVM + Robolectric), come la CI
./gradlew testDebugUnitTest --tests "com.igor.fridge.ui.CompareViewModelTest"          # una classe
./gradlew testDebugUnitTest --tests "com.igor.fridge.domain.prices.ChainsTest.riconosce*"  # un metodo (nomi con backtick: usare il carattere jolly)
```

Non c'è un linter configurato e non esiste un source set `androidTest`: tutti i test stanno in `app/src/test` e girano sulla JVM.

## Architettura

Pacchetto `com.igor.fridge`, strati:

- `domain/`: logica pura senza Android (scadenze, ipotesi di categoria, aggiunta rapida, lettura dello scontrino `domain/receipt`, prezzi e confronto negozi `domain/prices`). La maggior parte della logica nuova va qui ed è testabile con JUnit puro.
- `data/`: entità e DAO Room (`data/local`), repository, `prefs/SettingsStore` (DataStore, esposto come `Flow`), `openprices/` (client HTTP scritto a mano su `HttpTransport`, JSON con kotlinx-serialization letto a mano in `OpenPricesJson`), `photos/` (decodifica immagini e PDF ridotti, `decodeUpright`), `receipt/` (OCR ML Kit, offline).
- `di/AppContainer`: DI manuale con `by lazy`, senza annotation processor. Ogni nuova dipendenza si registra qui.
- `ui/`: una cartella per schermata, ognuna con `XxxScreen` + `XxxViewModel`; navigazione in `ui/navigation/IgorNavHost.kt`.
- `notification/`: controllo giornaliero delle scadenze con WorkManager.

Schemi ricorrenti da seguire:

- **ViewModel**: costruiti da un `companion object { val Factory = viewModelFactory { initializer { ... igorApplication().container ... } } }`. Le dipendenze esterne entrano nel costruttore come lambda o parametri con default (`fetchCommunity`, `findSimilar`, `today: () -> LocalDate`, `computeDispatcher`), così i test le sostituiscono senza mock. Lo stato è un unico `StateFlow<XxxUiState>` ottenuto con `combine(...).stateIn(WhileSubscribed)`; i calcoli pesanti su tutto lo storico usano `flowOn(computeDispatcher)`. Nei test va passato `computeDispatcher = dispatcher` (il `StandardTestDispatcher`), altrimenti i tempi diventano non deterministici.
- **Transazioni**: le operazioni che toccano più tabelle passano da `Transactor` (`RoomTransactor` in produzione, `Transactor.Direct` nei test).
- **Test**: DAO finti in memoria (`data/FakeXxxDao.kt`), `FakeTransport` per Open Prices (risponde per metodo + percorso senza query, registra le richieste), Robolectric con `@GraphicsMode(NATIVE)` dove servono Room reale o bitmap.
- **Nomi dei prodotti**: si confrontano tramite `nameWords`/`productKey` (minuscole, senza accenti e punteggiatura) e `receiptMatches` (abbreviazioni della cassa). Le catene di supermercati e le loro marche stanno in `domain/prices/Chains.kt` (`chainOf`, `storeLabel`, `privateLabelChain`).
- **Prezzi**: `PriceRecord` salva il totale della riga e il prezzo per unità di riferimento (kg, litro, pezzo); i prezzi di prodotti diversi si confrontano solo al kg o al litro. I prezzi della comunità passano sempre dai filtri di `ui/compare/CommunityObservations.kt` (Italia, EUR, non più vecchi di un anno, negozio riconoscibile).

## Database

Room, versione corrente in `IgorDatabase.kt`. Ogni cambio di schema richiede: aumento di `version`, una nuova `MIGRATION_x_y` aggiunta a `IgorDatabase.build`, un caso in `MigrationTest`, e il commit dello schema generato in `app/schemas/` (lo produce la build tramite KSP). Non si usa `fallbackToDestructiveMigration()`. `FoodItem` e `ShoppingItem` hanno chiavi UUID `String`; `FoodItem` si cancella solo logicamente (`removedAt` + `removalReason`).

## Convenzioni

- Tutto in italiano: interfaccia, commenti, nomi dei test (con backtick, es. `` `un prezzo vecchio non conta`() ``), messaggi di commit (`feat:`, `fix:`, `perf:`, `chore:` + descrizione in italiano).
- I commenti nel codice Kotlin evitano le lettere accentate (`e'`, `piu'`, `perche'`), mentre `strings.xml` e i messaggi mostrati all'utente usano accenti e apostrofi tipografici corretti.
- I commenti spiegano il perché (casi limite, motivi delle scelte), non cosa fa il codice.
- I testi fissi stanno in `res/values/strings.xml`; restano in Kotlin solo i messaggi composti a runtime dai ViewModel.

## Servizio prezzi online

`services/prices` e' indipendente da Gradle: Python3.12, FastAPI, SQLite, pytest
con venv e `requirements.lock`. Fonti e stato nel manifest verificato in
`docs/verification/2026-10-05-fonti-online-manifest.json`: attualmente tutte
candidate, non abilitare sulla sola risposta HTTP200. Android `onlineprices/`,
OnlinePricesRepository e OnlineComparison mantengono cache/associazioni separate
dagli scontrini. Room7 aggiunge solo tre tabelle, con migrazione6→7.
Endpoint BuildConfig.ONLINE_PRICES_URL da `igorOnlinePricesUrl`, default vuoto.
Consenso iniziale spento e CAP20125; niente lista completa, foto o scontrini
in rete. Il job periodico richiede consenso/rete/endpoint configurato.

## Hosting gratuito prezzi — preparazione Cloudflare

API Workers pubblicata il 6 ottobre 2026 su
https://igor-prices.andreatro.workers.dev, con archivio D1 e publisher Python
per GitHub Actions. Test CI, migrazioni remote e smoke HTTPS riusciti.
Sei fonti candidate e zero offerte; acquisizione giornaliera disattivata.
Il contratto Android v1 resta compatibile. L’app sul telefono conserva
per ora la configurazione precedente, senza URL del servizio.
Fonti reali e quote dell’account restano da verificare. Rapporto:
[prima pubblicazione](docs/verification/2026-10-06-deploy-cloudflare.md).

Istruzioni: [servizio Cloudflare](services/prices-cloudflare/README.md).
Prove e limiti: [verifica hosting gratuito](docs/verification/2026-10-05-hosting-cloudflare.md).
