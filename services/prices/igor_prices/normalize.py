import re
from decimal import Decimal

def money_cents(text: str) -> int:
    value = text.replace('€', '').strip()
    if ',' in value:
        if not re.fullmatch(r'(?:[0-9]+|[0-9]{1,3}(?:\.[0-9]{3})+),[0-9]{2}', value):
            raise ValueError('Importo ambiguo')
        value = value.replace('.', '').replace(',', '.')
    elif not re.fullmatch(r'[0-9]+(?:\.[0-9]{1,2})?', value):
        raise ValueError('Importo non valido')
    cents = int(Decimal(value) * 100)
    if not 0 < cents <= 2**63-1:
        raise ValueError('Importo fuori limite')
    return cents

def normalize_gtin(text: str) -> str | None:
    if not re.fullmatch(r'[0-9]{8}|[0-9]{12,14}', text): return None
    digits = [int(c) for c in text]
    checksum = sum(n * (3 if i % 2 == 0 else 1) for i,n in enumerate(reversed(digits[:-1])))
    if (checksum + digits[-1]) % 10: return None
    return text.lstrip('0') or '0'

def parse_pack(text: str):
    from .models import Pack
    if re.search(r'variabil|circa|oppure|al kg', text, re.I): return None
    matches = list(re.finditer(r'(?<![\w.,])(?:(\d+)\s*[x×]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|ml|g|l|pz)\b', text, re.I))
    if len(matches) != 1: return None
    match = matches[0]
    count = int(match[1] or 1)
    amount = Decimal(match[2].replace(',', '.')) * count
    unit = match[3].lower()
    if amount <= 0 or count < 1: return None
    if unit in ('g','ml'): amount /= 1000
    return Pack(amount=amount,unit={'g':'KG','kg':'KG','l':'L','ml':'L','pz':'PZ'}[unit],pack_count=count)
