# Cloudflare — prima esecuzione remota

6 ottobre 2026. Push dei commit preparati e deploy autorizzati dall'utente.
Account Cloudflare e D1 creati dall'utente; database `igor-prices` con scelta
di giurisdizione EU nella procedura guidata. Secrets inseriti direttamente
dall'utente con GitHub CLI; verificati solo i nomi, nessun token letto.

## Configurazione verificata

- Repository `Tro4Tro4/Igor`, branch `claude/android-fridge-monitor-app-vlbci3`.
- `CF_ACCOUNT_ID` e `CF_DATABASE_ID` impostati e letti dal workflow.
- Secrets `CF_DEPLOY_API_TOKEN` e `CF_D1_API_TOKEN` presenti.
- `IGOR_PRICES_DAILY_ENABLED=false`: acquisizione giornaliera disattivata.
- Modifiche Android/Open Food Facts non committate preservate ed escluse dal push.

## Esecuzioni

1. [Prima esecuzione](https://github.com/Tro4Tro4/Igor/actions/runs/37446984623):
   test e dry-run passati; migrazione remota fallita con `incomplete input`.
2. Correzione sintattica delle tre espressioni CASE nei trigger: parentesi
   esterne, senza cambiare condizioni o regole di pubblicazione. Compatibile
   con il problema documentato in
   [workers-sdk #4727](https://github.com/cloudflare/workers-sdk/issues/4727).
   Il parser Wrangler locale accettava anche la versione originale: il test
   locale non riproduce il problema del parser dell'API remota.
3. Verifica locale: 4 controlli Node e 25 test D1 passati. Il pilot sintetico
   aveva superato il timeout di 5 secondi sul PC; nuova esecuzione con
   `--testTimeout=20000` riuscita, senza cambiare il timeout della CI.
4. [Seconda esecuzione](https://github.com/Tro4Tro4/Igor/actions/runs/37447704991):
   test Python/Workers/Node, typecheck e dry-run passati; migrazione D1
   remota riuscita; inizializzazione metadata riuscita. Pubblicazione API
   interrotta per assenza del sottodominio workers.dev dell'account.

## Passaggio necessario

Registrare il sottodominio gratuito tramite l'onboarding Workers indicato
dal log Cloudflare. Poi rieseguire Prices deploy e verificare HTTPS,
sei fonti candidate, catalogo vuoto e risposte 404/422/405.

Nessun endpoint pubblicato o APK aggiornato in questa fase. Nessuna fonte
attivata, nessun catalogo sintetico pubblicato, nessuna acquisizione delle
catene. Piano, quote e impostazioni di fatturazione dell'account non sono
verificabili dai soli log del workflow; nessun upgrade richiesto dal codice.
