# Confronto solo dai propri scontrini

7 ottobre 2026. L'utente ha approvato la rimozione dei prezzi online e della
comunita', mantenendo storico personale e riconoscimento barcode.
Questa scelta sostituisce la precedente proposta di acquisizione personale
con conferma delle fonti pendente e prezzi indipendenti dal CAP.

## Risultato

- Confronta usa solo PriceRepository e lista della spesa: nessun client,
  consenso o dato online entra nel calcolo. Restano prezzi pagati per
  negozio, data, confronto al kg/litro/pezzo e prodotti simili gia' acquistati.
- Le voci senza prezzo vengono indicate e non entrano nei totali. Ogni
  negozio mostra copertura completa o numero di voci; la stima non viene
  presentata come listino attuale.
- Tolti catalogo delle catene, box della comunita', ricerca sui siti,
  impostazioni prezzi online/CAP e accesso a Open Prices. Tolti anche i
  relativi controlli dallo storico e la condivisione degli scontrini.
- Le factory storico/scontrino non ricevono piu' client o preferenze Open
  Prices. L'unico collegamento applicativo attivo resta Open Food Facts,
  con consenso separato e dati barcode; inventario, foto e prezzi non inviati.
- Il lavoro periodico prezzi e' cancellato all'avvio. OnlinePricesWorker
  termina senza rete anche se avviato da una precedente pianificazione.
- APK senza BuildConfig URL/token Cloudflare; client legacy configurato
  con URL vuoto. Nessuna chiave amministrativa o privata nel nuovo APK.
- Prices daily e Prices deploy disabilitati via GitHub CLI, stato verificato
  `disabled_manually`. Entrambi hanno anche `if: ${{ false }}` nel repository.

Librerie/adattatori sperimentali restano in archivio, scollegati dai flussi
attivi. Worker privato e D1 conservati: l'endpoint remoto non e' stato
eliminato, ma Igor non lo contatta. Nessuna nuova acquisizione o fonte
attivata. Cache e dati locali mantenuti: Room7 invariato, nessuna migrazione
o cancellazione degli acquisti o dei consensi barcode.

## Verifiche

`assembleDebug testDebugUnitTest --offline --console=plain`: build riuscita
in 4m2s. **374 test in 56 classi, zero failure/error/skipped**, conteggi XML.
Quattro test del confronto coprono voci senza prezzo/totali parziali,
fonti esclusivamente personali e date, ultimo prezzo e aggiornamento dopo
nuovo acquisto con quantita' in litri, simili personali e assenza di dati.
I vecchi test del caricamento della comunita' nella schermata Confronta sono
stati sostituiti per riflettere la rimozione della funzione.

Il primo giro ha rilevato la rimozione accidentale del callback alle
Impostazioni dell'inventario: callback ripristinato prima della build finale.
`git diff --check` passato. Riconoscimento Open Food Facts presente nei dex;
URL Cloudflare e chiave privata assenti da BuildConfig e dex.

## APK e telefono

`build/deliverables/igor-scontrini-barcode-debug-2026-10-07.apk`

SHA256: `bbc04eeb9b223a9f1758d77a2e5e8cc552703237eaf1329c882b7c0e10710029`.
Dimensione: 86.916.428 byte. Aggiornamento sul Samsung SM-A536B con
`adb install -r`: `Success`. Avvio `am start -W`: `Status: ok`.
Nessuna disinstallazione, nessun consenso modificato automaticamente.

Tentata ispezione nativa via UI Automator: Igor non risultava visibile nella
gerarchia al momento della verifica. Nessun blocco schermo o consenso e'
stato modificato per aggirare questo limite. Non e' quindi attestata una
verifica visiva di Confronta, tema scuro o font ingranditi sul telefono.
I test JVM verificano i calcoli, non sostituiscono una prova UI nativa.
