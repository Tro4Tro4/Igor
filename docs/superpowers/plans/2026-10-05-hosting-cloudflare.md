# Hosting gratuito Cloudflare Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pubblicare l'API prezzi Igor su Workers Free e D1, con acquisizione Python giornaliera tramite GitHub Actions, conservando contratto Android e integrita' del catalogo.

**Architecture:** Worker TypeScript di sola lettura e lotti D1 immutabili con puntatore corrente per fonte. Il publisher Python usa l'API REST D1; la pubblicazione finale e' un singolo UPDATE con trigger transazionali, cosi' non dipende da transazioni HTTP fra chiamate distinte. GitHub Actions esegue il publisher e i parser esistenti; FastAPI locale resta disponibile.

**Tech Stack:** Python 3.12/httpx/Pydantic esistenti; Node.js 24 LTS locale portabile; TypeScript, Wrangler, Vitest e pool Workers compatibile; D1 SQLite; GitHub Actions standard Linux. Risolvere versioni compatibili dai registri ufficiali all'avvio e bloccarle nel package-lock prima di commit; non usare latest nei workflow.

**Spec:** `docs/superpowers/specs/2026-10-05-hosting-cloudflare-design.md` (approvata con «s»).

## Global Constraints

- API `schema_version: 1`, CAP iniziale `20125`, `limit` 1..20; contratto Android preservato.
- Prezzi centesimi interi JSON, Decimal stringa, mai conversione dei payload in Number JavaScript.
- Catalogo pilota massimo 2.000 offerte complessive correnti; 20 offerte per batch di upload; job massimo 30 minuti.
- Schedule `17 4 * * *` UTC, concurrency unica senza cancellare importazioni.
- Fonti candidate restano candidate, nessun catalogo sintetico in produzione, Esselunga prima estensione.
- Piano Free esclusivo, nessun acquisto, dominio a pagamento, cambio visibilita' repo o modifica automatica fatturazione.
- Nessun dato inventario sul server, token nell'APK, log con ricerche o credenziali.
- Conservare lotto corrente e precedente, cleanup degli altri dopo almeno 24 ore.
- Preservare modifiche barcode non committate; staging selettivo e nessun reset/stash indiscriminato.
- Pubblicazione remota richiede accessi reali; assenza credenziali non equivale a successo.

## Review Focus

1. Pubblicazione interrotta o risposta HTTP persa: vecchio lotto visibile, retry idempotente senza versione doppia (task 1/4).
2. Fonte sospesa durante upload e manifest ancora active: niente riattivazione o offerte pubblicate (task 1/4/5).
3. Centesimi oltre 2^53, LIKE Unicode e GTIN con zeri: nessun arrotondamento o identita' inventata (task 2/3/7).
4. Due fonti rispettano ciascuna il limite ma insieme superano 2.000: rifiuto prima dell'upload e guardia atomica finale (task 1/4).
5. Credenziali assenti, quota esaurita o runner nuovo: errore esplicito, nessun catalogo cancellato e nessuna spesa automatica (task 3/5/6/8).

## Struttura e comandi

Worker: `services/prices-cloudflare/{src,test,migrations,scripts}`. Python:
`services/prices/igor_prices/cloudflare/{client,publisher,job,cli}.py`.
Contratto sintetico condiviso: `services/prices-contract/fixtures.json`.

Nel task 1 predisporre `.tools/node24/` e aggiungerlo solo al PATH del processo;
scaricare distribuzione Windows x64 dal sito Node ufficiale, verificare SHA256
con SHASUMS ufficiale, estrarre entro workspace. Non installare globalmente.
Ignorare `.tools/`, `node_modules/`, `.wrangler/`, `.dev.vars`, config generata
e output di build; non ignorare migrazioni, lockfile o test.

Comandi ripetuti (PowerShell, radice workspace):

```powershell
$env:PATH = "$PWD/.tools/node24;$env:PATH"
npm.cmd --prefix services/prices-cloudflare run typecheck
npm.cmd --prefix services/prices-cloudflare test
services/prices/.venv/Scripts/python.exe -m pytest services/prices/tests -q -c services/prices/pyproject.toml
git -c safe.directory=C:/Temp/Claude/Igor diff --check
```

