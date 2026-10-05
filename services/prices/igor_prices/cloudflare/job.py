from dataclasses import dataclass
from datetime import datetime,timezone
from pathlib import Path
from tempfile import TemporaryDirectory
from ..ingest import ingest
from ..store import CatalogStore
from .publisher import utc


@dataclass
class JobSummary:
    attempted: int = 0
    succeeded: int = 0
    skipped: int = 0
    failed: int = 0


def run_daily(publisher, manifest, *, now=None, ingest_fn=ingest):
    now=now or datetime.now(timezone.utc)
    utc(now)
    if manifest.get('schema_version') != 1 or not isinstance(manifest.get('sources'),list):
        raise ValueError('Manifest non supportato')
    publisher.sync_sources(manifest['sources'])
    sources=publisher.sources()
    result=JobSummary()
    with TemporaryDirectory(prefix='igor-prices-') as folder:
        local=CatalogStore(Path(folder)/'catalog.sqlite3')
        local.configure_sources(sources)
        for config in sources:
            source=config['id']
            if config['status']!='active' or config.get('reuse_verified') is not True:
                result.skipped+=1
                continue
            if not publisher.claim_run(source,now):
                result.skipped+=1
                continue
            result.attempted+=1
            try:
                ingest_fn(local,source,now=now)
                publisher.publish(source,local.source_offers(source),now)
                publisher.finish_run(source,now,'success')
                result.succeeded+=1
            except Exception:
                result.failed+=1
                state=next(s['status'] for s in local.sources() if s['source']==source)
                # A failed remote suspension propagates to CLI exit1, never reports success.
                if state=='suspended':publisher.suspend(source)
                publisher.finish_run(source,now,'failed')
    publisher.cleanup(now)
    return result
