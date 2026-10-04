# Servizio prezzi per Milano: disegno generale e verifica delle fonti

Data: 4 ottobre 2026.
Stato: disegno generale proposto; l'architettura di massima e' approvata in chat.
Le integrazioni delle singole catene richiedono ancora verifica tecnica.
Questo documento non autorizza attivazioni, hosting, spese o accordi esterni.

## Obiettivo concordato

Igor deve confrontare anche prezzi attuali dei supermercati, oltre ai prezzi
pagati negli scontrini personali, arricchendo i prodotti con Open Food Facts e
l'analisi con ISTAT. L'area iniziale e' Milano; le catene richieste sono
Esselunga, Tigros, Lidl, Eurospin, Conad e Carrefour.

Si intende una copertura progressiva e dichiarata. La disponibilita' di una
pagina pubblica non dimostra ancora che la sua raccolta automatica sia stabile
o riutilizzabile. Il supporto a una catena dipende dalla verifica della fonte.

## Ruolo delle fonti

| Fonte | Informazione usata | Frequenza proposta |
| --- | --- | --- |
| ISTAT | Serie e variazioni degli indici per categorie di consumo | Verifica mensile, dopo la pubblicazione |
| Open Food Facts | Identita' del prodotto, EAN, marca, categoria e formato | Arricchimento su richiesta e rinnovo mensile della cache pertinente |
| Cataloghi dei supermercati | Prezzi del canale online con punto vendita o area servita | Obiettivo giornaliero, nei limiti della fonte |
| Volantini e offerte | Prezzi promozionali e relative condizioni territoriali e temporali | Controlli periodici e validita' esplicita |
| Scontrini personali | Prezzi effettivamente pagati, negozio e data | A ogni importazione, sul dispositivo |

Un indice ISTAT esprime un andamento aggregato, non il prezzo in euro di un
prodotto con EAN. Il catalogo Open Food Facts descrive prodotti; le rilevazioni
dei prezzi della comunita' appartengono alla funzione Open Prices gia' presente.
Open Prices resta facoltativo e non e' l'unica fonte del nuovo confronto.

## Fonti candidate individuate

