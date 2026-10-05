import json
import re
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
    def parse(self,body,url,observed_at,scope,postcode)->list[Offer]:
        soup=BeautifulSoup(body,'html.parser')
        text=soup.get_text(' ',strip=True).lower()
        docs=[json.loads(script.get_text()) for script in soup.find_all('script',attrs={'type':'application/ld+json'})]
        result=[]
        for doc in docs:
            for product in products(doc):
                raw=product.get('offers')
                if isinstance(raw,list):
                    if len(raw)!=1: raise ValueError('Prezzi multipli non disambiguati')
                    raw=raw[0]
                if not isinstance(raw,dict) or raw.get('@type','Offer')!='Offer' or raw.get('priceCurrency')!='EUR': raise ValueError('Prezzo confezione mancante')
                sku=str(product.get('sku') or product.get('mpn') or '')
                if not sku: raise ValueError('SKU mancante')
                gtin=next((str(product[k]) for k in ('gtin','gtin13','gtin14','gtin12','gtin8') if product.get(k)),None)
                verified=bool(gtin and normalize_gtin(gtin))
                if gtin and not verified: raise ValueError('GTIN dichiarato non valido')
                availability={'InStock':'available','OutOfStock':'unavailable'}.get(str(raw.get('availability','')).rsplit('/',1)[-1],'unknown')
                if availability=='available' and re.search(r'non disponibile|esaurito',text): availability='unknown'
                condition='ordinary'
                if re.search(r'prezzo\s+con\s+carta|solo\s+con\s+carta|carta\s+fedelt',text): condition='loyalty'
                elif re.search(r'coupon|buono sconto',text): condition='coupon'
                elif re.search(r'\b[23]\s*[x×]\s*[12]\b|acquistando\s+\d',text): condition='multi_buy'
                brand=product.get('brand')
                if isinstance(brand,dict): brand=brand.get('name')
                name=product['name']
                # Una descrizione con altre quantita' non prova il formato acquistabile.
                pack=parse_pack(name)
                result.append(Offer(offer_id=f'{self.source}:{sku}:{scope}:{postcode or ""}',product_id=f'gtin:{normalize_gtin(gtin)}' if verified else f'{self.source}:{sku}',source=self.source,source_sku=sku,gtin=gtin,gtin_verified=verified,name=name,brand=brand,pack=pack,pack_price_cents=money_cents(str(raw['price'])),observed_at=observed_at,source_url=url,scope=scope,postcode=postcode,availability=availability,condition=condition))
        if not result: raise ValueError('Scheda priva di prodotto')
        return result
