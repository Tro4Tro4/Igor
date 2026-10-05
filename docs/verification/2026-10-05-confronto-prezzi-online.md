# Confronto prezzi online — risultato locale

5 ottobre 2026. Stato: **integrazione locale preparata, confronto reale non operativo**.
CAP20125, prima acquisizione prevista Carrefour/Conad; Esselunga prima estensione,
seguita da Tigros, Lidl ed Eurospin. Nessun prezzo stimato o fixture pubblicata.

## Implementazione e verifiche

- Servizio Python3.12: contratto v1, centesimi/Decimal, GTIN verificati, formati,
  parser provvisori, accessi/robots, limiti/retry, archivio atomico e API read-only.
  Venv locale e dipendenze fissate. `python -m pytest -q`: **81 test verdi**.
  Warning upstream Starlette: deprecazione dell'uso di httpx nel TestClient.
- Android: cache Room7, migrazione6→7 non distruttiva e test su schema6 reale
  con inventario, shopping, prezzi e barcode; export cache/associazioni/fonti;
  repository con consenso/CAP/fingerprint, equivalenze GTIN+formato;
  calcoli confezioni e insieme comune, sezione Confronta e impostazioni,
  aggiornamento WorkManager24h con rete/consenso/endpoint.
- `gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain`:
  **375 test, 56 classi, zero failure/error**, build riuscita in4m29s dopo fix.
  Log «incomplete input» dei decoder generati da test di immagini corrotte,
  senza test falliti. Nessun test HTML usato come prova Compose.
- API locale realmente avviata su127.0.0.1:8765: schema1, sei fonti candidate,
  zero fonti attive; ricerca latte per20125 restituisce catalogo vuoto corretto.
  Processo temporaneo arrestato dopo prova; non costituisce hosting.
- APK installato con `install -r` sul Samsung SM-A536B **RZCW10JE18H**, Success.
  Processo Igor avviato; nessun AndroidRuntime crash rilevato. Telefono bloccato:
  non e' verificata visivamente la conservazione delle singole voci reali.
- Verifica nativa sull'emulatore della sessione **Pixel_Fold_API_35**:
  un iniziale blocco SystemUI superato, notifiche rifiutate solo nell'emulatore.
  Impostazioni con consenso spento e20125, chiaro/scuro/font1.3; testi e controlli
  nuovi leggibili, scorrimento disponibile, nessun taglio del campo CAP.
  Tema/font ripristinati a night=no e1.0. Voce «Latte» creata solo nell'emulatore.
  Confronta verificato con consenso spento, poi attivo senza endpoint: stato
  «servizio prezzi non disponibile», nessun totale0 o candidato fittizio.
  Collegamento Impostazioni dal confronto apre le impostazioni generali corrette.
  Screenshot reali in `assets/2026-10-05-online-prices/`; scelta display Fold
  documentata dalle dimensioni2208x1840. TalkBack non eseguito.
  Telefono fisico: nessuna modifica a font/tema/consenso, solo installazione e avvio.

## Revisione indipendente

Un reviewer fresh gpt-6-astra ha esaminato backend e Android, senza modificare
checkout o eseguire rete/Gradle. Nessun Critical, otto Important corretti in
un'unica passata, con regressioni osservate rosse prima delle correzioni:

1. Sospensione fonte conservata alla ricarica manifest.
2. Stato fonte Android aggiornato anche quando le offerte non sono disponibili.
3. Robots wildcard/* e ancora/$ rispettati con precedenza Allow specifica.
4. Confronto PZ/CONF escluso dai totali comuni se il formato differisce fra fonti.
5. Candidati legati al fingerprint della ricerca, verificato anche nella selezione.
6. Ricerca per termini nome/marca senza obbligo di contiguita'.
7. Prezzo HTML discordante rifiutato nei selettori provvisori; validazione reale
   dei selettori ancora necessaria prima di attivare una fonte.
8. Binding fra fonti aggiunto solo per identita' GTIN e formato coerenti,
   senza sovrascrivere scelte gia' fatte per altre fonti.

## Decisioni e relativo costo se errate

- Checkout feature esistente e staging selettivo: preservare barcode gia' presenti;
  costo: isolamento affidato allo staging. Nessun reset/stash o file barcode nei commit.
- Ledger PowerShell al posto degli script bash: ambienteWindows;
  costo: progressione registrata manualmente.
- Un commit backend per task2–4 dopo prove distinte: costo review range piu' ampio.
- URL debug vuoto finche' manca endpoint: niente rete accidentale;
  costo: aggiornamento telefono non rende operativo il confronto quotidiano.
- Nessun barcode dedotto da nome globale della voce generica: evita conflazione;
  costo: prima scelta manuale, poi equivalenze GTIN verificate.
- Parser HTML provvisori fino a campioni validati: fixture non certifica siti;
  costo: adattatori non pronti per acquisizione reale.
- Siti reali restano candidate: mancano riutilizzo/campioni/territorio;
  costo: nessun listino funzionante.
- Solo localhost e opzioni hosting, nessuna pubblicazione: requisito esterno assente;
  costo: nessun servizio autonomo fuori dalla prova locale.
- Ledger/workspace conservati per ripresa del piano incompleto: costo scratch
  locale da pulire alla chiusura vera. Nessun merge, push o pubblicazione.

Esclusioni della review: barcode precedente fuori scope; siti reali non verificati
con rete dal reviewer; UI verificata successivamente sul Fold, non sul telefono
bloccato; hosting assente. Non dichiarare questi requisiti completi.

## Minor rimandati dalla review

- Refresh concorrenti serializzati, non deduplicati: possibili richieste duplicate.
- Paginazione candidati Android non esposta: selezionabili solo i primi20 risultati.
- Disponibilita' e limitazioni dettagliate fonte non mostrate nelle righe;
  restano data/scope/condizioni/link, da completare prima di fonti reali.
- Test worker dell'helper, non scheduling reale; parte test quote a fonte unica
  poco discriminante. Copertura dell'integrazione WorkManager incompleta.

## Requisiti esterni ancora incompleti

Tutte le fonti nel manifest restano candidate. Mancano dieci campioni per fonte,
due letture complete, riutilizzo e territorio verificati; Esselunga restituisce
la shell anche sulla scheda dalla sitemap. Nessuna sorgente attiva.
Non e' quindi stato verificato un confronto reale di almeno due catene.

Manca endpoint HTTPS autonomo. [Opzioni hosting](2026-10-05-hosting-prezzi.md),
[Conad](2026-10-05-fonte-conad-20125.md), [Esselunga](2026-10-05-fonte-esselunga-20125.md),
[fonti aggiuntive](2026-10-05-fonti-aggiuntive.md). Nessun costo, account o contatto
commerciale attivato. Il risultato resta incompleto rispetto all'obiettivo
«prezzi reali utilizzabili quotidianamente dal telefono».
