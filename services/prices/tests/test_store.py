from datetime import timedelta
import sqlite3
import pytest
from igor_prices.models import Offer
from igor_prices.store import CatalogStore
from test_contract import sample

@pytest.fixture
def store(tmp_path):
    s=CatalogStore(tmp_path/'catalog.sqlite3')
    s.configure_sources([{'id':'carrefour','status':'active','reuse_verified':True,'priority':1},{'id':'conad','status':'candidate','reuse_verified':False,'priority':2}])
    return s

def test_idempotenza(store):
    offer=Offer(**sample())
    assert store.replace_batch('carrefour',[offer],offer.observed_at)==1
    assert store.replace_batch('carrefour',[offer],offer.observed_at)==1
    assert len(store.offers(offer.product_id,'20125'))==1

def test_rollback_errore_seconda_scrittura(store):
    old=Offer(**sample())
    store.replace_batch('carrefour',[old],old.observed_at)
    with sqlite3.connect(store.path) as db:
        db.execute("CREATE TRIGGER reject_b BEFORE INSERT ON offers WHEN NEW.sku='b' BEGIN SELECT RAISE(ABORT,'disk error'); END")
    newer=old.model_copy(update={'observed_at':old.observed_at+timedelta(days=1),'pack_price_cents':199})
    second=Offer(**sample(offer_id='b',source_sku='b',product_id='carrefour:b'))
    with pytest.raises(sqlite3.DatabaseError): store.replace_batch('carrefour',[newer,second],newer.observed_at)
    assert store.offers(old.product_id,'20125')[0].pack_price_cents==139
    assert store.offers(old.product_id,'20125')[0].observed_at==old.observed_at

def test_fonti_non_attive_escluse(store):
    old=Offer(**sample())
    store.replace_batch('carrefour',[old],old.observed_at)
    store.set_status('carrefour','suspended')
    assert store.search('Latte',None,'20125',20,0)==[]
    assert store.sources()[0]['status']=='suspended'

def test_cap_isolato_e_ricerca_parametrizzata(store):
    offer=Offer(**sample(scope='postcode',postcode='20125',name="L'acqua"))
    store.replace_batch('carrefour',[offer],offer.observed_at)
    assert len(store.search("L'acqua",None,'20125',20,0))==1
    assert store.search("L'acqua",None,'20126',20,0)==[]
    assert store.search("' OR 1=1 --",None,'20125',20,0)==[]
