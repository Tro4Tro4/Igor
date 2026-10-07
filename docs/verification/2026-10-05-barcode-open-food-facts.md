# Riconoscimento barcode con Open Food Facts

Il flusso e' incluso nell'APK completo del 7 ottobre insieme ai prezzi
privati: [verifica della correzione](2026-10-07-ripristino-barcode-apk.md).
Le verifiche sotto restano quelle storiche del 5 ottobre.

Flusso circoscritto approvato in chat il 5 ottobre 2026 con brainstorming;
indicazioni Impeccable applicate a Jetpack Compose e Material 3.

## Comportamento

- Ricerca prima nello storico locale, anche per prodotti rimossi.
- Per EAN/UPC validi sconosciuti: lettura puntuale Open Food Facts API v3,
  solo con preferenza attiva. Nessun account o download dell'intero catalogo.
- Nome italiano quando presente, nome generico come ripiego, marca e categoria
  suggerita dal nome o da un insieme esplicito di categorie note della fonte.
  Categorie sconosciute non vengono forzate. Quantita', unita', luogo e scadenza
  non sono dedotti dai dati online.
- I campi scelti manualmente prevalgono anche se modificati durante l'attesa.
  Una seconda scansione o il salvataggio annulla la richiesta precedente.
- Distinti attesa, successo con attribuzione ODbL, nome mancante, prodotto
  assente, errore, limite di richieste, ricerca disattivata e codice non adatto.
- Timeout complessivo di 10 secondi, trasporto con timeout di 8 secondi,
  massimo 15 richieste al minuto per istanza e gestione HTTP 429.
- Dopo il salvataggio, il riconoscimento successivo usa i dati locali offline.
  Non c'e' una cache persistente separata per prodotti non salvati.

## Verifiche

Comando: `gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain`,
con `GRADLE_USER_HOME` impostato alla cache Gradle locale dell'utente.

Test del client con trasporto finto: campi e User-Agent, validazione GTIN,
lingua, dati incompleti, assenza, JSON malformato, codice non corrispondente,
categoria da tassonomia e limiti locali/remoti.
Test del ViewModel: precompilazione, offline dopo il salvataggio, priorita'
locale, consenso spento, modifiche durante l'attesa, scansioni consecutive,
assenza, rete e timeout. DataStore: default spento, indipendenza da Open Prices
e cancellazione delle preferenze.

**BUILD SUCCESSFUL: 356 test in 50 classi, zero fallimenti ed errori.**
Il primo giro aveva compilato il client prima dell'ultima modifica sulla
tassonomia e ha fallito il relativo nuovo test; la ricompilazione finale e la
suite completa sono passate. `git diff --check` superato.

Verifica visiva non completata: l'emulatore Pixel Fold API 35 avviato senza
finestra non ha completato il boot (`sys.boot_completed` vuoto e servizio
window assente). L'istanza avviata per questa prova e' stata chiusa. Non sono
stati installati APK sul dispositivo fisico collegato. Tema scuro, font grandi
e TalkBack restano da verificare sul dispositivo.

Schema Room invariato (versione 6), nessuna nuova dipendenza.

## Fonti tecniche

- [API prodotto v3](https://openfoodfacts.github.io/documentation/docs/Product-Opener/v3/products/get-api-v3-product-code/).
- [Politica API e limiti](https://github.com/openfoodfacts/openfoodfacts-server/blob/main/docs/api/index.md).
- [Catalogo ufficiale](https://huggingface.co/datasets/openfoodfacts/product-database/tree/main):
  `food.parquet` circa 7,88 GB alla consultazione del 5 ottobre 2026;
  questo export non viene scaricato dall'app.

Le risposte sono verificate con fixture; non e' stata eseguita una scansione
fisica end-to-end contro il servizio pubblico.
