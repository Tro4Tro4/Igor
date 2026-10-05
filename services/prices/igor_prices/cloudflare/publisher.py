from datetime import datetime, timezone, timedelta
import hashlib
import json
from .client import D1Error
from ..models import Offer, DOMAINS

UPLOAD_SQL = '''INSERT OR IGNORE INTO offers
  (batch_id,source,sku,scope,postcode,offer_id,product_id,gtin,name_key,payload)
SELECT ?,json_extract(value,'$.source'),json_extract(value,'$.sku'),
  json_extract(value,'$.scope'),json_extract(value,'$.postcode'),
  json_extract(value,'$.offer_id'),json_extract(value,'$.product_id'),
  json_extract(value,'$.gtin'),json_extract(value,'$.name_key'),
  json_extract(value,'$.payload') FROM json_each(?)'''


def utc(now):
    if now.tzinfo is None or now.utcoffset() is None:
        raise ValueError('Data senza fuso')
    return now.astimezone(timezone.utc).isoformat(timespec='microseconds')


class Publisher:
    def __init__(self, client):
        self.client = client

    def sources(self):
        rows = self.client.query('SELECT id,status,priority,metadata,current_batch,digest,last_successful_at FROM sources ORDER BY priority,id', retry_safe=True).results
        return [json.loads(r['metadata']) | {k:r[k] for k in
                ['id', 'status', 'priority', 'current_batch', 'digest', 'last_successful_at']} for r in rows]

    def sync_sources(self, configs):
        # Validate the entire manifest before writing any metadata.
        ids = set()
        for config in configs:
            source = config.get('id')
            if source not in DOMAINS or source in ids or config.get('status') not in ('candidate', 'active', 'suspended', 'unsupported'):
                raise ValueError('Manifest fonte non valido')
            ids.add(source)
            if type(config.get('priority')) is not int or config['priority'] < 1:
                raise ValueError('Priorita non valida')
            if config['status'] == 'active' and config.get('reuse_verified') is not True:
                raise ValueError('Fonte non validata')
        for config in configs:
            self.client.query('''INSERT INTO sources(id,status,priority,metadata) VALUES(?,?,?,?)
              ON CONFLICT(id) DO UPDATE SET status=CASE WHEN sources.status='suspended'
                THEN sources.status ELSE excluded.status END,
                priority=excluded.priority,metadata=excluded.metadata''',
              [config['id'], config['status'], config['priority'], json.dumps(config, sort_keys=True)], retry_safe=True)

    def version(self):
        rows = self.client.query('SELECT version FROM catalog WHERE id=1', retry_safe=True).results
        if not rows or type(rows[0].get('version')) is not int:
            raise D1Error('Versione catalogo assente')
        return rows[0]['version']

    def claim_run(self, source, now):
        timestamp = utc(now)
        # RETURNING is robust to response loss: a repeated claim never authorizes another crawl.
        result = self.client.query('''INSERT OR IGNORE INTO runs(source,day,status,checked_at)
          SELECT id,?,'running',? FROM sources WHERE id=? AND status='active' ''',
          [now.astimezone(timezone.utc).date().isoformat(), timestamp, source], retry_safe=False)
        return result.changes == 1

    def finish_run(self, source, now, status):
        if status not in ('success','failed'):
            raise ValueError('Esito run non valido')
        self.client.query('UPDATE runs SET status=?,checked_at=? WHERE source=? AND day=?',
                          [status,utc(now),source,now.astimezone(timezone.utc).date().isoformat()], retry_safe=True)

    def suspend(self, source):
        self.client.query("UPDATE sources SET status='suspended' WHERE id=? AND status!='suspended'", [source], retry_safe=True)

    def publish(self, source, offers, now):
        timestamp = utc(now)
        validated = [Offer.model_validate(o.model_dump()) for o in offers]
        if not validated or len(validated) > 2000 or any(o.source != source for o in validated):
            raise ValueError('Lotto vuoto, troppo grande o fonte incoerente')
        ids = [o.offer_id for o in validated]
        keys = [(o.source_sku,o.scope,o.postcode or '') for o in validated]
        if len(set(ids)) != len(ids) or len(set(keys)) != len(keys):
            raise ValueError('Offerte duplicate')
        values = sorted(validated, key=lambda o:o.offer_id)
        wires = [o.model_dump_json() for o in values]
        digest = hashlib.sha256('\n'.join(wires).encode()).hexdigest()
        batch_id = f'{source}:{digest}'
        config = next((s for s in self.sources() if s['id'] == source), None)
        if not config or config['status'] != 'active' or config.get('reuse_verified') is not True:
            raise ValueError('Fonte non attiva')
        if config['current_batch'] == batch_id:
            return self.version()
        count = self.client.query('''SELECT COALESCE(SUM(b.expected_count),0) AS count
          FROM sources s JOIN offer_batches b ON b.id=s.current_batch WHERE s.id!=?''', [source], retry_safe=True).results[0]['count']
        if count + len(values) > 2000:
            raise ValueError('Limite catalogo pilota superato')
        existing = self.client.query('SELECT id,status,expected_count,observed_at FROM offer_batches WHERE id=?', [batch_id], retry_safe=True).results
        if existing and existing[0]['status'] == 'published':
            # Reusing an older immutable batch is permitted, without inserting into it.
            timestamp = existing[0]['observed_at']
        else:
            self.client.query('''INSERT OR IGNORE INTO offer_batches
              (id,source,digest,expected_count,status,created_at,observed_at)
              VALUES(?,?,?,?,'staging',?,?)''',
              [batch_id,source,digest,len(values),timestamp,timestamp], retry_safe=True)
            existing = self.client.query('SELECT observed_at,expected_count FROM offer_batches WHERE id=?', [batch_id], retry_safe=True).results
            if existing[0]['expected_count'] != len(values):
                raise D1Error('Lotto staging incoerente')
            timestamp = existing[0]['observed_at']
            for start in range(0, len(values), 20):
                part = values[start:start+20]
                rows = [{'source':o.source, 'sku':o.source_sku, 'scope':o.scope,
                    'postcode':o.postcode or '', 'offer_id':o.offer_id, 'product_id':o.product_id,
                    'gtin':o.product_id[5:] if o.gtin_verified else None,
                    'name_key':(o.name+' '+(o.brand or '')).casefold(), 'payload':o.model_dump_json()} for o in part]
                self.client.query(UPLOAD_SQL, [batch_id,json.dumps(rows)], retry_safe=True)
                placeholders = ','.join('?' for _ in part)
                actual = self.client.query(f'SELECT offer_id,payload FROM offers WHERE batch_id=? AND offer_id IN ({placeholders})',
                    [batch_id,*[o.offer_id for o in part]], retry_safe=True).results
                if {r['offer_id']:r['payload'] for r in actual} != {o.offer_id:o.model_dump_json() for o in part}:
                    raise D1Error('Lotto staging incoerente')
        try:
            self.client.query('UPDATE sources SET current_batch=?,digest=?,last_successful_at=? WHERE id=? AND current_batch IS NOT ?',
                [batch_id,digest,timestamp,source,batch_id], retry_safe=True)
        except D1Error:
            current = self.client.query('SELECT current_batch FROM sources WHERE id=?', [source], retry_safe=True).results
            if not current or current[0]['current_batch'] != batch_id:
                raise
        return self.version()

    def cleanup(self, now):
        cutoff = utc(now-timedelta(hours=24))
        # The current pointer and the immediately preceding published batch are protected.
        rows = self.client.query('''SELECT b.id FROM offer_batches b
          WHERE b.created_at<? AND NOT EXISTS(SELECT 1 FROM sources s WHERE s.current_batch=b.id)
          AND (b.status='staging' OR b.id NOT IN (
            SELECT previous.id FROM offer_batches previous
              WHERE previous.source=b.source AND previous.status='published'
              AND NOT EXISTS(SELECT 1 FROM sources s WHERE s.current_batch=previous.id)
              ORDER BY previous.published_at DESC,previous.id DESC LIMIT 1))
          ORDER BY b.created_at,b.id LIMIT 12''', [cutoff], retry_safe=True).results
        for row in rows:
            self.client.query('DELETE FROM offer_batches WHERE id=? AND NOT EXISTS(SELECT 1 FROM sources WHERE current_batch=?)',
                [row['id'],row['id']], retry_safe=True)
        return {'deleted_batches':len(rows)}
