import json
from pathlib import Path
from fastapi.testclient import TestClient
from igor_prices.api import create_app
from igor_prices.models import Offer
from test_store import store

def test_shared_v1_fixtures(store):
    fixture=json.loads((Path(__file__).resolve().parents[2]/'prices-contract/fixtures.json').read_text())
    store.configure_sources([{'id':s,'status':'active','priority':i+1,'reuse_verified':True} for i,s in enumerate(['carrefour','conad'])])
    for source in ['carrefour','conad']:
        offers=[Offer.model_validate(o) for o in fixture['offers'] if o['source']==source]
        store.replace_batch(source,offers,offers[0].observed_at)
    client=TestClient(create_app(store))
    for case in fixture['queries']:
        body=client.get('/v1/products?'+case['query']).json()
        assert body['schema_version']==1
        assert [o['offer_id'] for o in body['items']]==case['ids']
        assert body['next_offset']==case.get('next_offset')
