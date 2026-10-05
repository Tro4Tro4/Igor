# Hosting gratuito Cloudflare — verifica locale

5 ottobre 2026. Codice e distribuzione preparati. **Servizio remoto non ancora
pubblicato:** Wrangler non risulta autenticato a Cloudflare. GitHub CLI e'
autenticato come Tro4Tro4 quando eseguito con accesso al keyring autorizzato.
Nessun account, piano a pagamento, database remoto o dominio creato.
Repository verificato in sola lettura: Tro4Tro4/Igor e' gia' pubblico,
branch predefinita claude/android-fridge-monitor-app-vlbci3. La visibilita'
non e' stata cambiata. Runner standard gratuiti per questo repository;
schedule pubblica soggetta a disabilitazione dopo 60 giorni senza attivita'.

## Implementazione

- API TypeScript read-only `/v1/sources`, `/v1/products`, offerte per prodotto;
  contratto v1 condiviso con FastAPI, centesimi Long non arrotondati, CAP,
  GTIN verificato, paginazione, fonti sospese escluse e risposte 404/422/503.
- Schema D1 con lotti immutabili, guardia di completezza e switch atomico;
  registrazione giornaliera persistente, limite globale 2.000 offerte.
- Publisher Python via REST D1, retry delle scritture idempotenti, verifica
  dei payload staging, conservazione dei lotti precedenti e cleanup limitato.
- Job giornaliero con SQLite temporaneo e stato remoto: non acquisisce fonti
  candidate, mantiene sospensioni fra runner e segnala errori senza pubblicare
  listini parziali. Corretto anche il caso di 403 sul robots.txt del servizio
  esistente con regressione osservata rossa e poi verde.
- Workflow GitHub di verifica, deploy manuale e job 04:17 UTC. Azioni fissate
  agli SHA ufficiali; secrets limitati agli step necessari; schedule protetta
  da variabile inizialmente non abilitata.

## Evidenze

- Python: **109 test verdi**, inclusi i test esistenti. Rimane il warning
  upstream Starlette sull'uso di httpx nel TestClient.
- Workers nel runtime locale Cloudflare: **25 test verdi**; typecheck riuscito.
- Configurazione deploy: **3 test Node verdi**, inclusi accessi mancanti e
  assenza di token nel file generato.
- Wrangler deploy `--dry-run`: riuscito, bundle 9.62 KiB, gzip 3.32 KiB,
  binding locale DB. Non costituisce una pubblicazione HTTPS.
- Tre workflow YAML letti con parser YAML, trigger riconosciuti correttamente.
- Test di integrazione riproduce il SQL del publisher Python su D1 locale:
  staging parziale invisibile, switch incompleto rifiutato, retry senza doppia
  versione, rollback di batch fallito e sospensione immediata.

## Pilot sintetico

Script `npm run pilot`, completamente locale e senza lettura di credenziali.
2.000 offerte, tre generazioni, cleanup della prima e ultimi due lotti conservati:

| Misura | Risultato |
|---|---:|
| Righe scritte complessive, incluse quelle degli indici | 32.046 |
| Righe lette dalle operazioni di preparazione/upload | 36.034 |
| Dimensione massima osservata | 4.362.240 byte, circa 4,16 MiB |
| Prima ricerca, tempo totale locale | circa 102 ms |
| Altre ricerche, tempo totale locale | circa 13–28 ms |

Questi tempi comprendono il runtime locale e SQL: **non certificano il limite
CPU Workers Free di 10 ms**. I conteggi non comprendono altre applicazioni
nell'account o traffico esterno. Il pilot non dimostra durata o disponibilita'
dell'acquisizione dei cataloghi reali, tuttora non validati.

## Decisioni registrate

- Checkout feature esistente e staging selettivo, per conservare il lavoro
  barcode non committato; costo: isolamento manuale.
- Ledger PowerShell al posto degli script bash della skill; costo: tracking
  manuale delle prove.
- Plugin ufficiale `@cloudflare/vitest-plugin` 1.3.6 con Vitest 4.1.11,
  in sostituzione del vecchio nome pool nel piano; costo: configurazione
  nuova, verificata nel runtime locale.
