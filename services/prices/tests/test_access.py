import pytest
from igor_prices.access import SourceAccess
from igor_prices.transport import Transport, HttpResult, AccessSuspended

def access(): return SourceAccess('carrefour',['https://www.carrefour.it/p/a'],True,'User-agent: *\nDisallow: /search')

@pytest.mark.parametrize('url',['https://localhost/p/a','http://www.carrefour.it/p/a','https://www.carrefour.it/search','https://www.carrefour.it/p/not-approved','https://www.carrefour.it:444/p/a'])
def test_url_non_ammesso(url):
    with pytest.raises(ValueError): access().validate(url)

def test_robots_esclude_url_autorizzato():
    a=SourceAccess('carrefour',['https://www.carrefour.it/search'],True,'User-agent: *\nDisallow: /search')
    with pytest.raises(ValueError): a.validate(a.urls[0])

@pytest.mark.parametrize('rule',['/*?search=','/p/a?search=x$'])
def test_robots_wildcard_e_ancora(rule):
    url='https://www.carrefour.it/p/a?search=x'
    a=SourceAccess('carrefour',[url],True,'User-agent: *\nDisallow: '+rule)
    with pytest.raises(ValueError): a.validate(url)

def test_robots_allow_piu_specifico():
    url='https://www.carrefour.it/p/a'
    a=SourceAccess('carrefour',[url],True,'User-agent: *\nDisallow: /p/\nAllow: /p/a$')
    a.validate(url)

def test_riutilizzo_necessario():
    with pytest.raises(ValueError): SourceAccess('carrefour',['https://www.carrefour.it/p/a'],False,'').validate('https://www.carrefour.it/p/a')

def test_403_sospende_senza_retry():
    calls=[]
    def fetch(url):
        calls.append(url)
        return HttpResult(403,'',{},url)
    transport=Transport(access(),fetch=fetch,sleep=lambda _:None)
    with pytest.raises(AccessSuspended): transport.get('https://www.carrefour.it/p/a')
    with pytest.raises(AccessSuspended): transport.get('https://www.carrefour.it/p/a')
    assert len(calls)==1

def test_429_riprova_rispettando_attesa():
    replies=iter([HttpResult(429,'',{'retry-after':'3'},'https://www.carrefour.it/p/a'),HttpResult(200,'ok',{},'https://www.carrefour.it/p/a')])
    waits=[]
    assert Transport(access(),fetch=lambda _:next(replies),sleep=waits.append).get('https://www.carrefour.it/p/a').body=='ok'
    assert 3 in waits

def test_attesa_eccessiva_non_blocca_job():
    with pytest.raises(ValueError): Transport(access(),fetch=lambda url:HttpResult(429,'',{'retry-after':'121'},url),sleep=lambda _:None).get('https://www.carrefour.it/p/a')

@pytest.mark.parametrize('result',[HttpResult(200,'ok',{},'https://localhost/p/a'),HttpResult(200,'x'*(2*1024*1024+1),{},'https://www.carrefour.it/p/a')])
def test_redirect_e_body_controllati(result):
    with pytest.raises(ValueError): Transport(access(),fetch=lambda _:result).get('https://www.carrefour.it/p/a')
