from .base import Adapter
from datetime import datetime
import re

class CarrefourAdapter(Adapter):
    source='carrefour'

    def context(self,soup,sku):
        roots=soup.select('.product-main[data-pid]')
        if not roots: return soup
        matches=[root for root in roots if root['data-pid']==sku]
        if len(matches)!=1: raise ValueError('Scheda e SKU discordanti')
        return matches[0]

    def visible_prices(self,context):
        if context.name=='[document]': return super().visible_prices(context)
        prices=context.select('.price .sales .value')
        if not prices: raise ValueError('Prezzo visibile della confezione mancante')
        return prices

    def promotion(self,context):
        if context.name=='[document]': return super().promotion(context)
        label=context.select_one('.offers-label')
        text=' '.join(e.get_text(' ',strip=True) for e in context.select('.offers-label, .promo-bundle-tooltip')).lower()
        if re.search(r'acquista almeno|acquistando|bottiglie \(o multipli\)',text): condition='multi_buy'
        elif 'payback' in text or 'con carta' in text: condition='loyalty'
        elif 'coupon' in text or 'buono sconto' in text: condition='coupon'
        elif label and label.get_text(' ',strip=True) and not re.fullmatch(r'offerta(?: 0 sprechi)?',label.get_text(' ',strip=True),re.I): condition='unknown'
        else: condition='ordinary'
        dates=set(re.findall(r'fino al\s+(\d{2}/\d{2}/\d{4})',' '.join(e.get_text(' ',strip=True) for e in context.select('.pricebook-valid-date, .promo-bundle-tooltip')),re.I))
        if len(dates)>1: raise ValueError('Date promozionali non disambiguate')
        valid_until=datetime.strptime(next(iter(dates)),'%d/%m/%Y').date() if dates else None
        return condition,valid_until
