from datetime import datetime,timezone
import sqlite3
from fastapi import FastAPI,HTTPException,Query
from fastapi.responses import JSONResponse
from .normalize import normalize_gtin

def create_app(store):
    app=FastAPI(title='Igor prezzi',version='1',docs_url=None,redoc_url=None)

    @app.exception_handler(sqlite3.Error)
    async def unavailable(request,error): return JSONResponse(status_code=503,content={'detail':'Catalogo temporaneamente indisponibile'})

    def envelope(items,postcode=None,next_offset=None):
        return {'schema_version':1,'catalog_version':store.version,'generated_at':datetime.now(timezone.utc).isoformat(),'postcode':postcode,'items':items,'next_offset':next_offset}

    @app.get('/v1/sources')
    def sources(): return envelope(store.sources())

    @app.get('/v1/products')
    def search(q:str|None=Query(default=None,min_length=2,max_length=100),gtin:str|None=None,postcode:str=Query(default='20125',pattern=r'^[0-9]{5}$'),limit:int=Query(default=20,ge=1,le=20),offset:int=Query(default=0,ge=0)):
        if (q is None)==(gtin is None) or q is not None and len(q.strip())<2: raise HTTPException(422,'Specificare nome o GTIN')
        normalized=normalize_gtin(gtin) if gtin else None
        if gtin is not None and normalized is None: raise HTTPException(422,'GTIN non valido')
        values=store.search(q,normalized,postcode,limit+1,offset)
        return envelope([o.model_dump(mode='json') for o in values[:limit]],postcode,offset+limit if len(values)>limit else None)

    @app.get('/v1/products/{product_id}/offers')
    def offers(product_id:str,postcode:str=Query(default='20125',pattern=r'^[0-9]{5}$')):
        values=store.offers(product_id,postcode)
        if not values: raise HTTPException(404,'Prodotto non presente')
        return envelope([o.model_dump(mode='json') for o in values],postcode)
    return app
