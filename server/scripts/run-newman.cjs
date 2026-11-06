/** Run the existing collection without serializing resolved Authorization headers. */
const fs = require('node:fs');
const path = require('node:path');
const newman = require(process.env.NEWMAN_MODULE || path.resolve(__dirname, '../.local/newman/node_modules/newman'));
const environment = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../postman/local.postman_environment.json'), 'utf8'));
for (const [key, value] of Object.entries({baseUrl: process.env.TEST_BASE_URL, adminToken: process.env.ADMIN_TOKEN})) {
  if (!value) throw new Error(`Missing ${key} environment value`);
  environment.values.find(v => v.key === key).value = value;
}
const base = new URL(process.env.TEST_BASE_URL);
if (base.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(base.hostname)) throw new Error('Only local test servers are allowed');
newman.run({collection: path.resolve(__dirname, '../postman/LAST-PAYCHECK.postman_collection.json'), environment,
  delayRequest: 100, timeoutRequest: 20000}, (error, summary) => {
  const result = {negativeRequests: (summary?.run.executions || []).filter(e => e.response?.code >= 400).length, stats: summary?.run.stats, failures: (summary?.run.failures || []).map(f => ({test: f.error.test, message: f.error.message}))};
  let output = JSON.stringify(result, null, 2);
  output = output.split(process.env.ADMIN_TOKEN).join('[REDACTED]');
  fs.writeFileSync(path.resolve(__dirname, '../.local/newman-summary.json'), output);
  console.log(output);
  process.exitCode = error || result.failures.length ? 1 : 0;
});
