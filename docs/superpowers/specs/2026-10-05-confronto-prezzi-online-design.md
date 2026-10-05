# Confronto prezzi online — prima versione

Data: 5 ottobre 2026. Stato: **specifica approvata in chat**.
Deriva dal disegno generale gia' approvato; non approva hosting o spese.

## Obiettivo e decisioni dell'utente

Confrontare la lista della spesa con i prezzi pubblicati online dalle catene,
usandoli come riferimento anche per gli acquisti fisici. Le differenze allo
scaffale sono accettate. CAP iniziale **20125**, Milano. Open Prices escluso
dalle fonti di questa integrazione; Open Food Facts resta utile per identita'
e formato dei prodotti. I dati personali rimangono sul telefono.

Le sei catene desiderate restano Esselunga, Tigros, Lidl, Eurospin, Conad e
Carrefour. La prima versione sviluppa gli adattatori Carrefour e Conad, i cui
campioni hanno risposto direttamente via HTTP con informazioni strutturate.
Questo e' un riscontro tecnico, non validazione della copertura territoriale,
stabilita' o riutilizzabilita'. Le altre catene compaiono come non disponibili
finche' non esiste un adattatore validato. ISTAT e' fuori da questa prima versione.

Priorita' confermata dall'utente dopo la revisione: **Esselunga e' la catena
piu' usata ed e' la prima estensione dopo Carrefour e Conad**. La verifica
dell'accesso Esselunga parte durante il lavoro sulle prime due fonti, senza
aspettare la fine del progetto. Tigros, Lidl ed Eurospin rimangono nel perimetro.
Il supporto non viene promesso prima di aver validato la fonte.

## Soluzione e alternative

Si mantiene il servizio separato concordato: adattatori sul server, archivio
pubblico e API versionata, cache Android e confronto locale. Proposta tecnica:
Python 3.12, FastAPI e SQLite per il servizio, Kotlin/Room/WorkManager nell'app.
Il primo ambiente e' locale, senza acquistare o attivare hosting. SQLite e'
sufficiente per il catalogo iniziale e un solo processo di ingestione.

Alternative considerate: lettura direttamente dal telefono, che duplica
richieste e manutenzione; feed di un fornitore, che puo' sostituire un
adattatore quando copertura, condizioni e costi sono verificati. Non viene
attivato automaticamente un fornitore a pagamento.

## Fonti e acquisizione

Ogni adattatore ha dominio ammesso, elenco di URL prodotto validati e contesto
territoriale esplicito. Parte da pagine pubbliche o feed riutilizzabili; non
aggira login, CAPTCHA, blocchi o percorsi esclusi da robots. La scoperta del
catalogo puo' usare sitemap pubbliche nei percorsi consentiti, senza assumere
che autorizzino la ripubblicazione. Le richieste non usano account personali.

La promozione da candidata ad attiva richiede: modalita' di accesso e riutilizzo
documentate, almeno dieci campioni con marche/formati diversi, confronto fra
risposta strutturata e pagina, due acquisizioni separate, almeno un caso
promozionale e gestione dei campi mancanti. Le condizioni Carrefour emerse
nella prima verifica richiedono risolvere il riutilizzo prima dell'attivazione.
Se una fonte non passa, resta non disponibile e si cerca un feed compatibile;
non viene pubblicata come funzionante sulla sola base del parser.

Per la zona: quando la fonte permette di impostare 20125 tramite un contesto
pubblico stabile, viene registrato quel contesto. In caso contrario il prezzo
e' marcato come catalogo online generico, con zona non verificata; non viene
attribuito a un negozio vicino. Il CAP e' una preferenza, non una prova della
provenienza territoriale della risposta.

Si raccolgono soltanto dati necessari: identificativo della fonte, GTIN quando
verificato, nome, marca, formato e multipack, prezzo confezione/unitario, valuta,
fonte e URL, zona, data di acquisizione, disponibilita', condizioni e date
delle offerte. Prezzi in centesimi, quantita' decimali, GTIN come stringa.
SKU o numeri nell'URL non vengono assunti come GTIN senza riscontro.

JSON-LD e contenuto visibile sono confrontati: in caso di disponibilita'
discordante lo stato e' sconosciuto. La data priceValidUntil non diventa da
sola scadenza di una promozione. Prezzi carta/coupon/multiacquisto sono
distinti dal prezzo ordinario; condizioni incomplete escludono lo sconto dal
totale standard. Un prezzo generico puo' essere mostrato come riferimento
anche con disponibilita' non verificata, come richiesto dall'utente.

## Archivio, aggiornamenti e API

