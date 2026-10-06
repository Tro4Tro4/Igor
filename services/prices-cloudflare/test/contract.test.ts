import {beforeEach,it,expect} from 'vitest';
import {env} from 'cloudflare:workers';
import worker from '../src/index';
import fixture from '../../prices-contract/fixtures.json';
import {applySchema,db,NOW,seedSource,stage,publish,privateRequest} from './helpers';
beforeEach(applySchema);
it('satisfies shared FastAPI Android v1 fixtures',async()=>{
  for(const [i,source] of ['carrefour','conad'].entries()) {
    await seedSource(source,'active',i+1);await stage(source,source);
    const offer=fixture.offers.find(o=>o.source===source)!;
    await db.prepare(`INSERT INTO offers VALUES(?,?,?,'generic','',?,?,NULL,?,?)`)
      .bind(source,source,offer.source_sku,offer.offer_id,offer.product_id,
        (offer.name+' '+offer.brand).toLowerCase(),JSON.stringify(offer)).run();
    await publish(source,source);
  }
  for(const query of fixture.queries) {
    const response=await worker.fetch(privateRequest('https://igor.test/v1/products?'+query.query),env as any);
    const body=await response.json() as any;
    expect(body.schema_version).toBe(1);expect(body.items.map((o:any)=>o.offer_id)).toEqual(query.ids);
    expect(body.next_offset).toBe(query.next_offset??null);
  }
});
