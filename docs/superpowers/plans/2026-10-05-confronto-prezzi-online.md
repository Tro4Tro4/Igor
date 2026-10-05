# Confronto prezzi online Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** confrontare la lista con prezzi online reali di Carrefour e Conad per 20125, preparando l'estensione prioritaria Esselunga e mantenendo Tigros, Lidl ed Eurospin nel perimetro.

**Architecture:** servizio Python con adattatori, archivio SQLite e API v1 di sola lettura; cache Room e confronto locale Android. Le due parti condividono il contratto definito nel task 2: svilupparle in sequenza evita due implementazioni incompatibili. La validazione delle fonti e la raggiungibilita' del servizio sono condizioni di completamento, non sostituibili con fixture.

**Tech Stack:** Python 3.12, FastAPI, SQLite, pytest; Kotlin 2.0, Jetpack Compose Material 3, Room, DataStore, WorkManager, trasporto HTTP esistente. Per HTML usare Beautiful Soup; versioni delle dipendenze fissate dopo verifica della compatibilita' con Python 3.12, in un ambiente virtuale locale al servizio.

**Spec:** `docs/superpowers/specs/2026-10-05-confronto-prezzi-online-design.md`, approvata in chat, e disegno generale `docs/superpowers/specs/2026-10-04-servizio-prezzi-milano-design.md`.

## Global Constraints

- Python 3.12; minSdk 26, targetSdk/compileSdk 35; JDK 17+; Kotlin 2.0.
- CAP iniziale **20125**; fonti della prima versione Carrefour e Conad; Open Prices escluso dalla nuova integrazione.
- Esselunga e' la prima estensione, con verifica durante i task iniziali; Tigros, Lidl ed Eurospin rimangono nel perimetro.
- Preferenza dedicata inizialmente spenta; nessun account Igor; inventario, lista completa, scontrini e foto non inviati al servizio.
- Un aggiornamento al giorno per fonte; massimo due tentativi; nessun bypass di login/CAPTCHA/blocchi o percorsi esclusi da robots.
- Fino a 48 ore prezzo aggiornato; dopo 48 ore e fino a 7 giorni vecchio; oltre 7 giorni solo storico cache. Offerte terminate escluse subito.
- Migrazione Room 6->7, schemi esportati e conservazione dei dati; niente migrazione distruttiva.
- Confronti su insieme comune; confezioni intere arrotondate per eccesso; niente totale inventato senza formato.
- Testi UI in italiano in strings.xml, commenti Kotlin senza accenti; tema Material 3 esistente, accessibilita', chiaro/scuro e font grandi.
- Nessun hosting, costo, contatto commerciale o invio di credenziali autorizzato da questo piano.
- Conservare le modifiche barcode gia' presenti nella working tree: non fare reset/stash automatici o includere file estranei nei commit.

## Review Focus

1. Cambio del CAP durante un download: una risposta per la vecchia zona non sostituisce quella nuova (task 6).
2. Cambio nome/marca di una voce gia' associata: l'associazione precedente non si applica al nuovo prodotto (task 6).
3. JSON-LD InStock ma pagina senza zona con indisponibilita': disponibilita' sconosciuta, non certezza di acquisto (task 3).
4. SKU numerico con checksum GTIN casualmente valido: non diventa identita' canonica senza evidenza della fonte (task 2/3).
5. Consenso revocato mentre WorkManager scarica: risultato scartato, lavoro annullato e nessuna nuova richiesta (task 6/8).

---

## Struttura dei file

Backend in `services/prices/`, senza modificare il progetto Gradle:

- `pyproject.toml`, `requirements.lock`, `README.md`: ambiente riproducibile, dipendenze fissate, comandi.
- `igor_prices/models.py`: contratto dati v1; `normalize.py`: soldi, GTIN, formati.
- `igor_prices/access.py`: fonti ammesse, URL e robots; `transport.py`: timeout, dimensione massima e ritardi.
- `igor_prices/adapters/base.py`, `carrefour.py`, `conad.py`: estrazione della fonte.
- `igor_prices/store.py`: transazioni e versioni; `ingest.py`: esecuzioni e pubblicazione.
- `igor_prices/api.py`: sola lettura; `__main__.py`: comandi serve/ingest.
- `tests/test_normalize.py`, `test_access.py`, `test_adapters.py`, `test_store.py`, `test_api.py` e `tests/fixtures/`: test isolati senza chiamate live in CI.

Android, prefisso `app/src/main/java/com/igor/fridge/`:

