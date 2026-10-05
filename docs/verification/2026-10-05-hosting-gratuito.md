# Hosting gratuito per Igor

Ricerca del 5 ottobre 2026. Nessun account, servizio o costo attivato.
Questa e' una proposta: il backend attuale Python/FastAPI/SQLite non e'
ancora distribuibile su Cloudflare senza adattamenti.

## Proposta preferita: Cloudflare Workers + D1 + GitHub Actions

- API di lettura su Workers Free: 100.000 richieste/giorno, CPU 10 ms per
  richiesta, 128 MB RAM. Iscrizione Workers senza carta. Endpoint workers.dev
  per evitare l'acquisto di un dominio.
- Catalogo persistente su D1 Free: 5 GB complessivi nell'account,
  5 milioni di righe lette/giorno e 100.000 scritte/giorno. Il limite di
  singolo database e' 500 MB, distinto dai 5 GB complessivi dell'account.
  Gli indici consumano ulteriori scritture; un rinnovo completo del catalogo
  comprende cancellazioni, inserimenti e indici, non solo numero di prodotti.
  Al raggiungimento della quota Free le operazioni falliscono; nessun
  passaggio al piano a pagamento necessario per iniziare.
- Acquisizione Python giornaliera su GitHub Actions, separata dall'API:
  GitHub Free include 2.000 minuti/mese per repository privati (quota
  condivisa dall'account). Senza metodo di pagamento l'extra quota e'
  bloccata; con una carta gia' presente occorre un budget con blocco spesa.
  Un job da 10 minuti/giorno consumerebbe circa 300 minuti/mese:
  e' un esempio, non una misura del catalogo ancora non disponibile.
- La pianificazione Actions puo' subire ritardi o perdere un'esecuzione.
  I workflow pianificati devono essere sulla branch predefinita; quelli
  pubblici si disabilitano dopo 60 giorni senza attivita'. Non rendere
  pubblico il progetto per ottenere minuti aggiuntivi.

Adattamenti necessari: API Workers che conservi il contratto Android v1,
schema/ricerche D1 indicizzate, importazione autenticata con pubblicazione
atomica del lotto e gestione della sospensione fonti, workflow Python con
segreti e limiti di durata. Il crawling non va spostato nei Workers Free:
10 ms CPU non sono adatti all'attuale parsing Python dei cataloghi.

Per il primo catalogo limitato e l'uso personale questa e' la proposta piu'
interessante, come valutazione progettuale. Prima di confermarla misurare
dimensione catalogo, righe scritte incluse quelle degli indici, durata job e
CPU delle ricerche. Non promette hosting senza limiti o disponibilita' SLA.

Fonti ufficiali:

- [Workers: piano gratuito e iscrizione](https://www.cloudflare.com/products/workers/)
- [Limiti Workers](https://developers.cloudflare.com/workers/platform/limits/)
- [Sottodominio workers.dev](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/)
- [Prezzi e comportamento delle quote D1](https://developers.cloudflare.com/d1/platform/pricing/)
- [Limiti D1](https://developers.cloudflare.com/d1/platform/limits/)
- [Quote e fatturazione GitHub Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [Pianificazione GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)

## Alternative

**Oracle Cloud Always Free**: soluzione piu' vicina al backend attuale,
con VM Linux, disco persistente e cron sullo stesso server. La documentazione
attuale indica A1 fino a 2 OCPU/12 GB totali e 200 GB di volumi complessivi,
oppure VM Micro AMD da 1 GB. Disponibilita' soggetta a capacita' nella regione.
Richiede carta per verifica identita'; Oracle puo' recuperare le VM considerate
inattive su una finestra di sette giorni. Un servizio personale poco usato
potrebbe rientrare in questo caso. Richiede amministrazione Linux e backup;
non e' la prima proposta per affidabilita' di un servizio leggero.

- [Risorse Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
- [Registrazione e carta](https://www.oracle.com/cloud/free/faq/)

**Render Free + Neon Free + GitHub Actions**: mantiene l'API Python,
ma occorre migrare SQLite a PostgreSQL. Render sospende l'API dopo 15 minuti
di inattivita', con riavvio di circa un minuto e filesystem effimero.
Neon conserva il database: l'annuncio ufficiale del 2 ottobre 2026 indica
1 GB per progetto e 100 CU-ore/mese; verificare i limiti effettivi nel proprio
account. L'acquisizione deve girare separatamente in Actions. Possibile
alternativa, meno piacevole per le prime ricerche dopo inattivita'.

- [Render gratuito](https://render.com/docs/free)
- [Neon: aggiornamento piano Free del 2 ottobre 2026](https://neon.com/blog/neon-free-plan-1-gb-per-project)

L'hosting non risolve la validazione delle fonti prezzi: quelle restano
candidate e nessun catalogo reale e' ancora pubblicato.
