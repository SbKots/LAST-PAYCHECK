const fs=require('node:fs');const path=require('node:path');
const newman=require(process.env.NEWMAN_MODULE||path.resolve(__dirname,'../.local/newman/node_modules/newman'));
const environment=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../postman/local.postman_environment.json'),'utf8'));
const values={baseUrl:process.env.TEST_BASE_URL,publicKey:'local-public-fixture',adminJwt:process.env.TEST_ADMIN_JWT,userJwt:process.env.TEST_USER_JWT};
const url=new URL(values.baseUrl);if(url.protocol!=='http:'||url.hostname!=='127.0.0.1')throw new Error('Local test server only');
for(const [key,value] of Object.entries(values)){if(!value)throw new Error('Missing test environment');environment.values.find(v=>v.key===key).value=value;}
newman.run({collection:path.resolve(__dirname,'../postman/LAST-PAYCHECK-Supabase.postman_collection.json'),environment,timeoutRequest:20000},(error,summary)=>{
 const result={stats:summary?.run.stats,failures:(summary?.run.failures||[]).map(f=>({test:f.error.test,message:f.error.message}))};
 let output=JSON.stringify(result,null,2);for(const value of [values.adminJwt,values.userJwt])output=output.split(value).join('[REDACTED]');
 fs.writeFileSync(path.resolve(__dirname,'../.local/newman-summary.json'),output);console.log(output);process.exitCode=error||result.failures.length?1:0;
});
