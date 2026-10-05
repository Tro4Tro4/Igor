import argparse
import json
from pathlib import Path
import uvicorn
from .store import CatalogStore
from .api import create_app
from .ingest import ingest

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--database',default='catalog.sqlite3')
    parser.add_argument('--manifest',default=str(Path(__file__).resolve().parents[3]/'docs/verification/2026-10-05-fonti-online-manifest.json'))
    commands=parser.add_subparsers(dest='command',required=True)
    serve=commands.add_parser('serve'); serve.add_argument('--host',default='127.0.0.1'); serve.add_argument('--port',type=int,default=8765)
    job=commands.add_parser('ingest'); job.add_argument('--source',required=True)
    args=parser.parse_args()
    store=CatalogStore(args.database)
    manifest=json.loads(Path(args.manifest).read_text(encoding='utf-8'))
    if manifest['schema_version']!=1: raise ValueError('Manifest non supportato')
    store.configure_sources(manifest['sources'])
    if args.command=='serve': uvicorn.run(create_app(store),host=args.host,port=args.port,access_log=False)
    else: ingest(store,args.source)

if __name__=='__main__': main()
