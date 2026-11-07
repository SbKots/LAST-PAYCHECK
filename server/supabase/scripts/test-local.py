"""Docker-free LOCAL verification. Never connect to hosted Supabase.
Requires an explicitly disposable PostgreSQL cluster; creates a random database.
Auth/JWT issuer and provider responses are fixtures; PostgreSQL/PostgREST/Deno are real.
"""
import argparse,base64,hashlib,hmac,json,os,secrets,shutil,subprocess,sys,time,uuid
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError,URLError
import xml.etree.ElementTree as ET
from decimal import Decimal
import psycopg
from psycopg import sql
import yaml
from openapi_schema_validator import OAS30Validator
SB=Path(__file__).resolve().parents[1]
ROOT=SB.parents[1]
LOCAL=SB/'.local'
sys.path.insert(0,str(SB/'tests'))
from sql_cases import run_sql

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disposable-postgres',action='store_true',required=True)
    parser.parse_args()
    LOCAL.mkdir(exist_ok=True)
    pgport=int(os.environ.get('TEST_PG_PORT','55432')); pgpassword=os.environ.get('TEST_PG_PASSWORD','')
    name='lastpay_sb_'+uuid.uuid4().hex[:12]
    def connect(db=name):return psycopg.connect(host='127.0.0.1',port=pgport,user='postgres',password=pgpassword,dbname=db,connect_timeout=5,options='-c statement_timeout=30000')
    with connect('postgres') as c:
        c.autocommit=True;c.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(name)))
    gateway_password=secrets.token_urlsafe(32)
    with connect() as c:
        c.execute((SB/'tests/bootstrap.sql').read_text(encoding='utf-8-sig'))
        c.execute(sql.SQL('ALTER ROLE lastpay_test_gateway PASSWORD {}').format(sql.Literal(gateway_password)))
    for file in sorted((SB/'migrations').glob('*.sql')):
        with connect() as c:c.execute(file.read_text(encoding='utf-8-sig'))
    ids={who:str(uuid.uuid4()) for who in ['admin','userA','userB','anonymous']}
    with connect() as c:
        for who,id in ids.items():c.execute('INSERT INTO auth.users(id,email) VALUES(%s,%s)',(id,who+'@example.invalid'))
        c.execute('INSERT INTO lastpay_private.admin_membership(user_id) VALUES(%s)',(ids['admin'],))
    results=[];processes=[];logs=[]
    def record(label,ok):
        results.append({'test':label,'passed':bool(ok)})
        if not ok:raise AssertionError(label)
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    if env.get('TEST_PG_BIN'):env['PATH']=env['TEST_PG_BIN']+os.pathsep+env.get('PATH','')
    deno=os.environ.get('DENO_BINARY') or (str(LOCAL/'deno/deno.exe') if os.name=='nt' else shutil.which('deno'))
    postgrest=os.environ.get('POSTGREST_BINARY') or (str(LOCAL/'postgrest/postgrest.exe') if os.name=='nt' else shutil.which('postgrest'))
    def command(args,log):
        with (LOCAL/log).open('w',encoding='utf-8') as output:completed=subprocess.run(list(map(str,args)),cwd=SB,env=env,stdout=output,stderr=subprocess.STDOUT)
        if completed.returncode:raise RuntimeError(log+' failed; inspect ignored local log')
    def launch(args,log):
        output=(LOCAL/log).open('w',encoding='utf-8');logs.append(output)
        p=subprocess.Popen(list(map(str,args)),cwd=SB,env=env,stdout=output,stderr=subprocess.STDOUT);processes.append(p)
    secret=secrets.token_urlsafe(48)
    def token(who='userA',role='authenticated',expiry=3600,signing_key=secret):
        def b64(data):return base64.urlsafe_b64encode(json.dumps(data,separators=(',',':')).encode()).decode().rstrip('=')
        a=b64({'alg':'HS256','typ':'JWT'});b=b64({'role':role,'sub':ids.get(who),'aud':'authenticated','exp':int(time.time())+expiry,'is_anonymous':who=='anonymous','user_metadata':{'role':'admin'}})
        sig=base64.urlsafe_b64encode(hmac.new(signing_key.encode(),(a+'.'+b).encode(),hashlib.sha256).digest()).decode().rstrip('=')
        return a+'.'+b+'.'+sig
    restport=int(os.environ.get('TEST_REST_PORT','18120'));apiport=int(os.environ.get('TEST_API_PORT','18121'));base=f'http://127.0.0.1:{apiport}'
    env.update(PGRST_DB_URI=f'postgresql://lastpay_test_gateway:{gateway_password}@127.0.0.1:{pgport}/{name}',PGRST_DB_SCHEMAS='lastpay_api',PGRST_DB_ANON_ROLE='anon',PGRST_JWT_SECRET=secret,PGRST_SERVER_HOST='127.0.0.1',PGRST_SERVER_PORT=str(restport),TEST_JWT_SECRET=secret,TEST_SERVICE_JWT=token(role='service_role'),TEST_ANON_JWT=token(who='catalog-public',role='anon'),TEST_USER_IDS=','.join(ids.values()),TEST_API_PORT=str(apiport),TEST_REST_PORT=str(restport))
    summary={'passed':False,'database':name,'cloudDeployed':False,'auth':'local signed JWT/Auth fixture; not GoTrue','providers':'controlled responses; no live external API','results':results}
    try:
        command([deno,'task','check'],'deno-check.log')
        command([deno,'task','test'],'deno-test.log')
        unit=ET.parse(LOCAL/'deno-unit.xml').getroot();summary['unitTests']=sum(int(s.get('tests','0')) for s in unit.iter('testsuite'))
        run_sql(connect,ids,record);summary['sqlChecks']=len(results);print('SQL checks PASS:',len(results),flush=True)
        launch([postgrest],'postgrest.log')
        launch([deno,'run','--allow-env','--allow-net=127.0.0.1',SB/'tests/local_server.ts'],'edge.log')
        for _ in range(100):
            if any(p.poll() is not None for p in processes):raise RuntimeError('Local server exited')
            try:
                with urlopen(base+'/rest/v1/',timeout=1) as r:
                    if r.status==200:break
            except (URLError,TimeoutError):time.sleep(.1)
        else:raise RuntimeError('Local server readiness timed out')
        def exchange(path,expected,body=None,who=None,method=None,override_token=None,label=None,browser=False):
            headers={} if browser else {'apikey':'local-public-fixture','Content-Profile':'lastpay_api','Accept-Profile':'lastpay_api'}
            if who or override_token:headers['Authorization']='Bearer '+(override_token or token(who))
            if body is not None:headers['Content-Type']='application/json'
            data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None
            req=Request(base+path,data=data,headers=headers,method=method or ('POST' if body is not None else 'GET'))
            try:r=urlopen(req,timeout=15)
            except HTTPError as error:r=error
            with r:
                raw=r.read();value=json.loads(raw) if raw else None
                record(label or path+' '+str(expected),r.status==expected)
                if r.status!=expected:raise AssertionError(str(value))
                if raw:record((label or path)+' JSON',r.headers.get_content_type()=='application/json')
                if expected>=400:
                    record((label or path)+' error envelope',isinstance(value,dict) and ('code' in value or set(value.get('error',{}))=={'code','message','requestId','details'}))
                return value,r.headers
        def rpc(fn,args,expected=200,who=None,label=None):return exchange('/rest/v1/rpc/'+fn,expected,{'args':args},who,label=label)
        spec=json.loads(json.dumps(yaml.safe_load((ROOT/'openapi.yaml').read_text(encoding='utf-8'))),parse_float=Decimal)
        def expand(v):
            if isinstance(v,dict) and '$ref' in v:
                node=spec
                for part in v['$ref'].removeprefix('#/').split('/'):node=node[part]
                return expand(node)
            if isinstance(v,dict):return {k:expand(x) for k,x in v.items()}
            if isinstance(v,list):return [expand(x) for x in v]
            return v
        mapping=json.loads((ROOT/'docs/supabase/operation-map.json').read_text(encoding='utf-8'))
        covered=set()
        manifest,_=rpc('get_catalog',{});record('manifest matches old schema',OAS30Validator(expand(spec['components']['schemas']['Manifest']),format_checker=OAS30Validator.FORMAT_CHECKER).is_valid(manifest))
        browser,headers=exchange('/functions/v1/catalog',200,label='browser catalog anonymous GET',browser=True)
        record('browser catalog exactly matches public RPC',browser==manifest)
        record('browser catalog JSON UTF-8',headers.get('Content-Type')=='application/json; charset=utf-8')
        _,headers=exchange('/functions/v1/catalog',204,method='OPTIONS',label='browser catalog OPTIONS',browser=True)
        record('browser catalog CORS',headers.get('Access-Control-Allow-Origin')=='*')
        _,headers=exchange('/functions/v1/catalog',405,method='POST',label='browser catalog POST forbidden',browser=True)
        record('browser catalog Allow',headers.get('Allow')=='GET, OPTIONS')
        exchange('/functions/v1/catalog?catalogVersion=draft',400,label='browser catalog cannot select draft',browser=True)
        version='api-draft';skill=dict(id='api-skill',code='api-skill',title='API test',description='',source='DEMO',externalId=None);etag='"rev-1"'
        for row in mapping:
            fn=row['rpc'];path=row['oldPath'];m=row['method'];args={};expected=200;who=None
            if not fn:
                new=row['newPath'].replace('{occupationCode}','53-7062.00')
                if 'worldbank' in new:new+='?country=RUS&yearFrom=2020&yearTo=2022'
                value,_=exchange(new,200,who='userA',label=row['id']+' provider success')
                record(row['id']+' envelope',set(['provider','fromCache','stale','data'])<=value.keys())
                contract=expand(spec['paths'][row['newPath']]['get']['responses']['200']['content']['application/json']['schema'])
                record(row['id']+' provider response schema',OAS30Validator(contract).is_valid(value))
                covered.add(row['id']);continue
            if row.get('resource'):
                args=dict(resource=row['resource'],catalogVersion='demo-1')
                if fn=='get_catalog_item':args['id']={'occupations':'warehouse','car-models':'city-hatch','tracks':'night-delivery'}[row['resource']]
                elif row['resource']=='inflation':args.update(country='RUS',yearFrom=2020,yearTo=2020)
            if '/admin/' in path:
                who='admin'
                if fn=='create_draft':args=dict(version=version,baseVersion='demo-1');expected=201
                elif fn in ['list_draft_skills','get_draft_skill']:
                    args=dict(catalogVersion=version)
                    if fn=='get_draft_skill':args['id']='api-skill'
                else:
                    args=dict(version=version,ifMatch=etag)
                    if fn in ['update_skill','delete_skill']:args['id']='api-skill'
                    if fn=='create_skill':args['body']=skill;expected=201
                    if fn=='update_skill':args['body']={k:v for k,v in skill.items() if k!='id'}
            request_schema=expand(spec['paths'][row['newPath']]['post']['requestBody']['content']['application/json']['schema'])
            record(row['id']+' Supabase request schema',OAS30Validator(request_schema).is_valid({'args':args}))
            value,headers=rpc(fn,args,expected,who,label=row['id']+' RPC success')
            if fn=='create_draft':
                public,_=exchange('/functions/v1/catalog',200,who='admin',label='browser catalog remains public with admin JWT')
                record('browser catalog hides new draft even from admin caller',public==manifest and public['catalogVersion']!=version)
            if headers.get('ETag'):etag=headers['ETag'];record(row['id']+' ETag',etag.startswith('"rev-'))
            if fn=='delete_skill':
                record(row['id']+' deletion receipt',value==dict(deleted=True,id='api-skill',catalogVersion=version))
            else:
                response=expand(spec['paths'][row['newPath']]['post']['responses'][str(expected)])
                record(row['id']+' Supabase response schema',OAS30Validator(expand(response['content']['application/json']['schema']),format_checker=OAS30Validator.FORMAT_CHECKER).is_valid(json.loads(json.dumps(value),parse_float=Decimal)))
            covered.add(row['id'])
        record('all 24 operations positive',len(covered)==24)
        negatives=[]
        for row in mapping:
            fn=row['rpc'];old=row['oldPath']
            if fn:
                args={'unexpected':True} if fn=='get_catalog' else {'resource':row['resource']} if row.get('resource') else {}
                status=401 if '/admin/' in old else 400
                rpc(fn,args,status,label=row['id']+' negative')
            else:
                p=row['newPath'].replace('{occupationCode}','bad')
                if 'worldbank' in p:status=400
                elif p.endswith('onet/occupations'):p+='?unexpected=1';status=400
                elif '/onet/skills/' in p:status=422
                elif p.endswith('/brands'):p+='?page=0';status=422
                else:p+='?brandId=!';status=422
                exchange(p,status,who='userA',label=row['id']+' negative')
            negatives.append(dict(operation=row['id'],expected=status,passed=True))
        summary['operations']=len(covered);summary['negativeScenarios']=len(negatives)
        rpc('create_draft',dict(version='unauthorized',baseVersion='demo-1'),403,who='userB',label='ordinary user cannot administer')
        exchange('/rest/v1/rpc/get_catalog',401,{'args':{}},override_token=token(signing_key='wrong-secret'),label='bad JWT signature')
        exchange('/rest/v1/rpc/get_catalog',401,{'args':{}},override_token=token(expiry=-3600),label='expired JWT')
        exchange('/functions/v1/providers/onet/occupations',401,label='Edge missing JWT')
        exchange('/functions/v1/providers/onet/occupations',401,override_token=token(signing_key='wrong-secret'),label='Edge forged JWT')
        exchange('/functions/v1/providers/onet/occupations',403,who='anonymous',label='Edge anonymous session denied')
        exchange('/functions/v1/providers/onet/occupations',405,method='POST',label='Edge unsupported method')
        exchange('/rest/v1/rpc/get_catalog',405,method='GET',label='Data API RPC unsupported GET')
        with connect() as c:c.execute('DELETE FROM lastpay_private.rate_window')
        cached,_=exchange('/functions/v1/providers/worldbank/inflation?country=RUS&yearFrom=2020&yearTo=2022',200,who='userA',label='Edge persistent cache hit')
        record('Edge persistent cache hit confirmed',cached['fromCache'] is True)
        with connect() as c:c.execute('DELETE FROM lastpay_private.rate_window')
        if time.time()%60>55:time.sleep(60-time.time()%60)
        for i in range(30):exchange('/functions/v1/providers/worldbank/inflation',400,who='userA',label='Edge limit request '+str(i+1))
        exchange('/functions/v1/providers/worldbank/inflation',429,who='userA',label='Edge persisted rate limit 31st request')
        with connect() as c:c.execute('DELETE FROM lastpay_private.rate_window')
        summary['apiChecksBeforeNewman']=len(results)-summary['sqlChecks']
        # Newman receives tokens in memory only; never export its raw request/header report.
        env.update(TEST_BASE_URL=base,TEST_ADMIN_JWT=token('admin'),TEST_USER_JWT=token('userA'))
        command([shutil.which('node'),SB/'scripts/run-newman.cjs'],'newman.log')
        summary['newman']=json.loads((LOCAL/'newman-summary.json').read_text())
        record('Newman assertions',not summary['newman']['failures'])
        # Only the PostgREST process started by this disposable local run is stopped.
        processes[0].terminate();processes[0].wait(timeout=10)
        unavailable,headers=exchange('/functions/v1/catalog',503,label='browser catalog database unavailable',browser=True)
        record('browser catalog unavailable message safe',unavailable['error']['message']=='Catalog temporarily unavailable')
        record('browser catalog unavailable Retry-After',headers.get('Retry-After')=='60')
        record('browser catalog unavailable JSON UTF-8',headers.get('Content-Type')=='application/json; charset=utf-8')
        record('browser catalog unavailable CORS',headers.get('Access-Control-Allow-Origin')=='*')
        summary['negativeResults']=negatives
        summary['passed']=True
    except Exception as error:
        summary['executionError']=type(error).__name__
        raise
    finally:
        for p in reversed(processes):
            if p.poll() is None:
                p.terminate()
                try:p.wait(timeout=10)
                except subprocess.TimeoutExpired:p.kill();p.wait()
        for log in logs:log.close()
        summary['checks']=len(results);summary['failed']=sum(not r['passed'] for r in results)
        (LOCAL/'results.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k not in ['results','negativeResults']},indent=2))
if __name__=='__main__':main()
