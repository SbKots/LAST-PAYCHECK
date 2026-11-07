"""Docker-free regression: disposable localhost PostgreSQL + real PostgREST.

Reproduces the original invalid 204 framing, then models Envoy's rejection/retry
at that exact boundary. This is not an Envoy binary or a hosted HTTPS test.
Applies migration 004 locally and checks HTTP responses against committed SQL.
Never accepts a remote database URL. JWTs are local fixtures, memory only.
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid

import psycopg
from psycopg import sql

ROOT = Path(__file__).resolve().parents[2]
SB = ROOT / 'supabase'
LOCAL = SB / '.local'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disposable-postgres', action='store_true', required=True)
    parser.parse_args()
    db = 'lastpay_response_' + uuid.uuid4().hex[:10]
    pgport = int(os.environ.get('TEST_PG_PORT', '55432'))
    port = int(os.environ.get('TEST_RESPONSE_PORT', '18130'))
    binary = os.environ.get('POSTGREST_BINARY', str(LOCAL / 'postgrest/postgrest.exe'))
    def connect(database=db):
        return psycopg.connect(host='127.0.0.1', port=pgport, user='postgres',
            password=os.environ.get('TEST_PG_PASSWORD', ''), dbname=database,
            connect_timeout=5, options='-c statement_timeout=30000')
    with connect('postgres') as c:
        c.autocommit = True
        c.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(db)))
    ids = {name: str(uuid.uuid4()) for name in ('admin', 'player')}
    password, secret = secrets.token_urlsafe(32), secrets.token_urlsafe(48)
    with connect() as c:
        c.execute((SB / 'tests/bootstrap.sql').read_text(encoding='utf-8-sig'))
        c.execute(sql.SQL('ALTER ROLE lastpay_test_gateway PASSWORD {}').format(sql.Literal(password)))
        for f in sorted((SB / 'migrations').glob('*.sql')):
            if f.name < '202611060004':
                c.execute(f.read_text(encoding='utf-8-sig'))
        for name, uid in ids.items():
            c.execute('INSERT INTO auth.users(id,email) VALUES(%s,%s)', (uid, name + '@example.invalid'))
        c.execute('INSERT INTO lastpay_private.admin_membership(user_id) VALUES(%s)', (ids['admin'],))
    def token(who):
        def b64(value):
            return base64.urlsafe_b64encode(json.dumps(value).encode()).decode().rstrip('=')
        a = b64({'alg': 'HS256', 'typ': 'JWT'})
        b = b64({'role': 'authenticated', 'sub': ids[who], 'exp': int(time.time()) + 3600, 'is_anonymous': False})
        sig = base64.urlsafe_b64encode(hmac.new(secret.encode(), (a + '.' + b).encode(), hashlib.sha256).digest()).decode().rstrip('=')
        return a + '.' + b + '.' + sig
    env = dict(os.environ, PGRST_DB_URI=f'postgresql://lastpay_test_gateway:{password}@127.0.0.1:{pgport}/{db}',
        PGRST_DB_SCHEMAS='lastpay_api', PGRST_DB_ANON_ROLE='anon', PGRST_JWT_SECRET=secret,
        PGRST_SERVER_HOST='127.0.0.1', PGRST_SERVER_PORT=str(port))
    env['PATH'] = os.environ.get('TEST_PG_BIN', str(LOCAL / 'pg/bin')) + os.pathsep + env.get('PATH', '')
    results = []
    def check(label, ok):
        results.append(dict(test=label, passed=bool(ok)))
        if not ok:
            raise AssertionError(label)
    def state():
        # Includes every application row, not rate counters or test-only auth fixtures.
        with connect() as c:
            tables = c.execute("SELECT tablename FROM pg_tables WHERE schemaname='lastpay' ORDER BY tablename").fetchall()
            return [(name, c.execute(sql.SQL('SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),\'[]\') FROM lastpay.{} t').format(sql.Identifier(name))).fetchone()[0]) for (name,) in tables]
    def draft(version):
        with connect() as c:
            return c.execute('SELECT revision,status,(SELECT count(*) FROM lastpay.skill WHERE catalog_version=%s AND id=\'probe\') FROM lastpay.catalog_version WHERE version=%s', (version, version)).fetchone()
    def clear_rate():
        with connect() as c:
            c.execute('DELETE FROM lastpay_private.rate_window')
    def request(fn, args, who='admin', encoding='identity'):
        headers = {'Content-Type': 'application/json', 'Content-Profile': 'lastpay_api',
            'Accept-Profile': 'lastpay_api', 'Accept-Encoding': encoding}
        if who:
            headers['Authorization'] = 'Bearer ' + token(who)
        req = Request(f'http://127.0.0.1:{port}/rpc/{fn}', data=json.dumps({'args': args}).encode(), headers=headers)
        try:
            r = urlopen(req, timeout=15)
        except HTTPError as error:
            r = error
        with r:
            raw = r.read()
            return r.status, dict(r.headers), json.loads(raw) if raw else None, len(raw)
    def call(fn, args, expected=200, who='admin', encoding='identity'):
        before = state()
        r = request(fn, args, who, encoding)
        check(fn + ' HTTP ' + str(expected), r[0] == expected)
        check(fn + ' valid framing', not (r[0] == 204 and 'Content-Length' in r[1]) and
            (r[0] == 204 or int(r[1].get('Content-Length', r[3])) == r[3]))
        if expected >= 400:
            check(fn + ' error leaves all application data unchanged', state() == before)
            check(fn + ' safe error JSON', isinstance(r[2], dict) and ('error' in r[2] or 'code' in r[2]))
        return r
    body = dict(id='probe', code='probe', title='LOCAL REGRESSION TEST', description='', source='DEMO', externalId=None)
    log = (LOCAL / 'mutation-response-postgrest.log').open('w', encoding='utf-8')
    p = subprocess.Popen([binary], env=env, stdout=log, stderr=log)
    report = dict(database=db, cloudWrites=False, results=results)
    try:
        for _ in range(100):
            if p.poll() is not None:
                raise RuntimeError('Local PostgREST exited')
            try:
                urlopen(f'http://127.0.0.1:{port}/', timeout=1).close()
                break
            except (URLError, TimeoutError):
                time.sleep(.1)
        else:
            raise RuntimeError('Local PostgREST readiness timeout')
        call('create_draft', dict(version='before-fix', baseVersion='demo-1'), 201)
        call('create_skill', dict(version='before-fix', ifMatch='"rev-1"', body=body), 201)
        args = dict(version='before-fix', id='probe', ifMatch='"rev-2"')
        first = request('delete_skill', args)
        check('baseline invalid 204 Content-Length 4 reproduced', first[0] == 204 and first[1].get('Content-Length') == '4')
        check('baseline first attempt committed', draft('before-fix') == (3, 'DRAFT', 0))
        # Envoy HTTP/1 codec rejects nonzero Content-Length on a 204 response.
        # Model its observed second attempt; never repeat this against the cloud.
        second = request('delete_skill', args)
        check('baseline retry yields 412 after first attempt committed', second[0] == 412 and draft('before-fix') == (3, 'DRAFT', 0))
        with connect() as c:
            acl_query = "SELECT oid,proacl,proowner FROM pg_proc WHERE oid IN ('lastpay_api.delete_skill(jsonb)'::regprocedure,'lastpay_private.etag(bigint)'::regprocedure) ORDER BY oid"
            acl = c.execute(acl_query).fetchall()
            c.execute((SB / 'migrations/202611060004_delete_response.sql').read_text(encoding='utf-8'))
            check('migration preserves both function OIDs owners ACLs', acl == c.execute(acl_query).fetchall())
        clear_rate()
        call('create_draft', dict(version='after-fix', baseVersion='demo-1'), 201)
        check('draft created once', draft('after-fix') == (1, 'DRAFT', 0))
        call('create_draft', dict(version='after-fix', baseVersion='demo-1'), 409)
        args = dict(version='after-fix', ifMatch='"rev-1"', body=body)
        call('create_skill', args, 403, who='player')
        call('create_skill', args, 401, who=None)
        call('create_skill', args, 201)
        check('create committed exactly one revision', draft('after-fix') == (2, 'DRAFT', 1))
        args['ifMatch'] = '"rev-2"'
        call('create_skill', args, 409)
        update = dict(version='after-fix', id='probe', ifMatch='"rev-1"', body={k:v for k,v in body.items() if k!='id'})
        update['body']['title'] = 'LOCAL UPDATED'
        call('update_skill', update, 412)
        update['ifMatch'] = 'W/"rev-2"'
        call('update_skill', update, 400)
        update['ifMatch'] = '"rev-2"'
        call('update_skill', update)
        check('update committed exactly one revision', draft('after-fix') == (3, 'DRAFT', 1))
        with connect() as c:
            check('updated value committed', c.execute("SELECT title FROM lastpay.skill WHERE catalog_version='after-fix' AND id='probe'").fetchone() == ('LOCAL UPDATED',))
        for encoding in ('identity', 'gzip, br'):
            read = call('get_draft_skill', dict(catalogVersion='after-fix', id='probe'), encoding=encoding)
            check('strong revision ETag ' + encoding, read[1].get('ETag') == '"rev-3"')
            check('no-transform directive ' + encoding, 'no-transform' in read[1].get('Cache-Control', ''))
        deletion = dict(version='after-fix', id='probe', ifMatch='"rev-2"')
        call('delete_skill', deletion, 412)
        deletion['ifMatch'] = '"rev-3"'
        call('delete_skill', deletion, 403, who='player')
        deleted = call('delete_skill', deletion)
        check('fixed delete receipt and ETag', deleted[2] == dict(deleted=True, id='probe', catalogVersion='after-fix') and deleted[1].get('ETag') == '"rev-4"')
        check('fixed delete committed exactly one revision', draft('after-fix') == (4, 'DRAFT', 0))
        check('fixed success does not trigger framing retry', not (deleted[0] == 204 and int(deleted[1].get('Content-Length', '0')) > 0))
        call('get_draft_skill', dict(catalogVersion='after-fix', id='probe'), 404)
        call('delete_skill', deletion, 412)  # Separate stale request must not mutate.
        clear_rate()
        call('publish_catalog', dict(version='after-fix', ifMatch='"rev-3"'), 412)
        call('publish_catalog', dict(version='after-fix', ifMatch='"rev-4"'))
        check('publish committed exactly one revision', draft('after-fix') == (5, 'PUBLISHED', 0))
        call('publish_catalog', dict(version='after-fix', ifMatch='"rev-5"'), 409)
        saved = call('save_game', dict(snapshot={'test':'LOCAL ONLY'}, expectedRevision=0), who='player')
        check('save response revision and owner', saved[2]['revision'] == 1 and saved[2]['user_id'] == ids['player'])
        call('save_game', dict(snapshot={'test':'STALE'}, expectedRevision=0), 412, who='player')
        with connect() as c:
            check('save receipt agrees with committed row', c.execute('SELECT revision,snapshot FROM lastpay.user_save WHERE user_id=%s', (ids['player'],)).fetchone() == (1, {'test':'LOCAL ONLY'}))
        # Force an exception AFTER deletion/revision update inside the protected
        # subtransaction. The caught 500 must roll back both data and revision.
        call('create_draft', dict(version='rollback', baseVersion='demo-1'), 201)
        call('create_skill', dict(version='rollback', ifMatch='"rev-1"', body=body), 201)
        with connect() as c:
            original = c.execute("SELECT pg_get_functiondef('lastpay_private.mutate(text,jsonb)'::regprocedure)").fetchone()[0]
            needle = ' PERFORM lastpay_private.etag(v.revision);'
            assert needle in original
            c.execute(original.replace(needle, " RAISE EXCEPTION 'LOCAL fault after mutation';" + needle))
        try:
            call('delete_skill', dict(version='rollback', id='probe', ifMatch='"rev-2"'), 500)
            check('post-mutation exception rolled back skill and revision', draft('rollback') == (2, 'DRAFT', 1))
        finally:
            with connect() as c:
                c.execute(original)
        report['passed'] = True
    finally:
        p.terminate()
        p.wait(timeout=10)
        log.close()
        report['checks'] = len(results)
        report['failed'] = sum(not item['passed'] for item in results)
        (LOCAL / 'mutation-response-results.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k != 'results'}))


if __name__ == '__main__':
    main()
