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

def test_prezzo_visibile_discordante_non_viene_pubblicato():
    with pytest.raises(ValueError): CarrefourAdapter().parse(html()+'<p class="price">Prezzo 2,49 EUR</p>','https://www.carrefour.it/p/a',NOW,'generic',None)

def test_prezzo_unitario_non_scambiato_per_confezione():
    offer=CarrefourAdapter().parse(html()+'<p class="price">Prezzo 1,39 EUR</p><p class="unit-price">2,78 €/kg</p>','https://www.carrefour.it/p/a',NOW,'generic',None)[0]
    assert offer.pack_price_cents==139

@pytest.mark.parametrize('body',['<script type="application/ld+json">broken</script>',html(offers={'@type':'AggregateOffer','lowPrice':'1.39'}),html(offers={'price':'0','priceCurrency':'EUR'}),'<html/>'])
def test_scheda_rotta_non_pubblica_zero(body):
    with pytest.raises(ValueError): CarrefourAdapter().parse(body,'https://www.carrefour.it/p/a',NOW,'generic',None)

def carrefour_page(price='1.39',label='OFFERTA',extra='',name='Latte 1 L'):
    return html(name=name)+f'''<header>Scopri PAYBACK</header>
      <div class="product-main" data-pid="8076800195057">
        <div class="offers-label">{label}</div>{extra}
        <div class="price"><span class="sales">
          <span class="unit-price">2,78 EUR al l</span>
          <span class="value" content="{price}">{price} EUR</span>
        </span><del><span class="value">9,99 EUR</span></del></div>
        <div class="pricebook-valid-date">Fino al 12/10/2026</div>
      </div><aside class="price">0,00 EUR</aside>'''

def test_carrefour_legge_totale_e_data_senza_prezzo_unitario_o_barrato():
    offer=CarrefourAdapter().parse(carrefour_page(),'https://www.carrefour.it/p/a',NOW,'generic',None)[0]
    assert offer.pack_price_cents==139
    assert offer.condition=='ordinary'
    assert str(offer.valid_until)=='2026-10-12'

@pytest.mark.parametrize('label,expected',[('PAYBACK','loyalty'),('Promozione speciale','unknown')])
def test_carrefour_condizione_prodotto_non_dedotta_dalla_navigazione(label,expected):
    offer=CarrefourAdapter().parse(carrefour_page(label=label),'https://www.carrefour.it/p/a',NOW,'generic',None)[0]
    assert offer.condition==expected

def test_carrefour_multiacquisto_e_carta_non_diventano_prezzo_ordinario():
    offer=CarrefourAdapter().parse(carrefour_page(extra='<div class="promo-bundle-tooltip">Acquista almeno 8 bottiglie con carta</div>'),'https://www.carrefour.it/p/a',NOW,'generic',None)[0]
    assert offer.condition=='multi_buy'

def test_carrefour_peso_variabile_non_usa_formato_nominale():
    offer=CarrefourAdapter().parse(carrefour_page(extra='<span>Peso variabile</span>'),'https://www.carrefour.it/p/a',NOW,'generic',None)[0]
    assert offer.pack is None

def test_carrefour_totale_diverso_dal_json_ld_viene_rifiutato():
    with pytest.raises(ValueError,match='discordanti'):
        CarrefourAdapter().parse(carrefour_page(price='2.49'),'https://www.carrefour.it/p/a',NOW,'generic',None)

def test_carrefour_date_promozionali_diverse_non_vengono_indovinate():
    with pytest.raises(ValueError,match='Date promozionali'):
        CarrefourAdapter().parse(carrefour_page(extra='<div class="promo-bundle-tooltip">Fino al 13/10/2026</div>'),'https://www.carrefour.it/p/a',NOW,'generic',None)

def test_carrefour_sku_del_blocco_visibile_deve_corrispondere():
    with pytest.raises(ValueError,match='SKU discordanti'):
        CarrefourAdapter().parse(carrefour_page().replace('data-pid="8076800195057"','data-pid="12345678"'),'https://www.carrefour.it/p/a',NOW,'generic',None)

def conad_page(price='1,39 EUR',extra=''):
    return html()+f'''<header>Coupon per la prossima spesa</header>
      <div class="product-header"><h1 class="product-title">Latte 1 L</h1>
        <div class="product-button-add"><span class="price f-roboto">{price}</span>
          <div class="add-quantity"><div class="price">0,00 EUR</div></div>
        </div>{extra}</div><aside class="price">9,99 EUR</aside>'''

def test_conad_ignora_totale_quantita_zero_e_prezzi_esterni():
    offer=ConadAdapter().parse(conad_page(),'https://spesaonline.conad.it/p/a',NOW,'generic',None)[0]
    assert offer.pack_price_cents==139
    assert offer.condition=='ordinary'

@pytest.mark.parametrize('price',['2,49 EUR','testo senza prezzo'])
def test_conad_prezzo_visibile_errato_o_illeggibile_non_pubblicato(price):
    with pytest.raises(ValueError):
        ConadAdapter().parse(conad_page(price),'https://spesaonline.conad.it/p/a',NOW,'generic',None)

def test_conad_senza_prezzo_strutturato_non_usa_un_prezzo_zero():
    body=conad_page().replace('"price": "1.39",','')
    with pytest.raises((ValueError,KeyError)):
        ConadAdapter().parse(body,'https://spesaonline.conad.it/p/a',NOW,'generic',None)

def test_prodotto_consigliato_non_sostituisce_la_scheda_richiesta():
    body=html()+html(sku='12345678',name='Altro prodotto 1 L')
    offer=ConadAdapter().parse(body,'https://spesaonline.conad.it/p/a--8076800195057',NOW,'generic',None)[0]
    assert offer.source_sku=='8076800195057'
    with pytest.raises(ValueError,match='SKU discordanti'):
        ConadAdapter().parse(html(),'https://spesaonline.conad.it/p/a--12345678',NOW,'generic',None)
