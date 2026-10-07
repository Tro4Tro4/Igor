# Ripristino del riconoscimento barcode nell'APK

7 ottobre 2026. Segnalazione dell'utente: il riconoscimento dei prodotti
tramite barcode sembra assente dal telefono.

## Causa e correzione

L'APK personale installato il 6 ottobre era stato compilato dalla copia
isolata dei commit prezzi, escludendo la funzione Open Food Facts presente
nel workspace e gia' approvata il 5 ottobre. Il riconoscimento locale dei
barcode salvati restava disponibile, ma mancava la ricerca online dei
prodotti sconosciuti e la relativa impostazione.

APK ricompilato dal workspace completo: include Open Food Facts, il suo
consenso separato e l'accesso prezzi Cloudflare privato. Nessun cambiamento
di schema Room, nessuna cancellazione di dati, nessuna attivazione automatica
dei consensi. L'integrazione barcode viene registrata nel repository per
includerla anche nelle prossime build dai commit.

## Verifiche

- `assembleDebug testDebugUnitTest --offline --console=plain`: build riuscita
  in 4m30s, 376 test in 56 classi, zero failure/error/skipped, conteggi XML.
- OpenFoodFactsClient e BarcodeLookupStatus presenti nei dex dell'APK.
- BuildConfig generato: URL Cloudflare e chiave dedicata corretti;
  nessun valore segreto stampato o versionato.
- Una richiesta reale all'API v3 Open Food Facts, con codice pubblico di
  prova 3017620422003, lingua italiana e soli campi usati dall'app: HTTP 200,
  codice corrispondente e nome presente. Il servizio risponde dal PC;
  questo controllo non attesta una scansione fisica end-to-end sul telefono.

## Artefatto e installazione

`build/deliverables/igor-barcode-private-prices-debug-2026-10-07.apk`

SHA256: `829a2e017e3acae6233056496544a92561c283c2beb586c619d1ecac729be33c`.
Dimensione: 86.916.032 byte. Artefatto personale con chiave privata inclusa,
non destinato alla distribuzione.

Telefono Samsung SM-A536B ricollegato successivamente. Installazione
`adb install -r`: `Success`, senza disinstallare Igor. Avvio `am start -W`:
`Status: ok`; processo presente al controllo successivo. Verifica visiva
e prova barcode sul telefono ancora da completare. Consensi non modificati.

Impostazione: **Riconosci con Open Food Facts**. Storico locale consultato
prima della rete; ricerca online solo per EAN/UPC validi sconosciuti e con
consenso attivo. Se la preferenza non era stata salvata, il default e' spento.
