from datetime import datetime,timezone,timedelta
import pytest
from igor_prices.cloudflare.publisher import Publisher
from igor_prices.cloudflare.client import D1Error
from igor_prices.models import Offer
from test_contract import sample
from d1_sql_harness import SqlD1

NOW=datetime(2026,10,5,tzinfo=timezone.utc)
def config(source='carrefour',status='active',priority=1):
    return {'id':source,'status':status,'priority':priority,'scope':'generic',
      'reuse_verified':status=='active','product_urls':['https://www.carrefour.it/p/a']}
def offers(count=1,source='carrefour',price=139,when=NOW):
    host='www.carrefour.it' if source=='carrefour' else 'spesaonline.conad.it'
    return [Offer(**sample(offer_id=f'{source}:{i}:generic',product_id=f'{source}:{i}',
      source=source,source_sku=str(i),source_url=f'https://{host}/p/{i}',
      observed_at=when,pack_price_cents=price)) for i in range(count)]
@pytest.fixture
def remote():return SqlD1()
@pytest.fixture
def publisher(remote):
    p=Publisher(remote);p.sync_sources([config()]);return p

def test_incomplete_upload_preserves_previous_and_retry_is_idempotent(publisher,remote):
    assert publisher.publish('carrefour',offers(),NOW)==1
    remote.uploads=0;remote.fail_after_uploads=1
    with pytest.raises(D1Error):publisher.publish('carrefour',offers(21,price=199),NOW+timedelta(days=1))
    assert remote.current_prices('carrefour')==[139]
    assert remote.version()==1
    remote.fail_after_uploads=None
    assert publisher.publish('carrefour',offers(21,price=199),NOW+timedelta(days=1))==2
    assert remote.current_prices('carrefour')==[199]*21
    assert publisher.publish('carrefour',offers(21,price=199),NOW+timedelta(days=1))==2

def test_lost_switch_response_does_not_advance_version_twice(publisher,remote):
    remote.lose_switch=True
    assert publisher.publish('carrefour',offers(),NOW)==1
    assert publisher.publish('carrefour',offers(),NOW)==1

def test_suspension_survives_manifest_and_prevents_publication(publisher,remote):
    publisher.publish('carrefour',offers(),NOW);publisher.suspend('carrefour')
    publisher.sync_sources([config()])
    assert publisher.sources()[0]['status']=='suspended'
    with pytest.raises(ValueError):publisher.publish('carrefour',offers(price=199),NOW)
    assert remote.current_prices('carrefour')==[139]

def test_global_limit_is_checked_before_upload(publisher,remote):
    publisher.sync_sources([config('conad',priority=2)])
    publisher.publish('carrefour',offers(1200),NOW)
    remote.uploads=0
    with pytest.raises(ValueError):publisher.publish('conad',offers(1000,source='conad'),NOW)
    assert remote.uploads==0
    assert remote.version()==1

def test_duplicate_and_wrong_source_offers_rejected_before_writes(publisher,remote):
    for values in [offers()*2,offers(source='conad'),[]]:
        with pytest.raises(ValueError):publisher.publish('carrefour',values,NOW)
    assert remote.version()==0

def test_daily_claim_persists_and_does_not_change_catalog_version(publisher,remote):
    assert publisher.claim_run('carrefour',NOW)
    assert not Publisher(remote).claim_run('carrefour',NOW)
    publisher.finish_run('carrefour',NOW,'success')
    assert not publisher.claim_run('carrefour',NOW)
    assert publisher.claim_run('carrefour',NOW+timedelta(days=1))
    assert remote.version()==0

def test_reuse_validation_and_long_cents(publisher,remote):
    with pytest.raises(ValueError):publisher.sync_sources([config()|{'reuse_verified':False}])
    publisher.publish('carrefour',offers(price=9223372036854775807),NOW)
    assert remote.current_prices('carrefour')==[9223372036854775807]

def test_cleanup_keeps_current_previous_and_recent_staging(publisher,remote):
    for days,price in [(0,139),(1,199),(2,249)]:
        publisher.publish('carrefour',offers(price=price,when=NOW+timedelta(days=days)),NOW+timedelta(days=days))
    publisher.cleanup(NOW+timedelta(days=4))
    batches=remote.query('SELECT count(*) AS count FROM offer_batches').results[0]['count']
    assert batches==2
    assert remote.current_prices('carrefour')==[249]

def test_publication_after_staging_suspension_rolls_back(publisher,remote):
    original=remote.query
    def race(sql,params=None,**kwargs):
        if sql.startswith('UPDATE sources SET current_batch'):
            original("UPDATE sources SET status='suspended' WHERE id='carrefour'")
        return original(sql,params,**kwargs)
    remote.query=race
    with pytest.raises(D1Error):publisher.publish('carrefour',offers(),NOW)
    assert remote.current_prices('carrefour')==[]
    assert remote.version()==1