Per pytest preferire `workdir=services/prices` e `.venv/Scripts/python.exe -m
pytest -q` se il pythonpath del comando dalla radice non risolve il pacchetto.
Git commit solo sui file elencati dal task; escludere file condivisi con OFF
se non cambiati dal lavoro hosting. Registrare RED/GREEN e prove remote nel
ledger `.superpowers/sdd/2026-10-05-hosting-cloudflare/progress.md`.

### Task 1: Schema D1 e invarianti atomiche

**Files:** Create `services/prices-cloudflare/package.json`, `package-lock.json`,
`tsconfig.json`, `vitest.config.ts`, `wrangler.local.jsonc`,
`migrations/0001_catalog.sql`, `test/schema.test.ts`, `test/helpers.ts`;
modify `.gitignore` solo per artefatti hosting.

**Interfaces:** `applySchema(db: D1Database): Promise<void>`;
`seedSource(db, id, status): Promise<void>` test-only.
DB tables `sources(id,status,priority,metadata,current_batch,last_successful_at,digest)`,
`offer_batches(id,source,digest,expected_count,status,created_at,published_at)`,
`offers(batch_id,source,sku,scope,postcode,offer_id,product_id,gtin,name_key,payload)`,
`catalog(id=1,version)`, `runs(source,day,status,checked_at)`.

- [ ] Predisporre Node portabile verificato e pacchetti test Workers: dependency pins e script `test=vitest run`, `typecheck=tsc --noEmit`, `dry-run=wrangler deploy --dry-run --config wrangler.local.jsonc`. Config locale con DB binding, `compatibility_date=2026-10-05`, osservabilita' disattivata e nessun account reale. Seguire configurazione pool ufficiale della versione risolta, senza inventare opzioni.
- [ ] Scrivere test RED che applica migrazione mancante e verifica che un UPDATE al puntatore di un lotto incompleto fallisca e non incrementi versione:

```ts
it('non pubblica un lotto incompleto', async () => {
  await seedSource(env.DB, 'carrefour', 'active');
  await env.DB.prepare(`INSERT INTO offer_batches
    (id,source,digest,expected_count,status,created_at)
    VALUES ('b','carrefour','digest',2,'staging','2026-10-05T00:00:00Z')`).run();
  await expect(env.DB.prepare(`UPDATE sources SET current_batch='b'
    WHERE id='carrefour'`).run()).rejects.toThrow();
  expect(await env.DB.prepare('SELECT version FROM catalog WHERE id=1')
    .first('version')).toBe(0);
});
```

- [ ] Eseguire `npm test -- schema` e osservare errore migrazione/simboli mancanti.
- [ ] Implementare PK/FK/UNIQUE(batch_id,offer_id), UNIQUE(batch_id,source,sku,scope,postcode), CHECK status e JSON valido; postcode indicizzato come stringa vuota per generic. Trigger BEFORE UPDATE OF current_batch verifica fonte active, batch stessa fonte, numero righe=expected_count>0 e totale corrente<=2000. Trigger AFTER UPDATE OF current_batch, solo quando cambia, marca batch pubblicato, assegna last_successful_at/digest e incrementa catalog.version con CHECK <=9007199254740991. Trigger AFTER UPDATE status incrementa versione solo se cambia. Trigger BEFORE DELETE batch vieta corrente; FK offers ON DELETE CASCADE.

```sql
CREATE TRIGGER guard_publication BEFORE UPDATE OF current_batch ON sources
WHEN NEW.current_batch IS NOT OLD.current_batch
BEGIN
  SELECT CASE WHEN NEW.status != 'active' OR NOT EXISTS (
    SELECT 1 FROM offer_batches b WHERE b.id=NEW.current_batch AND b.source=NEW.id
      AND b.expected_count>0
      AND b.expected_count=(SELECT COUNT(*) FROM offers WHERE batch_id=b.id)
  ) THEN RAISE(ABORT,'batch incomplete or source inactive') END;
  SELECT CASE WHEN (
    SELECT COUNT(*) FROM offers o JOIN sources s ON o.batch_id=s.current_batch
      WHERE s.id != NEW.id
  ) + (SELECT COUNT(*) FROM offers WHERE batch_id=NEW.current_batch) > 2000
    THEN RAISE(ABORT,'catalog limit') END;
END;
CREATE TRIGGER publish_version AFTER UPDATE OF current_batch ON sources
WHEN NEW.current_batch IS NOT OLD.current_batch
BEGIN
  UPDATE offer_batches SET status='published',published_at=NEW.last_successful_at
    WHERE id=NEW.current_batch;
  UPDATE catalog SET version=version+1 WHERE id=1;
END;
```

