# Correzioni di affidabilita' — 4 ottobre 2026

Disegno circoscritto approvato in chat con brainstorming; UI applicata secondo Impeccable,
senza ridisegno. Commit di prodotto verificato: **`67adbd4`**, ramo locale
`claude/android-fridge-monitor-app-vlbci3`. Il servizio prezzi ha una specifica separata
ed e' escluso da questo intervento.

## Correzioni

| Problema | Risultato e prova |
| --- | --- |
| Undo del consumo cancellava una voce gia' spuntata | Snapshot della voce prima e dopo: ripristino dello stesso UUID, dettagli e spunta. Regressione fallita prima e superata dopo |
| Undo sovrascriveva modifiche successive | Validazione di tutte le righe prima di scrivere, dentro la stessa transazione. Conflitto: nessuna modifica e messaggio esplicativo; regressioni consumo e acquisto parziale |
| Undo da una copia vecchia della scheda | Si annullano le modifiche sul record effettivamente letto e scritto, non sulla vecchia copia visualizzata |
| Errori temporanei di Undo | Token mantenuto per riprovare; il conflitto reale resta distinto dagli errori di scrittura |
| Snackbar precedente interferiva con l'ultima operazione | Callback legati all'identificatore del messaggio catturato prima della sospensione; test sul vecchio messaggio |
| Consumo ripetuto della stessa riga | Rilettura nella transazione: articolo gia' rimosso non produce una seconda operazione e non perde il primo Undo |
| Salva premuto piu' volte / quantita' NaN | Guardia sincrona prima della coroutine, pulsante disabilitato durante salvataggio, niente secondo UUID; validazione tramite parseQuantity |
| Errori di aggiunta/salvataggio/eliminazione | Feedback recuperabile, modulo conservato, transazioni completate prima della navigazione; test di errore e retry |
| Operazioni su piu' tabelle | Test su Room reale: errore seconda scrittura annulla anche la prima; Undo multiplo fallito fa rollback e resta ripetibile |
| Falso conflitto dei timestamp | Gli snapshot degli articoli creati si rileggono da Room: il confronto usa la precisione persistita in millisecondi, non i nanosecondi degli oggetti appena creati |
| Categorie manuali sovrascritte | Scanner protegge categoria/unita'/luogo gia' scelti; la categoria della spesa corrente precede lo storico nello scontrino |
| Categoria nuova con posizione dello storico incompatibile | La posizione si eredita solo per la stessa categoria; scegliendo Surgelati si usa Freezer |
| Ricerca sensibile agli accenti | “caffe”, “TE” e accento Unicode composto trovano le categorie corrette, senza cambiare le etichette o le chiavi salvate |
| Varianti commerciali non classificate | Apostrofi dritti/tipografici in sott'olio e qualificatori bio/biologico; conservata precedenza del prodotto principale e dei surgelati |
| Stato obsoleto al ritorno dalla modifica | Riprodotto con una pausa superiore a 5 secondi: osservazione Eagerly per la durata del ViewModel, rilasciata quando questo viene eliminato; test sia inventario sia spesa |

## Build e test

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain
git -c safe.directory=C:/Temp/Claude/Igor diff --check
git -c safe.directory=C:/Temp/Claude/Igor diff -- app/schemas
```

**BUILD SUCCESSFUL — 344 test, 49 classi, zero fallimenti, zero errori.**
Baseline precedente: 322 test, 48 classi. Schema Room invariato, versione 6;
nessuna migrazione o dipendenza nuova. Le regressioni funzionali sono state osservate
prima delle rispettive correzioni; i test Room aggiuntivi verificano rollback e retry.
La revisione conclusiva del diff e' stata eseguita dall'agente principale, senza altri subagenti.

## Android

Emulatore API 35, Pixel Fold con schermo compatto 1080 × 2340 e 420 dpi, 2048 MB,
SwiftShader software, APK debug. Userdata e fixture temporanei separati dai dati personali.

- Aggiunta attraverso la UI, attesa di oltre 6 secondi nella modifica, salvataggio e
  primo controllo della schermata ritornata: articolo presente senza polling o attesa
  aggiuntiva dopo il comando Salva. [Screenshot](correzioni-bug/salvataggio-ritorno.png).
- Consumo e Annulla: articolo nuovamente presente. [Screenshot](correzioni-bug/undo-consumo.png).
- Aggiunta rapida “latte bio di soia”: voce nella sezione Alternative vegetali.
- Ricerca “caffe” nel selettore: “Caffè, tè e infusi” visibile.
  [Screenshot](correzioni-bug/ricerca-senza-accenti.png).
- Campione di 200 articoli, scorrimento, tema chiaro e scuro/font 200%:
  [inizio](correzioni-bug/inventario-200-chiaro.png),
  [scorrimento](correzioni-bug/inventario-200-scorrimento.png),
  [font ingranditi](correzioni-bug/font-200-scuro.png).

La durata osservata fra preparazione del tap e lettura XML dopo il salvataggio e'
8,656 secondi **inclusi due dump UiAutomator e processi adb**: non misura la latenza
interna dell'app e non va presentata come benchmark del salvataggio.

## Accessibilita' e prestazioni: limiti reali

TalkBack installato e attivato: `dumpsys accessibility` conferma servizio collegato,
`FEEDBACK_SPOKEN`, touch exploration e gestione del doppio tocco. Proprietà dei controlli
verificate anche dalla gerarchia e dal codice: header, radio selezionabili, icone
decorative escluse e azioni distinte. Gli input sintetici/UiAutomation possono aggirare
o sospendere l'esplorazione tattile: l'apertura di una scheda tramite adb non certifica
il gesto TalkBack reale. **Audio e gesti completi non certificati; nessun dispositivo fisico disponibile.**
L'emulatore e' avviato senza audio. Stato e comandi di verifica sono conservati negli
[estratti diagnostici](correzioni-bug/accessibility-status.txt).

Misura `dumpsys gfxinfo ... framestats` dopo reset, 12 swipe da 300 ms, stesso emulatore:

| Superficie | Frame | Janky | Mediana | P95 |
| --- | ---: | ---: | ---: | ---: |
| Igor, 200 articoli | 121 | 93 (76,86%) | 65 ms | 150 ms |
| Android Settings, controllo | 202 | 147 (72,77%) | 69 ms | 150 ms |

Entrambe le superfici mostrano molti ritardi. Il confronto suggerisce un contributo
forte dell'ambiente con rendering software; non dimostra che l'app sia fluida su un
telefono, ne' isola tutto il costo dell'app. Gli indicatori GPU riportano 4950 ms
anche per Settings e non sono una misura GPU affidabile in questa configurazione.
I dati reali sono conservati: [Igor](correzioni-bug/gfxinfo-igor.txt),
[Settings](correzioni-bug/gfxinfo-settings.txt). Nessuna garanzia quantitativa di fluidita'
su hardware reale; questa verifica resta da eseguire sul dispositivo.
