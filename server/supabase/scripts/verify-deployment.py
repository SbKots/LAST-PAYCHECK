"""Post-deployment smoke. Do not run before cloud deployment is separately authorized.
Catalog RPC reads increment rate counters. No catalog/user data mutations, no signup.
"""
import argparse,base64,json,os
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request,urlopen
from urllib.error import HTTPError

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allow-api-requests',action='store_true',required=True)
    parser.parse_args()
    base=os.environ['SUPABASE_URL'].rstrip('/');public=os.environ['SUPABASE_PUBLIC_KEY']
    u=urlsplit(base)
    if u.scheme!='https' or not u.hostname or not u.hostname.endswith('.supabase.co') or u.username or u.path:raise ValueError('Expected project HTTPS URL')
    if not public.startswith('sb_publishable_'):
        try:
            payload=public.split('.')[1]
            role=json.loads(base64.urlsafe_b64decode(payload+'='*(-len(payload)%4)))['role']
        except (ValueError,KeyError,IndexError):raise ValueError('Use publishable or anon key only') from None
        if role!='anon':raise ValueError('Use publishable or anon key only')
    token=os.environ.get('SUPABASE_USER_JWT','')
    results=[]
    def rpc(name,args):
        headers={'apikey':public,'Content-Profile':'lastpay_api','Accept-Profile':'lastpay_api','Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        req=Request(base+'/rest/v1/rpc/'+name,data=json.dumps({'args':args}).encode(),headers=headers,method='POST')
        try:r=urlopen(req,timeout=20)
        except HTTPError as error:r=error
        with r:
            results.append({'rpc':name,'status':r.status})
            if r.status!=200:raise RuntimeError(name+' returned '+str(r.status)+'; no response body recorded')
            return json.loads(r.read())
    manifest=rpc('get_catalog',{});version=manifest['catalogVersion']
    for resource in ['occupations','skills','jobs','car-brands','car-models','tracks','race-configurations','inflation']:
        args={'resource':resource,'catalogVersion':version,'page':1,'pageSize':2}
        if resource=='inflation':args.update(country='RUS',yearFrom=2020,yearTo=2022)
        page=rpc('list_catalog',args)
        if not isinstance(page['data'],list):raise RuntimeError('Invalid page')
    print(json.dumps({'passed':True,'checks':results,'scope':'catalog reads only; rate counters incremented'},indent=2))
if __name__=='__main__':main()
