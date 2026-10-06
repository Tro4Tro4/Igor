import {InputError,parseInput} from './input';
import {readCatalog} from './catalog';
import {jsonEnvelope} from './envelope';
const error=(status:number,detail:string)=>Response.json({detail},{status,headers:{'cache-control':'no-store'}});
export type PricesEnv={DB:D1Database;PRIVATE_API_TOKEN?:string};
async function authorized(request:Request,token:string):Promise<boolean> {
  const header=request.headers.get('authorization');
  if(!header?.startsWith('Bearer ')||header.length>256)return false;
  const digest=async(value:string)=>new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)));
  const [actual,expected]=await Promise.all([digest(header.slice(7)),digest(token)]);
  let difference=0;
  for(let i=0;i<expected.length;i++)difference|=actual[i]^expected[i];
  return difference===0;
}
export default {
  async fetch(request:Request,env:PricesEnv):Promise<Response> {
    if(!env.PRIVATE_API_TOKEN||env.PRIVATE_API_TOKEN.length<32)return error(503,'Accesso privato non configurato');
    if(!await authorized(request,env.PRIVATE_API_TOKEN))return error(401,'Accesso non autorizzato');
    if(request.method!=='GET')return error(405,'Metodo non consentito');
    try {
      const query=parseInput(new URL(request.url));
      const result=await readCatalog(env.DB,query);
      if(query.kind==='offers'&&!result.payloads.length)return error(404,'Prodotto non presente');
      return jsonEnvelope(result,query.kind==='sources'?null:query.postcode,new Date());
    } catch(e) {
      if(e instanceof InputError)return error(e.message==='not-found'?404:422,'Parametri non validi');
      return error(503,'Catalogo temporaneamente indisponibile');
    }
  }
} satisfies ExportedHandler<PricesEnv>;
