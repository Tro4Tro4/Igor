from .base import Adapter
class ConadAdapter(Adapter):
    source='conad'

    def context(self,soup,sku):
        roots=soup.select('.product-header')
        if len(roots)>1: raise ValueError('Scheda principale non disambiguata')
        return roots[0] if roots else soup

    def visible_prices(self,context):
        if context.name=='[document]': return super().visible_prices(context)
        # Il totale del selettore quantita' parte da zero anche con un prezzo valido.
        prices=context.select('.product-button-add .price.f-roboto')
        if not prices: raise ValueError('Prezzo visibile della confezione mancante')
        return prices
