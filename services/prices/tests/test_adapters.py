from datetime import datetime,timezone
import json
import pytest
from igor_prices.adapters.carrefour import CarrefourAdapter
from igor_prices.adapters.conad import ConadAdapter

NOW=datetime(2026,10,5,tzinfo=timezone.utc)
def html(**changes):
    product={'@type':'Product','name':'Latte 1 L','sku':'8076800195057','brand':{'name':'Barilla'},'offers':{'@type':'Offer','price':'1.39','priceCurrency':'EUR','availability':'https://schema.org/InStock','priceValidUntil':'2026-11-05'}} | changes
    return '<script type="application/ld+json">'+json.dumps({'@graph':[product]})+'</script>'

def test_disponibilita_discordante_e_sku():
    offer=CarrefourAdapter().parse(html()+'<p>Prodotto al momento non disponibile</p>','https://www.carrefour.it/p/latte/a.html',NOW,'generic',None)[0]
    assert offer.availability=='unknown'
    assert offer.pack_price_cents==139
    assert offer.gtin_verified is False
    assert offer.valid_until is None

def test_gtin_conad_multipack():
    offer=ConadAdapter().parse(html(name='Mozzarella 3 x 125 g',sku='225225',gtin='8003170006584'),'https://spesaonline.conad.it/p/mozzarella--225225',NOW,'generic',None)[0]
    assert offer.product_id=='gtin:8003170006584'
    assert str(offer.pack.amount)=='0.375'

def test_carta_esclusa_dal_prezzo_ordinario():
    offer=CarrefourAdapter().parse(html()+'<p>Prezzo con carta fedeltà</p>','https://www.carrefour.it/p/a',NOW,'generic',None)[0]
    assert offer.condition=='loyalty'

@pytest.mark.parametrize('body',['<script type="application/ld+json">broken</script>',html(offers={'@type':'AggregateOffer','lowPrice':'1.39'}),html(offers={'price':'0','priceCurrency':'EUR'}),'<html/>'])
def test_scheda_rotta_non_pubblica_zero(body):
    with pytest.raises(ValueError): CarrefourAdapter().parse(body,'https://www.carrefour.it/p/a',NOW,'generic',None)