Entita' separate: prodotti della fonte, rilevazioni, esecuzioni e stato fonte.
Un lotto viene validato e pubblicato atomicamente; ripeterlo non crea duplicati.
Un errore non sostituisce l'ultimo lotto valido ne' ne aggiorna la data.
Un solo aggiornamento al giorno per fonte; timeout, richieste distanziate e
massimo due tentativi per errori transitori. HTTP 403 sospende l'accesso;
429 rispetta Retry-After e termina il lotto se l'attesa supera il budget.

API v1 di sola lettura: stato delle fonti, ricerca di candidati per nome/marca
o GTIN e offerte per prodotto scelto con CAP. Risposte con versione del
contratto, data e stato fonte; massimo 20 candidati, paginazione esplicita.
Nessuna chiamata inoltra inventario, lista completa, scontrini o foto.
Le ricerche espongono solo il termine o GTIN necessario e il CAP; i log non
registrano query o identificatori del dispositivo.

Le API servono il catalogo, non lanciano un crawl per ogni richiesta Android.
Fuori dall'ambiente locale il servizio richiede HTTPS e una configurazione
di hosting separata, da rendere concreta prima della relativa approvazione.
Non si promette una funzione sul telefono utilizzabile ovunque finche' non
e' disponibile un endpoint raggiungibile.

## Integrazione Android

La schermata Confronta aggiunge una sezione Prezzi online, separata dai prezzi
degli scontrini e dalla precedente funzione della comunita'. Preferenza
dedicata, inizialmente spenta, CAP modificabile con default 20125, descrizione
dei dati inviati e pulsante Aggiorna. Material 3 e tema esistente mantenuti.

Per un barcode verificato si cercano prodotti identici con formato coerente.
Per una voce generica si mostrano candidati con marca, formato e prezzo; la
scelta resta all'utente. L'associazione viene salvata per la singola voce della
spesa, non solo per il nome normalizzato: due marche omonime non si fondono.
Le alternative sono mostrate a parte e non sostituiscono automaticamente la voce.

Cache delle rilevazioni e associazioni in nuove tabelle Room; migrazione 6->7,
test di conservazione dati e schemi esportati. Non si modificano gli importi
degli scontrini. WorkManager aggiorna la cache solo con consenso e rete;
il refresh manuale e' sempre disponibile, con richieste deduplicate.

Per ogni catena: costo stimato, prodotti coperti, data e collegamento alla
fonte. Confronto fra totali calcolati sul medesimo insieme di prodotti;
se l'insieme comune e' vuoto si mostrano solo le singole voci. Formati diversi
usano kg/litro; il totale della spesa compra confezioni intere arrotondando
verso l'alto. Se non si conosce il formato, niente totale dedotto al pezzo.
Spese di consegna escluse dal costo dei prodotti e dichiarate separatamente.

Freschezza iniziale: fino a 48 ore il prezzo e' aggiornato; dopo 48 ore e fino
a 7 giorni si mostra come vecchio senza includerlo nella classifica attuale;
oltre 7 giorni resta solo nello storico cache. Offerte con data esplicita
terminata sono escluse immediatamente. Offline si mostra la cache con data
reale; senza cache si indica che non ci sono prezzi disponibili.

## Verifica e criteri di completamento

Backend: fixture per JSON-LD incompleto, prezzi e disponibilita' discordanti,
GTIN/SKU, multipack, carta fedelta', euro/decimali, peso variabile e formato
mancante; rollback, idempotenza, limiti di richieste e fonte sospesa.

Android: collegamento della voce corretta, protezione dei dati degli scontrini,
matching incerto, quantita'/arrotondamenti, copertura comune, cache scaduta,
offline, consenso revocato, cancellazione dati e migrazione Room reale.
Build e suite JVM completa; verifica nativa su telefono, tema chiaro/scuro,
font grandi e messaggi accessibili. Nessuna verifica browser sostituisce quella
Compose. Aggiornamento README e rapporto con esiti e limiti.

La funzione e' completata quando almeno due fonti validate e raggiungibili
producono confronti reali di prodotti/formati equivalenti sulla lista, con
provenienza e freschezza corrette. Fixture e fonti non attive non contano come
listini funzionanti. Se manca accesso riutilizzabile o hosting, il risultato
locale resta dichiarato incompleto rispetto all'uso quotidiano sul telefono.

## Riferimenti

- [Verifica Carrefour 20125](../../verification/2026-10-05-fonte-carrefour-20125.md).
- [Conad: scheda campione](https://spesaonline.conad.it/p/conad-3-mozzarelle-con-fermenti-lattici-vivi-3-x-125-g--225225).
- [FastAPI: distribuzione del servizio](https://fastapi.tiangolo.com/deployment/concepts/).
- [SQLite con Python](https://docs.python.org/3.12/library/sqlite3.html).

La revisione dell'utente approva stack proposto e partenza Carrefour/Conad,
con priorita' Esselunga per l'estensione. Il prossimo passaggio e' la revisione
del piano scritto e la scelta del metodo di esecuzione, prima del codice.
