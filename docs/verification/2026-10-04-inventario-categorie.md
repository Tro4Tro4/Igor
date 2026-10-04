# Verifica inventario e categorie — 4 ottobre 2026

Implementazione dei sei task del [piano approvato](../superpowers/plans/2026-10-04-inventario-categorie.md), sul ramo `claude/android-fridge-monitor-app-vlbci3`. Commit di prodotto: `35539d5`, `e879fac`, `a042939`, `d24063d`, `6202f3d`; test Room e documentazione: `a816038`; correzioni della revisione: **`9a8f52f`**, commit finale di prodotto verificato con build, suite completa e prove Android. Il servizio prezzi rimane un blocco separato, descritto nella [specifica Milano](../superpowers/specs/2026-10-04-servizio-prezzi-milano-design.md).

## Build e test

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain
git -c safe.directory=C:/Temp/Claude/Igor diff --check
git -c safe.directory=C:/Temp/Claude/Igor diff -- app/schemas
```

Esito: **BUILD SUCCESSFUL; 322 test in 48 classi, zero fallimenti, zero errori** (baseline: 304 test, 45 classi). Schema esportato invariato, Room versione 6; nessuna migrazione introdotta. I test verificano classificazione dettagliata, precedenza delle categorie conosciute, conversione dei codici storici, ordine delle corsie, filtri combinati, conteggi, ordinamento deterministico, ripristino dello stato, categoria selezionata rimasta vuota e cambio giorno/soglia. I nuovi test usano Room reale per rileggere categorie vecchie e nuove con quantita', posizione, date, rimozioni logiche e foto delle liste conservate.

## Verifica Android

APK debug installato su emulatore Android API 35, AVD Pixel Fold adattato a schermo compatto **1080 × 2340, densita' 420 dpi**. Emulatore avviato per questa verifica con userdata temporaneo separato; fixture da 0, 1, 30 e 200 record, senza modificare dati personali o aggiungere strumenti di seeding nell'app.

| Scenario | Esito ed evidenza |
| --- | --- |
| Inventario vuoto | Invito ad aggiungere, distinto dal caricamento: [screenshot](inventario-categorie/0-articoli.png) |
| Un articolo | Una sola sezione: [screenshot](inventario-categorie/1-articoli.png) |
| 30 articoli | Sezioni, contatori, nomi e azioni: [chiaro](inventario-categorie/30-articoli-chiaro.png), [scuro](inventario-categorie/30-articoli-scuro.png) |
| 200 articoli | Lista caricata e scorsa fra le sezioni con header persistente: [inizio](inventario-categorie/200-articoli.png), [scorrimento](inventario-categorie/200-articoli-scorrimento.png). Nessuna misura quantitativa di frame/performance |
| Categoria | Selezione Salumi: 3 di 30; [screenshot](inventario-categorie/salumi-filtrati.png) |
| Selettore e tastiera | Ricerca “formaggi”, risultati freschi/stagionati raggiungibili: [screenshot](inventario-categorie/selettore-ricerca-tastiera.png) |
| Nessun risultato | Ricerca senza corrispondenze, reset riporta tutti i 30: [screenshot](inventario-categorie/nessun-risultato.png) |
| Font 200% | Tema scuro, nomi multilinea e controlli raggiungibili in verticale: [screenshot](inventario-categorie/font-200-scuro.png) |
| Ricreazione del processo | Home, `am kill` (PID realmente scomparso), riapertura dello stesso task: ricerca “prosciutto”, Salumi, Frigo e Senza data mantenuti: [screenshot](inventario-categorie/filtri-ripristinati.png) |
| Ritorno dai dettagli | Articolo riclassificato da Salumi a Formaggi freschi e salvato: filtro conservato, risultati aggiornati da 17 a 16 |
| Rotazione | Ricerca e categoria conservate in orizzontale e al ritorno: [screenshot](inventario-categorie/rotazione-filtri.png) |
| Giorno/soglia | Verificati tramite test ViewModel e proiezione pura, senza cambiare l'orologio dell'emulatore |
| Accessibilita' | Ispezione del codice e della gerarchia Android: heading, selezione radio, emoji decorative escluse e azioni etichettate. Prova vocale completa con TalkBack non eseguita |

Limiti: nessuna prova su dispositivo fisico, nessun benchmark del rendering; font 200% verificato anche in orizzontale dopo la correzione descritta sotto. I flussi spesa e scontrino sono coperti dai test ViewModel e dalla compilazione; il controllo manuale aggiuntivo della spesa e' descritto nella revisione finale.

## Decisioni durante l'esecuzione

- Checkout corrente previsto dal piano: commit separati sul ramo esistente; costo se la scelta fosse errata: minore isolamento rispetto a un worktree. Nessun merge o push.
- Aggiornato anche `ShoppingViewModelTest`, consumatore del parser non elencato nei Files del task 2: nuove categorie separano latte e yogurt; ereditarieta' verificata tramite `vm.add`. Costo se errato: aspettative di regressione troppo permissive, mitigato dai test sulla precedenza manuale.

## Revisione indipendente

Revisore indipendente in sola lettura sul range `a000408..a816038`: nessun Critical, due Important, piccoli punti rinviati. Un solo passaggio di correzione, senza seconda revisione.

1. **Altezza ridotta:** ricerca e filtri fissi sottraevano tutta la lista in orizzontale al 200%. Regressione Android osservata: nessuna azione degli articoli raggiungibile dopo cinque scorrimenti. Ora i controlli fanno parte della lista scorrevole; solo gli header di categoria restano sticky. Anche titolo e ricerca del selettore scorrono sopra la tastiera. Lo stato vuoto si misura sul contenuto, per mantenere raggiungibile il reset; anche questa regressione e' stata osservata prima della correzione. Prove RED poi GREEN su emulatore: [azioni al 200%](inventario-categorie/font-200-orizzontale-green.png), [selettore con tastiera al 200%](inventario-categorie/selettore-200-orizzontale-green.png), [reset al 200%](inventario-categorie/reset-200-orizzontale-green.png).
2. **Nome composito:** `insalata con tonno in scatola` e `sugo al tonno sottolio` venivano classificati come conserve, scavalcando il prodotto principale. Il test `conserve di pesce non scavalcano il prodotto principale` fallisce prima della correzione e passa dopo; vere conserve e precedenza dei surgelati restano verificate. Suite completa finale: 322/322, schema invariato.

Digitazione nativa di “prosciutto” senza pause: testo completo e 3 risultati concordano; ricerca senza corrispondenze e azzeramento ripristinano testo vuoto e tutti i 30 record. Selettore spesa verificato manualmente cercando e selezionando “Merendine e snack dolci” ([screenshot](inventario-categorie/selettore-spesa.png)); salvataggio del selettore inventario verificato dai dettagli. Il selettore scontrino resta verificato tramite compilazione e test ViewModel, senza prova OCR manuale in questa sessione.

Punti minori rinviati:

- La ricerca del selettore distingue gli accenti: “caffe” non trova “Caffè, tè e infusi”.
- Le euristiche non coprono ancora tutte le varianti, ad esempio “tonno sott’olio” e “latte bio di soia”; la categoria si puo' scegliere manualmente.

Decisioni sui comportamenti che il revisore non ha giudicato:

- Undo, transazioni e conflitti storici restano nel backlog approvato: rischio residuo dei difetti preesistenti.
- Servizio prezzi separato: confronto prezzi ancora da implementare.
- Prova vocale TalkBack e hardware fisico non certificati: esperienza reale da confermare; semantica e verifica nativa su emulatore documentate.
- Nessun benchmark: fluidita' su dispositivi reali non quantificata.

Gli screenshot dei campioni 1/200, della rotazione e del ripristino precedono il passaggio di correzione; illustrano quei controlli. Chiaro/scuro/font verticale e le nuove regressioni in orizzontale sono stati ripetuti sulla versione finale. Nessuna dichiarazione di verifica empirica su hardware fisico.