Lo switch publisher imposta current_batch, digest e last_successful_at nello
stesso UPDATE. Il trigger usa questi valori e verifica che coincidano con
il lotto dichiarato; aggiungere tale verifica alla guardia e provarne il
rifiuto quando digest/data non corrispondono. Timestamp created_at del lotto
e observed_at della fonte devono essere definiti distintamente se necessari.
- [ ] Aggiungere RED/GREEN per sospensione durante staging, lotto completo, tentativo di superare totale2000 e rollback del trigger. Per limite globale usare due fonti con 1200+1000 offerte: secondo switch deve fallire, prima fonte resta invariata.
- [ ] Eseguire test schema nel runtime Workers e typecheck; commit `feat: add atomic D1 catalog schema`.

### Task 2: API Worker compatibile v1

**Files:** Create `src/index.ts`, `src/input.ts`, `src/catalog.ts`,
`src/envelope.ts`, `test/api.test.ts`, `services/prices-contract/fixtures.json`;
create `services/prices/tests/test_shared_contract.py`.

**Consumes:** schema task1. **Produces:** default ExportedHandler con
`fetch(request, env:{DB:D1Database}): Promise<Response>`;
`readCatalog(db, query:CatalogQuery): Promise<CatalogResponse>`;
`CatalogQuery` discriminated union sources/search/offers;
`CatalogResponse={version:number,payloads:string[],nextOffset:number|null}`;
`jsonEnvelope(result, postcode:string|null, now:Date): Response`.
`publishFixture(db:D1Database, override:{pack_price_cents?:string}):Promise<void>`
e' un helper solo test, definito in `test/helpers.ts`; importare `env` e `SELF`
da `cloudflare:test` e `it/expect` da Vitest negli esempi.

- [ ] Scrivere RED con fixtures condivise: sei candidate, ricerca nome+marca non contigua, CAP diverso escluso, wildcard `%/_` letterali, GTIN verificato e fonti sospese, paginazione21 record. JSON contiene campioni dichiarati sintetici e non viene referenziato dai comandi deploy.

```ts
it('preserva centesimi Long come JSON testuale', async () => {
  await publishFixture(env.DB, {pack_price_cents: '9223372036854775807'});
  const response = await SELF.fetch('https://igor.test/v1/products?q=latte');
  expect(await response.text()).toContain('"pack_price_cents":9223372036854775807');
});
```

`publishFixture` test-only inserisce payload con intero JSON grezzo, poi cambia
puntatore per i trigger task1; non serializzare il numero con JS Number.

- [ ] Eseguire API test RED; implementare request parser q 2..100 trimmed, GTIN checksum8/12/13/14 canonicalizzato, CAP ASCII5, limite1..20, offset integer sicuro >=0; q/gtin esclusivi, input duplicato ambiguo rifiutato422, metodo diverso GET405, route sconosciuta404. Non emettere stacktrace in503.
- [ ] Implementare due SELECT nello stesso `DB.batch`: versione e righe, JOIN sources current_batch e status active, filtro generic/CAP. Escape SQL LIKE e tutti i token; JS lowercase query e nome casefold Python: aggiungere caso caratteri accentati e documentare differenze Unicode residue se non identiche al servizio locale. LIMIT+1 per next_offset, sources metadata ordinati. SQL costante eccetto numero placeholder dei token, nessun input interpolato.

```ts
export function jsonEnvelope(r: CatalogResponse, postcode: string|null, now: Date) {
  const prefix = JSON.stringify({schema_version:1,catalog_version:r.version,
    generated_at:now.toISOString(),postcode,next_offset:r.nextOffset});
  return new Response(prefix.slice(0,-1)+',"items":['+r.payloads.join(',')+']}',
    {headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
}
```

- [ ] Riutilizzare fixtures in FastAPI test senza cambiare API locale: assert stessi campi/offerte/ordinamento per input condivisi, normalizzando solo generated_at. In Workers simulate errore DB=>503, non200vuoto; verifica versione e righe atomiche con switch durante lettura.
- [ ] Eseguire suite Worker e Python; commit `feat: expose compatible read-only prices API on Workers`.

### Task 3: Client D1 REST e configurazione publisher

