"""Regenerate the Supabase collection from the reviewed operation map; no credentials."""
from pathlib import Path
import json
SB=Path(__file__).resolve().parents[1]
ROOT=SB.parents[1]
mapping=json.loads((ROOT/'docs/supabase/operation-map.json').read_text(encoding='utf-8'))
items=[]
def add(name,path,body,status,auth=None,extra=None):
 headers=[dict(key='apikey',value='{{publicKey}}'),dict(key='Content-Profile',value='lastpay_api'),dict(key='Accept-Profile',value='lastpay_api')]
 if auth:headers.append(dict(key='Authorization',value='Bearer {{'+auth+'}}'))
 request=dict(method='GET' if body is None else 'POST',url='{{baseUrl}}'+path,header=headers,auth={'type':'noauth'})
 if body is not None:headers.append(dict(key='Content-Type',value='application/json'));request['body']=dict(mode='raw',raw=json.dumps({'args':body},ensure_ascii=False))
 script=[f"pm.test('HTTP {status}', () => pm.response.to.have.status({status}));"]
 if status!=204:
  script += ["const body = pm.response.json();", "pm.test('JSON Content-Type', () => pm.expect(pm.response.headers.get('Content-Type')).to.match(/^application\\/json/i));"]
  if status>=400:script.append("pm.test('Error code present', () => pm.expect(body.error?.code || body.code).to.be.a('string').and.not.empty);")
  else:script.append("pm.test('Response body', () => pm.expect(body).to.be.an('object'));")
 script += ["const etag=pm.response.headers.get('ETag'); if(etag) pm.collectionVariables.set('etag',JSON.stringify(etag).slice(1,-1));"]
 script += extra or []
 items.append(dict(name=name,request=request,event=[dict(listen='test',script=dict(type='text/javascript',exec=script))]))
for r in mapping:
 fn=r['rpc'];path=r['oldPath'];auth=None;args={};status=200
 if fn:
  if r['resource']:
   args=dict(resource=r['resource'],catalogVersion='demo-1')
   if fn=='get_catalog_item':args['id']={'occupations':'warehouse','car-models':'city-hatch','tracks':'night-delivery'}[r['resource']]
   elif r['resource']=='inflation':args.update(country='RUS',yearFrom=2020,yearTo=2020)
  if '/admin/' in path:
   auth='adminJwt'
   if fn=='create_draft':args=dict(version='{{draftVersion}}',baseVersion='demo-1');status=201
   elif fn in ['list_draft_skills','get_draft_skill']:
    args=dict(catalogVersion='{{draftVersion}}')
    if fn=='get_draft_skill':args['id']='newman-skill'
   else:
    args=dict(version='{{draftVersion}}',ifMatch='{{etag}}')
    if fn in ['update_skill','delete_skill']:args['id']='newman-skill'
    if fn in ['create_skill','update_skill']:
     args['body']=dict(code='newman-skill',title='Newman',description='',source='DEMO',externalId=None)
     if fn=='create_skill':args['body']['id']='newman-skill';status=201
  add(r['id']+' success '+fn,r['newPath'],args,status,auth)
 else:
  p=r['newPath'].replace('{occupationCode}','53-7062.00')
  if 'worldbank' in p:p+='?country=RUS&yearFrom=2020&yearTo=2022'
  add(r['id']+' success provider',p,None,200,'userJwt')
for r in mapping:
 if r['rpc']:
  args={'unexpected':True} if r['rpc']=='get_catalog' else {'resource':r['resource']} if r['resource'] else {}
  add(r['id']+' negative '+r['rpc'],r['newPath'],args,401 if '/admin/' in r['oldPath'] else 400)
 else:
  p=r['newPath'].replace('{occupationCode}','bad')
  if 'worldbank' in p:status=400
  elif p.endswith('onet/occupations'):p+='?unexpected=1';status=400
  elif '/onet/skills/' in p:status=422
  elif p.endswith('/brands'):p+='?page=0';status=422
  else:p+='?brandId=!';status=422
  add(r['id']+' negative provider',p,None,status,'userJwt')
collection=dict(info=dict(name='LAST PAYCHECK Supabase backend',schema='https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description='24 migrated operations, positive and negative. Run only on an isolated test project. Provider successes require controlled fixtures or configured live providers.'),item=items,variable=[dict(key='draftVersion',value=''),dict(key='etag',value='')],event=[dict(listen='prerequest',script=dict(type='text/javascript',exec=["if(!pm.collectionVariables.get('draftVersion')) pm.collectionVariables.set('draftVersion','newman-'+Date.now());"]))])
(SB/'postman/LAST-PAYCHECK-Supabase.postman_collection.json').write_text(json.dumps(collection,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(SB/'postman/local.postman_environment.json').write_text(json.dumps(dict(name='Supabase isolated test only',values=[dict(key=k,value=v,enabled=True,type='secret' if 'Jwt' in k else 'default') for k,v in dict(baseUrl='http://127.0.0.1:18121',publicKey='',adminJwt='',userJwt='').items()],_postman_variable_scope='environment'),indent=2)+'\n',encoding='utf-8')
print('Generated',len(items),'requests')
