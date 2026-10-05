import {beforeEach,it,expect} from 'vitest';
import {applySchema,db,seedSource,stage,insertOffer,publish} from './helpers';
beforeEach(applySchema);
it('rejects incomplete publication and preserves version',async()=>{
  await seedSource(); await stage('b','carrefour',2); await insertOffer('b');
  await expect(publish('b')).rejects.toThrow();
  expect(await db.prepare('SELECT version FROM catalog WHERE id=1').first('version')).toBe(0);
  expect(await db.prepare('SELECT current_batch FROM sources').first('current_batch')).toBeNull();
});
it('publishes once and rejects switching while suspended',async()=>{
  await seedSource(); await stage('a'); await insertOffer('a'); await publish('a');
  await publish('a');
  expect(await db.prepare('SELECT version FROM catalog WHERE id=1').first('version')).toBe(1);
  await stage('b');await insertOffer('b');
  await db.prepare("UPDATE sources SET status='suspended' WHERE id='carrefour'").run();
  await expect(publish('b')).rejects.toThrow();
  expect(await db.prepare('SELECT current_batch FROM sources').first('current_batch')).toBe('a');
});
it('protects current batch from cleanup and rejects mismatched source',async()=>{
  await seedSource();await seedSource('conad','active',2);
  await stage('a');await insertOffer('a');await publish('a');
  await expect(db.prepare("DELETE FROM offer_batches WHERE id='a'").run()).rejects.toThrow();
  await expect(publish('a','conad')).rejects.toThrow();
});
it('rejects two sources exceeding the global offer limit',async()=>{
  await seedSource();await seedSource('conad','active',2);
  await stage('a','carrefour',1200);await stage('b','conad',1000);
  for (const [id,source,count] of [['a','carrefour',1200],['b','conad',1000]] as const) {
    for(let start=0;start<count;start+=20) await db.batch(Array.from({length:Math.min(20,count-start)},(_,i)=>
      db.prepare(`INSERT INTO offers VALUES(?,?,?,'generic','',?,?,NULL,'latte','{}')`)
        .bind(id,source,`${start+i}`,`${source}:${start+i}`,`${source}:${start+i}`)));
  }
  await publish('a');await expect(publish('b','conad')).rejects.toThrow();
  expect(await db.prepare('SELECT version FROM catalog WHERE id=1').first('version')).toBe(1);
});
