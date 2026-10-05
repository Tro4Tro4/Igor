from datetime import date
from decimal import Decimal
from typing import Literal
from urllib.parse import urlsplit
import re
from pydantic import BaseModel, Field, AwareDatetime, ConfigDict, model_validator
from .normalize import normalize_gtin

DOMAINS = {'carrefour':'www.carrefour.it', 'conad':'spesaonline.conad.it', 'esselunga':'spesaonline.esselunga.it', 'tigros':'www.tigros.it', 'lidl':'www.lidl.it', 'eurospin':'www.eurospin.it'}

class Pack(BaseModel):
    model_config = ConfigDict(frozen=True, extra='forbid')
    amount: Decimal = Field(gt=0,allow_inf_nan=False)
    unit: Literal['KG','L','PZ']
    pack_count: int = Field(default=1,ge=1,strict=True)

class Offer(BaseModel):
    model_config = ConfigDict(frozen=True, extra='forbid')
    offer_id: str = Field(min_length=1,max_length=256)
    product_id: str = Field(min_length=1,max_length=256)
    source: str
    source_sku: str = Field(min_length=1,max_length=128)
    gtin: str | None = None
    gtin_verified: bool = False
    name: str = Field(min_length=1,max_length=500)
    brand: str | None = None
    pack: Pack | None = None
    pack_price_cents: int = Field(gt=0,le=2**63-1,strict=True)
    currency: Literal['EUR'] = 'EUR'
    observed_at: AwareDatetime
    source_url: str
    scope: Literal['generic','postcode']
    postcode: str | None = None
    availability: Literal['available','unavailable','unknown']
    condition: Literal['ordinary','loyalty','coupon','multi_buy','unknown']
    valid_until: date | None = None

    @model_validator(mode='after')
    def coherent(self):
        url = urlsplit(self.source_url)
        if self.source not in DOMAINS or url.scheme != 'https' or url.hostname != DOMAINS[self.source] or url.username or url.password or url.port not in (None,443):
            raise ValueError('URL fonte non ammesso')
        if (self.scope == 'postcode' and (self.postcode is None or not re.fullmatch(r'[0-9]{5}',self.postcode))) or (self.scope == 'generic' and self.postcode is not None):
            raise ValueError('Zona incoerente')
        gtin = normalize_gtin(self.gtin) if self.gtin else None
        expected = f'gtin:{gtin}' if self.gtin_verified and gtin else f'{self.source}:{self.source_sku}'
        if self.gtin_verified and gtin is None or self.product_id != expected:
            raise ValueError('Identita prodotto non verificata')
        if not self.name.strip(): raise ValueError('Nome mancante')
        return self
