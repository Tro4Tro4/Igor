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

Build Android e distribuzione remota in corso; gli esiti verranno registrati
al completamento. La build usa la copia isolata del precedente APK con
le sole modifiche prezzi approvate, escludendo il lavoro locale Open Food Facts.

## Stato delle fonti

Sei fonti candidate e zero offerte. L'uso personale non viene usato per
marcare `reuse_verified=true` o inventare copertura del CAP 20125.
L'API riservata non rende disponibili feed ancora assenti. La ricerca
Android legge il catalogo gia' acquisito, senza avviare acquisizioni delle
catene. Consensi sul telefono non modificati automaticamente.