| Catena | Evidenza pubblica | Esito della ricognizione |
| --- | --- | --- |
| Esselunga | [Promozioni del punto vendita di Milano Lorenteggio](https://www.esselunga.it/it-it/promozioni/volantini.esselunga-di-via-lorenteggio-milano.lor.html) e [spesa online](https://spesaonline.esselunga.it/commerce/nav/supermercato/) | Promozioni locali presenti; catalogo e raccolta automatica da validare |
| Tigros | [Portale punti vendita](https://www.tigros.it/punti-vendita/19) | La lettura testuale restituisce una pagina dipendente da JavaScript; prezzi e accesso automatico non validati |
| Lidl | [Punto vendita Milano Frattini](https://www.lidl.it/s/it-IT/ricerca-negozio/milano-mi/piazza-pietro-frattini-4/) e [volantini](https://www.lidl.it/c/volantino-lidl/s10018048) | Offerte e volantini locali presenti; nessuna promessa di listino completo |
| Eurospin | [Negozio Milano via Fabrizio de Andre'](https://www.eurospin.it/punti-vendita/milano-via-fabrizio-de-andre/) e [spesa online](https://online.eurospin.com/) | Servizio online individuato; catalogo dinamico e raccolta automatica da validare |
| Conad | [Spesa online Milano](https://spesaonline.conad.it/aree-coperte-dal-servizio/milano) e [FAQ](https://spesaonline.conad.it/faq-e-supporto/spesa-online-e-ordini) | Servizio locale presente; prezzi e assortimento dipendono dal negozio |
| Carrefour | [Spesa online](https://www.carrefour.it/spesa-online.html) | Pagine con prezzi, formati e offerte leggibili; contesto territoriale e raccolta automatica da validare |

La ricognizione e' stata una lettura di pagine e documentazione ufficiali, non
un test di importatori. Non e' stato verificato un feed stabile per nessuna
catena. Le date riportate nelle pagine vanno controllate sui dati raccolti e
non ricavate dai risultati indicizzati dei motori di ricerca.

## Architettura scelta e alternative

Si propone un servizio backend indipendente dall'app, con adattatori per fonte,
normalizzazione, pubblicazione di dati versionati e cache Android. Questo
permette manutenzione e pianificazione centralizzate senza distribuire un nuovo
APK a ogni modifica di una pagina commerciale.

La raccolta interamente sul telefono aumenterebbe consumo, duplicazione e
dipendenza dall'esecuzione in background: non e' la scelta proposta. Un feed
ufficiale o un fornitore autorizzato, quando disponibile, puo' sostituire un
adattatore e ridurre la manutenzione; disponibilita' e costi restano da valutare.

Flusso pubblico: fonti -> adattatori -> validazione -> normalizzazione ->
archivio versionato -> API/dataset pubblicato -> cache Android -> confronto
locale con inventario e lista della spesa.

L'app conserva sul dispositivo inventario, lista, scontrini e storico personale.
Non li invia al backend. Le eventuali richieste di ricerca espongono al servizio
solo EAN o termini cercati e il contesto territoriale necessario; questo uso
della rete deve essere dichiarato. Non si introduce un account Igor per usare
il confronto. Il consenso alle nuove fonti online e' separato dall'accesso a
Open Prices; disattivarlo lascia operative le funzioni locali.

## Dati pubblici e identita' del prodotto

Separare catalogo prodotti, offerte e indici statistici. Una rilevazione di prezzo
deve conservare: fonte e collegamento, identificativo prodotto nella fonte,
EAN se disponibile, descrizione, marca, quantita' netta, unita', multipack,
prezzo della confezione, prezzo unitario normalizzato, valuta, catena, negozio
o zona, canale, data di rilevazione, validita' dell'offerta e condizioni.

Le condizioni includono carta fedelta', coupon, numero minimo di confezioni e
disponibilita', quando la fonte le espone. Una condizione sconosciuta resta
esplicitamente sconosciuta; non diventa un'offerta universale.

Un EAN e' una stringa: preservare gli zeri iniziali e verificarne il formato.
Non usare il nome normalizzato attuale di Igor come unica identita' canonica
di catalogo: nomi simili possono indicare marche o confezioni diverse. Gli
acquisti storici rimangono collegati alle proprie informazioni originali.

Le serie ISTAT conservano codice della serie, classificazione, periodo, area,
base dell'indice e stato provvisorio/definitivo. L'associazione alle categorie
Igor e' esplicita e versionata: non si associa una serie statistica a un EAN.
Non si confrontano indici di basi differenti senza il raccordo necessario.

## Matching e confronto

Tre esiti distinti: prodotto identico, alternativa comparabile, abbinamento
incerto. Un EAN identico e coerente con il formato ha priorita'. Senza codice,
si usano descrizione normalizzata, marca, categoria e formato; la sola similarita'
testuale propone candidati e non li dichiara identici. L'utente conferma i
casi incerti, e la conferma resta memorizzata localmente.

Le marche proprie di Lidl, Eurospin o altre catene possono produrre alternative
comparabili: non diventano lo stesso prodotto di un'altra marca. Il confronto
fra prodotti diversi usa kg o litri e informazioni coerenti sul formato; un
prezzo al pezzo e' confrontabile solo quando il contenuto e' equivalente.
Multipack, pesi variabili e requisiti di acquisto sono trattati esplicitamente.

Una lista generica come "latte" deve consentire la scelta del formato/prodotto
o mostrare chiaramente che il risultato riguarda alternative. Il costo stimato
per la spesa comprende le confezioni effettivamente necessarie, gli arrotondamenti
di quantita' e le condizioni applicabili. La consegna resta una voce separata.

L'app mostra fonte, canale online/fisico, negozio/zona, data e validita'. Il
catalogo online non certifica automaticamente un prezzo allo scaffale.
Prezzi non disponibili, scaduti e storici hanno stati distinti. I prezzi degli
scontrini non vengono presentati come listino corrente.

Per ogni negozio si mostra la copertura (per esempio 8 articoli su 12). La
classifica del totale usa lo stesso insieme di articoli, stessa modalita' di
matching e stesse ipotesi sulle condizioni per tutti i negozi. Senza un insieme
comune utile, si confrontano le singole voci senza attribuire un vincitore.
Le alternative non vengono sostituite automaticamente nella lista personale.

## Ingestione, aggiornamenti ed errori

Ogni fonte ha adattatore e pianificazione separati. Un'importazione deve essere
idempotente; un errore o un dato anomalo non sostituisce la versione valida.
Validazione prima della pubblicazione, salvataggio atomico, log delle esecuzioni,
tentativi limitati e ritardi crescenti. Un job fallito non aggiorna la data
della rilevazione precedente.

La fonte puo' avere stato candidata, validata, attiva o sospesa. Il mancato
aggiornamento mantiene una copia consultabile con data reale, ma la esclude dai
risultati "attuali" dopo la soglia di freschezza concordata per quella fonte.
Un'offerta viene esclusa anche quando la sua validita' e' terminata.

Per Open Food Facts, usare cache e campi necessari. La documentazione consiglia
esportazioni bulk per grandi volumi: l'aggiornamento mensile non consiste nel
richiedere uno a uno tutti i prodotti del catalogo. Rispettare i limiti correnti
delle API e mantenere attribuzione/provenienza dei dati importati.

Android scarica aggiornamenti pubblicati con WorkManager quando la funzione e'
attiva e la rete e' disponibile, con refresh manuale e cache locale. WorkManager
non e' una garanzia di esecuzione a un orario esatto: i job di ingestione delle
fonti vengono eseguiti sul backend.

## Verifica delle fonti prima degli importatori definitivi

Per ciascuna catena: scegliere un negozio o zona a Milano; documentare una fonte
e le sue modalita' d'accesso e riutilizzo; verificare un campione con confezioni,
prezzi ordinari, offerte condizionate e validita'; ripetere la lettura per
valutare aggiornamenti e stabilita'; confrontare i risultati con la fonte.

Non aggirare blocchi, autenticazione o protezioni del sito per rendere attiva
una fonte. Se il percorso disponibile non e' sostenibile, documentare il motivo
e cercare un feed o un'importazione autorizzata. Una fonte non validata non
riceve prezzi inventati o stimati da ISTAT.

Prima candidata per un importatore: Carrefour, perche' la ricognizione ha
mostrato pagine prodotto leggibili. Questa e' una priorita' di verifica, non
una promessa di migliore copertura. Gli altri adattatori si validano uno alla
volta; Lidl parte dalle offerte pubblicate. Tutte le sei catene restano nel
perimetro desiderato, con disponibilita' visibile nell'app.

## Verifiche necessarie

- Fixture di fonti reali: descrizioni, quantita', prezzi, condizioni e date.
- Job ripetuto, fallimento a meta', fonte cambiata e dati palesemente anomali.
- Matching con EAN, codici sconosciuti, stesso nome e marche diverse, multipack
  e alternative delle marche proprie; casi incerti sottoposti all'utente.
- Confronti con quantita' diverse, copertura parziale, condizioni di fedelta'
  e offerte scadute; assenza di classifiche ingannevoli.
- Funzionamento Android offline, cache vuota, cache vecchia e rete disabilitata;
  inventario e scontrini restano utilizzabili.
- API/dataset versionati e attribuzioni; nessuna esposizione dei dati personali
  della spesa nei log o nel backend di catalogo.

## Decisioni di dettaglio da risolvere nel sottoprogetto prezzi

La revisione di questo disegno approva perimetro, separazione dei dati e regole
di confronto. Le scelte di stack backend, hosting, costi ricorrenti e formato
del contratto API saranno proposte dopo la verifica della prima fonte, prima
del piano di implementazione di quel sottoprogetto. I negozi concreti e le
soglie di freschezza vengono fissati nelle schede delle fonti validate.

Ordine dei lavori: inventario e categorie; verifica prima fonte; specifica
esecutiva e piano del servizio prezzi; importatore e confronto Android;
estensione alle altre catene; ripresa dei bug rimasti in coda.

## Riferimenti tecnici

- [ISTAT: indici e pubblicazione dei prezzi al consumo](https://www.istat.it/notizia/faq-domande-frequenti-sui-prezzi-al-consumo/).
- [ISTAT: accesso machine-to-machine SDMX](https://www.istat.it/classificazioni-e-strumenti/web-services-sdmx/).
- [Open Food Facts: informazioni prodotto e formato](https://openfoodfacts.github.io/documentation/docs/Product-Opener/v3/products/get-api-v3-product-code/).
- [Open Food Facts: API, cache, limiti e dati bulk](https://openfoodfacts.github.io/openfoodfacts-server/api/).

Le fonti e i limiti operativi vanno ricontrollati prima dell'implementazione
degli adattatori: la documentazione consultata definisce questo disegno,
non garantisce disponibilita' permanente degli endpoint.
