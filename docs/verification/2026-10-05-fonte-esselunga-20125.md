# Prima verifica Esselunga — Milano 20125

Data: 5 ottobre 2026. Priorita' indicata dall'utente: prima estensione dopo
Carrefour e Conad, perche' Esselunga e' la catena piu' usata.
Esito: **fonte candidata, catalogo prezzi non ancora validato**.

Richieste anonime dal computer, User-Agent Igor esplicito, senza account:

- [Spesa online](https://spesaonline.esselunga.it/commerce/nav/supermercato/):
  HTTP 200, 6962 caratteri HTML, titolo Esselunga Spesa Online, nessun JSON-LD;
  carica Angular 1.8.2 e `/ng-app/shell.min.js`. La risposta iniziale e' una
  shell, non un catalogo leggibile di prodotti e prezzi. Non e' dimostrato che
  il catalogo richieda un account: serve una verifica nel browser ordinario.
- [Robots.txt](https://spesaonline.esselunga.it/robots.txt): HTTP 200;
  per User-agent * esclude percorsi di account, autenticazione, ordini,
  ricerca, visite e parametri freevisit/freeVisit/state. Indica
  `https://spesaonline.esselunga.it/sitemap_index.xml`. Questi percorsi esclusi
  non sono stati interrogati. Robots non e' una licenza dei dati.
- [Offerte volantino](https://www.esselunga.it/it-it/promo-e-news/landing/offerte-volantino.html):
  HTTP 200, pagina pubblica che richiede la scelta del negozio. La disponibilita'
  di offerte non prova un listino completo o una fonte valida per 20125.

Prossimi controlli nell'esecuzione: catalogo anonimo con browser ordinario,
contesto 20125, dieci prodotti con prezzi/formati/condizioni, stabilita' di due
acquisizioni e modalita' di riutilizzo. Se serve autenticazione o un accesso
non riutilizzabile, cercare un feed compatibile; niente bypass o credenziali
personali negli adattatori. La fonte resta visibile come non disponibile
finche' queste verifiche non passano.
