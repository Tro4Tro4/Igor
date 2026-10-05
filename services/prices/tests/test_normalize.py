from decimal import Decimal
import pytest
from igor_prices.normalize import money_cents, normalize_gtin, parse_pack

@pytest.mark.parametrize('text,want', [('€ 1,39',139),('1.234,56 €',123456),('0.99',99)])
def test_importi_esatti(text,want):
    assert money_cents(text) == want

@pytest.mark.parametrize('text',['-1','NaN','Infinity','1.234','0','1,234.56','1,39 €/kg'])
def test_importi_ambigui_o_non_positivi(text):
    with pytest.raises(ValueError): money_cents(text)

@pytest.mark.parametrize('text,amount,unit,count', [('2 x 500 g','1','KG',2),('3 x 125 g','0.375','KG',3),('1,5 l','1.5','L',1),('1000 ml','1','L',1),('6 pz','6','PZ',1)])
def test_formato_totale_multipack(text,amount,unit,count):
    pack=parse_pack(text)
    assert (pack.amount,pack.unit,pack.pack_count)==(Decimal(amount),unit,count)

@pytest.mark.parametrize('text',['peso variabile 500 g','circa 1 kg','500 g oppure 1 kg','500 g 2 kg','Latte','0 g'])
def test_formato_incerto_non_diventa_un_pezzo(text): assert parse_pack(text) is None

@pytest.mark.parametrize('text,want',[('0000080050865','80050865'),('80050865','80050865'),('8076800195057','8076800195057'),('8076800195058',None),('locale-1',None)])
def test_gtin_checksum_e_padding(text,want): assert normalize_gtin(text)==want
