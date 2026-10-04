# Inventario leggibile e categorie dettagliate

Data: 4 ottobre 2026.
Stato: specifica proposta, in attesa della revisione dell'utente.
L'impostazione generale e l'ordine dei lavori sono stati approvati in chat.

## Obiettivo e perimetro

Chi usa Igor deve trovare rapidamente un alimento, riconoscere la categoria e
capire quali prodotti richiedono attenzione. L'inventario viene raggruppato per
categoria e filtrabile per categoria, insieme ai filtri gia' presenti. La
classificazione diventa piu' dettagliata e resta unica per inventario, spesa,
liste salvate e scontrini.

Questo e' il primo blocco di lavoro. Funziona offline, nell'app Android attuale,
senza dipendere dal futuro servizio prezzi. I bug individuati nella revisione
iniziale restano nella coda di lavoro successiva, salvo problemi che impediscano
direttamente di verificare questo blocco.

## Scelte

Si estende `FoodCategory`, mantenendo i codici esistenti. Una tassonomia separata
per ogni schermata moltiplicherebbe conversioni e incoerenze. Una tassonomia
dinamica con categorie personalizzate introdurrebbe gestione e migrazioni che
questa richiesta non richiede: resta fuori da questo blocco.

Le categorie restano piatte: i gruppi nella tabella seguente organizzano la
specifica, senza aggiungere un secondo livello al database. Le schermate
mostrano sezioni per la categoria effettiva del prodotto.

## Categorie visibili

| Ambito | Categorie specifiche |
| --- | --- |
| Ortofrutta | Frutta; verdura; erbe aromatiche; frutta secca e semi |
| Carne e pesce | Carne; salumi e affettati; pesce e frutti di mare |
| Latticini e uova | Uova; latte e panna; yogurt e dessert al latte; burro e margarina; formaggi freschi; formaggi stagionati |
| Pane e forno | Pane e prodotti da forno; piadine e basi per pizza; crackers, grissini e gallette |
| Colazione e dolci | Biscotti; merendine e snack dolci; cereali da colazione; confetture, miele e creme spalmabili; dolci, caramelle e cioccolato |
| Pasta e dispensa | Pasta secca; pasta fresca e gnocchi; riso e cereali; legumi; conserve vegetali e sottoli; pesce e carne in conserva; farine e ingredienti per dolci |
| Condimenti | Sughi e passate; salse; oli e aceti; sale, spezie e insaporitori |
| Altri alimenti | Piatti pronti e gastronomia; alternative vegetali; snack salati; surgelati; gelati |
| Bevande | Acqua; succhi e bibite; caffe', te' e infusi; birra, vino e alcolici |
| Non alimentari | Casa e pulizia; bucato; igiene personale |
| Residuale | Altro |

I codici `FRUTTA`, `VERDURA`, `CARNE`, `PESCE`, `PANE`, `SURGELATI`, `CASA`,
`IGIENE` e `ALTRO` continuano a rappresentare le categorie corrispondenti.
`LATTICINI`, `DISPENSA`, `CONDIMENTI` e `BEVANDE` restano categorie generiche
compatibili, con etichette che ne rendono chiara la natura. Sono ancora
selezionabili quando non si dispone di una classificazione piu' precisa.

Le etichette sopra definiscono il contenuto; i nuovi codici Kotlin saranno nomi
stabili in italiano, indipendenti dall'etichetta e dall'ordine di visualizzazione.
Ogni categoria deve avere etichetta, icona, posizione predefinita e proprieta'
merceologiche esplicite. Le stringhe fisse nuove vanno nelle risorse Android.

Non si aggiungono categorie dietetiche come "senza glutine" o "biologico": un
biscotto resta nella categoria Biscotti. Eventuali attributi dietetici saranno
un'estensione distinta.

## Inventario e interazione

La composizione mantiene tema e navigazione di Igor e applica Impeccable in
modalita' Operate, con componenti Material 3 nativi.

1. Ricerca per nome o marca in alto.
2. Filtri di stato: Tutti, In scadenza, Scaduti, Senza data.
3. Due controlli compatti per Categoria e Luogo, con selezione corrente visibile.
4. Numero di articoli mostrati rispetto al totale dell'inventario attivo.
5. Lista raggruppata in sezioni con icona, nome e conteggio.

