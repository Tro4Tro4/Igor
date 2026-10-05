from dataclasses import dataclass
from urllib.parse import urlsplit
from urllib.robotparser import RobotFileParser
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
        robot=RobotFileParser()
        robot.parse(self.robots.splitlines())
        if not robot.can_fetch('Igor',url): raise ValueError('Percorso escluso da robots')
