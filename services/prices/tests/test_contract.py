from datetime import datetime, timezone
import pytest
from pydantic import ValidationError
from igor_prices.models import Offer

def sample(**changes):
    return dict(offer_id='carrefour:locale-1:generic',product_id='carrefour:locale-1',source='carrefour',source_sku='locale-1',name='Latte 1 L',pack_price_cents=139,observed_at=datetime(2026,10,5,tzinfo=timezone.utc),source_url='https://www.carrefour.it/p/latte/locale-1.html',scope='generic',availability='unknown',condition='ordinary') | changes

def test_sku_non_diventa_gtin():
    offer=Offer(**sample(source_sku='8076800195057',product_id='carrefour:8076800195057'))
    assert offer.gtin_verified is False

@pytest.mark.parametrize('changes',[{'observed_at':datetime(2026,10,5)}, {'pack_price_cents':0}, {'source_url':'http://www.carrefour.it/p/a'}, {'source_url':'https://localhost/p/a'}, {'scope':'postcode'}, {'postcode':'20125'}, {'gtin_verified':True}, {'product_id':'gtin:8076800195057'}, {'name':''}, {'pack':{'amount':'NaN','unit':'KG'}}, {'pack_price_cents':1.5}])
def test_contratto_rifiuta_dati_incoerenti(changes):
    with pytest.raises(ValidationError): Offer(**sample(**changes))

def test_decimal_wire_e_gtin_originale():
    offer=Offer(**sample(gtin='0000080050865',gtin_verified=True,product_id='gtin:80050865',pack={'amount':'0.375','unit':'KG','pack_count':3}))
    wire=offer.model_dump(mode='json')
    assert wire['pack']['amount']=='0.375'
    assert wire['gtin']=='0000080050865'
