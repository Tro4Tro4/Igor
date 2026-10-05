import type {CatalogQuery} from './input';
export type CatalogResponse={version:number,payloads:string[],nextOffset:number|null};
export async function readCatalog(db:D1Database,query:CatalogQuery):Promise<CatalogResponse> {
  const base=`SELECT o.payload FROM offers o JOIN sources s ON s.current_batch=o.batch_id
    WHERE s.status='active' AND (o.scope='generic' OR o.postcode=?)`;
  let statement:D1PreparedStatement;
  if(query.kind==='sources')statement=db.prepare(`SELECT id,status,priority,metadata,last_successful_at FROM sources ORDER BY priority,id`);
  else if(query.kind==='offers')statement=db.prepare(base+' AND o.product_id=? ORDER BY s.priority,o.offer_id')
    .bind(query.postcode,query.productId);
  else {
    const filter=query.gtin!==null?'o.gtin=?':query.terms.map(()=> 'instr(o.name_key,?)>0').join(' AND ');
    const args=query.gtin!==null?[query.gtin]:query.terms;
    statement=db.prepare(base+' AND '+filter+' ORDER BY s.priority,o.offer_id LIMIT ? OFFSET ?')
      .bind(query.postcode,...args,query.limit+1,query.offset);
  }
  const [versionResult,rowsResult]=await db.batch<Record<string,unknown>>([db.prepare('SELECT version FROM catalog WHERE id=1'),statement]);
  if(!versionResult.success||!rowsResult.success)throw new Error('D1 unavailable');
  const version=versionResult.results[0]?.version;
  if(typeof version!=='number'||!Number.isSafeInteger(version)||version<0)throw new Error('Catalog unavailable');
  let rows=rowsResult.results;
  const nextOffset=query.kind==='search'&&rows.length>query.limit?query.offset+query.limit:null;
  if(query.kind==='search')rows=rows.slice(0,query.limit);
  const payloads=query.kind==='sources'?rows.map(row=>JSON.stringify({...JSON.parse(row.metadata as string),
    source:row.id,status:row.status,last_successful_at:row.last_successful_at})):
    rows.map(row=>row.payload as string);
  return {version,payloads,nextOffset};
}
