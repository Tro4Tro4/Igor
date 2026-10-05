from pathlib import Path
import sqlite3
from igor_prices.cloudflare.client import D1Result,D1Error

SCHEMA=Path(__file__).resolve().parents[2]/'prices-cloudflare/migrations/0001_catalog.sql'
class SqlD1:
    """Execute publisher SQL against real SQLite; only the HTTP boundary is replaced."""
    def __init__(self):
        self.db=sqlite3.connect(':memory:');self.db.row_factory=sqlite3.Row
        self.db.execute('PRAGMA foreign_keys=ON');self.db.executescript(SCHEMA.read_text())
        self.fail_after_uploads=None;self.uploads=0;self.lose_switch=False

    def query(self,sql,params=None,*,retry_safe=False):
        if 'json_each' in sql:
            if self.fail_after_uploads is not None and self.uploads>=self.fail_after_uploads:
                raise D1Error('simulated upload interruption')
            self.uploads+=1
        before=self.db.total_changes
        try:
            with self.db:
                cursor=self.db.execute(sql,params or [])
                rows=[dict(r) for r in cursor.fetchall()] if cursor.description else []
                changed=self.db.execute('SELECT changes()').fetchone()[0]
        except sqlite3.Error:
            raise D1Error('D1 SQL rejected') from None
        if self.lose_switch and sql.startswith('UPDATE sources SET current_batch'):
            self.lose_switch=False
            raise D1Error('lost response after commit')
        return D1Result(rows,changed,0,self.db.total_changes-before)

    def version(self):return self.db.execute('SELECT version FROM catalog').fetchone()[0]
    def current_prices(self,source):
        import json
        return [json.loads(r[0])['pack_price_cents'] for r in self.db.execute(
          'SELECT o.payload FROM offers o JOIN sources s ON s.current_batch=o.batch_id WHERE s.id=? ORDER BY offer_id',(source,))]
