import {beforeEach,it,expect} from 'vitest';
import {env} from 'cloudflare:workers';
import worker from '../src/index';
import {applySchema,db,seedSource,stage,insertOffer,publish,privateRequest,TEST_TOKEN} from './helpers';
beforeEach(applySchema);
const get=(path:string)=>worker.fetch(privateRequest('https://igor.test'+path),env as any);
it('returns six candidates without fake offers',async()=>{
  for(const [i,id] of ['carrefour','conad','esselunga','tigros','lidl','eurospin'].entries())await seedSource(id,'candidate',i+1);
  const response=await get('/v1/sources');const body=await response.json() as any;
  expect(body.schema_version).toBe(1);expect(body.items.length).toBe(6);
  expect(body.items[2].source).toBe('esselunga');expect(body.items[2].status).toBe('candidate');
  expect((await (await get('/v1/products?q=latte&postcode=20125')).json() as any).items).toEqual([]);
});
it.each(['q=a','q=latte&gtin=80050865','q=latte&postcode=abcde','q=latte&limit=21',
  'q=latte&offset=-1','gtin=123','q=%20%20','','q=latte&q=acqua','q=latte&offset=9007199254740992'])
('rejects invalid search %s',async query=>expect((await get('/v1/products?'+query)).status).toBe(422));
it('searches independent tokens and exposes paging',async()=>{
  await seedSource();await stage('a','carrefour',2);await insertOffer('a','one');await insertOffer('a','two');await publish('a');
  const body=await (await get('/v1/products?q=LATTE%20Marca&limit=1')).json() as any;
  expect(body.items[0].source_sku).toBe('one');expect(body.next_offset).toBe(1);
  expect((await get('/v1/products/carrefour%3Atwo/offers')).status).toBe(200);
  expect((await get('/v1/products/missing/offers')).status).toBe(404);
});
it('does not round Long cents or Decimal pack amounts',async()=>{
  await seedSource();await stage('a');
  const payload=JSON.stringify({name:'Latte',pack_price_cents:'LONG',pack:{amount:'0.375',unit:'KG',pack_count:3}})
    .replace('"LONG"','9223372036854775807');
  await insertOffer('a','one','carrefour',payload);await publish('a');
  const wire=await (await get('/v1/products?q=latte')).text();
  expect(wire).toContain('"pack_price_cents":9223372036854775807');
  expect(wire).toContain('"amount":"0.375"');
});
it('excludes suspended source immediately',async()=>{
  await seedSource();await stage('a');await insertOffer('a');await publish('a');
  await db.prepare("UPDATE sources SET status='suspended' WHERE id='carrefour'").run();
  expect((await get('/v1/products/carrefour%3Aone/offers')).status).toBe(404);
  const body=await (await get('/v1/sources')).json() as any;
  expect(body.items[0].status).toBe('suspended');expect(body.catalog_version).toBe(2);
});
it('filters territory, exact GTIN and literal wildcard searches',async()=>{
  await seedSource();await stage('a');await insertOffer('a');
  await db.prepare("UPDATE sources SET status='candidate' WHERE id='carrefour'").run();
  // Updating test-only staging rows needs removing the immutability guard, not production code.
  await db.prepare('DROP TRIGGER immutable_offer').run();
  await db.prepare("UPDATE offers SET scope='postcode',postcode='20125',gtin='80050865',name_key='caffè 100% marca'").run();
  await db.prepare("UPDATE sources SET status='active' WHERE id='carrefour'").run();await publish('a');
  expect((await (await get('/v1/products?gtin=0000080050865&postcode=20125')).json() as any).items.length).toBe(1);
  expect((await (await get('/v1/products?gtin=80050865&postcode=20126')).json() as any).items).toEqual([]);
  expect((await (await get('/v1/products?q=CAFF%C3%88%20100%25')).json() as any).items.length).toBe(1);
  expect((await (await get('/v1/products?q=%25_')).json() as any).items).toEqual([]);
});
it('supports 100-character query without D1 LIKE pattern limit',async()=>{
  expect((await get('/v1/products?q='+encodeURIComponent('à'.repeat(100)))).status).toBe(200);
});
it('reports database failure and rejects writes',async()=>{
  const broken={PRIVATE_API_TOKEN:TEST_TOKEN,DB:{batch:async()=>{throw new Error('private internal error')}}} as any;
  const response=await worker.fetch(privateRequest('https://igor.test/v1/sources'),broken);
  expect(response.status).toBe(503);expect(await response.text()).not.toContain('private');
  expect((await worker.fetch(privateRequest('https://igor.test/v1/sources',{method:'POST'}),env as any)).status).toBe(405);
});
it.each([undefined,'Bearer wrong','Basic '+TEST_TOKEN,'Bearer '+TEST_TOKEN+'x'])
('rejects unauthorized requests before database access',async authorization=>{
  const protectedEnv={PRIVATE_API_TOKEN:TEST_TOKEN,get DB():D1Database{throw new Error('Must not access database');}};
  const headers:Record<string,string>={};if(authorization)headers.Authorization=authorization;
  const response=await worker.fetch(new Request('https://igor.test/v1/sources',{headers}),protectedEnv);
  expect(response.status).toBe(401);
  expect(response.headers.get('cache-control')).toBe('no-store');
  expect(await response.text()).not.toContain(TEST_TOKEN);
});
it('fails closed when server secret is absent',async()=>{
  expect((await worker.fetch(privateRequest('https://igor.test/v1/sources'),{DB:env.DB})).status).toBe(503);
});
