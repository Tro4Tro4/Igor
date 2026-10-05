import {writeFileSync} from 'node:fs';
const account=process.env.CF_ACCOUNT_ID??'';
const database=process.env.CF_DATABASE_ID??'';
if(!/^[a-f0-9]{32}$/i.test(account)||!/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(database)) {
  process.stderr.write('CF_ACCOUNT_ID e CF_DATABASE_ID mancanti o non validi.\n');process.exit(1);
}
const config={name:'igor-prices',main:'src/index.ts',compatibility_date:'2026-10-05',workers_dev:true,
  account_id:account,observability:{enabled:false},
  d1_databases:[{binding:'DB',database_name:'igor-prices',database_id:database,migrations_dir:'migrations'}]};
writeFileSync(process.argv[2]??'wrangler.deploy.jsonc',JSON.stringify(config,null,2)+'\n',{mode:0o600});
