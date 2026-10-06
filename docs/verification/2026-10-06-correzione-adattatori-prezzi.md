# Correzione degli adattatori prezzi

6 ottobre 2026. Correzioni autorizzate dall'utente dopo la verifica sui
campioni reali. Nessuna fonte attivata e nessun listino pubblicato.

## Cambiamenti

- Identita' del prodotto verificata rispetto allo SKU nell'URL quando
  esplicito; prodotti consigliati esclusi e ambiguita' rifiutate.
- Carrefour: contesto `.product-main` legato allo SKU, prezzo corrente
  della confezione distinto da prezzo unitario e prezzo barrato;
  confronto fra testo, attributo content e JSON-LD.
- Badge PAYBACK conservato come condizione loyalty; multiacquisto
  conservato come multi_buy, etichette non riconosciute come unknown.
  Il richiamo PAYBACK nella navigazione non classifica altri prodotti.
- Data esplicita della promozione letta dal blocco prezzi; date discordanti
  rifiutate, priceValidUntil del JSON-LD non assunto come scadenza.
- Peso variabile non diventa una confezione di peso nominale.
- Conad: confronto limitato al prezzo della confezione nella scheda
  principale, senza totale zero del selettore quantita' o prezzi delle
  altre schede. Prezzi assenti, illeggibili o discordanti rifiutati.

## Verifiche

Suite Python completa: **122 test passati**, inclusi 22 test degli adattatori
e i test del publisher D1. Rimane il warning upstream Starlette/httpx.
Replay offline delle 40 risposte HTML raccolte durante la verifica
precedente, senza nuove richieste alle catene:

| Fonte | Prima, per lettura | Dopo, per lettura | Limite |
|---|---:|---:|---|
| Carrefour | 5/10 | 10/10 | Solo 7 formati noti, nessun GTIN verificato |
| Conad | 0/10 | 2/10 | Otto schede prive di prezzo strutturato |

Fra i dieci Carrefour: cinque loyalty, quattro ordinary, uno multi_buy.
Le due schede Conad accettate hanno formato e GTIN verificati. Questo non
dimostra equivalenza con i prodotti Carrefour, copertura del CAP o
disponibilita' nel negozio fisico.

I test nuovi usano markup sintetico che riproduce la struttura rilevante,
non copie delle pagine complete. Il replay resta nella cartella locale
ignorata `.tools/source-validation-2026-10-06/replay-fixed.json`.
Il report della prima verifica resta storico e non descrive il codice corretto.

## Requisiti esterni

Le condizioni di riuso restano da chiarire. Le [bozze delle richieste](2026-10-06-richieste-feed-prezzi.md)
includono ora i canali ufficiali di contatto: nessun messaggio inviato.
Non e' stata ottenuta o promessa una risposta dalle catene.
Manifest e job giornaliero restano invariati; nessun account cliente usato.
APK non aggiornato, nessuna modifica Android in questo intervento.
