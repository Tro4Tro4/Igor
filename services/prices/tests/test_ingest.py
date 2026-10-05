from datetime import timedelta
import pytest
from igor_prices.ingest import ingest
from igor_prices.transport import HttpResult,AccessSuspended
from igor_prices.models import Offer
from test_contract import sample
from test_adapters import html,NOW
from test_store import store

def activate(store):
    store.configure_sources([{'id':'carrefour','status':'active','reuse_verified':True,'priority':1,'scope':'generic','product_urls':['https://www.carrefour.it/p/a']}])

def test_un_solo_lotto_al_giorno(store):
    activate(store)
    class Fake:
        def get(self,url): return HttpResult(200,html(),{},url)
    assert ingest(store,'carrefour',transport_factory=lambda _:Fake(),robots_fetch=lambda _:'User-agent: *\nAllow: /',now=NOW)==1
    assert ingest(store,'carrefour',transport_factory=lambda _:Fake(),robots_fetch=lambda _:'',now=NOW) is None

def test_errore_conserva_precedente_e_403_sospende(store):
    activate(store)
    old=Offer(**sample())
    store.replace_batch('carrefour',[old],NOW)
    class Fake:
        def get(self,url): raise AccessSuspended('403')
    with pytest.raises(AccessSuspended): ingest(store,'carrefour',transport_factory=lambda _:Fake(),robots_fetch=lambda _:'User-agent: *\nAllow: /',now=NOW+timedelta(days=1))
    assert store.sources()[0]['status']=='suspended'
    assert store.sources()[0]['last_successful_at']==NOW.isoformat()
