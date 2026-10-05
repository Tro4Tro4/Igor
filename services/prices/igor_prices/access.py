from dataclasses import dataclass
from urllib.parse import urlsplit, unquote
import re
from .models import DOMAINS

@dataclass(frozen=True)
class SourceAccess:
    source: str
    urls: list[str]
    reuse_verified: bool
    robots: str

    def validate(self,url: str, *, redirect=False):
        parts=urlsplit(url)
        if not self.reuse_verified or parts.scheme!='https' or parts.hostname!=DOMAINS.get(self.source) or parts.port not in (None,443) or parts.username or parts.password:
            raise ValueError('Accesso fonte non ammesso')
        if not redirect and url not in self.urls: raise ValueError('URL non validato nel manifest')
        if not robots_allowed(self.robots,url): raise ValueError('Percorso escluso da robots')

def robots_allowed(body:str,url:str)->bool:
    groups=[]; agents=[]; rules=[]
    for raw in body.splitlines()+['User-agent: __end__']:
        line=raw.split('#',1)[0].strip()
        if ':' not in line: continue
        key,value=line.split(':',1); key=key.strip().lower(); value=value.strip()
        if key=='user-agent':
            if rules:
                groups.append((agents,rules)); agents=[]; rules=[]
            agents.append(value.lower())
        elif key in ('allow','disallow') and agents:
            rules.append((key=='allow',value))
    if agents and rules: groups.append((agents,rules))
    applicable=[(max((len(a) if a!='*' else 0 for a in aa if a=='*' or a in 'igor'),default=-1),rr) for aa,rr in groups]
    specificity=max((length for length,_ in applicable),default=-1)
    if specificity<0: return True
    parts=urlsplit(url); path=unquote(parts.path or '/')+('?' + unquote(parts.query) if parts.query else '')
    matches=[]
    for length,rr in applicable:
        if length!=specificity: continue
        for allow,pattern in rr:
            if not pattern: continue
            pattern=unquote(pattern)
            anchored=pattern.endswith('$')
            literal=pattern[:-1] if anchored else pattern
            expression='^'+re.escape(literal).replace(r'\*','.*')+('$' if anchored else '')
            if re.search(expression,path): matches.append((len(literal.replace('*','')),allow))
    return max(matches,default=(0,True))[1]
