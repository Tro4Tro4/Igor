# Hosting gratuito del servizio prezzi Igor

5 ottobre 2026. Progetto proposto per la soluzione scelta dall'utente:
Cloudflare Workers + D1 + GitHub Actions. App Android esistente, CAP iniziale
20125, acquisizione Carrefour/Conad e prima estensione Esselunga.

## Obiettivo e autorizzazione

Rendere raggiungibile l'API prezzi via HTTPS e conservare il catalogo fuori
dal telefono, aggiornandolo ogni giorno senza un server sempre acceso.
L'utente ha scelto il piano gratuito. Non acquistare domini, attivare piani
a pagamento, rendere pubblico il repository o modificare la fatturazione.

Questa scelta autorizza la preparazione del servizio e della distribuzione.
L'accesso all'account Cloudflare e ai segreti GitHub va configurato nel conto
dell'utente; non inserire token nella conversazione, nei file o nei log.
La pubblicazione effettiva richiede tali accessi. Non inventare identificativi
di account/database o trattare l'assenza di credenziali come una prova riuscita.

Il confronto reale richiede separatamente fonti validate. Le sei fonti
candidate restano candidate: pubblicare un'API vuota non attiva i listini.
Il lavoro sui barcode non committato rimane preservato.

## Scelta architetturale

Alternative considerate: Oracle Always Free conserva Python/SQLite ma puo'
recuperare VM poco utilizzate; Render Free con Neon conserva Python ma
introduce PostgreSQL e un risveglio lento dell'API. L'utente ha scelto Workers
e D1, accettando l'adattamento del servizio evidenziato nella ricerca hosting.

Tre componenti con responsabilita' distinte:

1. **Worker TypeScript**: sola lettura, espone il contratto v1 Android via
   HTTPS sul sottodominio gratuito workers.dev. Nessun parsing HTML,
   acquisizione su richiesta o dato dell'inventario sul server.
2. **D1**: fonti, lotti immutabili delle offerte, puntatore al lotto pubblicato
   per fonte, versione globale del catalogo e registro delle acquisizioni.
3. **Job Python su GitHub Actions**: riutilizza parser, regole accesso,
   normalizzazione e modelli del servizio esistente. Legge lo stato operativo
   delle fonti da D1, acquisisce solo fonti attive e pubblica i lotti validi.

Il servizio FastAPI/SQLite locale resta disponibile per sviluppo e test.
Nuovi componenti in `services/prices-cloudflare/`; estensioni di pubblicazione
Python in `services/prices/igor_prices/`; workflow dedicati sotto `.github`.
Nessun ridisegno della UI o nuova migrazione Room.

## API e compatibilita'

Conservare esattamente campi, tipi e significato dell'envelope:
`schema_version: 1`, `catalog_version` intero, `generated_at` UTC,
`postcode`, `items`, `next_offset`.

- `GET /v1/sources`: tutte le fonti ordinate per priorita', stato operativo e
  ultima acquisizione riuscita. Carrefour/Conad prima coppia; Esselunga prima
  estensione. Metadata compatibili con il client Android corrente.
- `GET /v1/products`: nome con termini indipendenti oppure GTIN verificato,
  esclusione reciproca, CAP ASCII di cinque cifre, limite 1..20 e offset
  non negativo. Stessi vincoli e ordinamento dell'API locale.
- `GET /v1/products/{product_id}/offers`: solo fonti attive e territorio
  compatibile; 404 se assente, 422 per input non valido, 503 per errore D1.

Prezzi in centesimi rimangono interi JSON senza conversione in floating point
JavaScript: conservare il payload validato come JSON testuale e assemblare
gli envelope senza riparsare i campi monetari in Number. Decimal resta stringa.
La versione catalogo deve restare nell'intervallo sicuro degli interi JS.
Non associare SKU a GTIN senza verifica.

SQL parametrico. Colonna nome+marca normalizzata dal publisher Python;
ricerca con tutti i termini e wildcard utente trattate come testo, come nel
servizio locale. Indicizzare fonte/lotto, product_id e GTIN. Il catalogo pilota
limitato rende misurabile la scansione dei nomi; non introdurre un motore di
ricerca esterno o promettere un catalogo nazionale entro le quote gratuite.

