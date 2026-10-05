import type {CatalogResponse} from './catalog';
export function jsonEnvelope(result:CatalogResponse,postcode:string|null,now:Date):Response {
  const prefix=JSON.stringify({schema_version:1,catalog_version:result.version,generated_at:now.toISOString(),
    postcode,next_offset:result.nextOffset});
  // Payload is validated JSON stored by the Python publisher. Do not round Long cents through Number.
  return new Response(prefix.slice(0,-1)+',"items":['+result.payloads.join(',')+']}',{
    headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'}});
}