**Files:** Create `igor_prices/cloudflare/__init__.py`, `client.py`,
`tests/test_d1_client.py` in servizio Python.

**Produces:** `D1Client(account_id:str,database_id:str,token:str,transport=None)`;
`query(sql:str, params:list[object]|None=None)->D1Result` dove
`D1Result` contiene `results:list[dict]`, `changes:int`, `rows_read:int`,
`rows_written:int`, `size_after:int|None`; `D1Error` sanitized.
`from_environment()->D1Client` legge CF_ACCOUNT_ID, CF_DATABASE_ID,
CF_D1_API_TOKEN, mai effettua una chiamata se mancanti o malformati.

- [ ] Scrivere RED MockTransport per query parametrica, JSON success:false HTTP200,429/503 retry,403 senza retry, timeout e body errore contenente token.

```python
def test_token_non_compare_negli_errori(monkeypatch):
    secret = 'test-secret-never-log'
    client = D1Client('a'*32, '00000000-0000-4000-8000-000000000001', secret,
      transport=httpx.MockTransport(lambda _:httpx.Response(403,text=secret)))
    with pytest.raises(D1Error) as error:
        client.query('SELECT 1')
    assert secret not in str(error.value)
```

- [ ] Run `.venv/Scripts/python.exe -m pytest tests/test_d1_client.py -q`, osservare RED.
- [ ] Implementare POST fisso `https://api.cloudflare.com/client/v4/accounts/{account}/d1/database/{db}/query`, body `{sql,params}`, Bearer token. Validare account di 32 caratteri hex e database UUID, non seguire redirect; timeout 15 secondi, al massimo due tentativi per 429, 5xx ed errori rete, Retry-After massimo 120 secondi. Query scritture ritentabili solo se idempotenti definite dai task 4/5; senza certezza non ripetere automaticamente SQL arbitrario. Risultato REST deve contenere un risultato success valido: errori protocollo=>D1Error. Nessun log request/body/header.

```python
reply = session.post(
    f'https://api.cloudflare.com/client/v4/accounts/{account_id}/d1/database/{database_id}/query',
    headers={'Authorization':f'Bearer {token}'},
    json={'sql':sql,'params':params or []}, timeout=15, follow_redirects=False)
if reply.status_code != 200:
    raise D1Error(f'D1 HTTP {reply.status_code}')
wire = reply.json()
if wire.get('success') is not True or len(wire.get('result',[])) != 1:
    raise D1Error('D1 response invalid')
result = wire['result'][0]
if result.get('success') is not True:
    raise D1Error('D1 query failed')
```

Per non ritentare scritture arbitrarie, aggiungere parametro keyword
`retry_safe:bool=False` a query; publisher lo imposta solo per letture,
INSERT OR IGNORE coerenti e UPDATE idempotenti. Query con risposta JSON
malformata produce D1Error sanitizzato senza body originale.
- [ ] Aggiungere test environment assente e response inattesa; GREEN e commit `feat: add authenticated D1 publisher client`.

### Task 4: Publisher, prenotazione e cleanup

**Files:** Create `publisher.py`, `tests/test_d1_publisher.py`,
`tests/d1_sql_harness.py` in Python.

**Consumes:** D1Client e schema task1, Offer esistente.
**Produces:** `Publisher(client)`, `sync_sources(manifest_sources)->None`,
`sources()->list[dict]`, `claim_run(source,now)->bool`,
`finish_run(source,now,status)->None`, `suspend(source)->None`,
`publish(source,offers:list[Offer],now)->int`, `cleanup(now)->dict`.

- [ ] Harness test esegue il SQL reale del publisher su sqlite3 con migrazione condivisa; nessuna reimplementazione dei trigger. Scrivere RED interruzione dopo primo chunk di upload: puntatore e versione restano vecchi; retry completo pubblica una sola volta.

```python
def test_switch_non_avviene_con_chunk_mancante(publisher, remote, offers):
    previous = publisher.publish('carrefour', offers[:1], NOW)
    remote.fail_after_uploads = 1
    with pytest.raises(D1Error):
        publisher.publish('carrefour', newer(offers, count=21), TOMORROW)
    assert remote.current_prices('carrefour') == [offers[0].pack_price_cents]
    assert remote.version() == previous
```

`newer` genera Offer validate con SKU/offer_id unici, timestamp nuovo; `remote`
e' harness che interrompe chiamate SQL e legge puntatori reali, non mock della
logica publisher. La prova runtime D1 corrispondente e' nel task7.

