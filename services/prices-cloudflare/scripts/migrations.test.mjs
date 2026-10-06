import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {unstable_splitSqlQuery} from 'wrangler';

test('la migrazione resta completa dopo il parser SQL di Wrangler',()=>{
  const sql=readFileSync('migrations/0001_catalog.sql','utf8');
  const db=new DatabaseSync(':memory:');
  try {
    // Le prove D1 per istruzione non coprono la separazione usata dal deploy.
    for(const statement of unstable_splitSqlQuery(sql)) db.exec(statement);
    assert.equal(db.prepare("SELECT count(*) AS n FROM sqlite_master WHERE type='trigger'").get().n,6);
    assert.equal(db.prepare('SELECT version FROM catalog WHERE id=1').get().version,0);
  } finally {db.close();}
});