- `data/onlineprices/OnlinePricesModels.kt`, `OnlinePricesJson.kt`, `OnlinePricesClient.kt`: contratto e HTTP.
- `data/local/OnlineOffer.kt`, `OnlineBinding.kt`, `OnlineSource.kt`, `OnlinePricesDao.kt`: cache e associazioni.
- `data/repository/OnlinePricesRepository.kt`: aggiornamenti, selezione e cancellazione.
- `domain/prices/OnlineComparison.kt`: costo e copertura comuni, separati dal confronto storico.
- `ui/compare/OnlineCompareViewModel.kt`, `OnlinePricesSection.kt`: sezione integrata nella schermata esistente.
- `notification/OnlinePricesWorker.kt`, `OnlinePricesWorkScheduler.kt`: aggiornamento periodico.
- Modifiche mirate a `IgorDatabase.kt`, `SettingsStore.kt`, `AppContainer.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `CompareScreen.kt`, `DataExport.kt`, `strings.xml` e relativi test.

## Task 1: validare le fonti iniziali e verificare Esselunga

**Files:** aggiornare `docs/verification/2026-10-05-fonte-carrefour-20125.md`, creare `2026-10-05-fonte-conad-20125.md`, aggiornare `2026-10-05-fonte-esselunga-20125.md`; creare `docs/verification/2026-10-05-fonti-online-manifest.json`.

**Interfaces:** produce manifest con fonte, URL prodotti verificati, stato accesso/riutilizzo, scope territoriale, evidenza GTIN, limitazioni e timestamp. Le fonti non validate non possono essere abilitate dal task 4.

- [ ] Rileggere i tre rapporti. Per Carrefour/Conad selezionare almeno dieci prodotti ciascuna includendo una marca comune (Barilla/Ferrero), un multipack, un'offerta carta e un peso variabile. Usare link dal catalogo pubblico e sitemap consentite, mai endpoint esclusi.
- [ ] Consultare nel browser ordinario anonimo la selezione della zona 20125. Registrare se richiede indirizzo/account e distinguere catalogo generico da catalogo territorializzato; non creare account o immettere dati personali.
- [ ] Fare due letture separate delle schede, confrontare prezzo visibile/JSON-LD/formato/condizioni e documentare accesso e riutilizzo. Per una fonte non attivabile registrare il motivo ed esaminare feed ufficiali compatibili, senza inviare richieste a terzi.
- [ ] Verificare subito Esselunga: la prima HTTP 200 restituisce una shell Angular di 6962 caratteri senza JSON-LD. Controllare il catalogo con browser ordinario e le sitemap consentite; non usare parametri freevisit o percorsi ricerca/auth/visit esclusi da robots. Ripetere la verifica dei termini della fonte.
- [ ] Salvare il manifest con valori verificati. Esempio di stato iniziale, che deve restare inattivo finche' mancano le evidenze:

```json
{"schema_version":1,"sources":[
  {"id":"carrefour","status":"candidate","priority":1,"scope":"generic","reuse_verified":false,"product_urls":[]},
  {"id":"conad","status":"candidate","priority":2,"scope":"generic","reuse_verified":false,"product_urls":[]},
  {"id":"esselunga","status":"candidate","priority":3,"scope":"unknown","reuse_verified":false,"product_urls":[]},
  {"id":"tigros","status":"candidate","priority":4,"scope":"unknown","reuse_verified":false,"product_urls":[]},
  {"id":"lidl","status":"candidate","priority":5,"scope":"unknown","reuse_verified":false,"product_urls":[]},
  {"id":"eurospin","status":"candidate","priority":6,"scope":"unknown","reuse_verified":false,"product_urls":[]}
]}
```

- [ ] Controllare che ogni attivazione abbia evidenze nel rapporto, non semplicemente HTTP 200. Committare solo i rapporti/manifest con `docs: verifica accesso e cataloghi online iniziali`.

## Task 2: definire e testare il contratto canonico

**Files:** creare ambiente/backend, `models.py`, `normalize.py`, `tests/test_normalize.py`, `tests/test_contract.py`, `docs/verification/online-prices-v1.json` come esempio di risposta; definire gli stessi nomi wire nei modelli Kotlin del task 5.

**Interfaces:** `money_cents(text: str) -> int`; `normalize_gtin(text: str) -> str | None`; `parse_pack(text: str) -> Pack | None`; modello `Offer` serializzato in JSON. Date UTC ISO-8601, Decimal in stringa, valuta EUR. `product_id` uguale a `gtin:<GTIN senza padding ridondante>` soltanto per GTIN verificati; altrimenti `<source>:<sku>`. Conservare anche il GTIN originale come stringa.

- [ ] Scrivere i test rossi, inclusi euro con virgola e multipack:

```python
from decimal import Decimal
from igor_prices.normalize import money_cents, parse_pack

