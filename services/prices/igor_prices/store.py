from contextlib import contextmanager
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import sqlite3
from .models import Offer

class CatalogStore:
    def __init__(self,path):
        self.path=Path(path)
        with self.connect() as db:
            db.executescript('''
              CREATE TABLE IF NOT EXISTS sources(id TEXT PRIMARY KEY,status TEXT NOT NULL,priority INTEGER NOT NULL,metadata TEXT NOT NULL,last_successful_at TEXT,digest TEXT);
              CREATE TABLE IF NOT EXISTS offers(source TEXT NOT NULL,sku TEXT NOT NULL,scope TEXT NOT NULL,postcode TEXT NOT NULL,offer_id TEXT NOT NULL UNIQUE,product_id TEXT NOT NULL,name TEXT NOT NULL,gtin TEXT,payload TEXT NOT NULL,PRIMARY KEY(source,sku,scope,postcode));
              CREATE INDEX IF NOT EXISTS offers_product ON offers(product_id);
              CREATE TABLE IF NOT EXISTS catalog(version INTEGER NOT NULL);
              INSERT INTO catalog SELECT 0 WHERE NOT EXISTS(SELECT 1 FROM catalog);
              CREATE TABLE IF NOT EXISTS runs(source TEXT NOT NULL,day TEXT NOT NULL,status TEXT NOT NULL,checked_at TEXT NOT NULL,PRIMARY KEY(source,day));
            ''')

    @contextmanager
    def connect(self):
        db=sqlite3.connect(self.path,timeout=15)
        db.row_factory=sqlite3.Row
        try:
            with db: yield db
        finally: db.close()

    def configure_sources(self,sources):
        with self.connect() as db:
            for s in sources:
                if s['status']=='active' and not s.get('reuse_verified'): raise ValueError('Fonte non validata')
                db.execute("INSERT INTO sources(id,status,priority,metadata) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET status=CASE WHEN sources.status='suspended' AND excluded.status='active' THEN sources.status ELSE excluded.status END,priority=excluded.priority,metadata=excluded.metadata",(s['id'],s['status'],s['priority'],json.dumps(s)))

    def set_status(self,source,status):
        with self.connect() as db: db.execute('UPDATE sources SET status=? WHERE id=?',(status,source))

    def sources(self):
        with self.connect() as db:
            return [json.loads(r['metadata']) | {'source':r['id'],'status':r['status'],'last_successful_at':r['last_successful_at']} for r in db.execute('SELECT * FROM sources ORDER BY priority')]

    @property
    def version(self):
        with self.connect() as db: return db.execute('SELECT version FROM catalog').fetchone()[0]

    def replace_batch(self,source,offers,observed_at):
        validated=[Offer.model_validate(o.model_dump()) for o in offers]
        if not validated or any(o.source!=source for o in validated): raise ValueError('Lotto vuoto o fonte incoerente')
        wire=[o.model_dump_json() for o in sorted(validated,key=lambda o:o.offer_id)]
        digest=hashlib.sha256('\n'.join(wire).encode()).hexdigest()
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT status,digest FROM sources WHERE id=?',(source,)).fetchone()
            if not row or row['status']!='active': raise ValueError('Fonte non attiva')
            if row['digest']==digest: return db.execute('SELECT version FROM catalog').fetchone()[0]
            db.execute('DELETE FROM offers WHERE source=?',(source,))
            for o,payload in zip(sorted(validated,key=lambda o:o.offer_id),wire):
                db.execute('INSERT INTO offers VALUES(?,?,?,?,?,?,?,?,?)',(source,o.source_sku,o.scope,o.postcode or '',o.offer_id,o.product_id,o.name+' '+(o.brand or ''),o.product_id[5:] if o.gtin_verified else None,payload))
            db.execute('UPDATE sources SET digest=?,last_successful_at=? WHERE id=?',(digest,observed_at.isoformat(),source))
            db.execute('UPDATE catalog SET version=version+1')
            return db.execute('SELECT version FROM catalog').fetchone()[0]

    def search(self,q,gtin,postcode,limit,offset):
        terms=[gtin] if gtin else ['%'+token.casefold().replace('\\','\\\\').replace('%','\\%').replace('_','\\_')+'%' for token in q.split()]
        clause='o.gtin=?' if gtin else ' AND '.join("lower(o.name) LIKE ? ESCAPE '\\'" for _ in terms)
        if not terms: return []
        with self.connect() as db:
            rows=db.execute("SELECT o.payload FROM offers o JOIN sources s ON s.id=o.source WHERE s.status='active' AND (o.scope='generic' OR o.postcode=?) AND "+clause+' ORDER BY s.priority,o.offer_id LIMIT ? OFFSET ?',(postcode,*terms,limit,offset))
            return [Offer.model_validate_json(r[0]) for r in rows]

    def offers(self,product_id,postcode):
        with self.connect() as db:
            rows=db.execute("SELECT o.payload FROM offers o JOIN sources s ON s.id=o.source WHERE s.status='active' AND o.product_id=? AND (o.scope='generic' OR o.postcode=?) ORDER BY s.priority,o.offer_id",(product_id,postcode))
            return [Offer.model_validate_json(r[0]) for r in rows]

    def claim_run(self,source,now):
        with self.connect() as db:
            result=db.execute('INSERT OR IGNORE INTO runs VALUES(?,?,?,?)',(source,now.astimezone(timezone.utc).date().isoformat(),'running',now.isoformat()))
            return result.rowcount==1

    def finish_run(self,source,now,status):
        with self.connect() as db: db.execute('UPDATE runs SET status=? WHERE source=? AND day=?',(status,source,now.astimezone(timezone.utc).date().isoformat()))