- [ ] Implementare digest SHA256 di payload ordinati per offer_id, batch ID deterministico fonte+digest, Pydantic revalidation e unicita' prima HTTP. Prima staging leggere numero offerte correnti delle altre fonti e rifiutare totale > 2000; trigger finale protegge da concorrenti. Stesso digest corrente ritorna versione senza scritture.
- [ ] INSERT OR IGNORE batch; upload chunk da 20 usando parametro JSON di righe e `INSERT ... SELECT ... FROM json_each(?)` con json_extract dei campi; payload e' stringa JSON interna (mai Number). INSERT OR IGNORE rende retry chunk idempotente; verificare righe gia' presenti coerenti col digest. Switch singolo UPDATE sources.current_batch condizionato, con trigger task1: nessun BEGIN/COMMIT fra richieste HTTP. Dopo risposta persa rileggere puntatore prima di ritentare. Versione letto dopo switch, nessun incremento lato Python.

```python
UPLOAD_SQL = '''INSERT OR IGNORE INTO offers
  (batch_id,source,sku,scope,postcode,offer_id,product_id,gtin,name_key,payload)
SELECT ?,json_extract(value,'$.source'),json_extract(value,'$.sku'),
  json_extract(value,'$.scope'),json_extract(value,'$.postcode'),
  json_extract(value,'$.offer_id'),json_extract(value,'$.product_id'),
  json_extract(value,'$.gtin'),json_extract(value,'$.name_key'),
  json_extract(value,'$.payload') FROM json_each(?)'''
rows = [{'source':o.source,'sku':o.source_sku,'scope':o.scope,
  'postcode':o.postcode or '', 'offer_id':o.offer_id,'product_id':o.product_id,
  'gtin':o.product_id[5:] if o.gtin_verified else None,
  'name_key':(o.name+' '+(o.brand or '')).casefold(),
  'payload':o.model_dump_json()} for o in offers]
for start in range(0,len(rows),20):
    client.query(UPLOAD_SQL,[batch_id,json.dumps(rows[start:start+20])],retry_safe=True)
client.query('''UPDATE sources SET current_batch=?,digest=?,last_successful_at=?
  WHERE id=? AND current_batch IS NOT ?''',
  [batch_id,digest,now.isoformat(),source,batch_id],retry_safe=True)
```

Test del rifiuto batch incoerente prima delle scritture e dopo staging. Non
considerare INSERT OR IGNORE prova che payload diversi siano equivalenti:
verificare digest/count contro le righe effettive prima dello switch.
- [ ] sync_sources valida reuse_verified peractive e mantiene suspended anche se manifest active; aggiornamento metadata non cancella lotti. claim INSERT OR IGNORE runs(source,day...) con changes, versione non cambia; finish update idempotente. suspend singoloUPDATE triggerstatus=>version. No sorgenti arbitrarie fuoriDOMAINS.
- [ ] Cleanup elimina solo batch non correnti, non precedente pubblicato per fonte, creati prima now-24h, in numero limitato per chiamata; CASCADE offers. Test vecchio corrente, precedente, staging recente e vecchio; nessun delete di tables/catalog/sources.
- [ ] RED/GREEN risposta persa dopo commit, fonte sospesa prima switch, totale 1200+1000, doppia claim stesso giorno UTC, fonte candidata attivata senza reuse rifiutata, prezzo Long preservato. Suite Python; commit `feat: publish D1 price batches atomically`.

### Task 5: Job Python remoto con stato persistente

**Files:** Create `job.py`, `cli.py`, `tests/test_cloudflare_job.py`;
modify `igor_prices/store.py` aggiungendo export read-only solo se necessario.

**Consumes:** Publisher e ingest esistente; **Produces:**
`run_daily(publisher, manifest:dict, *, now:datetime, ingest_fn=ingest)->JobSummary`;
`JobSummary` conteggi attempted/succeeded/skipped/failed (nessun token/query).
`python -m igor_prices.cloudflare.cli daily --manifest PATH` e `sync-sources`.
`CatalogStore.source_offers(source)->list[Offer]` legge lotti locali senza
alterare APIsearch; utilizzato solo dopo ingest riuscito.

- [ ] Scrivere RED fonte sospesa remota con manifest active non chiama ingest; tutte le sei candidate job termina con exit 0 e zero richieste alle catene; runner nuovo conserva sospensione e prenotazioni.

