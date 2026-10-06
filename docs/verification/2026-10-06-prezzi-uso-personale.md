# Prezzi per uso esclusivamente personale

Il 6 ottobre 2026 l'utente ha confermato che Igor non verra' distribuita
e ha approvato API riservata, catalogo limitato e aggiornamenti manuali.

## Modifiche

- Worker: richiede `Authorization: Bearer ...` prima di leggere D1. Chiave
  assente o troppo corta: 503; chiave errata o assente nel client: 401.
  Confronto su digest SHA-256 senza uscita anticipata sui byte. Risposte
  non conservabili in cache; nessun valore segreto nelle risposte o nei log.
- Android: chiave dedicata letta dalla proprieta' Gradle
  `igorOnlinePricesToken` attraverso l'ambiente, inclusa nel solo APK
  personale. Nessun token amministrativo Cloudflare nel client.
- Deploy: secret GitHub `IGOR_PRICES_API_TOKEN` trasferito al Worker come
  `PRIVATE_API_TOKEN`. Il valore non e' versionato. La copia locale resta
  nella directory `.tools` ignorata, per ricompilazione e verifica.
- **Prices daily**: solo `workflow_dispatch`, senza cron e senza dipendere
  dalla variabile storica `IGOR_PRICES_DAILY_ENABLED`. La prenotazione D1
  continua a limitare a un tentativo per fonte/giorno UTC.
- Importazione: massimo 50 URL complessivi nel manifest, controllato prima
  di aggiornare metadata o acquisire dati. Nessuna scansione dei cataloghi.
- Richieste alle catene aggiornate per uso personale; non inviate.

La chiave e' recuperabile da chi possiede l'APK: l'artefatto non deve essere
distribuito. Ruotarla richiede aggiornamento del secret, deploy e nuovo APK.
Il servizio locale FastAPI di sviluppo non viene esposto da questa modifica.

## Verifiche locali

123 test Python passati; rimane il warning upstream Starlette/httpx.
Cartella temporanea nuova nel workspace e cache pytest disabilitata per
evitare directory preesistenti non accessibili sul PC.

30 test Workers/D1 passati, inclusi accesso non autorizzato prima di D1,
server senza chiave, contratto API e pubblicazione atomica. I due test
voluminosi superano 5 secondi sul PC: suite locale eseguita con
`--testTimeout=20000`, senza cambiare il timeout della CI.
Typecheck, quattro controlli Node e dry-run Workers passati.

## Distribuzione e telefono

Commit del codice `001ab5c` pubblicato nella branch
`claude/android-fridge-monitor-app-vlbci3`.
[Deploy privato](https://github.com/Tro4Tro4/Igor/actions/runs/37465929006),
[Prices checks](https://github.com/Tro4Tro4/Igor/actions/runs/37465926315) e
[CI Android](https://github.com/Tro4Tro4/Igor/actions/runs/37465926274)
completati con successo.

Versione Worker privata: `e6707349-cfe1-4e19-a820-8a109be4c2b5`.

[Avvio manuale](https://github.com/Tro4Tro4/Igor/actions/runs/37466129606)
completato con successo: attempted 0, succeeded 0, skipped 6, failed 0.
Nessuna fonte attivata, nessuna richiesta di prodotti alle catene.

Smoke remoto con lo User-Agent esatto del client Android:

- `/v1/sources` senza chiave e con chiave errata: 401.
- `/v1/sources` autorizzato: 200, sei candidate, catalog_version 0.
- Ricerca latte per 20125 autorizzata: 200, zero offerte.
- CAP non valido: 422; prodotto assente: 404; POST autorizzato: 405.
- Tutte le risposte hanno `Cache-Control: no-store` e non contengono la chiave.

Build Android locale riuscita in 10m25s: **364 test in 55 classi, zero
failure/error/skipped**, conteggi verificati dai report XML. L'URL e la
chiave generati in BuildConfig corrispondono al servizio distribuito.
La build usa la copia isolata del precedente APK con
le sole modifiche prezzi approvate, escludendo il lavoro locale Open Food Facts.

APK: `build/deliverables/igor-private-prices-debug-2026-10-06.apk`.
SHA256: `f7958d6bfde2cfbf2fd76d81dd1c44a1ae72f29c59fb8d43d84e630676fefdfe`.
Dimensione: 86.296.891 byte. Installazione `adb install -r` sul Samsung
SM-A536B: `Success`, senza disinstallazione. Avvio con `am start -W`:
`Status: ok`; processo presente al controllo successivo. Dati personali
e consensi non modificati; non verificato visivamente il contenuto delle
singole voci o una ricerca online dal telefono. Il client con chiave e'
coperto dal test Android e l'endpoint reale dalle prove HTTPS sopra.

## Stato delle fonti

Sei fonti candidate e zero offerte. L'uso personale non viene usato per
marcare `reuse_verified=true` o inventare copertura del CAP 20125.
L'API riservata non rende disponibili feed ancora assenti. La ricerca
Android legge il catalogo gia' acquisito, senza avviare acquisizioni delle
catene. Consensi sul telefono non modificati automaticamente.