Il selettore Categoria e' un foglio inferiore Material con ricerca, opzione
"Tutte le categorie" e selezione singola. Include solo categorie presenti
nell'inventario attivo, piu' quella eventualmente gia' selezionata: questa non
deve scomparire quando un aggiornamento svuota il risultato. La disponibilita'
delle categorie non dipende dagli altri filtri, cosi' si puo' cambiare categoria
anche quando la combinazione corrente non trova risultati.

Luogo permette Tutti, Frigo, Freezer e Dispensa. I criteri si combinano con AND.
L'inventario continua a includere tutte le posizioni; il titolo colloquiale
"frigorifero" della richiesta non restringe automaticamente i dati al solo frigo.

Le sezioni vuote non compaiono. Il loro ordine e' quello predefinito delle
categorie, con Altro in fondo; l'ordine personalizzato delle corsie della spesa
continua a riguardare la spesa. Dentro la sezione: scadenza crescente, prodotti
senza data in fondo, poi nome; UUID come spareggio stabile. Il raggruppamento
e l'ordinamento sono calcolati fuori dai composable.

I conteggi dei filtri di stato si riferiscono ai prodotti che soddisfano ricerca,
luogo e categoria, prima di applicare il filtro di stato. Il totale generale
resta il numero di record attivi nell'inventario. Si contano articoli, senza
sommare quantita' di unita' diverse.

Le righe privilegiano nome, scadenza e quantita'. Marca e luogo sono informazioni
secondarie. La categoria e' nell'intestazione e non viene ripetuta in ogni riga.
Resta possibile aprire i dettagli, consumare ed eliminare con le azioni attuali.
Si riduce il rumore delle schede senza introdurre gesti nascosti obbligatori.

Le intestazioni restano visibili durante lo scorrimento della propria sezione.
La dimensione dei nomi segue le preferenze del dispositivo e consente piu' righe.
Scadenze e selezioni hanno anche testo: il solo colore non porta significato.
Le emoji decorative non vengono lette due volte da TalkBack; bersagli di tocco
di almeno 48 dp, contrasto e temi chiaro/scuro rispettano Material 3.

## Stati e aggiornamenti

- Primo caricamento: stato esplicito, senza mostrare prematuramente "vuoto".
- Inventario vuoto: invito ad aggiungere un articolo.
- Nessun risultato: indica che sono attivi ricerca o filtri e offre di azzerarli.
- Ritorno dai dettagli: filtri mantenuti e dati osservati da Room.
- Cambio di giorno o soglia: stato di scadenza, ordine e conteggi si aggiornano.
- Rotazione: ricerca, luogo, categoria e stato mantengono la selezione.
- Ricreazione del processo: si ripristinano i criteri tramite stato salvato Android;
  annullamenti e messaggi transitori continuano a seguire il comportamento attuale.
- Eliminazione dell'ultimo prodotto di una categoria: il filtro resta selezionato
  e mostra lo stato senza risultati, con possibilita' di azzerarlo.

## Classificazione e inserimento

La scelta manuale dell'utente prevale sempre. Per aggiunta rapida e scontrini si
mantiene l'ordine corrente: categoria gia' conosciuta dal prodotto, altrimenti
proposta dal parser del nome. Il parser viene ampliato con parole, espressioni
e abbreviazioni per le nuove categorie, con test delle ambiguita'.

Esempi attesi: prosciutto cotto -> Salumi e affettati; mozzarella -> Formaggi
freschi; parmigiano -> Formaggi stagionati; biscotti -> Biscotti; merendine ->
Merendine; tonno in scatola -> Pesce e carne in conserva; gelato al latte ->
Gelati; latte di soia -> Alternative vegetali. I marcatori di surgelazione
continuano a distinguere gli alimenti congelati dai freschi. Parole generiche
o ambigue continuano a produrre una categoria generica o Altro.

Tutti i selettori di categoria devono gestire l'elenco ampliato con ricerca o
scorrimento adeguato. Questo vale per inventario, dettagli della spesa, revisione
scontrino e ordine delle corsie, anche quando l'elenco e' lungo.