Ogni risposta legge versione e dati nello stesso batch D1 transazionale per
evitare envelope e righe appartenenti a pubblicazioni diverse. Niente cache
HTTP del catalogo nella prima versione: una sospensione fonte deve essere
visibile immediatamente. Disabilitare log applicativi con query di ricerca;
verificare e documentare le impostazioni di logging della piattaforma.

## Pubblicazione atomica e stato delle fonti

Schema D1 dedicato, versionato con migrazioni SQL:

- `sources`: id, priorita', metadata JSON, stato operativo, lotto corrente,
  ultima acquisizione riuscita e digest.
- `offer_batches`: id del lotto, fonte, digest, numero offerte, stato staging
  o pubblicato e date UTC.
- `offers`: lotto, chiavi fonte/SKU/territorio, offer_id, product_id, GTIN
  canonico verificato, nome normalizzato, payload JSON validato. Unicita'
  delle chiavi nel lotto e controlli fonte/territorio prima della scrittura.
- `catalog`: una riga versione; `runs`: fonte/giorno UTC, stato, timestamp.

Il publisher usa l'API REST D1 autenticata: nessun endpoint pubblico di
scrittura nel Worker e nessun token nell'APK. Credenziali Cloudflare limitate
all'account e alle operazioni necessarie; separare il token deploy Workers
dal token D1 del job dove i permessi Cloudflare lo consentono.

Flusso giornaliero:

1. Sincronizzare il manifest mantenendo una sospensione operativa gia'
   presente. Una configurazione `active` non riattiva una fonte sospesa.
2. Registrare la prenotazione fonte/giorno su D1 con vincolo univoco prima
   dell'acquisizione, oltre alla concurrency del workflow. Una seconda
   esecuzione nello stesso giorno non scarica nuovamente la fonte.
3. Avviare l'acquisizione esistente in SQLite temporaneo, senza assumere che
   il filesystem del runner sopravviva al job. Lo stato remoto sospeso ha
   precedenza sul manifest e viene applicato prima della chiamata ingest.
4. Per ogni fonte riuscita validare tutto il lotto con Pydantic, calcolare
   digest e caricare righe di staging in piccoli batch SQL parametrizzati.
   Se il lotto e' gia' pubblicato non duplicarlo; i retry delle scritture sono
   idempotenti. Un lotto vuoto o con chiavi duplicate non viene pubblicato.
5. Un batch D1 finale verifica completezza e fonte attiva, cambia il
   puntatore, aggiorna data/digest, marca il lotto pubblicato e incrementa
   versione. Qualsiasi errore annulla l'intero passaggio finale.
6. Errori rete/parser mantengono il lotto precedente. Un 403 pubblica lo
   stato sospeso indipendentemente dal successo del lotto; se D1 fallisce,
   il job fallisce e segnala che lo stato remoto non e' stato aggiornato.
7. Conservare il lotto precedente; eliminare staging abbandonati e lotti
   piu' vecchi solo dopo una finestra di almeno 24 ore, mai quello corrente.
   Cleanup limitato e parametrico, incluso nel budget delle scritture.

Il manifest candidate puo' inizializzare sei fonti senza avviare scraping.
La riattivazione resta un'operazione amministrativa esplicita con nuove
evidenze; non aggiungere un pannello o un endpoint pubblico per farlo.

## Quote gratuite e pianificazione

Limiti documentati: Workers Free 100.000 richieste/giorno e CPU 10 ms per
richiesta; D1 Free 500 MB per database, 5 milioni di righe lette/giorno e
100.000 scritte/giorno, incluse modifiche agli indici. GitHub Free comprende
2.000 minuti/mese per repository privati, condivisi con gli altri workflow.

Prima versione: catalogo pilota massimo 2.000 offerte complessive correnti,
20 offerte per batch di upload e massimo 30 minuti per il job giornaliero.
Rifiutare un upload oltre il limite prima di iniziare le scritture.
Conservazione di due lotti e cleanup entrano nella misura delle quote.
I limiti locali sono conservativi, non una garanzia rispetto all'utilizzo
complessivo dell'account o alle richieste pubbliche di terzi.

Pianificazione una volta al giorno alle 04:17 UTC e avvio manuale di recupero,
con concurrency unica che non cancella un'importazione in corso. GitHub
puo' ritardare o perdere un'esecuzione: Android continua a usare le regole
esistenti per prezzi vecchi e cache, senza presentare i dati come attuali.
Il workflow pianificato deve essere sulla branch predefinita. Non pubblicare
o cambiare visibilita' del repository per ottenere ulteriori minuti gratuiti.

