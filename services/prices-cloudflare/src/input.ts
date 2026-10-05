import {casefold} from './casefold';
export class InputError extends Error {}
export type CatalogQuery = {kind:'sources'} | {kind:'search',postcode:string,terms:string[],gtin:string|null,limit:number,offset:number}
  | {kind:'offers',postcode:string,productId:string};
function fail():never {throw new InputError('Parametri non validi');}
export function normalizeGtin(value:string):string|null {
  if(!/^(?:[0-9]{8}|[0-9]{12,14})$/.test(value))return null;
  const digits=[...value].map(Number);let sum=0;
  for(let i=digits.length-2,weight=3;i>=0;i--,weight=weight===3?1:3)sum+=digits[i]*weight;
  if((10-sum%10)%10!==digits.at(-1))return null;
  return value.replace(/^0+/,'')||'0';
}
export function parseInput(url:URL):CatalogQuery {
  const p=url.searchParams;
  for(const key of ['q','gtin','postcode','limit','offset'])if(p.getAll(key).length>1)fail();
  if(url.pathname==='/v1/sources')return {kind:'sources'};
  const postcode=p.get('postcode')??'20125';if(!/^[0-9]{5}$/.test(postcode))fail();
  if(url.pathname==='/v1/products') {
    const q=p.get('q'),rawGtin=p.get('gtin');if((q===null)===(rawGtin===null))fail();
    const integer=(value:string|null, fallback:number):number=>{
      if(value===null)return fallback;
      if(!/^[0-9]+$/.test(value))fail();
      const n=Number(value);if(!Number.isSafeInteger(n))fail();return n;
    };
    const limit=integer(p.get('limit'),20),offset=integer(p.get('offset'),0);
    if(limit<1||limit>20||offset>Number.MAX_SAFE_INTEGER-20)fail();
    let gtin:string|null=null,terms:string[]=[];
    if(rawGtin!==null) {gtin=normalizeGtin(rawGtin);if(gtin===null)fail();}
    else {if(q===null||[...q].length>100||[...q.trim()].length<2)fail();terms=casefold(q).trim().split(/\s+/u);}
    return {kind:'search',postcode,terms,gtin,limit,offset};
  }
  const match=/^\/v1\/products\/([^/]+)\/offers$/.exec(url.pathname);
  if(!match)throw new InputError('not-found');
  let productId:string;try{productId=decodeURIComponent(match[1]);}catch{fail();}
  if(!productId||[...productId].length>256)fail();
  return {kind:'offers',postcode,productId};
}