## Dati esistenti e conservazione

Room salva il nome del valore enum. I valori esistenti non vengono rinominati
o rimossi, e i record non sono riclassificati automaticamente in questo blocco.
Il nome del prodotto potrebbe non bastare per dedurre una categoria dettagliata;
l'utente puo' correggerla nella normale modifica del prodotto.

Questo garantisce che inventario, articoli rimossi, spesa e liste salvate continuino
a essere leggibili. UUID, storico, foto, quantita', posizioni e date restano
conservati. Le nuove categorie nell'ordine delle corsie seguono la normalizzazione
gia' presente: si accodano quelle mancanti, mantenendo l'ordine relativo scelto
dall'utente, e possono essere riordinate nelle impostazioni.

L'aggiunta di valori enum e di stato UI non cambia da sola lo schema Room.
Se l'implementazione resta entro questo disegno, non serve una migrazione SQL.
Qualsiasi successivo cambio di schema richiede aumento di versione, migrazione,
test e schema esportato secondo le regole del repository.

Le nuove categorie alimentari mantengono `isFood = true`; le nuove categorie
non alimentari hanno `isFood = false`. I freschi (salumi, formaggi, yogurt,
latte, uova, pasta fresca e gastronomia) partecipano alla richiesta di scadenza.
I nuovi gruppi da dispensa e le bevande hanno default Dispensa; surgelati e
gelati hanno default Freezer; gli altri freschi hanno default Frigo. Le posizioni
gia' scelte dall'utente continuano a essere rispettate.

La vendita a peso riguarda frutta, verdura, carne, pesce, salumi e formaggi.
Il formato reale o una quantita' nota prevalgono sulla sola categoria. Le date
restano inserite o confermate dall'utente: nessuna riclassificazione calcola una
scadenza. Le nuove categorie riusano i suggerimenti di calendario dei gruppi
esistenti; questo blocco non introduce nuove durate numeriche di conservazione.

## Componenti coinvolti

- `data/local/Enums.kt`: valori e proprieta' delle categorie.
- `domain/CategoryGuess.kt` e `CategoryOrder.kt`: classificazione e ordine.
- Modello della lista inventario: sezioni, filtri, ordinamento e conteggi puri.
- `InventoryViewModel` e `InventoryScreen`: stato osservabile e interazione.
- `FoodItemCard` o componente equivalente: riga leggibile con azioni esistenti.
- Selettori comuni e schermate di modifica, spesa e scontrino.
- `Formatters`, risorse e test: etichette, icone e nuovi casi.

## Verifica e criteri di accettazione

Test della logica: ogni record appare una volta; sezioni e ordine sono stabili;
filtri combinati e conteggi concordano; dati senza scadenza vengono ordinati
correttamente; i cambi di data e soglia si riflettono nello stato.

Test delle categorie: parole e abbreviazioni significative; alimenti e non
alimenti; conversione Room di valori vecchi e nuovi; normalizzazione di un
ordine salvato prima dell'ampliamento; inventario e liste salvate preesistenti
leggibili senza perdita o riclassificazione implicita.

Verifica Android: build debug e suite JVM completa; screenshot su emulatore o
dispositivo di inventario misto, filtrato, vuoto e senza risultati; tema chiaro
e scuro, caratteri ingranditi e nomi lunghi. Provare almeno 1, 30 e 200 articoli.
Verificare rotazione, ritorno dai dettagli, ripristino dei criteri dopo
ricreazione del processo e navigazione con TalkBack. Le screenshot del browser
non valgono come verifica dell'interfaccia Android.

Successo: un utente trova salumi, formaggi, biscotti e merendine sia come sezioni
sia dal filtro; i prodotti urgenti restano riconoscibili; tutti i dati della
versione precedente rimangono accessibili; i flussi della spesa e degli scontrini
continuano a usare la stessa categoria del prodotto.

## Dopo la revisione

Questa specifica deve essere approvata prima del piano di implementazione.
Il piano dettagliera' modifiche e test di questo blocco. Il servizio prezzi
seguira' il disegno separato `2026-10-04-servizio-prezzi-milano-design.md` e la
sua revisione, senza diventare una dipendenza dell'inventario.
