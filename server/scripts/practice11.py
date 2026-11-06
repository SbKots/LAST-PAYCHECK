"""Practice 11 runner. Requires an explicitly disposable LOCAL PostgreSQL cluster.
Creates a unique database; never reads .env, never deletes a database, never uses cloud.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
from urllib.request import urlopen, Request
from urllib.error import HTTPError, URLError
import uuid
import xml.etree.ElementTree as ET
import psycopg
from psycopg import sql
import yaml

SERVER = Path(__file__).resolve().parents[1]
LOCAL = SERVER / '.local'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disposable-postgres', action='store_true', required=True,
                        help='Confirm this entire local cluster is disposable: migrations create cluster-wide roles')
    args = parser.parse_args()
    LOCAL.mkdir(exist_ok=True)
    port = int(os.environ.get('TEST_PG_PORT', '55432'))
    pg_password = os.environ.get('TEST_PG_PASSWORD', '')
    database = 'lastpay_test_' + uuid.uuid4().hex[:12]
    with psycopg.connect(host='127.0.0.1', port=port, dbname='postgres', user='postgres', password=pg_password, autocommit=True) as connection:
        connection.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
    env = dict(os.environ, ENV_ONLY='true', DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/{database}',
               DB_USER='postgres', DB_PASSWORD=pg_password or secrets.token_urlsafe(32),
               DB_READER_PASSWORD=secrets.token_urlsafe(36), DB_WRITER_PASSWORD=secrets.token_urlsafe(36),
               ADMIN_TOKEN=secrets.token_urlsafe(36), ADMIN_SCOPE='catalog:write', ONET_API_KEY='', CARS_API_TOKEN='',
               HOST='127.0.0.1', PORT=os.environ.get('TEST_API_PORT','18111'), DEMO_MODE='true', LASTPAY_TEST_DATABASE='true')
    # Gradle Unix sockets need a short writable directory on Windows.
    temp = LOCAL/'t';temp.mkdir(exist_ok=True)
    env['JAVA_TOOL_OPTIONS'] = f'-Djava.io.tmpdir="{temp}" -Djdk.net.unixdomain.tmpdir="{temp}"'
    if os.name == 'nt' and not env.get('JAVA_HOME'):
        env['JAVA_HOME'] = r'C:\Program Files\Android\Android Studio\jbr'
    def run(command, log):
        with (LOCAL/log).open('w', encoding='utf-8') as output:
            completed = subprocess.run(list(map(str,command)),cwd=SERVER,env=env,stdout=output,stderr=subprocess.STDOUT)
        if completed.returncode:
            # Do not dump environment or credentials, even on failure.
            raise RuntimeError(f'{log} failed ({completed.returncode}); inspect the local log')
        print(log+' PASS', flush=True)
    gradle=[str(SERVER/('gradlew.bat' if os.name=='nt' else 'gradlew'))]
    if os.name!='nt':gradle=['bash',*gradle]
    run([*gradle,'installDist','--console=plain'],'prepare-build.log')
    java=Path(env['JAVA_HOME'])/'bin/java.exe' if os.name=='nt' else Path(shutil.which('java'))
    libs=next((SERVER/'build/install').glob('*/lib'))
    for command in ['migrate','provision-roles','seed']:
        run([java,'-cp',str(libs/'*'),'lastpay.MainKt',command],command+'.log')
    # Deterministic World Bank fixture in the isolated cache; no live upstream request.
    payload=[{'pages':1},[{'countryiso3code':'RUS','indicator':{'id':'FP.CPI.TOTL.ZG'},'date':str(y),'value':4.0} for y in (2020,2021,2022)]]
    request_hash=hashlib.sha256(b'WORLD_BANK:RUS:FP.CPI.TOTL.ZG:2020:2022').hexdigest()
    with psycopg.connect(host='127.0.0.1',port=port,dbname=database,user='postgres',password=pg_password) as c:
        c.execute("INSERT INTO provider_cache(id,provider,request_hash,payload,fetched_at,expires_at) VALUES(%s,'WORLD_BANK',%s,%s::jsonb,now(),now()+interval '1 day')",(uuid.uuid4(),request_hash,json.dumps(payload)))
    run([*gradle,'clean','test','build','installDist','--console=plain'],'gradle-test-build.log')
    base=f"http://127.0.0.1:{env['PORT']}/api/v1"
    env['TEST_BASE_URL']=base
    def phase(name, action):
        with (LOCAL/(name+'-server.log')).open('w',encoding='utf-8') as output:
            # Use java directly so termination also works on Windows without orphaning cmd children.
            java=Path(env['JAVA_HOME'])/'bin/java.exe' if os.name=='nt' else Path(shutil.which('java'))
            libs=next((SERVER/'build/install').glob('*/lib'))
            process=subprocess.Popen([str(java),'-cp',str(libs/'*'),'lastpay.MainKt'],cwd=SERVER,env=env,stdout=output,stderr=subprocess.STDOUT)
            try:
                for _ in range(100):
                    if process.poll() is not None:raise RuntimeError(name+' server failed')
                    try:
                        with urlopen(base+'/catalog',timeout=1) as r:
                            if r.status==200:break
                    except (URLError,TimeoutError):time.sleep(.2)
                else:raise RuntimeError('Server readiness timed out')
                action()
            finally:
                process.terminate()
                try:process.wait(timeout=15)
                except subprocess.TimeoutExpired:process.kill();process.wait()
    rows=json.loads((SERVER/'postman/negative-cases.json').read_text(encoding='utf-8'))
    specs=[yaml.safe_load(p.read_text(encoding='utf-8')) for p in [SERVER.parent/'openapi.yaml',SERVER/'openapi-integrations.yaml']]
    expected={m.upper()+' '+p for s in specs for p,item in s['paths'].items() for m in item if m in {'get','post','put','delete'}}
    assert {r['operation'] for r in rows}==expected and len(rows)==len(expected)==24
    evidence=[]
    def negatives():
        for row in rows:
            try:r=urlopen(Request(base+row['path'],method=row['method']),timeout=10)
            except HTTPError as error:r=error
            with r:
                assert r.status==row['status'],(row['id'],r.status)
                body=json.loads(r.read());error=body['error']
                assert set(error)=={'code','message','requestId','details'}
                assert error['requestId'] and isinstance(error['details'],list)
                assert r.headers.get_content_type()=='application/json'
                evidence.append(dict(row,actual=r.status,errorCode=error['code']))
        (LOCAL/'negative-results.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
    phase('negative',negatives)
    phase('contract',lambda:run([sys.executable,SERVER/'scripts/practice10-contract.py','--base-url',base],'contract.log'))
    pwsh=shutil.which('pwsh')
    if pwsh:
        phase('smoke',lambda:run([pwsh,'-NoProfile','-File',SERVER/'scripts/smoke.ps1','-BaseUrl',base],'smoke.log'))
        run([sys.executable,SERVER/'scripts/validate-http.py'],'validate-http.log')
    phase('newman',lambda:run([shutil.which('node'),SERVER/'scripts/run-newman.cjs'],'newman.log'))
    suites=[ET.parse(p).getroot() for p in (SERVER/'build/test-results/test').glob('TEST-*.xml')]
    junit={s.attrib['name']:{key:int(s.get(key,'0')) for key in ['tests','failures','errors','skipped']} for s in suites}
    assert sum(s['tests'] for n,s in junit.items() if n in {'lastpay.ValidationTest','lastpay.SecurityTest','lastpay.ProviderTest'}) >= 10
    assert all(not (s['failures'] or s['errors'] or s['skipped']) for s in junit.values()), junit
    assert junit.get('lastpay.DatabaseTest',{}).get('tests',0)>0
    newman=json.loads((LOCAL/'newman-summary.json').read_text(encoding='utf-8'))
    assert not newman['failures'] and newman['stats']['assertions']['failed']==0
    summary=dict(database=database,operations=len(expected),negativeScenarios=len(evidence),junit=junit,
                 gradle='clean test build installDist PASS',newman=newman,smoke='PASS' if pwsh else 'NOT RUN: pwsh unavailable',
                 worldBank='controlled fresh PostgreSQL cache fixture; no live provider',githubActions='not run on GitHub')
    (LOCAL/'practice11-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print(json.dumps(summary,indent=2))


if __name__=='__main__':main()