- Test D1 ripulito esplicitamente nel binding locale: il plugin corrente
  condivide storage fra test; costo: test sequenziali nello stesso binding.
- Ricerca con instr invece di LIKE: preserva termini letterali e query di
  100 caratteri nonostante il limite D1 del pattern LIKE; costo: scansione
  dei nomi, misurata sul pilot limitato.
- observed_at del batch separato da created_at: retry e lotti riutilizzati
  conservano il timestamp corretto; costo: una colonna aggiuntiva.
- Mapping casefold generato da Python 3.12 per query compatibili con i nomi
  normalizzati dal publisher; costo: tabella generata e possibile differenza
  futura fra versioni Unicode dei runtime.
- Config/log/cache Wrangler locali nel workspace durante le prove, dopo
  accesso negato al profilo: nessuna modifica delle credenziali utente.

## Passaggi remoti ancora necessari

Accedere all'account Cloudflare Free dell'utente, creare D1 e configurare
identificativi e secrets GitHub seguendo
[`services/prices-cloudflare/README.md`](../../services/prices-cloudflare/README.md).
Poi migrazioni, deploy, smoke HTTPS e job manuale; verificare quote/budget
prima di attivare la pianificazione sulla branch predefinita.

Solo dopo un URL reale verificato aggiornare l'APK sul telefono. Non e'
stata reinstallata l'app durante questo lavoro di hosting: il suo URL resta
vuoto. Tutte le fonti restano candidate; Esselunga mantiene la priorita'
di prima estensione dopo Carrefour e Conad.

## Revisione indipendente finale

Revisore fresh-context su `7ea1843..0b188ed`, sola lettura: nessun Critical
 o Important individuato. La gravita' e' stata rivalutata in base all'effetto
sull'utente; nessun fix funzionale necessario. Nessuna seconda revisione.

**Minor rinviato:** dopo A → B → C → A riutilizzato → D, il cleanup puo'
conservare C e D anziche' A e D, perche' ordina published_at che nel riuso
mantiene la vecchia data di osservazione. D resta corretto e disponibile;
si perde il lotto per ripristinare lo stato immediatamente precedente.
Riproduzione del revisore con Publisher e SqlD1 reali, SQLite in memoria.
Il rinvio segue la regola della skill executing-plans per i finding Minor.

### Ambiti non certificati: decisioni e costi

- Accesso e permessi Cloudflare: non dichiarati validi finche' l'utente
  non accede e si verificano gli scope; costo: deploy ancora in attesa.
- D1, migrazioni, HTTPS e smoke remoti: task 8 incompleto; costo: il servizio
  non e' disponibile all'app prima della verifica remota.
- GitHub Actions reali, secrets e schedule: preparati ma non eseguiti;
  costo: errori di configurazione emergono solo alla prima esecuzione.
- Piano Free, budget, quote condivise e log dell'account: da verificare
  nell'account prima dell'attivazione; costo: nessuna garanzia operativa
  di gratuitita' certificata dal solo codice.
- CPU remota, traffico ostile, quote effettive e guasti distribuiti:
  il pilot sintetico locale non li dimostra; costo: possibili limiti
  operativi da misurare dopo il deploy.
- Listini reali e riuso dei dati: fonti candidate fino alla validazione;
  costo: inizialmente zero offerte, nessun confronto prezzi effettivo.
- Android con URL reale, consenso e telefono fuori USB: task 9 non avviato;
  costo: il telefono conserva la configurazione precedente.
- OFF estraneo al range, audit completo dipendenze e parser preesistenti:
  preservati senza dichiararli certificati da questa revisione;
  costo: questa verifica non copre quei rischi preesistenti.

Ulteriore decisione tecnica: pilot Miniflare v5 tramite conversione delle
opzioni v4; costo: dipendenza di sviluppo transitiva bloccata dal lockfile,
non runtime del Worker pubblicato. Workspace e ledger conservati per
riprendere i task remoti; nessun push, merge o pubblicazione eseguiti.
