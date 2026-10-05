import httpx
import pytest
from igor_prices.cloudflare.client import D1Client, D1Error

ACCOUNT='a'*32
DATABASE='00000000-0000-4000-8000-000000000001'

def success(rows=None,changes=0):
    return {'success':True,'result':[{'success':True,'results':rows or [],
      'meta':{'changes':changes,'rows_read':1,'rows_written':changes,'size_after':8192}}]}

def test_query_parameterized_and_result():
    def handle(request):
        import json
        assert request.url.host=='api.cloudflare.com'
        assert json.loads(request.content)=={'sql':'SELECT ?','params':['secret search']}
        return httpx.Response(200,json=success([{'value':7}]))
    result=D1Client(ACCOUNT,DATABASE,'private',transport=httpx.MockTransport(handle)).query('SELECT ?',['secret search'])
    assert result.results==[{'value':7}]
    assert result.rows_read==1

@pytest.mark.parametrize('response',[
    httpx.Response(403,text='token-secret'),
    httpx.Response(200,json={'success':False,'errors':[{'message':'token-secret'}]}),
    httpx.Response(200,json={'success':True,'result':[{'success':False}]}),
    httpx.Response(200,json={'success':True,'result':[]}),
    httpx.Response(200,text='not json token-secret')])
def test_errors_are_explicit_and_never_expose_token(response):
    client=D1Client(ACCOUNT,DATABASE,'token-secret',transport=httpx.MockTransport(lambda _:response))
    with pytest.raises(D1Error) as caught: client.query('SELECT 1')
    assert 'token-secret' not in str(caught.value)

def test_retry_safe_only():
    calls=[]
    def handle(request):
        calls.append(request)
        return httpx.Response(503) if len(calls)==1 else httpx.Response(200,json=success())
    client=D1Client(ACCOUNT,DATABASE,'private',transport=httpx.MockTransport(handle),sleep=lambda _:None)
    with pytest.raises(D1Error): client.query('UPDATE catalog SET version=version+1')
    assert len(calls)==1
    calls.clear()
    assert client.query('SELECT 1',retry_safe=True).results==[]
    assert len(calls)==2

def test_missing_credentials_fail_before_network(monkeypatch):
    for key in ['CF_ACCOUNT_ID','CF_DATABASE_ID','CF_D1_API_TOKEN']:monkeypatch.delenv(key,raising=False)
    with pytest.raises(D1Error):D1Client.from_environment()

@pytest.mark.parametrize('account,database',[("not-account",DATABASE),(ACCOUNT,'bad/database')])
def test_invalid_identifiers_rejected(account,database):
    with pytest.raises(D1Error): D1Client(account,database,'private')

def test_transient_timeout_never_leaks_credentials():
    def handle(request):raise httpx.ReadTimeout('token-secret',request=request)
    client=D1Client(ACCOUNT,DATABASE,'token-secret',transport=httpx.MockTransport(handle),sleep=lambda _:None)
    with pytest.raises(D1Error) as caught:client.query('SELECT 1',retry_safe=True)
    assert 'token-secret' not in str(caught.value)
