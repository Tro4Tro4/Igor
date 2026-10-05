from fastapi.testclient import TestClient
import pytest
from igor_prices.api import create_app
from igor_prices.models import Offer
from test_contract import sample
from test_store import store

@pytest.mark.parametrize('query',['q=a','q=latte&gtin=80050865','q=latte&postcode=abcde','q=latte&limit=21','q=latte&offset=-1','gtin=123','q=%20%20',''])
def test_input_non_valido(store,query):
    assert TestClient(create_app(store)).get('/v1/products?'+query).status_code==422

def test_paginazione_e_schema(store):
    a=Offer(**sample())
    b=Offer(**sample(offer_id='b',source_sku='b',product_id='carrefour:b'))
    store.replace_batch('carrefour',[a,b],a.observed_at)
    client=TestClient(create_app(store))
    response=client.get('/v1/products?q=Latte&limit=1').json()
    assert response['schema_version']==1
    assert len(response['items'])==1
    assert response['next_offset']==1
    assert client.get('/v1/products/carrefour:b/offers').status_code==200
    assert client.get('/v1/products/missing/offers').status_code==404

def test_database_indisponibile_non_diventa_lista_vuota(store):
    store.path.unlink()
    assert TestClient(create_app(store)).get('/v1/products?q=latte').status_code==503
