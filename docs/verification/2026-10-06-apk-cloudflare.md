# APK Android collegato a Cloudflare

Questo APK e' stato successivamente sostituito sul telefono dall'APK
con accesso privato: [verifica uso personale](2026-10-06-prezzi-uso-personale.md).

6 ottobre 2026. APK preparato dai commit verificati fino a `5347e74`,
senza includere le modifiche locali non committate. Archivio Git estratto
nella cartella ignorata `.tools/android-online-prices-2026-10-06/source`.

## Verifica

Comando dalla copia isolata, JDK 17 di Android Studio e SDK esistente:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain -PigorOnlinePricesUrl=https://igor-prices.andreatro.workers.dev
```

Build riuscita in 15m16s. **363 test in 55 classi, zero failure/error/skipped**,
conteggi letti dai report XML di questa build. Messaggi dei decoder su immagini
corrotte prodotti dai test, senza test falliti. Deprecazioni Gradle esistenti.

`BuildConfig.ONLINE_PRICES_URL` generato contiene l'URL HTTPS reale.
GET remoto con lo User-Agent esatto del client Android,
`Igor/0.1.0 (Android)`: 200, sei fonti candidate. GET ricerca latte per
20125 con identificativo Igor: 200, catalogo vuoto. Una richiesta diagnostica
con lo User-Agent predefinito Python aveva risposto 403; le richieste con
gli identificativi Igor rispondono correttamente. Nessuna impostazione
Cloudflare modificata durante queste prove.

## Artefatto

`build/deliverables/igor-cloudflare-debug-2026-10-06.apk`

SHA256:
`5ce631d3f37406ab4186df475e4894346223f0eadd5c91376905c6be17644b14`

L'URL e' specifico di questa compilazione. Le build senza la proprieta'
continuano a usare il default vuoto, come previsto dal progetto.

## Installazione sul telefono

Telefono Samsung SM-A536B collegato e autorizzato via USB il 6 ottobre 2026.
Installazione di aggiornamento completata con esito `Success`, senza
disinstallare l'app:

```powershell
adb -s RZCW10JE18H install -r build/deliverables/igor-cloudflare-debug-2026-10-06.apk
```

Avvio di `com.igor.fridge.debug/com.igor.fridge.MainActivity` con
`am start -W`: `Status: ok`. Processo ancora presente al controllo successivo.
Non sono stati modificati i consensi dell'utente. La conservazione delle
singole voci e il confronto online non sono stati verificati visivamente.

## Passaggi rimasti

L'APK collegato non rende disponibili listini inesistenti: fonti candidate,
job giornaliero spento e zero offerte restano lo stato reale. Le
[richieste del feed](2026-10-06-richieste-feed-prezzi.md) sono pronte ma
non inviate. La risposta delle catene e la validazione territoriale restano
necessarie per completare il confronto con prezzi reali.