```python
def test_candidate_non_avviano_rete(remote_publisher, candidate_manifest):
    def forbidden(*args, **kwargs):
        raise AssertionError('acquisizione fonte candidata')
    result = run_daily(remote_publisher, candidate_manifest, now=NOW, ingest_fn=forbidden)
    assert result.attempted == 0
    assert result.failed == 0
```

- [ ] Implementare TemporaryDirectory per SQLite, sincronizzare il manifest e leggere lo stato remoto; applicare le sospensioni prima di ingest. Prenotare il run D1 prima di SQLite; la prenotazione locale separata usa lo stesso giorno UTC. Per una fonte active validata con adattatore esistente chiamare ingest_fn, poi source_offers e publish. Un errore di rete conserva il lotto remoto; se lo stato locale risulta suspended, inviare suspend remoto anche dopo l'eccezione. Il mancato aggiornamento dello stato remoto deve produrre exit non zero. Continuare le fonti indipendenti, ma result.failed>0 implica exit1. Nessuna validazione o attivazione di nuovi listini in questo task.

```python
with TemporaryDirectory(prefix='igor-prices-') as folder:
    local = CatalogStore(Path(folder)/'catalog.sqlite3')
    local.configure_sources(remote_sources)
    for config in remote_sources:
        source = config['id']
        if config['status'] != 'active' or not config.get('reuse_verified'):
            continue
        if not publisher.claim_run(source,now):
            continue
        try:
            ingest_fn(local,source,now=now)
            publisher.publish(source,local.source_offers(source),now)
            publisher.finish_run(source,now,'success')
        except Exception:
            state = next(s['status'] for s in local.sources() if s['source']==source)
            if state == 'suspended':
                publisher.suspend(source)
            publisher.finish_run(source,now,'failed')
            # JobSummary registra failure; CLI restituisce exit1.
```

remote_sources deriva dal manifest validato con gli stati operativi D1
applicati; usa chiave `id` per CatalogStore e non elimina fonti candidate.
JobSummary e CLI implementano conteggi e messaggi sanitizzati, senza
intercettare un errore di suspend come se fosse un import riuscito.
- [ ] RED/GREEN errore del parser che conserva il catalogo precedente;403 che propaga la sospensione remota;errore publisher che non registra successo; secondo job nello stesso giorno senza scraping; runner nuovo il giorno successivo che riparte. Configurazione o environment mancante fallisce prima di SQLite e rete. Suite completa Python GREEN e commit `feat: run persistent daily price imports against D1`.

### Task 6: Configurazione deploy e workflow gratuiti

**Files:** Create `services/prices-cloudflare/scripts/configure.mjs`,
`scripts/configure.test.mjs`, `README.md`, `.github/workflows/prices-check.yml`,
`prices-deploy.yml`, `prices-daily.yml`; generated ignored `wrangler.deploy.jsonc`.

**Produces:** configure script legge CF_ACCOUNT_ID e CF_DATABASE_ID validati,
genera config con binding DB, compatibility_date fissata, logging disattivato,
name=igor-prices e workers_dev=true.
Variabili workflow `CF_ACCOUNT_ID`, `CF_DATABASE_ID`, `IGOR_PRICES_URL`,
`IGOR_PRICES_DAILY_ENABLED`; secrets `CF_DEPLOY_API_TOKEN`, `CF_D1_API_TOKEN`.

- [ ] RED test Node `node --test scripts/configure.test.mjs` con environment mancante o malformato: non crea config e non stampa credenziali. Poi implementare generazione deterministica in JSON.

```js
const config = {name:'igor-prices',main:'src/index.ts',compatibility_date:'2026-10-05',
  workers_dev:true,observability:{enabled:false},
  d1_databases:[{binding:'DB',database_name:'igor-prices',database_id:databaseId}],
  account_id:accountId};
```

