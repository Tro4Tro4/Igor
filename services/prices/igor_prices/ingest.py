from datetime import datetime,timezone
from urllib.parse import urlsplit
import httpx
from .access import SourceAccess
from .transport import Transport,AccessSuspended
from .adapters.carrefour import CarrefourAdapter
from .adapters.conad import ConadAdapter

ADAPTERS={'carrefour':CarrefourAdapter,'conad':ConadAdapter}

def ingest(store,source,*,transport_factory=Transport,robots_fetch=None,now=None):
    now=now or datetime.now(timezone.utc)
    config=next((s for s in store.sources() if s['source']==source),None)
    if not config or config['status']!='active' or not config.get('reuse_verified') or source not in ADAPTERS:
        raise ValueError('Fonte non validata o adattatore assente')
    if not config.get('product_urls'): raise ValueError('Manifest senza campioni validati')
    if not store.claim_run(source,now): return None
    try:
        root='https://'+urlsplit(config['product_urls'][0]).hostname
        if robots_fetch: robots=robots_fetch(root+'/robots.txt')
        else:
            with httpx.Client(timeout=15,follow_redirects=False,headers={'User-Agent':'Igor/0.1.0'}) as client:
                reply=client.get(root+'/robots.txt')
                if reply.status_code==404: robots='User-agent: *\nAllow: /'
                else:
                    reply.raise_for_status()
                    if len(reply.content)>2*1024*1024: raise ValueError('Robots troppo grande')
                    robots=reply.text
        access=SourceAccess(source,config['product_urls'],True,robots)
        transport=transport_factory(access)
        adapter=ADAPTERS[source]()
        batch=[]
        for url in config['product_urls']:
            result=transport.get(url)
            batch.extend(adapter.parse(result.body,result.final_url,now,config['scope'],config.get('postcode')))
        version=store.replace_batch(source,batch,now)
        store.finish_run(source,now,'success')
        return version
    except Exception as error:
        if isinstance(error,AccessSuspended) or isinstance(error,httpx.HTTPStatusError) and error.response.status_code==403:
            store.set_status(source,'suspended')
        store.finish_run(source,now,'failed')
        raise