def test_euro_e_multipack():
    assert money_cents("€ 1,39") == 139
    assert money_cents("1.234,56 €") == 123456
    pack = parse_pack("2 x 500 g")
    assert pack.amount == Decimal("1")
    assert pack.unit == "KG"
    assert pack.pack_count == 2
```

- [ ] Eseguire `python -m pytest tests/test_normalize.py tests/test_contract.py -q` da `services/prices`, attendendo import fallito prima dell'implementazione.
- [ ] Implementare modelli Pydantic v2 con identificativi obbligatori e limiti. Forma wire fissata:

```python
from datetime import datetime, date
from decimal import Decimal
from typing import Literal
from pydantic import BaseModel, Field

class Pack(BaseModel):
    amount: Decimal = Field(gt=0)
    unit: Literal["KG", "L", "PZ"]
    pack_count: int = Field(default=1, ge=1)

class Offer(BaseModel):
    offer_id: str
    product_id: str
    source: str
    source_sku: str
    gtin: str | None = None
    gtin_verified: bool = False
    name: str
    brand: str | None = None
    pack: Pack | None = None
    pack_price_cents: int = Field(gt=0)
    currency: Literal["EUR"] = "EUR"
    observed_at: datetime
    source_url: str
    scope: Literal["generic", "postcode"]
    postcode: str | None = None
    availability: Literal["available", "unavailable", "unknown"]
    condition: Literal["ordinary", "loyalty", "coupon", "multi_buy", "unknown"]
    valid_until: date | None = None
```

`Offer` non usa productKey(nome) come identita'. Validare timestamp con timezone, postcode obbligatorio solo per scope postcode, GTIN verificato coerente con product_id, URL HTTPS del dominio della fonte. `parse_pack` converte g/ml in kg/l, tratta virgole e multipack; testo ambiguo/peso variabile senza quantita' certa produce None. Non scambiare prezzo unitario per prezzo confezione.

- [ ] Aggiungere test per zeri iniziali/UPC-EAN equivalenti, checksum errato, SKU non verificato, importi negativi/non finiti, peso variabile, formati mancanti, condizioni e timezone. Fissare dipendenze compatibili e rieseguire test; commit `feat: definisce contratto e normalizzazione prezzi online`.

## Task 3: adattatori Carrefour/Conad e trasporto controllato

**Files:** `access.py`, `transport.py`, `adapters/base.py`, `carrefour.py`, `conad.py`, `tests/test_access.py`, `tests/test_adapters.py`; fixture sanitizzate dai campioni autorizzati del task 1, senza cookie/token/dati personali.

**Interfaces:** `HttpResult(status: int, body: str, headers: dict[str,str], final_url: str)`; `Transport.get(url: str) -> HttpResult`; `Adapter.parse(body: str, url: str, observed_at: datetime, scope: str, postcode: str | None) -> list[Offer]`. Sorgenti e URL provengono solo dal manifest verificato, non dall'utente/API.

- [ ] Testare su HTML ridotto il disaccordo di disponibilita', non affidandosi solo al JSON-LD:

```python
def test_disponibilita_discordante(carrefour_adapter, now):
    body = '''<script type="application/ld+json">{"@type":"Product",
      "name":"Latte 1 L","sku":"locale-1","offers":{"price":"1.39",
      "priceCurrency":"EUR","availability":"https://schema.org/InStock"}}
      </script><p>Prodotto al momento non disponibile</p>'''
    offers = carrefour_adapter.parse(body,
        "https://www.carrefour.it/p/latte/locale-1.html", now, "generic", None)
    assert offers[0].availability == "unknown"
    assert offers[0].pack_price_cents == 139
    assert offers[0].gtin_verified is False
```

- [ ] Eseguire test rossi `python -m pytest tests/test_access.py tests/test_adapters.py -q`.
- [ ] Estrarre JSON-LD Product anche dentro liste/@graph, normalizzare Offer senza confondere AggregateOffer. Carrefour: SKU/MPN non bastano per GTIN; Conad: campo gtin verificato separato dallo SKU. Usare il testo della pagina per formato e condizioni; condizioni carta/coupon/quantita' ignote escludono il prezzo dal totale ordinario. Una data JSON-LD sola non certifica fine promo.

```python
from bs4 import BeautifulSoup
import json