- [ ] Workflow di verifica su modifiche hosting/backend e workflow_dispatch: checkout, Node 24, Python 3.12, npm ci, typecheck, test Worker e Python, dry-run locale. Fissare azioni a SHA verificandola release ufficiale prima di commit.
- [ ] Workflow deploy solo workflow_dispatch, permission contents: read, timeout 15 minuti, concurrency prices-deploy, generazione configurazione, test,migrazioni D1 remote,Python sync-sources poi Wrangler deploy; nessuna inizializzazione con fixture. Esplicitare l’ordine e non eseguire nuovo deploy se test o migrazioni falliscono. Non mettere token in argomenti CLI, solo environment degli step necessari. Il deploy del manifest candidate inizializza soltanto le fonti senza pubblicare prodotti sintetici.
- [ ] Workflow giornaliero schedule e workflow_dispatch, job condizionato vars.IGOR_PRICES_DAILY_ENABLED=='true', permission contents: read, timeout 30 minuti, concurrency prices-import e cancel-in-progress: false, installazione Python dal lockfile e avvio CLI. Schedule gate inizialmente false, attivabile dopo verifica del piano gratuito, fatturazione e quote, nessuno scraping di candidate. Nessun artifact SQL o catalogo completo con URL/token; summary solo conteggi. Documentare il requisito della branch predefinita.

```yaml
on:
  schedule:
    - cron: '17 4 * * *'
  workflow_dispatch:
permissions:
  contents: read
concurrency:
  group: prices-import
  cancel-in-progress: false
jobs:
  daily:
    if: vars.IGOR_PRICES_DAILY_ENABLED == 'true'
    runs-on: ubuntu-latest
    timeout-minutes: 30
```

- [ ] Eseguire test della configurazione, typecheck, Wrangler dry-run e verifica YAML con parser che supporti GitHub on e tipi delle azioni. Verificare manualmente segreti, protezioni e piano Free. Commit `ci: prepare free Workers deployment and daily imports`.

### Task 7: Integrazione locale e misura quote

**Files:** Create `services/prices-cloudflare/test/publication.test.ts`,
`scripts/pilot.mjs`, `docs/verification/2026-10-05-hosting-cloudflare.md`.

**Consumes:** schema, Worker,SQL del publisher; **Produces:** evidenze runtime reali
Miniflare/D1 locale e metriche pilot, nettamente distinte da Cloudflare remoto.

- [ ] RED Worker test semina lotto precedente e staging parziale con SQL publisher condiviso (esportare dati SQL necessaria come fixture generata dai test Python, mai produzione), ricerca restituisce il lotto precedente; switch finale rende visibile il nuovo lotto; retry dopo risposta persa conserva la versione; sospensione restituisce 404 per il prodotto e sources mostra suspended. Usare le fixture del contratto del task 2 senza duplicare la logica di pubblicazione.
- [ ] Verificare GREEN per schema, API e trigger SQL,errore del batch D1 rollback e lettura coerente di versione e dati, oltre testi Unicode, query massima di 100 caratteri e product_id codificato nella URL. Se il limite D1 LIKE di 50 byte per pattern colpisce query lunga, usa `instr(name_key,?)>0` per token (stessa semantica letterale) anziche' cambiare il contratto v1; aggiornare i test wildcard compatibili. Non troncare le ricerche.
- [ ] Pilot con 2.000 offerte sintetiche, pubblicazione di due lotti e cleanup: misurare rows_read, rows_written e size_after dai metadata D1, query per nome, GTIN e 20 risultati, durata documentata. Simulare risposta persa e quota esaurita senza avanzamento del catalogo. Il tempo totale locale non certifica il limite CPU Cloudflare di 10 ms; misurare la CPU remota solo con account reale e fixture separate dalla produzione.

```powershell
npm.cmd --prefix services/prices-cloudflare run typecheck
npm.cmd --prefix services/prices-cloudflare test
npm.cmd --prefix services/prices-cloudflare run dry-run
```

- [ ] Eseguire suite completa Python, verificare diff --check e riportare API candidate senza listini reali. Commit `test: verify D1 publication and free-tier pilot limits`.

### Task 8: Accessi reali e pubblicazione HTTPS

**Files:** Modify `services/prices-cloudflare/README.md` e report del task 7.
Nessun segreto o identificativo del database reale deve essere committato.

**Prerequisites:** tutti i task locali verdi; accesso account Cloudflare Free e GitHub,
budget che blocca uso extra se una carta e’ presente. Richiedere gli accessi dopo aver preparato gli artefatti.

