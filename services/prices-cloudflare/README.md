# Igor prices on Cloudflare Free

API Workers di sola lettura, archivio D1 persistente e acquisizione Python
giornaliera su GitHub Actions. Il servizio locale FastAPI resta disponibile.
Il codice e' preparato; nessun account o catalogo reale e' attivato dalla build.
Tutte le sei catene rimangono candidate fino alla validazione delle fonti.

## Stato remoto — 6 ottobre 2026

API pubblicata e verificata su https://igor-prices.andreatro.workers.dev.
Migrazioni D1 e inizializzazione metadata riuscite: sei fonti candidate,
zero offerte. Test CI e smoke HTTPS 200/404/422/405 passati.
`IGOR_PRICES_DAILY_ENABLED=false`: acquisizione giornaliera spenta.
APK sul telefono ancora senza URL; fonti reali e quote account da verificare.
Rapporto: [prima pubblicazione](../../docs/verification/2026-10-06-deploy-cloudflare.md).

## Sviluppo e test

Node 24 e Python 3.12. Le dipendenze Node sono fissate in package-lock.json.
Da questa directory:

```powershell
npm ci --no-audit --no-fund
npm run typecheck
npm test
npm run test:config
npm run dry-run
```

Il runtime D1 dei test e' locale. Fixture e lotti sintetici sono esclusi dai
comandi di pubblicazione. Config locale non contiene un database remoto.
I payload monetari vengono inoltrati come JSON senza conversione JS Number.
I dati dell'inventario non vengono caricati sul server.

## Collegare l'account gratuito

1. Accedere al proprio account Cloudflare Free. Dal workspace con Node
   disponibile eseguire `npx --no-install wrangler login` in questa directory;
   autenticarsi nel browser. Non inviare token in chat.
2. Creare D1 con `npx --no-install wrangler d1 create igor-prices`.
   Annotare database_id e account_id nell'account. Nessun dominio da comprare:
   il Worker usa il sottodominio gratuito workers.dev.
3. Nel repository GitHub **Tro4Tro4/Igor**, Settings > Secrets and variables >
   Actions, configurare le variabili `CF_ACCOUNT_ID`, `CF_DATABASE_ID` e
   `IGOR_PRICES_DAILY_ENABLED=false`.
4. Creare token Cloudflare limitati al proprio account, senza chiave globale.
   Il segreto `CF_DEPLOY_API_TOKEN` richiede Workers Scripts Edit, D1 Edit per
   applicare migrazioni e le letture account/subdomain richieste da Wrangler.
   `CF_D1_API_TOKEN` richiede D1 Edit e viene usato solo dal publisher.
   Inserirli direttamente in GitHub Secrets, senza file, chat o argomenti CLI.
   Verificare i permessi correnti nel pannello Cloudflare, non espanderli
   indiscriminatamente in caso di errore.
5. I file devono essere disponibili nella branch selezionata per il deploy.
   Avviare manualmente **Prices deploy**: test, migrazioni, inizializzazione
   metadata candidate e distribuzione API. Nessuna fixture viene pubblicata.
6. Verificare via HTTPS `/v1/sources`, ricerca `q=latte&postcode=20125`,
   422 per CAP invalido, 404 per prodotto assente e 405 per POST.
   Sei candidate e zero offerte sono il risultato iniziale previsto.
7. Verificare piano Free, quote condivise GitHub e budget che blocchi uso
   extra se una carta e' gia' presente. Non attivare un piano Paid.
   Impostare poi `IGOR_PRICES_DAILY_ENABLED=true` e provare **Prices daily**
   manualmente. Con fonti candidate non effettua richieste alle catene.
8. La pianificazione 04:17 UTC richiede il workflow sulla branch predefinita.
   Un eventuale merge viene revisionato separatamente. GitHub puo' ritardare
   o perdere esecuzioni; la cache Android mantiene i limiti di anzianita'.
   Nel repository pubblico i workflow pianificati si disabilitano dopo
   60 giorni senza attivita': controllare Actions e riabilitarli quando serve.

La pubblicazione locale alternativa usa `npm run configure` con
CF_ACCOUNT_ID/CF_DATABASE_ID impostati nell'ambiente, poi migrazioni e deploy
su `wrangler.deploy.jsonc`. Il file generato e' ignorato da Git.
Il publisher legge il token solo da CF_D1_API_TOKEN nell'ambiente.

## Integrita' e quote

Lotti immutabili caricati a chunk di 20 offerte; lo switch corrente e'
transazionale tramite trigger D1. Retry idempotenti, limite complessivo
2.000 offerte, conservazione del lotto corrente e di un lotto precedente,
cleanup dopo almeno 24 ore. Limite noto: dopo il riuso di un vecchio lotto,
il cleanup puo' conservare un precedente diverso da quello immediatamente
precedente; il catalogo corrente rimane integro. Dettagli nel rapporto
`docs/verification/2026-10-05-hosting-cloudflare.md`.
Errori mantengono il lotto precedente; 403 sospende la fonte e il manifest
non la riattiva. Prenotazione D1 di una sola acquisizione per fonte/giorno UTC.

Limiti Free da verificare nel proprio account: Workers100.000 richieste/giorno,
D1 500 MB/database, 5 milioni righe lette e100.000 scritte/giorno incluse quelle
degli indici; GitHub Free2.000 minuti/mese privati condivisi con altre Actions.
Il repository Tro4Tro4/Igor risulta gia' pubblico e la branch predefinita e'
claude/android-fridge-monitor-app-vlbci3: per questo repository i runner
standard GitHub-hosted sono gratuiti, con la limitazione dei 60 giorni sopra.
La visibilita' non e' stata modificata e non vengono usati runner maggiorati.
Il limite catalogo non garantisce le quote complessive dell'account, la durata
di ogni acquisizione o una disponibilita' SLA. Nessun upgrade automatico.

Observability Workers disattivata nella config, nessun console.log con query.
I log infrastrutturali Cloudflare e l'accesso amministrativo rimangono soggetti
alle impostazioni dell'account. Non usare live tail per registrare ricerche.

Dopo un deploy HTTPS verificato, compilare Android con la proprieta'
`igorOnlinePricesUrl` impostata all'URL reale. L'APK predefinita senza URL
non attiva il servizio; gli accessi dell'utente rimangono facoltativi.
