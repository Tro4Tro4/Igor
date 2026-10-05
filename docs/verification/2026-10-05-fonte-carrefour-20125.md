# Prima verifica Carrefour — Milano 20125

Data: 5 ottobre 2026. CAP indicato dall'utente: **20125**.
Esito: **fonte candidata, non validata per importazione automatica**.
Nessun importatore o servizio esterno attivato; app invariata.

## Evidenze

- Il [Carrefour Express di via Ponte Seveso 26](https://www.carrefour.it/punti-vendita/carrefour-express-milano-via-ponte-seveso-26-4587.html)
  e' nel CAP 20125. Non prova che sia il negozio assegnato alla consegna online.
- Le [FAQ sulla consegna](https://www.carrefour.it/customer-care/articoli?area=Acquisti_online&category=Servizi_Spesa_Online&subcategory=Come_funziona_la_consegna_della_spesa)
  richiedono di impostare CAP/indirizzo per verificare i servizi attivi; il negozio
  della consegna viene assegnato automaticamente. Non e' stata impostata una
  sessione territoriale: la copertura specifica per 20125 resta da verificare.
- Le [condizioni di vendita, sezione 5](https://www.carrefour.it/condizioni-generali-di-vendita.html)
  dichiarano che i prezzi online possono differire da quelli fisici.
- Le [condizioni d'uso, sezione 8](https://www.carrefour.it/condizioni-generali.html)
  limitano la riproduzione dei contenuti. Non e' stato individuato un feed
  pubblico ufficiale con condizioni di riutilizzo per Igor. La lettura delle
  pagine non valida la raccolta e ripubblicazione nel servizio.
  Questo rapporto descrive le condizioni pubblicate, non e' un parere legale.

## Campione consultato senza zona selezionata

- [Latte Carrefour Classic 1000 ml](https://www.carrefour.it/p/carrefour-classic-latte-uht-parzialmente-scremato-1000-ml/8012666051267.html).
- [Spaghetti Barilla n.5 500 g](https://www.carrefour.it/p/barilla-pasta-spaghetti-n.5-500g/8076800195057.html).
- [Nutella 450 g](https://www.carrefour.it/p/nutella-450-g/0000080050865.html).

Schede leggibili con marca, descrizione, formato, prezzo della confezione e
prezzo unitario; compare anche indisponibilita'. I prezzi non vengono copiati
in un catalogo Igor ne' attribuiti al CAP 20125. L'identificativo numerico
nell'URL va verificato prima di considerarlo il GTIN della confezione,
preservando gli zeri iniziali.

## Passi necessari

1. Priorita' risolta dall'utente: prezzi online come riferimento anche per gli
   acquisti fisici; differenze allo scaffale accettate. Open Prices escluso
   dalle fonti della nuova integrazione.
2. Verificare una fonte riutilizzabile (feed autorizzato o fornitore con
   condizioni compatibili). Nessun contatto inviato o costo attivato.
3. Verificare territorio, disponibilita', offerte condizionate, aggiornamenti
   e stabilita' prima di promuovere la fonte ad attiva.

Scontrini e Open Prices sono gia' integrati; le osservazioni della comunita'
non rappresentano listini correnti garantiti delle catene.

## Verifica HTTP diretta successiva

Richieste anonime dal computer, User-Agent Igor esplicito, senza account,
cookie territoriali o acquisti: scheda Barilla Carrefour HTTP 200, HTML con
JSON-LD Product/Offer e prezzo EUR; homepage e scheda mozzarella Conad HTTP
200, con JSON-LD e, nella scheda, GTIN distinto dallo SKU.

Nel campione Carrefour il JSON-LD indica InStock mentre il contenuto visibile
senza contesto territoriale indica indisponibilita': la disponibilita' effettiva
rimane sconosciuta. `priceValidUntil` del JSON-LD non dimostra da solo la fine
di una promozione. Sono necessari controlli sul testo e sulle condizioni.

Il robots.txt Carrefour risponde HTTP 200 e per User-agent * esclude tra
l'altro /search, /multisearch e alcuni parametri di filtraggio; indica il
sitemap_index.xml. Non sono stati interrogati gli endpoint esclusi. Robots
non risolve le condizioni di riutilizzo. Nessuna fonte promossa ad attiva.
