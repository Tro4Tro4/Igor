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

## Seconda verifica

La sitemap_index risponde HTTP 200 e indica sitemap_product, listing_page,
landing_page e page. La sitemap prodotti supera 2 MB; lettura limitata,
senza conservare l'intero catalogo. Primo URL consentito individuato:
`/commerce/nav/supermercato/store/prodotto/553743/langhe-bianco-ca-rossa-75-cl`.
Due letture di questa scheda restituiscono entrambe HTTP 200, 6962 caratteri
e nessun JSON-LD: la stessa shell della home, senza prezzo estraibile.
Nessun endpoint escluso o parametro freevisit interrogato.
Il percorso ipotizzato /it-it/condizioni-generali.html restituisce 404 e non
costituisce evidenza dei termini. Riutilizzo ancora non verificato.
Gli strumenti della sessione non includono un browser controllabile; il
controllo del catalogo JavaScript resta incompleto. Nessun adattatore
Esselunga attivato sulla base della sitemap.
