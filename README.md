# Igor — monitoraggio del frigorifero

App Android per tenere sotto controllo cosa c'e' in frigo, cosa sta per scadere e
cosa va ricomprato. Tutti i dati restano sul dispositivo: nessun account, nessun server.

## Funzionalita' (MVP)

- **Inventario**: aggiunta, modifica ed eliminazione di alimenti con quantita', unita' di
  misura, categoria, luogo di conservazione (frigo / freezer / dispensa) e note.
- **Scadenze**: la lista e' ordinata per urgenza; ogni articolo mostra lo stato
  (scaduto, in scadenza, fresco) con il numero di giorni residui. Filtri rapidi e ricerca per nome.
- **Notifiche**: un controllo giornaliero (WorkManager) invia una notifica riepilogativa
  dei prodotti scaduti o in scadenza entro la soglia di preavviso (3 giorni di default).
- **Codice a barre**: lettura EAN/UPC/Code-128 con fotocamera (CameraX + ML Kit). Se il codice
  e' gia' stato registrato in passato, nome, categoria e unita' vengono precompilati.
- **Lista della spesa**: voci aggiunte a mano, oppure generate dai prodotti consumati o in
  scadenza; spunta e rimozione in blocco degli articoli presi.

## Stack tecnico

| Ambito | Scelta |
| --- | --- |
| Linguaggio | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Navigazione | Navigation Compose |
| Persistenza | Room (SQLite) |
| Background | WorkManager |
| Fotocamera | CameraX + ML Kit Barcode Scanning |
| DI | container manuale (`AppContainer`), senza annotation processor |
| minSdk / targetSdk | 26 / 35 |

`minSdk 26` permette di usare `java.time` senza desugaring e le icone adattive senza fallback.

## Struttura del progetto

```
app/src/main/java/com/igor/fridge/
├── data/
│   ├── local/        # entita' Room, DAO, database, type converter
│   ├── prefs/        # preferenze utente (SharedPreferences)
│   └── repository/   # FoodRepository, ShoppingRepository
├── di/               # AppContainer
├── domain/           # logica di scadenza, indipendente da Android
├── notification/     # notifica riepilogativa, worker e pianificazione
└── ui/               # tema, navigazione e schermate Compose
    ├── inventory/    # elenco, filtri, ricerca
    ├── edit/         # inserimento e modifica
    ├── scanner/      # lettura codice a barre
    └── shopping/     # lista della spesa
```

## Build

```bash
./gradlew assembleDebug      # APK di debug
./gradlew test               # test unitari JVM
./gradlew installDebug       # installa su un dispositivo collegato
```

Serve JDK 17+ e l'Android SDK con `platform-35` e i build-tools corrispondenti
(Android Studio li installa automaticamente aprendo il progetto).

## Stato

Prima iterazione: il codice non e' ancora stato compilato in CI perche' l'ambiente di
sviluppo remoto non ha accesso all'Android SDK. Il primo `./gradlew assembleDebug` su una
macchina con SDK e' il passo di verifica mancante.

## Prossimi passi

- Ricerca del prodotto da codice a barre su Open Food Facts (con cache locale).
- Schermata impostazioni: giorni di preavviso, ora della notifica, attivazione notifiche
  (i valori sono gia' persistiti da `SettingsStore`, manca la UI).
- Statistiche sugli sprechi (quanto viene buttato per scadenza).
- Backup/ripristino dell'inventario ed eventuale sincronizzazione fra dispositivi.
