import {cloudflareTest} from '@cloudflare/vitest-plugin';
import {defineConfig} from 'vitest/config';
import {readFileSync} from 'node:fs';

export default defineConfig({
  plugins:[cloudflareTest({wrangler:{configPath:'./wrangler.local.jsonc'},
    miniflare:{bindings:{SCHEMA:readFileSync('./migrations/0001_catalog.sql','utf8')}}})],
  test:{include:['test/**/*.test.ts']}
});
