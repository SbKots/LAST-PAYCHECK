"""Build the current Supabase HTTPS contract from checked-in domain schemas."""
import copy
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[2]
models = yaml.safe_load((ROOT/'supabase/contracts/catalog-models.yaml').read_text(encoding='utf-8'))['schemas']
schemas = copy.deepcopy(models)
ref = lambda name: {'$ref': '#/components/schemas/'+name}
def obj(properties, required=()):
    value=dict(type='object', properties=properties, additionalProperties=False)
    if required:value['required']=list(required)
    return value
version = ref('CatalogVersion')
identifier = ref('Identifier')
match = dict(type='string', pattern='^"rev-[1-9][0-9]*"$', example='"rev-2"')
resources = ['occupations','skills','jobs','inflation','car-brands','car-models','tracks','race-configurations']
pages = ['OccupationPage','SkillPage','JobPage','InflationObservationPage','CarBrandPage','CarModelPage','TrackPage','RaceConfigurationPage']
schemas.update({
 'ListArgs': obj(dict(resource=dict(type='string',enum=resources),catalogVersion=version,
    page=dict(type='integer',minimum=1,maximum=1000000,default=1),pageSize=dict(type='integer',minimum=1,maximum=100,default=20),
    country=dict(type='string',pattern='^[A-Z]{3}$'),yearFrom=dict(type='integer',minimum=1900,maximum=2100),
    yearTo=dict(type='integer',minimum=1900,maximum=2100),brandId=identifier,trackId=identifier), ['resource','catalogVersion']),
 'ItemArgs': obj(dict(resource=dict(type='string',enum=['occupations','car-models','tracks']),catalogVersion=version,id=identifier),['resource','catalogVersion','id']),
 'DraftListArgs': obj(dict(catalogVersion=version,page=dict(type='integer',minimum=1,maximum=1000000),pageSize=dict(type='integer',minimum=1,maximum=100)),['catalogVersion']),
 'DraftItemArgs': obj(dict(catalogVersion=version,id=identifier),['catalogVersion','id']),
 'CreateDraftArgs': obj(dict(version=version,baseVersion=version),['version','baseVersion']),
 'CreateSkillArgs': obj(dict(version=version,ifMatch=match,body=ref('SkillCreate')),['version','ifMatch','body']),
 'UpdateSkillArgs': obj(dict(version=version,id=identifier,ifMatch=match,body=ref('SkillInput')),['version','id','ifMatch','body']),
 'DeleteSkillArgs': obj(dict(version=version,id=identifier,ifMatch=match),['version','id','ifMatch']),
 'PublishArgs': obj(dict(version=version,ifMatch=match),['version','ifMatch']),
 'DeleteReceipt': obj(dict(deleted=dict(type='boolean',enum=[True]),id=identifier,catalogVersion=version),['deleted','id','catalogVersion']),
 'SaveArgs': obj(dict(snapshot=dict(type='object',additionalProperties=True,description='JSON snapshot, at most 256 KiB serialized'),expectedRevision=dict(type='integer',minimum=0,maximum=2147483647)),['snapshot','expectedRevision']),
 'UserSave': obj(dict(user_id=dict(type='string',format='uuid'),snapshot=dict(type='object',additionalProperties=True),revision=dict(type='integer',minimum=1),updated_at=dict(type='string',format='date-time')),['user_id','snapshot','revision','updated_at']),
 'PostgrestError': dict(type='object',properties=dict(code=dict(type='string'),message=dict(type='string'),details=dict(nullable=True),hint=dict(nullable=True)),required=['code','message']),
 'ProviderEnvelope': dict(type='object',required=['provider','fromCache','stale','data'],properties=dict(provider=dict(type='string',enum=['WORLD_BANK','ONET','CARS_BASE']),fromCache=dict(type='boolean'),stale=dict(type='boolean'),data=dict(oneOf=[dict(type='object',additionalProperties=True),dict(type='array',items={})]))),
})
public = [{'PublicKey': []}]
session = [{'PublicKey': [], 'UserJWT': []}]
def response(schema, description='Успешный JSON ответ'):
    return dict(description=description,content={'application/json': {'schema':schema}})
error = dict(oneOf=[ref('Error'),ref('PostgrestError')])
errors = {str(code):response(error, label) for code,label in [(400,'Неверный формат'),(401,'Нет действительной сессии'),(403,'Нет доступа'),(404,'Не найдено'),(405,'Метод не поддерживается'),(409,'Конфликт данных или опубликованная версия'),(412,'Устаревшая ревизия'),(422,'Ошибка валидации'),(429,'Лимит запросов'),(500,'Внутренняя ошибка'),(503,'Источник недоступен')]}
profiles = [dict(name=name,**{'in':'header'},required=True,schema=dict(type='string',enum=['lastpay_api'])) for name in ['Content-Profile','Accept-Profile']]
paths = {}
def rpc(name,args,result,status=200,admin=False,description=''):
    headers = dict(ETag=dict(description='Strong revision validator including quotes. Pass through args.ifMatch; weak W/ is invalid.',schema=dict(type='string')),**{'Cache-Control':dict(schema=dict(type='string'),description='Revision responses: private, no-store, no-transform')})
    ok=response(result);ok['headers']=headers
    paths['/rest/v1/rpc/'+name]={'post':dict(operationId=name,summary=name,description=description,
        tags=['Admin' if admin else 'Catalog'],security=session if admin else public,parameters=profiles,
        requestBody=dict(required=True,content={'application/json':dict(schema=obj(dict(args=args),['args']))}),
        responses={str(status):ok,**copy.deepcopy(errors)})}