- [ ] Verificare accessi disponibili con gh auth status e Wrangler whoami senza stampare token; se l'account Cloudflare non e' configurato, chiedere login nel browser o segreti inseriti direttamente in GitHub, **mai token in chat**. Se impossibile, marcare task incomplete e riportare istruzioni esatte: creare account Free, token D1 Edit limitato all'account e token deploy Workers Scripts Edit con letture account necessarie, variabili e segreti del task 6. Niente account a nome dell'agente.
- [ ] Confermare piano Free e progetto: `igor-prices` Worker, `igor-prices` D1. Creazione database con Wrangler documentata, senza altri servizi. L'utente ha autorizzato la soluzione Free; dopo le approvazioni procedurali previste creare il database, applicare migrazioni, inizializzare sei candidate e distribuire il codice verificato. Nessuna attivazione delle fonti.
- [ ] Smoke HTTPS `/v1/sources`, `/v1/products?q=latte&postcode=20125`, CAP invalido 422, prodotto assente 404, POST 405; controllare URL workers.dev e TLS. Catalogo vuoto corretto, senza attribuirgli una verifica di prezzi reali. Usare `--fail-with-body` solo con risposte di servizio senza segreti.
- [ ] Esecuzione manuale giornaliera con candidate senza HTTP verso le catene: report successo e sei fonti saltate. Verificare assenza segreti nel summary, logging disattivato e piano Free. Attivare enabled=true solo dopo verifica quote e budget account, e workflow sulla branch predefinita revisionato. Nessun merge autonomo per attivare la schedule: se il workflow e' ancora sulla branch feature, preparare un risultato revisionabile; il merge richiede approvazione finale sul risultato concreto.
- [ ] Registrare URL, ID run workflow e numero fonti nel report, commit documentazione; task completo solo con HTTPS raggiungibile e job reale verificato. Se mancano accessi resta incomplete; non avviare task 9.

### Task 9: Collegamento Android e verifica telefono

**Files:** report del task 7; eventuale `local.properties` ignorato da Git.
Nessun default BuildConfig inventato o componente UI da riscrivere.

**Consumes:** URL HTTPS reale verificato nel task 8. **Produces:** APK configurata per il servizio online.

- [ ] Build con `-PigorOnlinePricesUrl=<URL HTTPS verificato>` con argomento reale passato in modo sicuro, senza token; Gradle offline assembleDebug e testDebugUnitTest, test client wire schema 1 e cache. Cambio del solo URL: non introdurre migrazioni Room o test che copiano l’implementazione.
- [ ] Installazione: `adb -s RZCW10JE18H install -r app/build/outputs/apk/debug/app-debug.apk`, avvio app e verifica assenza crash AndroidRuntime. Nessuna cancellazione dati, attivazione automatica del consenso sul telefono o associazione inventata.
- [ ] Sul telefono sbloccato verificare, con consenso utente, l'API fuori dal collegamento USB senza adb reverse: sei candidate e messaggio di assenza offerte, nessun prezzo sintetico. Se il telefono e' bloccato riportare il limite e richiedere solo lo sblocco; la verifica visiva resta incompleta.
- [ ] Report con hosting raggiungibile ma fonti ancora candidate. README/CLAUDE aggiornati con staging selettivo che preserva i barcode; commit documentazione `docs: record Cloudflare hosting and device verification`.

## Self-review e chiusura

Copertura: API/task 2; atomicita' e sospensione/task 1/4; runner nuovo/task 5;
quote/task 4/6/7; segreti e pubblicazione/task 3/6/8; Android/task 9.
Review Focus: punto 1/task 1/4/7; 2/task 1/4/5; 3/task 2/3/7;
4/task 1/4; 5/task 3/5/6/8.
Interfacce condivise: D1Result, Publisher, CatalogResponse, con nomi univoci.
Dipendenze:1→2,1+3→4,4→5,2+5→6,1..6→7→8→9.

Una review indipendente finale secondo percorso Native gia' scelto nella
sessione; unica passata di correzioni con regressioni RED/GREEN. Non ripetere
testsuite dopo soli aggiornamenti documentali senza nuovi dubbi. Niente merge,
push o pubblicazioni a pagamento come scorciatoia. Preservare ledger e stato
incompleto se manca accesso remoto o listino validato.

Riferimenti durante esecuzione: spec approvata; documentazione ufficiale
Cloudflare D1 REST query, pool Vitest Workers, Node releases e GitHub Actions
schedule/billing. Versioni/permessi correnti vanno verificati al momento della
configurazione reale, evitando istruzioni obsolete o dichiarazioni di quote
garantite per l'intero account.
