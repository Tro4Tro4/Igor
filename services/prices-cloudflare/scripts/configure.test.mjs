import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,readFileSync,existsSync,mkdirSync,rmSync} from 'node:fs';
import {resolve} from 'node:path';
const base=resolve('.wrangler/config-tests');mkdirSync(base,{recursive:true});
function run(values,fn) {
  const directory=mkdtempSync(base+'/test-');const output=resolve(directory,'deploy.jsonc');
  const environment={...process.env,CF_ACCOUNT_ID:'',CF_DATABASE_ID:'',...values};
  try {fn(spawnSync(process.execPath,['scripts/configure.mjs',output],{env:environment,encoding:'utf8'}),output);}
  finally {if(!directory.startsWith(base+'/')&&!directory.startsWith(base+'\\'))throw new Error('unsafe cleanup');rmSync(directory,{recursive:true});}
}
test('missing IDs create no deploy config or leaked token',()=>run({CF_DEPLOY_API_TOKEN:'secret-never-print'},(r,path)=>{
  assert.notEqual(r.status,0);assert.equal(existsSync(path),false);
  assert.equal((r.stdout+r.stderr).includes('secret-never-print'),false);
}));
test('valid IDs bind the real database without credentials',()=>run({CF_ACCOUNT_ID:'a'.repeat(32),
  CF_DATABASE_ID:'00000000-0000-4000-8000-000000000001',CF_DEPLOY_API_TOKEN:'secret-never-print'},(r,path)=>{
  assert.equal(r.status,0,r.stderr);const config=JSON.parse(readFileSync(path,'utf8'));
  assert.equal(config.d1_databases[0].binding,'DB');
  assert.equal(config.d1_databases[0].database_id,'00000000-0000-4000-8000-000000000001');
  assert.equal(config.observability.enabled,false);assert.equal(config.workers_dev,true);
  assert.equal(readFileSync(path,'utf8').includes('secret-never-print'),false);
}));
test('malformed account is rejected',()=>run({CF_ACCOUNT_ID:'../../other',CF_DATABASE_ID:'bad'},(r,path)=>{
  assert.notEqual(r.status,0);assert.equal(existsSync(path),false);
}));
