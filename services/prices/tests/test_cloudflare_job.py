from datetime import timedelta
import pytest
from igor_prices.cloudflare.job import run_daily
from igor_prices.cloudflare.publisher import Publisher
from igor_prices.cloudflare.client import D1Error
from igor_prices.transport import AccessSuspended
from d1_sql_harness import SqlD1
from test_d1_publisher import config,offers,NOW

def test_candidate_manifest_does_not_crawl():
    p=Publisher(SqlD1())
    manifest={'schema_version':1,'sources':[config(status='candidate')]}
    def forbidden(*args,**kwargs):raise AssertionError('candidate crawl forbidden')
    result=run_daily(p,manifest,now=NOW,ingest_fn=forbidden)
    assert result.attempted==0
    assert result.skipped==1
    assert result.failed==0

def test_remote_suspension_survives_new_runner_and_active_manifest():
    p=Publisher(SqlD1());p.sync_sources([config()]);p.suspend('carrefour')
    def forbidden(*args,**kwargs):raise AssertionError('suspended crawl forbidden')
    result=run_daily(p,{'schema_version':1,'sources':[config()]},now=NOW,ingest_fn=forbidden)
    assert result.skipped==1
    assert p.sources()[0]['status']=='suspended'

def test_daily_claim_prevents_repeat_and_next_day_runs():
    p=Publisher(SqlD1())
    def ingest(store,source,*,now):return store.replace_batch(source,offers(when=now),now)
    manifest={'schema_version':1,'sources':[config()]}
    assert run_daily(p,manifest,now=NOW,ingest_fn=ingest).succeeded==1
    assert run_daily(p,manifest,now=NOW,ingest_fn=ingest).skipped==1
    assert run_daily(p,manifest,now=NOW+timedelta(days=1),ingest_fn=ingest).succeeded==1

def test_error_preserves_remote_and_403_is_propagated():
    remote=SqlD1();p=Publisher(remote);p.sync_sources([config()]);p.publish('carrefour',offers(),NOW)
    def blocked(store,source,*,now):
        store.set_status(source,'suspended');raise AccessSuspended('403')
    result=run_daily(p,{'schema_version':1,'sources':[config()]},now=NOW,ingest_fn=blocked)
    assert result.failed==1
    assert remote.current_prices('carrefour')==[139]
    assert p.sources()[0]['status']=='suspended'

def test_publisher_failure_is_failed_run_not_success():
    remote=SqlD1();p=Publisher(remote);remote.fail_after_uploads=0
    def ingest(store,source,*,now):return store.replace_batch(source,offers(),now)
    result=run_daily(p,{'schema_version':1,'sources':[config()]},now=NOW,ingest_fn=ingest)
    assert result.failed==1
    assert result.succeeded==0
    assert remote.query('SELECT status FROM runs').results[0]['status']=='failed'

def test_invalid_manifest_fails_before_any_acquisition():
    with pytest.raises(ValueError):run_daily(Publisher(SqlD1()),{'schema_version':2,'sources':[]},now=NOW)