def jsonld_documents(body: str) -> list[object]:
    soup = BeautifulSoup(body, "html.parser")
    documents = []
    for script in soup.find_all("script", attrs={"type": "application/ld+json"}):
        documents.append(json.loads(script.get_text()))
    return documents
```

Implementare ricerca ricorsiva di @type Product e Offer nei documenti; una scheda rotta segnala errore del lotto, non prodotto da zero centesimi. Trasporto con User-Agent Igor, timeout totale 15 s, risposta massima 2 MB, almeno 1 s fra richieste della stessa fonte, redirect solo HTTPS su domini ammessi. Robots verificato prima di accesso; 403 sospende, 429 rispetta Retry-After entro budget 120 s, due tentativi solo per 429/5xx/rete.

- [ ] Coprire redirect verso localhost/dominio estraneo, body eccessivo, robots escluso, 403/429, JSON non valido, carta, formato ambiguo, SKU checksum valido ma non GTIN, timezone. Nessuna rete nei test. Verificare campioni live separatamente solo per fonti ammesse; commit `feat: legge e valida prezzi Carrefour e Conad`.

## Task 4: archivio atomico, ingestione e API v1

**Files:** `store.py`, `ingest.py`, `api.py`, `__main__.py`, `tests/test_store.py`, `tests/test_api.py`, README servizio.

**Interfaces:** `CatalogStore.replace_batch(source: str, offers: list[Offer], observed_at: datetime) -> int` restituisce versione monotona; `sources() -> list[dict]`; `search(q: str | None, gtin: str | None, postcode: str, limit: int, offset: int) -> list[Offer]`; `offers(product_id: str, postcode: str) -> list[Offer]`. Query SQL parametrizzate; nessun URL da API. Fonti non attive escluse dai risultati, ma visibili nello stato.

- [ ] Testare idempotenza, rollback e fonte inattiva:

```python
def test_lotto_ripetuto_non_duplica(store, offer, now):
    store.replace_batch("carrefour", [offer], now)
    store.replace_batch("carrefour", [offer], now)
    assert len(store.offers(offer.product_id, "20125")) == 1