rpc('get_catalog',obj({}),ref('Manifest'),description='Последняя опубликованная версия; запрос также учитывается в лимите чтений.')
paths['/functions/v1/catalog'] = {
    'get': dict(
        operationId='get_browser_catalog',
        summary='Манифест опубликованного каталога для браузера',
        description='Не принимает параметры запроса. Возвращает тот же манифест, что и get_catalog. Заголовки apikey и Authorization не требуются.',
        tags=['Catalog'],
        security=[],
        responses={
            '200': response(ref('Manifest')),
            '400': response(ref('Error'), 'Переданы параметры запроса'),
            '429': response(ref('Error'), 'Лимит запросов'),
            '503': response(ref('Error'), 'Каталог временно недоступен'),
        },
    ),
    'options': dict(
        operationId='browser_catalog_preflight',
        summary='Предварительный запрос CORS для браузерного каталога',
        tags=['Catalog'],
        security=[],
        responses={'204': dict(description='Предварительный запрос принят')},
    ),
}
rpc('list_catalog',ref('ListArgs'),dict(anyOf=[ref(p) for p in pages]),description='Восемь категорий. Для inflation обязательны country/yearFrom/yearTo. brandId применим только к car-models, trackId к race-configurations. Фильтры других ресурсов отклоняются.')
rpc('get_catalog_item',ref('ItemArgs'),dict(oneOf=[ref(p) for p in ['OccupationEnvelope','CarModelEnvelope','TrackEnvelope']]))
rpc('list_draft_skills',ref('DraftListArgs'),ref('SkillPage'),admin=True)
rpc('get_draft_skill',ref('DraftItemArgs'),ref('SkillEnvelope'),admin=True)
rpc('create_draft',ref('CreateDraftArgs'),ref('Draft'),201,True,'Только реестр admin_membership; копия опубликованной baseVersion.')
rpc('create_skill',ref('CreateSkillArgs'),ref('SkillEnvelope'),201,True)
rpc('update_skill',ref('UpdateSkillArgs'),ref('SkillEnvelope'),admin=True)
rpc('delete_skill',ref('DeleteSkillArgs'),ref('DeleteReceipt'),admin=True,description='После миграции 004 успешное удаление возвращает HTTP 200, JSON receipt и новый сильный ETag. Не 204. Запрос со старой ревизией возвращает 412 без изменения данных.')
rpc('publish_catalog',ref('PublishArgs'),ref('Manifest'),admin=True,description='Публикация меняет активную версию. Облачные тесты публикации требуют отдельного разрешения.')
rpc('save_game',ref('SaveArgs'),ref('UserSave'),admin=True,description='Постоянный пользователь, только auth.uid(); назначать администратора не требуется. Начальная expectedRevision=0, конфликт=412.')
paths['/rest/v1/rpc/save_game']['post']['tags']=['Saves']
paths['/rest/v1/user_save']={'get':dict(operationId='read_own_save',summary='Прочитать только своё сохранение',tags=['Saves'],security=session,
    parameters=[profiles[1]],responses={'200':response(dict(type='array',items=ref('UserSave'))),**copy.deepcopy(errors)})}
def query(name,typ='string',required=True,**extra):
    return dict(name=name,**{'in':'query'},required=required,schema=dict(type=typ,**extra))
providers = {
 '/worldbank/inflation':[query('country',pattern='^[A-Z]{3}$'),query('yearFrom','integer',minimum=1900,maximum=2100),query('yearTo','integer',minimum=1900,maximum=2100)],
 '/onet/occupations':[],
 '/onet/skills/{occupationCode}':[dict(name='occupationCode',**{'in':'path'},required=True,schema=dict(type='string',pattern=r'^[0-9]{2}-[0-9]{4}\.[0-9]{2}$'))],
 '/cars/brands':[query('page','integer',False,minimum=1,maximum=1000000,default=1),query('pageSize','integer',False,minimum=1,maximum=1000,default=1000)],
 '/cars/models':[query('brandId',required=False,pattern='^[A-Za-z0-9_-]{1,64}$'),query('page','integer',False,minimum=1,maximum=1000000,default=1),query('pageSize','integer',False,minimum=1,maximum=1000,default=1000)]
}
for path,parameters in providers.items():
    paths['/functions/v1/providers'+path]={'get':dict(operationId='providers_'+path.split('/')[1]+'_'+path.split('/')[2].replace('{occupationCode}','skills'),summary='Provider '+path,
        tags=['Providers'],description='Permanent Supabase Auth JWT is verified by Auth /user in the handler. Missing O*NET/CarsBase keys yield safe 503. World Bank requires no provider key.',security=session,parameters=parameters,
        responses={'200':response(ref('ProviderEnvelope')),**copy.deepcopy(errors)})}
spec=dict(openapi='3.0.3',info=dict(title='LAST PAYCHECK Supabase API',version='2.0.1',description='Текущий HTTPS-контракт Supabase. Все RPC используют JSON args и профиль lastpay_api. Закрытые lastpay/lastpay_private не экспонируются. provider_cache намеренно не включён в клиентский контракт. 24 исходные операции объединены в RPC и пять маршрутов providers; save_game/user_save добавляют приватную синхронизацию. Доменные и инфраструктурные ошибки имеют разные JSON-форматы.'),
    servers=[dict(url='https://mmuytwyhdwttzxuredfm.supabase.co',description='Существующий проект last-paycheck Frankfurt Free')],security=public,paths=paths,
    components=dict(securitySchemes=dict(PublicKey=dict(type='apiKey',**{'in':'header'},name='apikey',description='Only publishable/anon key; never service_role/secret'),UserJWT=dict(type='http',scheme='bearer',bearerFormat='JWT')),schemas=schemas))
(ROOT/'openapi.yaml').write_text(yaml.safe_dump(spec,allow_unicode=True,sort_keys=False),encoding='utf-8')
print('Generated current Supabase contract:',len(paths),'paths')
