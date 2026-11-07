"""Real PostgreSQL assertions; all writes are confined to the disposable test database."""
import json,uuid,time
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import psycopg
from psycopg import sql


def run_sql(connect, ids, record):
    def call(name,args=None,who='admin',role='authenticated'):
        claims={'role':role,'sub':ids.get(who),'is_anonymous':who=='anonymous','user_metadata':{'role':'admin'},'app_metadata':{'role':'admin'}}
        with connect() as c:
            c.execute(sql.SQL('SET LOCAL ROLE {}').format(sql.Identifier(role)))
            c.execute("SELECT set_config('request.jwt.claims',%s,true),set_config('response.status','200',true),set_config('response.headers','[]',true)",(json.dumps(claims),))
            value=c.execute(sql.SQL('SELECT lastpay_api.{}(%s::jsonb)').format(sql.Identifier(name)),(json.dumps(args or {}),)).fetchone()[0]
            status=int(c.execute("SELECT current_setting('response.status')").fetchone()[0])
            return status,value
    def check(label,condition):record(label,bool(condition))
    def denied(label,query,role='authenticated',who='userA'):
        try:
            with connect() as c:
                c.execute(sql.SQL('SET LOCAL ROLE {}').format(sql.Identifier(role)))
                c.execute("SELECT set_config('request.jwt.claims',%s,true)",(json.dumps({'role':role,'sub':ids[who]}),))
                c.execute(query)
        except psycopg.Error as e:check(label,e.sqlstate=='42501');return
        check(label,False)
    check('catalog manifest published',call('get_catalog',role='anon')[0]==200)
    for resource in ['occupations','skills','jobs','car-brands','car-models','tracks','race-configurations','inflation']:
        args=dict(resource=resource,catalogVersion='demo-1',page=1,pageSize=2)
        if resource=='inflation':args.update(country='RUS',yearFrom=2020,yearTo=2022)
        status,body=call('list_catalog',args,role='anon')
        check('published list '+resource,status==200 and 'data' in body and body['meta']['pageSize']==2)
    for resource,id in [('occupations','warehouse'),('car-models','city-hatch'),('tracks','night-delivery')]:
        status,body=call('get_catalog_item',dict(resource=resource,catalogVersion='demo-1',id=id),role='anon')
        check('published detail '+resource,status==200 and body['data']['id']==id)
    for args,expected in [({},400),({'resource':'skills','catalogVersion':'BAD'},400),({'resource':'skills','catalogVersion':'demo-1','pageSize':101},422),({'resource':'skills','catalogVersion':'demo-1','page':'1.0'},400),({'resource':'skills','catalogVersion':'missing'},404),({'resource':'inflation','catalogVersion':'demo-1','country':'WLD','yearFrom':2020,'yearTo':2022},422)]:
        check('list validation '+str(args),call('list_catalog',args,role='anon')[0]==expected)
    check('no admin from user or app metadata',call('create_draft',{'version':'forged','baseVersion':'demo-1'},who='userA')[0]==403)
    check('signed anonymous user cannot admin',call('create_draft',{'version':'forged','baseVersion':'demo-1'},who='anonymous')[0]==401)
    check('admin draft creation',call('create_draft',{'version':'sql-draft','baseVersion':'demo-1'})[0]==201)
    check('duplicate version conflict',call('create_draft',{'version':'sql-draft','baseVersion':'demo-1'})[0]==409)
    def rows(who,role,query):
        with connect() as c:
            c.execute(sql.SQL('SET LOCAL ROLE {}').format(sql.Identifier(role)))
            c.execute("SELECT set_config('request.jwt.claims',%s,true)",(json.dumps({'role':role,'sub':ids.get(who),'is_anonymous':who=='anonymous'}),))
            return c.execute(query).fetchall()
    check('RLS anon draft hidden',len(rows('userA','anon',"SELECT * FROM lastpay.skill WHERE catalog_version='sql-draft'"))==0)
    check('RLS userA draft hidden',len(rows('userA','authenticated',"SELECT * FROM lastpay.skill WHERE catalog_version='sql-draft'"))==0)
    check('RLS admin draft visible',len(rows('admin','authenticated',"SELECT * FROM lastpay.skill WHERE catalog_version='sql-draft'"))==1)
    denied('direct skill write denied',"UPDATE lastpay.skill SET title='hacked'")
    denied('admin registry inaccessible',"INSERT INTO lastpay_private.admin_membership VALUES(auth.uid(),now())")
    denied('provider RPC inaccessible to user',"SELECT lastpay_api.provider_cache('{}')")
    denied('private publisher inaccessible',"SELECT lastpay_private.publish('sql-draft')")
    body=dict(id='sql-skill',code='sql-skill',title='SQL test',description='',source='DEMO',externalId=None)
    check('missing ifMatch',call('create_skill',dict(version='sql-draft',body=body))[0]==400)
    check('invalid skill whitespace',call('create_skill',dict(version='sql-draft',ifMatch='"rev-1"',body=dict(body,title='  ')))[0]==422)
    for invalid in [dict(body,title='\u2003'),dict(body,id='BAD'),dict(body,extra=True),{k:v for k,v in body.items() if k!='id'}]:
        check('skill input parity '+str(invalid),call('create_skill',dict(version='sql-draft',ifMatch='"rev-1"',body=invalid))[0]==422)
    check('create skill',call('create_skill',dict(version='sql-draft',ifMatch='"rev-1"',body=body))[0]==201)
    check('duplicate skill SQL constraint',call('create_skill',dict(version='sql-draft',ifMatch='"rev-2"',body=body))[0]==409)
    check('FK referenced skill',call('delete_skill',dict(version='sql-draft',id='logistics',ifMatch='"rev-2"'))[0]==409)
    status,listed=call('list_draft_skills',dict(catalogVersion='sql-draft'))
    check('failed writes rollback revision',status==200 and listed['meta']['total']==2)
    update={k:v for k,v in body.items() if k!='id'}
    args=dict(version='sql-draft',id='sql-skill',ifMatch='"rev-2"',body=update)
    with ThreadPoolExecutor(2) as pool:results=list(pool.map(lambda _:call('update_skill',args)[0],range(2)))
    check('concurrent revision update exactly one wins',sorted(results)==[200,412])
    status,receipt=call('delete_skill',dict(version='sql-draft',id='sql-skill',ifMatch='"rev-3"'))
    check('delete skill receipt',status==200 and receipt==dict(deleted=True,id='sql-skill',catalogVersion='sql-draft'))
    check('publish',call('publish_catalog',dict(version='sql-draft',ifMatch='"rev-4"'))[0]==200)
    check('published RPC immutable',call('delete_skill',dict(version='sql-draft',id='logistics',ifMatch='"rev-5"'))[0]==409)
    try:
        with connect() as c:c.execute("UPDATE lastpay.skill SET title='hacked' WHERE catalog_version='demo-1'")
    except psycopg.Error as e:check('trigger protects even migration owner',e.sqlstate=='23503')
    else:check('trigger protects even migration owner',False)
    check('save userA',call('save_game',{'snapshot':{'day':1},'expectedRevision':0},who='userA')[0]==200)
    check('save stale revision',call('save_game',{'snapshot':{},'expectedRevision':0},who='userA')[0]==412)
    check('save userB',call('save_game',{'snapshot':{'day':2},'expectedRevision':0},who='userB')[0]==200)
    check('cannot choose save owner',call('save_game',{'user_id':ids['userA'],'snapshot':{},'expectedRevision':1},who='userB')[0]==400)
    for who in ['userA','userB','admin','anonymous']:
        values=rows(who,'authenticated','SELECT user_id FROM lastpay_api.user_save')
        check('save RLS '+who,values==[(uuid.UUID(ids[who]),)] if who in ['userA','userB'] else len(values)==0)
    denied('anon cannot read private saves','SELECT * FROM lastpay_api.user_save',role='anon')
    check('anonymous cannot save',call('save_game',{'snapshot':{},'expectedRevision':0},who='anonymous')[0]==401)
    check('publication validation draft',call('create_draft',{'version':'invalid-publish','baseVersion':'demo-1'})[0]==201)
    with connect() as c:c.execute("DELETE FROM lastpay.car_balance WHERE catalog_version='invalid-publish'")
    check('publish rejects missing balance with 422',call('publish_catalog',{'version':'invalid-publish','ifMatch':'"rev-1"'})[0]==422)
    owner=str(uuid.uuid4()); other=str(uuid.uuid4()); key=dict(provider='ONET',hash='a'*64)
    check('cache lease acquired',call('provider_cache',dict(op='acquire',owner=owner,**key),role='service_role')[1]['acquired'])
    check('cache lease rejects concurrent owner',not call('provider_cache',dict(op='acquire',owner=other,**key),role='service_role')[1]['acquired'])
    check('cache wrong owner cannot store',not call('provider_cache',dict(op='store',owner=other,payload={},ttl=7,**key),role='service_role')[1]['stored'])
    check('cache holder stores',call('provider_cache',dict(op='store',owner=owner,payload={'career':[{'code':'x','title':'Test'}]},ttl=7,**key),role='service_role')[1]['stored'])
    check('cache lookup is fresh',call('provider_cache',dict(op='lookup',**key),role='service_role')[1]['fresh'])
    with connect() as c:
        check('canonical JSON exact decimal',c.execute("SELECT lastpay_private.canonical('{\"b\":[true,null,0.010],\"a\":1.00}')").fetchone()[0]=='{\"a\":1,\"b\":[true,null,0.01]}')
        check('fixture Cyrillic preserved',c.execute("SELECT title FROM lastpay.skill WHERE catalog_version='demo-1' AND id='logistics'").fetchone()[0]=='Логистика')
    with connect() as c:
        bad=c.execute("""SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
          WHERE n.nspname IN ('lastpay_api','lastpay_private') AND (p.prosecdef AND NOT coalesce(p.proconfig,'{}') @> ARRAY['search_path=""']
          OR EXISTS(SELECT FROM aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a WHERE a.grantee=0 AND a.privilege_type='EXECUTE'))""").fetchone()[0]
        check('definer search_path and no PUBLIC EXECUTE',bad==0)
        check('all 14 application tables have RLS',c.execute("SELECT count(*) FROM pg_class t JOIN pg_namespace n ON t.relnamespace=n.oid WHERE n.nspname='lastpay' AND t.relkind='r' AND t.relrowsecurity AND t.relforcerowsecurity").fetchone()[0]==14)
        check('no legacy roles in migrations',not any('lastpay_reader' in p.read_text(encoding='utf-8-sig') or 'lastpay_writer' in p.read_text(encoding='utf-8-sig') for p in (Path(__file__).resolve().parents[1]/'migrations').glob('*.sql')))
        c.execute('DELETE FROM lastpay_private.rate_window')
    # Keep this boundary assertion away from a minute rollover and use one connection.
    # The HTTP suite separately proves that counters persist across requests/transactions.
    if time.time()%60>55:time.sleep(60-time.time()%60)
    with connect() as c:
        c.execute('SET LOCAL ROLE anon')
        c.execute("SELECT set_config('request.jwt.claims','{\"role\":\"anon\"}',true)")
        for _ in range(120):
            c.execute("SELECT lastpay_api.list_catalog('{}')")
            assert c.execute("SELECT current_setting('response.status')").fetchone()[0]=='400'
        c.execute("SELECT lastpay_api.list_catalog('{}')")
        limited=c.execute("SELECT current_setting('response.status')").fetchone()[0]=='429'
    check('invalid requests count toward persistent limit',limited)
    with connect() as c:c.execute('DELETE FROM lastpay_private.rate_window')
