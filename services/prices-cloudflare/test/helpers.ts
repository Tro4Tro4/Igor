import {env} from 'cloudflare:workers';
export const db = env.DB as D1Database;
export const NOW='2026-10-05T00:00:00+00:00';
export const TEST_TOKEN='synthetic-test-private-token-00000000';
export function privateRequest(url:string,init:RequestInit={}) {
  const headers=new Headers(init.headers);
  headers.set('Authorization','Bearer '+TEST_TOKEN);
  return new Request(url,{...init,headers});
}
export async function applySchema() {
  // Current plugin shares D1 storage across tests; reset only the local test binding.
  await db.prepare('DROP TRIGGER IF EXISTS protect_current').run();
  for (const table of ['offers','runs','offer_batches','sources','catalog']) {
    await db.prepare(`DROP TABLE IF EXISTS ${table}`).run();
  }
  // D1 exec is line based; execute complete SQLite statements, including triggers.
  const sql=env.SCHEMA as string;
  const statements=sql.split('-- statement boundary').map(s=>s.trim()).filter(Boolean);
  for (const statement of statements) await db.prepare(statement).run();
}
export async function seedSource(source='carrefour',status='active',priority=1) {
  await db.prepare('INSERT INTO sources(id,status,priority,metadata) VALUES(?,?,?,?)')
    .bind(source,status,priority,JSON.stringify({id:source,priority,status,reuse_verified:status==='active'})).run();
}
export async function stage(id:string,source='carrefour',count=1) {
  await db.prepare(`INSERT INTO offer_batches(id,source,digest,expected_count,status,created_at,observed_at)
    VALUES(?,?,?,?,'staging',?,?)`).bind(id,source,id,count,NOW,NOW).run();
}
export async function insertOffer(batch:string,sku='one',source='carrefour',payload?:string) {
  const wire=payload??JSON.stringify({offer_id:`${source}:${sku}:generic`,product_id:`${source}:${sku}`,
    source,source_sku:sku,gtin:null,gtin_verified:false,name:'Latte 1 L',brand:'Marca',pack:null,
    pack_price_cents:139,currency:'EUR',observed_at:NOW,source_url:`https://www.carrefour.it/p/${sku}`,
    scope:'generic',postcode:null,availability:'unknown',condition:'ordinary',valid_until:null});
  await db.prepare(`INSERT INTO offers(batch_id,source,sku,scope,postcode,offer_id,product_id,gtin,name_key,payload)
    VALUES(?,?,?,'generic','',?,?,NULL,'latte 1 l marca',?)`)
    .bind(batch,source,sku,`${source}:${sku}:generic`,`${source}:${sku}`,wire).run();
}
export async function publish(id:string,source='carrefour') {
  await db.prepare('UPDATE sources SET current_batch=?,digest=?,last_successful_at=? WHERE id=?')
    .bind(id,id,NOW,source).run();
}
