// Local, disposable D1 only. This script never reads account IDs or API tokens.
import {readFileSync} from 'node:fs';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
import {build} from 'esbuild';
const bundle=await build({entryPoints:['src/index.ts'],bundle:true,format:'esm',write:false,platform:'browser',target:'es2022'});
const runtime=new Miniflare(convertV4MiniflareOptions({modules:true,script:bundle.outputFiles[0].text,compatibilityDate:'2026-10-05',
  bindings:{PRIVATE_API_TOKEN:'synthetic-test-private-token-00000000'},d1Databases:{DB:'igor-synthetic-pilot'},d1Persist:false}));
const fixture=JSON.parse(readFileSync('../prices-contract/publication.json','utf8'));
const offer=JSON.parse(readFileSync('../prices-contract/fixtures.json','utf8')).offers[0];
const upload=fixture.operations.find(o=>o.sql.includes('json_each')).sql;
const result={synthetic:true,local_only:true,offers:2000,batches:3,rows_written:0,rows_read:0,bytes:0,queries:[]};
const meter=r=>{result.rows_written+=r.meta.rows_written;result.rows_read+=r.meta.rows_read;
  result.bytes=Math.max(result.bytes,r.meta.size_after??0);};
try {
  const db=await runtime.getD1Database('DB');
  for(const sql of readFileSync('migrations/0001_catalog.sql','utf8').split('-- statement boundary')) {
    if(sql.trim())meter(await db.prepare(sql.trim()).run());
  }
  meter(await db.prepare("INSERT INTO sources(id,status,priority,metadata) VALUES('carrefour','active',1,?)")
    .bind(JSON.stringify({id:'carrefour',priority:1,reuse_verified:true,status:'active'})).run());
  for(let generation=1;generation<=3;generation++) {
    const batch=`pilot${generation}`,timestamp=`2026-10-0${generation}T00:00:00+00:00`;
    meter(await db.prepare("INSERT INTO offer_batches VALUES(?,'carrefour',?,2000,'staging',?, ?,NULL)")
      .bind(batch,batch,timestamp,timestamp).run());
    for(let start=0;start<2000;start+=20) {
      const rows=Array.from({length:20},(_,i)=>{
        const sku=String(start+i),item={...offer,name:`Latte Marca ${sku}`,source_sku:sku,
          offer_id:`carrefour:${sku}:generic`,product_id:`carrefour:${sku}`,pack_price_cents:139+generation,
          source_url:`https://www.carrefour.it/p/${sku}`,observed_at:timestamp};
        return {source:'carrefour',sku,scope:'generic',postcode:'',offer_id:item.offer_id,
          product_id:item.product_id,gtin:null,name_key:item.name.toLowerCase(),payload:JSON.stringify(item)};
      });
      meter(await db.prepare(upload).bind(batch,JSON.stringify(rows)).run());
    }
    meter(await db.prepare('UPDATE sources SET current_batch=?,digest=?,last_successful_at=? WHERE id=?')
      .bind(batch,batch,timestamp,'carrefour').run());
  }
  meter(await db.prepare("DELETE FROM offer_batches WHERE id='pilot1'").run());
  for(const q of ['latte','marca 1999','assente','à'.repeat(100)]) {
    const start=performance.now(),response=await runtime.dispatchFetch('https://igor.test/v1/products?q='+encodeURIComponent(q),{headers:{Authorization:'Bearer synthetic-test-private-token-00000000'}});
    const body=await response.json();
    if(response.status!==200)throw new Error('Pilot search failed');
    result.queries.push({query:q.length>20?'100 unicode characters':q,wall_ms:performance.now()-start,items:body.items.length});
  }
  if(result.rows_written>=100000||result.bytes>=500*1024*1024)throw new Error('Pilot exceeds free quotas');
  console.log(JSON.stringify(result,null,2));
} finally {await runtime.dispose();}
