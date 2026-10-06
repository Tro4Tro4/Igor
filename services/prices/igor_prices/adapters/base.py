import json
import re
from urllib.parse import urlsplit
from bs4 import BeautifulSoup
from ..models import Offer
from ..normalize import money_cents, normalize_gtin, parse_pack

def products(node):
    if isinstance(node,list):
        for child in node: yield from products(child)
    elif isinstance(node,dict):
        types=node.get('@type',[])
        if types=='Product' or isinstance(types,list) and 'Product' in types: yield node
        else:
            for child in node.values(): yield from products(child)

class Adapter:
    source:str
    def context(self,soup,sku):
        return soup

    def visible_prices(self,context):
        return context.select('.price, [itemprop="price"], [data-price]')

    def promotion(self,context):
        text=context.get_text(' ',strip=True).lower()
        if re.search(r'prezzo\s+con\s+carta|solo\s+con\s+carta|carta\s+fedelt',text): return 'loyalty',None
        if re.search(r'coupon|buono sconto',text): return 'coupon',None
        if re.search(r'\b[23]\s*[x×]\s*[12]\b|acquistando\s+\d',text): return 'multi_buy',None
        return 'ordinary',None

    def parse(self,body,url,observed_at,scope,postcode)->list[Offer]:
        soup=BeautifulSoup(body,'html.parser')
        docs=[json.loads(script.get_text()) for script in soup.find_all('script',attrs={'type':'application/ld+json'})]
        candidates=[p for doc in docs for p in products(doc)]
        # Le schede possono includere prodotti consigliati con un altro SKU.
        expected=urlsplit(url).path.rsplit('/',1)[-1].removesuffix('.html').rsplit('--',1)[-1]
        matched=[p for p in candidates if str(p.get('sku') or p.get('mpn') or '')==expected]
        if expected.isdigit() and not matched:
            raise ValueError('URL e SKU discordanti')
        if matched: candidates=matched
        if len(candidates)!=1: raise ValueError('Prodotto principale non disambiguato')
        result=[]
        for product in candidates:
            raw=product.get('offers')
            if isinstance(raw,list):
                if len(raw)!=1: raise ValueError('Prezzi multipli non disambiguati')
                raw=raw[0]
            if not isinstance(raw,dict) or raw.get('@type','Offer')!='Offer' or raw.get('priceCurrency')!='EUR' or raw.get('price') is None: raise ValueError('Prezzo confezione mancante')
            sku=str(product.get('sku') or product.get('mpn') or '')
            if not sku: raise ValueError('SKU mancante')
            context=self.context(soup,sku)
            text=context.get_text(' ',strip=True).lower()
            gtin=next((str(product[k]) for k in ('gtin','gtin13','gtin14','gtin12','gtin8') if product.get(k)),None)
            verified=bool(gtin and normalize_gtin(gtin))
            if gtin and not verified: raise ValueError('GTIN dichiarato non valido')
            availability={'instock':'available','outofstock':'unavailable'}.get(str(raw.get('availability','')).rsplit('/',1)[-1].lower(),'unknown')
            if availability=='available' and re.search(r'non disponibile|esaurito',text): availability='unknown'
            condition,valid_until=self.promotion(context)
            brand=product.get('brand')
            if isinstance(brand,dict): brand=brand.get('name')
            name=product['name']
            cents=money_cents(str(raw['price']))
            # Il selettore e' provvisorio: ogni fonte deve validarlo sui campioni reali.
            # Non confondere prezzo al kg, prezzo barrato e prezzo corrente.
            for price in self.visible_prices(context):
                if price.name=='meta' or re.search(r'unit|old|original|strike', ' '.join(price.get('class',[])),re.I): continue
                visible=price.get_text(' ',strip=True)
                if re.search(r'/\s*(kg|l)\b|al\s+(kg|litro|l)\b',visible,re.I): continue
                amount=re.search(r'([0-9]+(?:[.,][0-9]{2}))\s*(?:€|EUR)|(?:€|EUR)\s*([0-9]+(?:[.,][0-9]{2}))',visible,re.I)
                if context.name!='[document]' and not amount and not price.has_attr('content'):
                    raise ValueError('Prezzo visibile non leggibile')
                if amount and money_cents(amount[1] or amount[2])!=cents:
                    raise ValueError('Prezzo visibile e JSON-LD discordanti')
                if price.has_attr('content') and money_cents(price['content'])!=cents:
                    raise ValueError('Prezzo visibile e JSON-LD discordanti')
            # Una descrizione con altre quantita' non prova il formato acquistabile.
            pack=None if 'peso variabile' in text else parse_pack(name)
            result.append(Offer(offer_id=f'{self.source}:{sku}:{scope}:{postcode or ""}',product_id=f'gtin:{normalize_gtin(gtin)}' if verified else f'{self.source}:{sku}',source=self.source,source_sku=sku,gtin=gtin,gtin_verified=verified,name=name,brand=brand,pack=pack,pack_price_cents=cents,observed_at=observed_at,source_url=url,scope=scope,postcode=postcode,availability=availability,condition=condition,valid_until=valid_until))
        if not result: raise ValueError('Scheda priva di prodotto')
        return result