```

Per rollback preparare un lotto con due offerte, far fallire la seconda scrittura e verificare che prima offerta e observed_at del lotto precedente restino identici. `replace_batch` usa una transazione SQLite con commit solo dopo validazione completa.

- [ ] Eseguire test rossi. Implementare tabelle `sources`, `offers` (chiave source+source_sku+scope+postcode), `runs`; timestamp di controllo distinto da observed_at. Conservare l'ultimo lotto valido su errore. Job giornaliero invocabile da CLI; mutex per fonte e registro impediscono doppia ingestione nello stesso giorno UTC. Contesto generico restituito anche per CAP 20125, sempre marcato generic.
- [ ] Implementare FastAPI con `GET /v1/sources`, `GET /v1/products?q=...&postcode=20125&limit=20&offset=0`, alternativa `gtin=...` e `GET /v1/products/{product_id}/offers?postcode=20125`. Envelope:

```json
{"schema_version":1,"catalog_version":1,"generated_at":"2026-10-05T10:00:00Z","postcode":"20125","items":[],"next_offset":null}
```

Richiedere q o gtin, non entrambi; q 2..100 caratteri, CAP cinque cifre ASCII, limit 1..20, offset >=0. HTTP 422 per input invalido, 404 per prodotto assente, 503 se archivio indisponibile; nessuna risposta finge lista valida vuota in caso di errore. API non avvia scraping. Disabilitare access log con query; nessun identificativo dispositivo richiesto.

- [ ] Testare isolamento CAP, paginazione, ricerca SQL con apostrofi, prodotto assente, fonte sospesa, errore del disco, schema_version. Documentare `python -m igor_prices serve --host 127.0.0.1 --port 8765` e `python -m igor_prices ingest --source carrefour`; server locale senza attivare hosting. Test completi e commit `feat: pubblica catalogo prezzi online versionato`.

## Task 5: cache Room e client Android

**Files:** modelli/client/JSON onlineprices, quattro file Room indicati in Struttura; modificare IgorDatabase/AppContainer/DataExport; test `data/OnlinePricesJsonTest.kt`, `OnlinePricesRepositoryTest.kt`, `MigrationTest.kt`, `data/export/DataExportTest.kt`, schema versione 7 generato.

**Interfaces:** `OnlinePricesClient.sources(): List<OnlineSource>`; `search(query: String?, gtin: String?, postcode: String, offset: Int = 0): OnlinePage`; `offers(productId: String, postcode: String): List<OnlineOffer>`. Trasporto HTTP esistente iniettato, baseUrl HTTPS configurata; eccezioni distinte da risultati vuoti. Tipo wire come task 2, BigDecimal per amount, Instant per observedAt e LocalDate per validUntil.

Room: `OnlineOffer` PK offerId+requestedPostcode, campi snake wire convertiti camelCase, formato nullable; `OnlineSource` PK source, status e ultima acquisizione; `OnlineBinding` PK shoppingUuid+source, productId, selectedNameKey, selectedBrandKey e selectedPackAmount/unit. Associazioni per fonte permettono confermare un abbinamento senza GTIN senza fondere prodotti diversi. DAO osserva per CAP e UUID; migrazione aggiunge solo le nuove tabelle e indici, senza alterare FoodItem/PriceRecord/ShoppingItem.

Definire anche `OnlinePage(items: List<OnlineOffer>, nextOffset: Int?, catalogVersion: Long)` e `OnlinePricesSettings(enabled: Boolean = false, postcode: String = "20125")`. Stato fonte `OnlineSource(source: String, status: String, priority: Int, lastSuccessfulAt: Instant?)`; conservare separatamente in Room le descrizioni/limitazioni provenienti dall'API. Binding include `equivalenceConfirmed: Boolean`: senza GTIN comune, un abbinamento solo testuale non entra nel totale prima della conferma. Alternative esplicite rimangono fuori dal confronto di prodotti identici.

- [ ] Scrivere test rossi per versione JSON non supportata, zeri GTIN, Decimal, prezzo mancante, CAP errato e roundtrip Room. Esempio:

```kotlin
@Test fun `gtin conserva gli zeri nella cache`() {
    val offer = OnlinePricesJson.parseOffers(validEnvelopeWithGtin("0000080050865")).single()
    assertEquals("0000080050865", offer.gtin)
}
```

Definire `validEnvelopeWithGtin(code: String): String` nel file di test come envelope del task 4 con un Offer completo; i fake restituiscono sempre gli stessi campi del contratto, non modelli ad hoc.

- [ ] Implementare parsing JSON manuale come OpenPricesJson, schema_version strettamente 1, timezone obbligatoria, URL HTTPS, importi/formati validati. Risposta generica ha requestedPostcode della richiesta e scope generic della fonte: non falsificare postcode sorgente.
- [ ] Implementare migrazione 6->7, registrarla in build; test Room che apra DB6 con food/shopping/price/barcode reali, confronti UUID/importi/marca/rimozioni e aggiunga cache. Estendere export con cache e associazioni; clearAllTables elimina nuove tabelle, preferenze azzerate come gia' accade.
- [ ] Eseguire test mirati e build KSP, ispezionare schema 7 generato; commit `feat: aggiunge cache locale e client prezzi online` con schema e test migrazione.

## Task 6: repository e associazioni protette

**Files:** `OnlinePricesRepository.kt`, SettingsStore, AppContainer, test repository e SettingsStore; fake DAO `data/FakeOnlinePricesDao.kt`.

**Interfaces:** preferenza `onlinePrices: Flow<OnlinePricesSettings(enabled=false, postcode="20125")>` e setter validati. Repository `observeOffers(postcode): Flow<List<OnlineOffer>>`, `observeBindings(): Flow<List<OnlineBinding>>`, `searchCandidates(item: ShoppingItem): List<OnlineOffer>`, `select(itemUuid, offerId)`, `unselect(itemUuid, source)`, `refresh(): RefreshResult`, `clear()`. Inject settings, client, DAO, ShoppingRepository, Transactor, clock; nessuna dipendenza Android nel calcolo.

Le funzioni repository search/select/unselect/refresh/clear sono suspend; `RefreshResult(updatedSources: Set<String>, errors: Map<String,String>, discarded: Boolean)` non confonde errori con zero prezzi. `SettingsStore.setOnlinePricesEnabled(enabled: Boolean)` e `setOnlinePostcode(postcode: String)` sono suspend. Per CAP invalido il setter rifiuta la scrittura e il ViewModel conserva il testo con errore.

- [ ] Testare revoca e cambio CAP durante richiesta usando CompletableDeferred:

```kotlin
@Test fun `risposta per cap precedente non sostituisce la cache corrente`() = runTest {
    val pending = CompletableDeferred<List<OnlineOffer>>()
    val fixture = repositoryFixture(fetchOffers = { pending.await() })
    fixture.repository.refreshIn(backgroundScope)
    runCurrent()
    fixture.settings.setOnlinePostcode("20126")
    pending.complete(listOf(fixture.offer(postcode = "20125")))
    advanceUntilIdle()
    assertTrue(fixture.dao.forPostcode("20126").isEmpty())
}
```

Il test definisce `repositoryFixture` nel proprio file con fake impostazioni/client/DAO e il dispatcher test; `refreshIn` e' helper test che lancia `repository.refresh()` nello scope passato, non API di produzione.

- [ ] Eseguire test rossi. Implementare Mutex per refresh e timeout complessivo 30 s; catturare settings iniziali, ricontrollare consenso/CAP prima della transazione. Se cambiati scartare risultato. Eccezioni conservano cache precedente; risultati parziali non diventano lotti completi. Download per singoli prodotti scelti, senza inviare lista completa.
- [ ] `select` rilegge voce nel Transactor e salva chiavi di nome/marca/formato correnti; osservazione invalida binding se voce cancellata o nome/marca cambia. Non sovrascrivere prezzo o negozio della spesa. Con GTIN verificato cercare equivalente nelle altre fonti; senza GTIN proporre candidati e chiedere scelta per fonte.
- [ ] Testare due tap/refresh, revoca consenso, voce cancellata mentre si seleziona, nome/marca cambiati, barcode ambiguo condiviso da nomi diversi, retry e clear. Commit `feat: protegge aggiornamenti e abbinamenti prezzi online`.

## Task 7: confronto corretto e ViewModel

**Files:** OnlineComparison.kt, OnlineCompareViewModel.kt; test `domain/prices/OnlineComparisonTest.kt`, `ui/OnlineCompareViewModelTest.kt`.

**Interfaces:** `compareOnline(items: List<ShoppingItem>, bindings: List<OnlineBinding>, offers: List<OnlineOffer>, now: Instant): OnlineComparison`; risultato con quote per UUID/fonte, current/old, copertura, insieme comune e totali nullable. `OnlineCompareViewModel` riceve repository, shopping, settings, clock, computeDispatcher; unico StateFlow, azioni search/select/unselect/refresh e messaggi distinti.

Contratti Kotlin del risultato, definiti in OnlineComparison.kt:

```kotlin
data class OnlineQuote(
    val shoppingUuid: String,
    val source: String,
    val offerId: String,
    val totalCents: Long?,
    val freshness: OnlineFreshness,
)
enum class OnlineFreshness { CURRENT, OLD, EXPIRED }
data class OnlineEstimate(
    val source: String,
    val covered: Int,
    val commonTotalCents: Long?,
)
data class OnlineComparison(
    val quotes: List<OnlineQuote> = emptyList(),
    val estimates: List<OnlineEstimate> = emptyList(),
    val commonItemUuids: Set<String> = emptySet(),
)
data class OnlineCompareUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val settings: OnlinePricesSettings = OnlinePricesSettings(),
    val comparison: OnlineComparison = OnlineComparison(),
    val candidates: Map<String, List<OnlineOffer>> = emptyMap(),
    val sources: List<OnlineSource> = emptyList(),
    val message: String? = null,
) {
    val hasWinner: Boolean get() = comparison.commonItemUuids.isNotEmpty() &&
        comparison.estimates.count { it.commonTotalCents != null } >= 2
}
```

I tipi UiState vivono nel file ViewModel, non nel dominio; il blocco mostra il
contratto condiviso e non richiede collocare UI nel dominio. Con due totali
pari mostrare parita', senza attribuire un vincitore unico.

- [ ] Test rosso arrotondamento confezioni e insieme comune:

```kotlin
@Test fun `settecentocinquanta grammi richiedono due confezioni da mezzo chilo`() {
    val cents = packCost(BigDecimal("0.75"), BigDecimal("0.5"), 99L)
    assertEquals(198L, cents)
}
```

- [ ] Implementare `packCost(required: BigDecimal, pack: BigDecimal, priceCents: Long): Long` con precondizioni finite/positive e calcolo verificato:

```kotlin
fun packCost(required: BigDecimal, pack: BigDecimal, priceCents: Long): Long {
    require(required > BigDecimal.ZERO && pack > BigDecimal.ZERO && priceCents > 0)
    val packs = required.divide(pack, 0, RoundingMode.CEILING).longValueExact()
    return Math.multiplyExact(packs, priceCents)
}
```

PZ significa numero di confezioni solo dopo selezione di formato. Unita' kg/l convertite da g/ml; unita' incompatibili o formato assente escludono il costo, senza fallback 1. Escludere condizioni diverse da ordinary dal totale standard e indicarle a parte. Filtrare offerte scadute e applicare soglie 48 ore/7 giorni a clock iniettato. Data futura anomala esclusa.

- [ ] Calcolare l'intersezione degli UUID coperti per fonti attive con quote correnti coerenti. I totali comparabili usano solo quell'intersezione; la copertura totale resta visibile separatamente. Non assegnare vincitore con intersezione vuota. Prezzo mine/community non sostituisce online; confronto storico esistente rimane separato.
- [ ] Testare multipack, PZ frazionari arrotondati, kg/l, overflow, offerte carta terminate, cache vecchia, fonte unica, lista vuota, parita', copertura 2/10 contro 8/10. ViewModel calcola con flowOn(computeDispatcher), test dispatcher esplicito; errori/retry non perdono selezioni. Commit `feat: confronta prezzi online su prodotti equivalenti`.

## Task 8: UI Android e WorkManager

**Files:** OnlinePricesSection.kt, CompareScreen, SettingsScreen/ViewModel, strings.xml, OnlinePricesWorker/WorkScheduler, AppContainer; test `ui/OnlineCompareViewModelTest.kt`, `notification/OnlinePricesWorkerTest.kt`.

**Interfaces:** `OnlinePricesSection(state: OnlineCompareUiState, onRefresh: () -> Unit, onSearch: (String) -> Unit, onSelect: (String,String) -> Unit, onOpenSource: (String) -> Unit)`; `OnlinePricesWorkScheduler.sync(enabled: Boolean)` e worker con repository iniettato tramite factory/manual DI coerente con worker esistente.

- [ ] Usare Impeccable e leggere craft-floor immediatamente prima delle modifiche UI; seguire guida Android. Mantenere Compose/Material 3 e identita' esistente, aggiungere sezione online alla schermata Confronta.
- [ ] Testare che stato fonte inattiva non diventi un prezzo 0 e che lista generica richieda la selezione:

```kotlin
@Test fun `fonte senza prezzi non appare gratuita`() = runTest(dispatcher) {
    val fixture = onlineViewModelFixture(activeSources = emptyList())
    val state = fixture.vm.uiState.first { !it.loading }
    assertTrue(state.comparison.estimates.isEmpty())
    assertFalse(state.hasWinner)
}
```

Definire helper `onlineViewModelFixture` nel test con repository fake e scope/dispatcher test. Nome stati wire/UiState coerente col task 7; evitare callback di vecchie ricerche che aggiornano la nuova voce.

- [ ] Implementare opt-in e CAP nelle impostazioni, selettore candidati con nome/marca/formato, fonte/data/scope, mostrare storico/costi attuali separati. Righe catene inattive: Esselunga per prima, poi Tigros/Lidl/Eurospin, con stato reale. Aggiorna disabilitato durante refresh; errori recuperabili, offline con cache e data; collegamento fonte nel browser usando HTTPS validato, senza aprire automaticamente pagine.
- [ ] WorkManager: uniquePeriodicWork ogni 24 ore con rete connessa; cancellare quando disattivato e ricontrollare consenso nel worker e repository. Worker non legge query/lista nei log. Retry per errore transitorio, failure per schema invalido; revoca durante attesa scarta risultato. Usare componenti Material etichettati, live region solo per feedback, touch 48 dp, colori tema e testi in risorse.
- [ ] Test worker/consenso/cancellazione e build, nessun test HTML/CSS come prova nativa; commit `feat: integra confronto online e aggiornamenti in Android`.

## Task 9: servizio raggiungibile e verifica end-to-end

**Files:** README servizio/progetto, rapporto `docs/verification/2026-10-05-confronto-prezzi-online.md`, configurazione build debug separata se necessaria.

Configurazione concreta: `app/build.gradle.kts` abilita BuildConfig e legge la
proprieta' Gradle `igorOnlinePricesUrl`; default release vuoto significa
servizio non configurato. Debug puo' usare `http://localhost:8765` per la prova
USB. Creare `app/src/debug/AndroidManifest.xml` e
`app/src/debug/res/xml/online_prices_network_security.xml` che consentano HTTP
solo al dominio localhost. Non abilitare cleartext nel manifest principale.
Per la prova USB eseguire `adb -s <seriale verificato> reverse tcp:8765 tcp:8765`;
rimuovere il reverse a fine prova. Il client riconosce il servizio non
configurato senza tentare richieste a URL vuoti.