La prima versione non esegue cron di acquisizione dentro Workers Free.
Workflow deployment manuale, separato dall'importazione quotidiana; nessun
deploy automatico a ogni push. Permessi GitHub minimi, actions fissate a
revisioni verificate, dipendenze bloccate e assenza di segreti nei log.

Se l'account GitHub ha una carta, verificare un budget che blocchi l'extra
uso prima di attivare la schedule; senza carta l'extra quota e' bloccata.
Non cambiare automaticamente il budget dell'utente. Superamento quota
Cloudflare Free produce indisponibilita', non attivazione del piano Paid.

## Configurazione, accessi e pubblicazione

Configurazione Wrangler con `DB` binding, compatibilita' fissata, nessun
dominio a pagamento. Identificativi reali forniti tramite configurazione
di deploy validata: comandi falliscono chiaramente se mancanti.

Segreti GitHub: token Cloudflare deploy e publisher; variabili account ID e
database ID. I test locali non richiedono tali segreti. Esplorazione iniziale:
nessuna variabile di ambiente Cloudflare trovata; nessun connettore Cloudflare
disponibile tra gli strumenti esposti; repository origin Tro4Tro4/Igor.
Node/npm non risultano sul PATH, quindi il piano dovra' predisporre un runtime
di sviluppo locale senza installazione globale non necessaria.

Preparare prima tutti gli artefatti verificabili. Successivamente configurare
l'account gratuito dell'utente, creare il database, applicare schema e
manifest candidate e pubblicare il Worker. Verificare via HTTPS fonti,
catalogo vuoto, errori e assenza di endpoint di scrittura. Solo dopo usare
l'URL reale nella build Android, installare l'APK preservando i dati.

Senza credenziali fermarsi alla distribuzione pronta e indicare esattamente
gli accessi mancanti. Non dichiarare concluso l'hosting remoto. Con fonti
ancora candidate, dichiarare distinta la disponibilita' dell'API dalla
disponibilita' di prezzi reali.

## Verifiche e criteri di accettazione

- Test di contratto condivisi fra API locale e Worker: envelope v1,
  ricerca nome/GTIN, CAP, offset, 404/422/503, tutte le sei candidate,
  fonti sospese escluse, prezzi oltre Number.MAX_SAFE_INTEGER preservati.
- Test su D1 locale nel runtime Workers: migrazioni, unicita', batch
  transazionale, rollback, risposta coerente durante pubblicazione,
  cleanup che non elimina lotti correnti.
- Test publisher Python: interruzione fra chunk senza dati parziali
  visibili, retry idempotente, digest, prenotazione giornaliera, sospensione
  persistente, manifest che non riattiva fonti e limite catalogo.
- Misurare query e scritture su fixture pilota a 2.000 offerte chiaramente
  sintetiche, mai pubblicate in produzione. Documentare il margine delle
  quote; errore o limite runtime richiede correzione, non upgrade a pagamento.
- Eseguire suite Python esistente e nuove suite Worker/publisher; verificare
  workflow, compilazione TypeScript e dry-run della distribuzione.
- Dopo deploy reale: smoke HTTPS, job manuale candidate senza scraping,
  impostazioni piano Free e assenza di dati/segreti nei log.
- Solo dopo modifica dell'URL Android: build/test appropriati, installazione
  sul telefono, API raggiungibile fuori dal collegamento USB. Nessuna prova
  reale prezzi positiva finche' non esistono fonti attive validate.

## Riferimenti ufficiali

- [Workers Free](https://developers.cloudflare.com/workers/platform/limits/)
- [Quote D1](https://developers.cloudflare.com/d1/platform/pricing/)
- [Limiti D1](https://developers.cloudflare.com/d1/platform/limits/)
- [Atomicita' batch D1](https://developers.cloudflare.com/d1/worker-api/d1-database/)
- [Deploy da CI](https://developers.cloudflare.com/workers/ci-cd/external-cicd/)
- [GitHub Actions e fatturazione](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [GitHub Actions schedule](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)

Decisioni e vincoli completati nella spec; implementazione e piano esecutivo
attendono la revisione scritta prevista da brainstorming.
