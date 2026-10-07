> Ritirato il 7 ottobre 2026: Igor usa solo i prezzi degli scontrini.
> Codice conservato come archivio; workflow acquisizione/deploy disabilitati.
> Worker privato e D1 conservati, scollegati dall'app. Le istruzioni sotto
> descrivono la precedente configurazione e non autorizzano una riattivazione.

# Servizio prezzi Igor

Python 3.12, API v1 di sola lettura, SQLite e acquisizione giornaliera separata.
Dal percorso `services/prices`:

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.lock
.venv/Scripts/python.exe -m pytest -q
.venv/Scripts/python.exe -m igor_prices serve --host 127.0.0.1 --port 8765
.venv/Scripts/python.exe -m igor_prices ingest --source carrefour
```

L'ultimo comando rifiuta le fonti candidate. Il manifest iniziale conserva
le sei catene, senza abilitare cataloghi non validati. Ogni attivazione richiede
le evidenze del piano: riutilizzo, campioni, due letture e territorio.
Gli adattatori Carrefour/Conad sono testati su HTML sintetico, non certificati
come listini riutilizzabili. Esselunga e' la prima estensione.

`GET /v1/sources`, `/v1/products?q=latte&postcode=20125` (oppure `gtin=`),
`/v1/products/{product_id}/offers?postcode=20125`. Massimo 20 candidati,
`next_offset` esplicito. Decimal come stringa, centesimi interi, date con fuso.
Una fonte generica non certifica disponibilita' nel CAP richiesto.

Nessuna API avvia acquisizioni. I log HTTP sono disabilitati per non registrare
ricerche. Il job CLI puo' essere pianificato una volta al giorno, solo dopo
validazione; il registro SQLite impedisce due lotti della stessa fonte/giorno.
Un errore mantiene il lotto precedente, un 403 sospende la fonte. Nessun login,
CAPTCHA, account, lista completa o scontrino viene inviato alle catene.

Il server locale e l'eventuale prova USB non costituiscono hosting autonomo.
Pubblicazione HTTPS e costi richiedono una scelta separata dell'utente.