**Interfaces:** base URL configurato del client deve servire contratto v1. Per sviluppo locale: tunnel USB con adb reverse a porta 8765 e HTTP solo in build debug, limitato a localhost; release accetta solo HTTPS. L'uso via USB e' una prova, non servizio quotidiano autonomo.

- [ ] Eseguire suite backend e `gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain`; impostare GRADLE_USER_HOME alla cache locale quando serve. Verificare migrazione 6->7 e diff schemi. Non aumentare la suite dopo il pass senza cambi o problemi nuovi.
- [ ] Verificare API reale: due fonti attive, prodotto comune con GTIN/formato confermato, generico selezionato manualmente, multipack, condizione carta e errore di una fonte. Catalogo generico identificato come tale; nessuna copertura 20125 inventata.
- [ ] Rendere concreto un ambiente HTTPS per l'uso autonomo se l'utente dispone di hosting. In assenza, preparare opzioni con requisiti/costi reali prima di chiedere la relativa approvazione; non attivare costi o servizi. Segnalare esplicitamente che il requisito di uso quotidiano e' incompleto se manca l'endpoint.
- [ ] Installazione sul telefono solo nell'ambito autorizzato della verifica: verificare seriale con adb devices, usare install -r senza cancellare dati e non toccare dispositivi estranei. Prova cache offline, consenso revocato e ripresa; screenshot nativi chiaro/scuro/font 1.3 con ripristino impostazioni a fine prova. Controllare dati locali originali dopo migrazione.
- [ ] Aggiornare documenti con test eseguiti, fonti attive e limiti, dati personali invariati. Commit `docs: verifica confronto online Android e servizio`; nessuna dichiarazione di completamento se accesso fonte o hosting mancano.

