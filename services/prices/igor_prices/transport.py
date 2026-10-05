from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import time
import httpx
from .access import SourceAccess

@dataclass(frozen=True)
class HttpResult:
    status: int
    body: str
    headers: dict[str,str]
    final_url: str

class AccessSuspended(RuntimeError): pass

class Transport:
    def __init__(self,access:SourceAccess,*,fetch=None,sleep=time.sleep):
        self.access=access
        self.sleep=sleep
        self.fetch=fetch or self._fetch
        self.suspended=False
        self.last_request:float|None=None

    def _fetch(self,url):
        deadline=time.monotonic()+15
        with httpx.Client(timeout=15,follow_redirects=False,headers={'User-Agent':'Igor/0.1.0 (prezzi online)'}) as client:
            for _ in range(6):
                remaining=deadline-time.monotonic()
                if remaining<=0: raise TimeoutError('Timeout totale')
                self.access.validate(url,redirect=True)
                with client.stream('GET',url,timeout=remaining) as response:
                    if response.is_redirect:
                        url=str(response.url.join(response.headers['location']))
                        self.access.validate(url,redirect=True)
                        continue
                    chunks=[]; length=0
                    for chunk in response.iter_bytes():
                        length+=len(chunk)
                        if length>2*1024*1024: raise ValueError('Risposta troppo grande')
                        if time.monotonic()>deadline: raise TimeoutError('Timeout totale')
                        chunks.append(chunk)
                    return HttpResult(response.status_code,b''.join(chunks).decode('utf-8'),dict(response.headers),str(response.url))
        raise ValueError('Troppi redirect')

    def get(self,url:str)->HttpResult:
        if self.suspended: raise AccessSuspended('Fonte sospesa')
        self.access.validate(url)
        for attempt in range(2):
            if self.last_request is not None: self.sleep(max(0,1-(time.monotonic()-self.last_request)))
            self.last_request=time.monotonic()
            try: result=self.fetch(url)
            except (httpx.TransportError,OSError,TimeoutError):
                if attempt==1: raise
                self.sleep(1); continue
            self.access.validate(result.final_url,redirect=True)
            if len(result.body.encode('utf-8'))>2*1024*1024: raise ValueError('Risposta troppo grande')
            if result.status==403:
                self.suspended=True
                raise AccessSuspended('Fonte sospesa dopo HTTP 403')
            if result.status==429:
                raw={k.lower():v for k,v in result.headers.items()}.get('retry-after','1')
                try: wait=float(raw)
                except ValueError: wait=(parsedate_to_datetime(raw)-datetime.now(timezone.utc)).total_seconds()
                if not 0<=wait<=120: raise ValueError('Retry-After fuori budget')
                if attempt==0: self.sleep(wait); continue
            elif 500<=result.status<600 and attempt==0:
                self.sleep(1); continue
            if result.status!=200: raise ValueError(f'Fonte HTTP {result.status}')
            return result
        raise ValueError('Tentativi esauriti')
