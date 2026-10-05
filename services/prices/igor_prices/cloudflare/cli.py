import argparse
from dataclasses import asdict
import json
from pathlib import Path
import sys
from .client import D1Client,D1Error
from .publisher import Publisher
from .job import run_daily


def main():
    parser=argparse.ArgumentParser(description='Igor D1 publisher')
    parser.add_argument('command',choices=['daily','sync-sources'])
    parser.add_argument('--manifest',default=str(Path(__file__).resolve().parents[4]/'docs/verification/2026-10-05-fonti-online-manifest.json'))
    args=parser.parse_args()
    client=None
    try:
        client=D1Client.from_environment()
        manifest=json.loads(Path(args.manifest).read_text(encoding='utf-8'))
        if manifest.get('schema_version')!=1 or not isinstance(manifest.get('sources'),list):
            raise ValueError('Manifest non supportato')
        publisher=Publisher(client)
        if args.command=='sync-sources':
            publisher.sync_sources(manifest['sources'])
            print(json.dumps({'sources':len(publisher.sources())}))
            return 0
        result=run_daily(publisher,manifest)
        print(json.dumps(asdict(result)))
        return 1 if result.failed else 0
    except (D1Error,ValueError,OSError):
        print('Pubblicazione D1 non riuscita: verificare configurazione, accessi e catalogo.',file=sys.stderr)
        return 1
    finally:
        if client is not None:client.close()


if __name__=='__main__':sys.exit(main())