## Task 10: estensione, prima Esselunga

**Files:** aggiornare rapporto Esselunga e aggiungere rapporti Tigros/Lidl/Eurospin; aggiornare manifest. Creare adattatori concreti solo dopo aver identificato un contratto/fixture riutilizzabile della fonte, riusando modelli/trasporto/archivio dei task 2-4.

**Interfaces:** ogni nuova fonte implementa esattamente `Adapter.parse` e produce Offer v1. Nessun cambiamento del contratto Android per il solo inserimento di una catena; l'elenco fonti viene dal servizio, con priorita' iniziali stabilite dal manifest.

- [ ] Riprendere per prima la verifica Esselunga dal task 1. Se validata, preparare la scheda di adattatore con URL/identificativi/formati/condizioni realmente rilevati e i relativi test fixture, poi implementarla nel medesimo ciclo rosso-verde dei task 3-4. Non inventare endpoint dietro la shell Angular.
- [ ] Per ciascuna catena applicare dieci campioni, due acquisizioni, territorio e riutilizzo prima dell'attivazione. Lidl puo' avere solo offerte pubblicate: dichiarare copertura promozionale, non listino completo. Per ogni nuova fonte richiedere confronto manuale di prodotto e prezzo e test di sospensione, senza sommare prezzi vecchi/condizionati come attuali.
- [ ] Ordine successivo: Tigros, Lidl, Eurospin. Se un accesso dipende da una fonte non disponibile, documentarlo e continuare sulle altre: nessuna fonte mancante riceve prezzi stimati. La sola implementazione di un adattatore generico non chiude questo task.
- [ ] Ogni fonte validata ha commit dedicato, test backend e verifica che app la mostri con data/copertura reali. Aggiornare README e stato fonti senza richiedere all'utente di ripetere l'importanza delle catene gia' concordate.

## Revisione del piano e handoff

Copertura specifica: fonti/accesso task 1/3/10; contratto/privacy task 2/4/6;
persistenza/migrazione/export task 5; matching/task 6; totali/freschezza task 7;
UI/offline/lavoro periodico task 8; distribuzione/verifica reale task 9.
I cinque Review Focus hanno test assegnati nei task 2/3/6/8.

Prerequisiti esterni espliciti: accesso riutilizzabile delle fonti e endpoint
raggiungibile; non sono risolti con fixture. Non bloccano sviluppo/test locali,
ma bloccano la dichiarazione di confronto online quotidiano completato.

Metodo consigliato: **Native**, implementazione sequenziale in questa sessione
con revisione finale indipendente; backend e Android condividono un contratto
stretto, quindi ridurre passaggi di contesto aiuta. Alternativa:
**Subagent-driven**, implementatore e revisore per task con costo superiore.
L'utente deve revisionare questo piano e scegliere il metodo prima del codice,
come richiedono brainstorming e writing-plans. Nessuna nuova approvazione
della specifica gia' accettata e' necessaria.
