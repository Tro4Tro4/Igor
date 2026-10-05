"""Generate synthetic integration SQL from the real publisher, never for deployment."""
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from datetime import timedelta
from d1_sql_harness import SqlD1
from test_d1_publisher import offers,config,NOW
from igor_prices.cloudflare.publisher import Publisher

remote=SqlD1();operations=[];query=remote.query
def record(sql,params=None,**kwargs):
    result=query(sql,params,**kwargs)
    if sql.startswith(('INSERT','UPDATE','DELETE')):operations.append({'sql':sql,'params':params or []})
    return result
remote.query=record
publisher=Publisher(remote);publisher.sync_sources([config()])
publisher.publish('carrefour',offers(),NOW)
initial=len(operations)
publisher.publish('carrefour',offers(21,price=199,when=NOW+timedelta(days=1)),NOW+timedelta(days=1))
path=Path(__file__).resolve().parents[2]/'prices-contract/publication.json'
path.write_text(json.dumps({'synthetic':True,'initial':initial,'operations':operations},indent=2)+'\n',encoding='utf-8')
