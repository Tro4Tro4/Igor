from dataclasses import dataclass
import os
import re
import time
import httpx


class D1Error(RuntimeError):
    """Sanitized remote failure; never include response bodies or headers."""


@dataclass(frozen=True)
class D1Result:
    results: list[dict]
    changes: int = 0
    rows_read: int = 0
    rows_written: int = 0
    size_after: int | None = None


class D1Client:
    def __init__(self, account_id, database_id, token, *, transport=None, sleep=time.sleep):
        if not re.fullmatch(r'[a-fA-F0-9]{32}', account_id or '') or not re.fullmatch(
                r'[a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12}', database_id or '') or not token:
            raise D1Error('Configurazione Cloudflare mancante o non valida')
        self.url = f'https://api.cloudflare.com/client/v4/accounts/{account_id}/d1/database/{database_id}/query'
        self._session = httpx.Client(transport=transport, timeout=15, follow_redirects=False,
                                     headers={'Authorization': f'Bearer {token}'})
        self._sleep = sleep

    @classmethod
    def from_environment(cls):
        return cls(*(os.environ.get(key, '') for key in ('CF_ACCOUNT_ID', 'CF_DATABASE_ID', 'CF_D1_API_TOKEN')))

    def close(self):
        self._session.close()

    def query(self, sql, params=None, *, retry_safe=False):
        for attempt in range(2 if retry_safe else 1):
            try:
                reply = self._session.post(self.url, json={'sql': sql, 'params': params or []})
            except httpx.HTTPError:
                if retry_safe and attempt == 0:
                    self._sleep(1)
                    continue
                raise D1Error('D1 errore di rete') from None
            if reply.status_code in (429, 500, 502, 503, 504) and retry_safe and attempt == 0:
                try:
                    delay = min(120, max(1, int(reply.headers.get('Retry-After', '1'))))
                except ValueError:
                    delay = 1
                self._sleep(delay)
                continue
            if reply.status_code != 200:
                raise D1Error(f'D1 HTTP {reply.status_code}')
            try:
                wire = reply.json()
                result = wire['result']
                if wire['success'] is not True or not isinstance(result, list) or len(result) != 1:
                    raise ValueError()
                item = result[0]
                if item['success'] is not True or not isinstance(item['results'], list):
                    raise ValueError()
                meta = item.get('meta', {})
                return D1Result(item['results'], **{name: meta.get(name, default) for name, default in
                    [('changes', 0), ('rows_read', 0), ('rows_written', 0), ('size_after', None)]})
            except (ValueError, KeyError, TypeError):
                raise D1Error('D1 risposta non valida') from None
        raise D1Error('D1 temporaneamente indisponibile')
