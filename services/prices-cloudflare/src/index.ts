import {InputError,parseInput} from './input';
import {readCatalog} from './catalog';
import {jsonEnvelope} from './envelope';
const error=(status:number,detail:string)=>Response.json({detail},{status,headers:{'cache-control':'no-store'}});
export default {
  async fetch(request:Request,env:{DB:D1Database}):Promise<Response> {
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
} satisfies ExportedHandler<{DB:D1Database}>;
