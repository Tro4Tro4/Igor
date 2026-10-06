# Carrefour e Conad — verifica su schede reali

6 ottobre 2026. Esito: **nessuna fonte supera i criteri di attivazione**.
Servizio Cloudflare disponibile, ma senza prezzi reali. Manifest e
acquisizione giornaliera conservano lo stato precedente.

## Metodo ed evidenze

Dieci schede pubbliche per catena, due letture anonime nella stessa sessione,
richieste distanziate di almeno un secondo. Nessun login, selezione di un
indirizzo, percorso escluso da robots o pubblicazione delle offerte.
URL Carrefour ricavati dalla pagina spesa online, URL Conad dalla sitemap
pubblica. Robots Carrefour 200; robots Conad 404, come nella prima verifica.

Tutte le **40 risposte HTTP sono 200**. I dati strutturati dei dieci prodotti
per fonte sono identici fra le due letture. Questo verifica la ripetibilita'
ravvicinata, non l'aggiornamento giornaliero o la stabilita' nel tempo.
I dati non sono riferibili al CAP 20125: nessun contesto territoriale impostato.

| Catena | Campioni distinti | Letture | Accettati dal parser per lettura | Esito |
|---|---:|---:|---:|---|
| Carrefour | 10 | 2 | 5 | Prezzi e condizioni non disambiguati |
| Conad | 10 | 2 | 0 | Prezzi assenti o confronto con elementi estranei |

[Indice delle prove](2026-10-06-fonti-prezzi-campioni.json): URL, timestamp,
hash e risultato diagnostico, senza prezzi o HTML ripubblicati. Evidenze
integrali solo nella cartella ignorata `.tools/source-validation-2026-10-06`.
Il primo riepilogo della sonda usava erroneamente `asdict` su modelli Pydantic;
la riparsificazione offline con `model_dump` corregge il conteggio Carrefour
da zero a cinque. Il report usa solo `samples-corrected.json`.

## Problemi tecnici verificati

### Carrefour

Cinque prodotti sono rifiutati per prezzo visibile/JSON-LD discordante.
Il selettore generico legge il primo importo del blocco `.price`, che puo'
essere il prezzo al litro anziche' il totale. Il filtro esistente non copre
la forma testuale `al l`. Non e' quindi prova che il prezzo della fonte sia errato.

I cinque accettati comprendono due prodotti a peso variabile e una box
senza formato preciso, conservati con `pack=null`. Nessun GTIN verificato
in questi cinque: lo SKU numerico non viene trasformato automaticamente in GTIN.

I prodotti con badge PAYBACK possono essere classificati `ordinary` dal
parser corrente: il contesto della promozione non e' interpretato. Un badge
da solo non dimostra quale condizione si applichi; serve una verifica sul
blocco prodotto, distinta dal richiamo generico PAYBACK nella navigazione.
Date promozionali visibili non vengono conservate. Non attivare la fonte.

Esempi pubblici: [multipack](https://www.carrefour.it/p/coca-cola-zero-zuccheri-pet-2-x-15-l/5000112600421.html),
[peso variabile](https://www.carrefour.it/p/formaggio-bel-paese-da-banco/2201011000000.html).

### Conad

Otto schede su dieci hanno `Offer` senza prezzo/valuta, anche se dichiarano
`inStock`: non si deduce un prezzo zero o un prezzo da altri prodotti.
Due schede hanno prezzo strutturato ma sono rifiutate: il confronto globale
dei selettori `.price` incontra importi zero estranei alla scheda principale.
La [mozzarella multipack](https://spesaonline.conad.it/p/conad-3-mozzarelle-con-fermenti-lattici-vivi-3-x-125-g--225225)
ha un prezzo visibile e strutturato, mentre la pagina contiene anche altri
blocchi prezzi. Il controllo deve essere limitato al prodotto corretto.

Campioni comprendono marche nazionali, marca Conad, multipack e frutta.
Promozioni e selezione territoriale non risultano validati da queste letture.

## Accesso e riuso

Le [condizioni Carrefour, sezione 8](https://www.carrefour.it/condizioni-generali.html)
mantengono il divieto di riproduzione anche parziale dei contenuti e prevedono
la valutazione di eventuali richieste. Non e' stato individuato un feed
ufficiale con condizioni di riuso compatibili.

Le [condizioni Conad](https://spesaonline.conad.it/condizioni-di-vendita)
disciplinano l'acquisto e distinguono operatori territoriali. La consultazione
non ha individuato una licenza esplicita per raccolta e redistribuzione dei
prezzi in Igor. Non si deduce autorizzazione dall'assenza di una clausola
trovata nella pagina. Questa e' verifica delle condizioni pubblicate,
non una conclusione legale sulla liceita' delle singole attivita'.

## Alternative esaminate e prossimo passo concreto

[Pepesto Conad](https://www.pepesto.com/supermarkets/conad/) dichiara dati da
pagine pubbliche, senza accordo speciale con il rivenditore. Non risolve da
solo la verifica del riuso. La [pagina prezzi](https://www.pepesto.com/pricing/)
richiede crediti o abbonamento: escluso dall'attivazione gratuita corrente.
Retail Shake propone un'API Conad, ma condizioni, gratuitita' e diritti di
redistribuzione non sono verificati. Nessun servizio comprato o collegato.

Il prossimo passo utile e' ottenere dalle catene un feed o condizioni
esplicite compatibili, parallelamente alla correzione degli adattatori.
[Bozze delle richieste](2026-10-06-richieste-feed-prezzi.md), preparate ma
non inviate. Una risposta positiva non sostituisce le prove tecniche:
selettori per fonte, condizioni, formati e contesto territoriale devono
passare i campioni prima di attivare il job.

Nessuna modifica ai parser, nessun prezzo caricato su D1, nessun aggiornamento
APK durante questa verifica. Le fonti restano candidate e il confronto reale
fra due catene resta incompleto.
