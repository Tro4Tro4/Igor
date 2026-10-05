import {beforeEach,it,expect} from 'vitest';
import {env} from 'cloudflare:workers';
import worker from '../src/index';
import fixture from '../../prices-contract/publication.json';
import {applySchema,db} from './helpers';
beforeEach(applySchema);
async function run(operation:{sql:string,params:unknown[]}) {
  return db.prepare(operation.sql).bind(...operation.params).run();
}
async function search() {
  return await (await worker.fetch(new Request('https://igor.test/v1/products?q=latte'),env as any)).json() as any;
}
it('executes real publisher SQL without exposing partial catalog',async()=>{
  for(const operation of fixture.operations.slice(0,fixture.initial))await run(operation);
  expect((await search()).items.map((o:any)=>o.pack_price_cents)).toEqual([139]);
  const next=fixture.operations.slice(fixture.initial);
  await run(next[0]);await run(next[1]); // Stage metadata and the first 20 of 21 offers.
  expect((await search()).items.map((o:any)=>o.pack_price_cents)).toEqual([139]);
  const switchOperation=next.at(-1)!;
  await expect(run(switchOperation)).rejects.toThrow();
  expect((await search()).catalog_version).toBe(1);
  for(const operation of next.slice(2))await run(operation);
  const current=await search();expect(current.catalog_version).toBe(2);
  expect(current.items.every((o:any)=>o.pack_price_cents===199)).toBe(true);
  expect(current.next_offset).toBe(20);
  await run(switchOperation);expect((await search()).catalog_version).toBe(2);
});
it('rolls back a failed D1 batch and excludes a suspended source',async()=>{
  for(const operation of fixture.operations.slice(0,fixture.initial))await run(operation);
  await expect(db.batch([
    db.prepare("UPDATE sources SET status='suspended' WHERE id='carrefour'"),
    db.prepare('INSERT INTO missing_table VALUES(1)')])).rejects.toThrow();
  expect((await search()).items.length).toBe(1);
  await db.prepare("UPDATE sources SET status='suspended' WHERE id='carrefour'").run();
  expect((await search()).items).toEqual([]);
});
it('measures a synthetic 2000-offer pilot in D1 locally',async()=>{
  await db.prepare("INSERT INTO sources(id,status,priority,metadata) VALUES('carrefour','active',1,'{\"reuse_verified\":true}')").run();
  let written=0,read=0,bytes=0;const timings:number[]=[];
  for(const [batch,price] of [['pilot1',139],['pilot2',199]] as const) {
    const stage=await db.prepare(`INSERT INTO offer_batches VALUES(?,'carrefour',?,2000,'staging','2026-10-05T00:00:00Z','2026-10-05T00:00:00Z',NULL)`)
      .bind(batch,batch).run();written+=stage.meta.rows_written;
    const insert=fixture.operations.find(op=>op.sql.includes('json_each'))!.sql;
    for(let start=0;start<2000;start+=20) {
      const rows=Array.from({length:20},(_,i)=>{
        const n=start+i;
        return {source:'carrefour',sku:`${n}`,scope:'generic',postcode:'',offer_id:`carrefour:${n}:generic`,
          product_id:`carrefour:${n}`,gtin:null,name_key:`latte marca ${n}`,payload:JSON.stringify({
            name:`Latte Marca ${n}`,source:'carrefour',source_sku:`${n}`,offer_id:`carrefour:${n}:generic`,
            product_id:`carrefour:${n}`,pack_price_cents:price,currency:'EUR',pack:{amount:'1',unit:'L',pack_count:1},
            scope:'generic',postcode:null,condition:'ordinary',availability:'unknown',observed_at:'2026-10-05T00:00:00Z',
            source_url:`https://www.carrefour.it/p/${n}`,gtin:null,gtin_verified:false,brand:'Marca',valid_until:null})};
      });
      const result=await db.prepare(insert).bind(batch,JSON.stringify(rows)).run();
      written+=result.meta.rows_written;read+=result.meta.rows_read;
      bytes=Math.max(bytes,result.meta.size_after??0);
    }
    const result=await db.prepare("UPDATE sources SET current_batch=?,digest=?,last_successful_at='2026-10-05T00:00:00Z' WHERE id='carrefour'")
      .bind(batch,batch).run();written+=result.meta.rows_written;
  }
  for(const token of ['latte','marca 1999','absent']) {
    const start=performance.now();const body=await worker.fetch(new Request('https://igor.test/v1/products?q='+encodeURIComponent(token)),env as any);
    expect(body.status).toBe(200);await body.text();timings.push(performance.now()-start);
  }
  expect(written).toBeLessThan(100000);expect(bytes).toBeGreaterThan(0);expect(bytes).toBeLessThan(500*1024*1024);
  console.log('SYNTHETIC_LOCAL_PILOT',JSON.stringify({offers:2000,batches:2,rows_written:written,rows_read_upload:read,
    bytes,query_wall_ms:timings}));
});
