BEGIN;
-- Domain exceptions remain internal. Entry points return the old JSON envelope and HTTP status.
CREATE FUNCTION lastpay_private.reject(status integer, message text, field text DEFAULT NULL) RETURNS void
LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
 RAISE EXCEPTION USING ERRCODE=('LP'||status::text), MESSAGE=message, DETAIL=coalesce(field,'');
END $$;
CREATE FUNCTION lastpay_private.problem(status integer,message text,field text DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE code text; headers jsonb:='[]';
BEGIN
 code:=CASE status WHEN 400 THEN 'BADREQUEST' WHEN 401 THEN 'UNAUTHORIZED' WHEN 403 THEN 'FORBIDDEN'
 WHEN 404 THEN 'NOTFOUND' WHEN 405 THEN 'METHODNOTALLOWED' WHEN 409 THEN 'CONFLICT' WHEN 412 THEN 'PRECONDITIONFAILED'
 WHEN 422 THEN 'VALIDATIONERROR' WHEN 429 THEN 'RATELIMITED' WHEN 503 THEN 'UNAVAILABLE' ELSE 'INTERNALERROR' END;
 IF status=401 THEN headers:='[{"WWW-Authenticate":"Bearer"}]'; END IF;
 IF status IN (429,503) THEN headers:='[{"Retry-After":"60"}]'; END IF;
 PERFORM set_config('response.status',status::text,true);
 PERFORM set_config('response.headers',headers::text,true);
 RETURN jsonb_build_object('error',jsonb_build_object('code',code,'message',left(message,300),'requestId',gen_random_uuid()::text,
 'details',CASE WHEN nullif(field,'') IS NULL THEN '[]'::jsonb ELSE jsonb_build_array(jsonb_build_object('field',field,'message',message)) END));
END $$;
CREATE FUNCTION lastpay_private.shape(data jsonb,allowed text[],required text[] DEFAULT '{}',status integer DEFAULT 400) RETURNS void
LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
 IF data IS NULL OR jsonb_typeof(data)<>'object' OR EXISTS(SELECT FROM jsonb_object_keys(data) k WHERE NOT k=ANY(allowed))
 OR EXISTS(SELECT FROM unnest(required) k WHERE NOT data ? k) THEN
 PERFORM lastpay_private.reject(status,'Unknown or missing parameters'); END IF;
END $$;
CREATE FUNCTION lastpay_private.str(data jsonb,key text,max_len integer,nullable boolean DEFAULT false,allow_empty boolean DEFAULT false) RETURNS text
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE value text;
BEGIN
 IF NOT data ? key THEN PERFORM lastpay_private.reject(422,'Missing field',key); END IF;
 IF data->key='null'::jsonb AND nullable THEN RETURN NULL; END IF;
 IF jsonb_typeof(data->key)<>'string' THEN PERFORM lastpay_private.reject(400,'Expected a string',key); END IF;
 value:=data->>key;
 IF char_length(value)>max_len OR (NOT allow_empty AND value !~ '[^[:space:]   -     　]') THEN
 PERFORM lastpay_private.reject(422,'Invalid string length',key); END IF;
 RETURN value;
END $$;
CREATE FUNCTION lastpay_private.id(value text,is_version boolean DEFAULT false) RETURNS text
LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
 IF value IS NULL OR value !~ '^[a-z0-9][a-z0-9._-]*$' OR char_length(value)>(CASE WHEN is_version THEN 32 ELSE 64 END) THEN
 PERFORM lastpay_private.reject(400,'Invalid identifier'); END IF;
 RETURN value;
END $$;
CREATE FUNCTION lastpay_private.integer_arg(data jsonb,key text,fallback integer,lo integer,hi integer) RETURNS integer
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE v numeric; raw text;
BEGIN
 IF NOT data ? key THEN
  IF fallback IS NULL THEN PERFORM lastpay_private.reject(400,'Missing integer',key); END IF;
  RETURN fallback;
 END IF;
 raw:=data->>key;
 IF raw IS NULL OR raw !~ '^-?[0-9]+$' OR jsonb_typeof(data->key) NOT IN ('string','number') THEN PERFORM lastpay_private.reject(400,'Expected integer',key); END IF;
 IF length(raw)>20 THEN PERFORM lastpay_private.reject(422,'Integer out of range',key); END IF;
 v:=raw::numeric;
 IF v<lo OR v>hi THEN PERFORM lastpay_private.reject(422,'Integer out of range',key); END IF;
 RETURN v::integer;
END $$;
CREATE FUNCTION lastpay_private.table_for(resource text) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT CASE resource WHEN 'occupations' THEN 'occupation' WHEN 'skills' THEN 'skill' WHEN 'jobs' THEN 'job_definition'
 WHEN 'inflation' THEN 'inflation_observation' WHEN 'car-brands' THEN 'car_brand' WHEN 'car-models' THEN 'car_model'
 WHEN 'tracks' THEN 'race_track' WHEN 'race-configurations' THEN 'race_configuration' END
$$;
CREATE FUNCTION lastpay_private.camel(data jsonb) RETURNS jsonb
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE k text;v jsonb; parts text[]; camel text; i integer; result jsonb:='{}';
BEGIN
 FOR k,v IN SELECT * FROM jsonb_each(data-'catalog_version') LOOP
  parts:=string_to_array(k,'_');camel:=parts[1];
  IF cardinality(parts)>1 THEN FOR i IN 2..cardinality(parts) LOOP camel:=camel||upper(left(parts[i],1))||substring(parts[i],2); END LOOP; END IF;
  result:=result||jsonb_build_object(camel,v);
 END LOOP;
 RETURN result;
END $$;
CREATE FUNCTION lastpay_private.resources() RETURNS text[] LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT ARRAY['occupations','skills','jobs','inflation','car-brands','car-models','tracks','race-configurations']::text[]
$$;
CREATE FUNCTION lastpay_private.resource(resource text,version text,item_id text DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE tbl text:=lastpay_private.table_for(resource); r jsonb; result jsonb:='[]'; item jsonb; nested jsonb;
BEGIN
 IF tbl IS NULL THEN PERFORM lastpay_private.reject(400,'Unknown catalog resource'); END IF;
 FOR r IN EXECUTE format('SELECT to_jsonb(t) FROM lastpay.%I t WHERE catalog_version=$1 AND ($2 IS NULL OR id=$2) ORDER BY %s',tbl,
 CASE WHEN resource='inflation' THEN 'year,id' ELSE 'id' END) USING version,item_id LOOP
  item:=lastpay_private.camel(r);
  IF resource='inflation' THEN item:=jsonb_set(item,'{fetchedAt}',to_jsonb(to_char((r->>'fetched_at')::timestamptz AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))); END IF;
  IF resource='occupations' THEN
   SELECT coalesce(jsonb_agg(lastpay_private.camel(to_jsonb(s)) ORDER BY skill_id),'[]') INTO nested
   FROM (SELECT skill_id,importance FROM lastpay.occupation_skill WHERE catalog_version=version AND occupation_id=r->>'id') s;
   item:=item||jsonb_build_object('skills',nested);
  ELSIF resource='car-models' THEN
   SELECT lastpay_private.camel(to_jsonb(s)-'model_id') INTO nested FROM lastpay.car_specification s WHERE catalog_version=version AND model_id=r->>'id';
   item:=item||jsonb_build_object('specification',nested);
   SELECT lastpay_private.camel(to_jsonb(s)-'model_id') INTO nested FROM lastpay.car_balance s WHERE catalog_version=version AND model_id=r->>'id';
   IF nested IS NULL THEN PERFORM lastpay_private.reject(503,'Missing car balance'); END IF;
   item:=item||jsonb_build_object('balance',nested);
  END IF;
  result:=result||jsonb_build_array(item);
 END LOOP;
 RETURN result;
END $$;
CREATE FUNCTION lastpay_private.manifest(v lastpay.catalog_version) RETURNS jsonb LANGUAGE sql SET search_path='' AS $$
 SELECT jsonb_build_object('catalogVersion',v.version,'schemaVersion','1','publishedAt',to_char(v.published_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
 'contentSha256',v.content_sha256,'resources',to_jsonb(lastpay_private.resources()),'sources',
 (SELECT jsonb_agg(value||jsonb_build_object('resource',key) ORDER BY key) FROM jsonb_each(v.source_metadata)))
$$;
CREATE FUNCTION lastpay_private.canonical(data jsonb) RETURNS text
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE result text;
BEGIN
 CASE jsonb_typeof(data)
 WHEN 'object' THEN SELECT '{'||coalesce(string_agg(to_jsonb(key)::text||':'||lastpay_private.canonical(value),',' ORDER BY key COLLATE "C"),'')||'}' INTO result FROM jsonb_each(data);
 WHEN 'array' THEN SELECT '['||coalesce(string_agg(lastpay_private.canonical(value),',' ORDER BY ord),'')||']' INTO result FROM jsonb_array_elements(data) WITH ORDINALITY x(value,ord);
 WHEN 'number' THEN result:=trim_scale((data::text)::numeric)::text;
 ELSE result:=data::text;
 END CASE;
 RETURN result;
END $$;
-- Fixed window persisted outside domain exception subtransactions. No untrusted forwarded-IP header.
CREATE FUNCTION lastpay_private.take_limit(bucket text) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE subject text; lim integer; v_minute bigint:=floor(extract(epoch FROM clock_timestamp())/60); hits integer;
BEGIN
 IF bucket NOT IN ('public','admin','providers','save') THEN RETURN false; END IF;
 subject:=coalesce(auth.uid()::text,'shared-anon');lim:=CASE bucket WHEN 'public' THEN 120 WHEN 'admin' THEN 30 WHEN 'save' THEN 30 ELSE 30 END;
 DELETE FROM lastpay_private.rate_window w WHERE w.minute<v_minute-2;
 INSERT INTO lastpay_private.rate_window AS w VALUES(bucket,subject,v_minute,1)
 ON CONFLICT ON CONSTRAINT rate_window_pkey DO UPDATE SET minute=excluded.minute,hits=CASE WHEN w.minute=excluded.minute THEN w.hits+1 ELSE 1 END
 RETURNING w.hits INTO hits;
 RETURN hits<=lim;
END $$;
CREATE FUNCTION lastpay_private.header(version text,draft boolean DEFAULT false,lock_row boolean DEFAULT false) RETURNS lastpay.catalog_version
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE v lastpay.catalog_version;
BEGIN
 PERFORM lastpay_private.id(version,true);
 IF lock_row THEN SELECT * INTO v FROM lastpay.catalog_version c WHERE c.version=header.version FOR UPDATE;
 ELSE SELECT * INTO v FROM lastpay.catalog_version c WHERE c.version=header.version; END IF;
 IF v.version IS NULL OR (NOT draft AND v.status<>'PUBLISHED') THEN PERFORM lastpay_private.reject(404,'Catalog version not found'); END IF;
 IF draft AND v.status<>'DRAFT' THEN PERFORM lastpay_private.reject(409,'Published catalog is immutable'); END IF;
 RETURN v;
END $$;
CREATE FUNCTION lastpay_private.etag(revision bigint) RETURNS void LANGUAGE plpgsql SET search_path='' AS $$
BEGIN PERFORM set_config('response.headers',jsonb_build_array(jsonb_build_object('ETag','"rev-'||revision||'"'))::text,true); END $$;
CREATE FUNCTION lastpay_private.read_catalog(action text,args jsonb) RETURNS jsonb
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE v lastpay.catalog_version; ver text; resource text; items jsonb; page integer; size integer; total integer;
 from_year integer; to_year integer; country text; filter text; fetched timestamptz; stale boolean;
BEGIN
 IF action='get_catalog' THEN
  PERFORM lastpay_private.shape(args,'{}');
  SELECT * INTO v FROM lastpay.catalog_version c WHERE status='PUBLISHED' ORDER BY published_at DESC,c.version LIMIT 1;
  IF v.version IS NULL THEN PERFORM lastpay_private.reject(503,'No published catalog'); END IF;
  RETURN lastpay_private.manifest(v);
 END IF;
 IF action IN ('list_draft_skills','get_draft_skill') THEN
  IF NOT lastpay_private.is_member() THEN PERFORM lastpay_private.reject(401,'Sign in required'); END IF;
  IF NOT lastpay_private.is_admin() THEN PERFORM lastpay_private.reject(403,'Catalog administrator required'); END IF;
  args:=args||'{"resource":"skills"}';
 END IF;
 PERFORM lastpay_private.shape(args,ARRAY['resource','catalogVersion','page','pageSize','id','country','yearFrom','yearTo','brandId','trackId'],ARRAY['catalogVersion','resource']);
 ver:=lastpay_private.str(args,'catalogVersion',32);resource:=lastpay_private.str(args,'resource',24);
 v:=lastpay_private.header(ver,action IN ('list_draft_skills','get_draft_skill'));
 IF action IN ('list_draft_skills','get_draft_skill') THEN PERFORM lastpay_private.etag(v.revision); END IF;
 IF action IN ('get_catalog_item','get_draft_skill') THEN
  PERFORM lastpay_private.shape(args,ARRAY['resource','catalogVersion','id'],ARRAY['resource','catalogVersion','id']);
  IF action='get_catalog_item' AND resource NOT IN ('occupations','car-models','tracks') THEN PERFORM lastpay_private.reject(400,'Unsupported detail resource'); END IF;
  items:=lastpay_private.resource(resource,ver,lastpay_private.id(lastpay_private.str(args,'id',64)));
  IF jsonb_array_length(items)=0 THEN PERFORM lastpay_private.reject(404,'Item not found'); END IF;
  RETURN jsonb_build_object('catalogVersion',ver,'data',items->0);
 END IF;
 PERFORM lastpay_private.shape(args,ARRAY['resource','catalogVersion','page','pageSize']||CASE resource WHEN 'inflation' THEN ARRAY['country','yearFrom','yearTo'] WHEN 'car-models' THEN ARRAY['brandId'] WHEN 'race-configurations' THEN ARRAY['trackId'] ELSE '{}'::text[] END,ARRAY['resource','catalogVersion']);
 page:=lastpay_private.integer_arg(args,'page',1,1,1000000);size:=lastpay_private.integer_arg(args,'pageSize',20,1,100);
 items:=lastpay_private.resource(resource,ver);
 IF resource='inflation' THEN
  country:=lastpay_private.str(args,'country',3);
  IF country !~ '^[A-Z]{3}$' THEN PERFORM lastpay_private.reject(400,'Invalid country','country'); END IF;
  IF NOT EXISTS(SELECT FROM lastpay_private.iso_country WHERE code=country) THEN PERFORM lastpay_private.reject(422,'ISO alpha-3 country required','country'); END IF;
  from_year:=lastpay_private.integer_arg(args,'yearFrom',NULL,1900,2100);to_year:=lastpay_private.integer_arg(args,'yearTo',NULL,1900,2100);
  IF from_year>to_year THEN PERFORM lastpay_private.reject(422,'Reversed period','yearTo'); END IF;
  SELECT coalesce(jsonb_agg(e ORDER BY (e->>'year')::integer,e->>'id'),'[]') INTO items FROM jsonb_array_elements(items) e WHERE e->>'countryCode'=country AND (e->>'year')::integer BETWEEN from_year AND to_year;
 END IF;
 IF args ? 'brandId' OR args ? 'trackId' THEN
  filter:=lastpay_private.id(coalesce(args->>'brandId',args->>'trackId'));
  SELECT coalesce(jsonb_agg(e ORDER BY e->>'id'),'[]') INTO items FROM jsonb_array_elements(items) e WHERE e->>CASE WHEN args ? 'brandId' THEN 'brandId' ELSE 'trackId' END=filter;
 END IF;
 total:=jsonb_array_length(items);
 SELECT coalesce(jsonb_agg(e ORDER BY ord),'[]') INTO items FROM jsonb_array_elements(items) WITH ORDINALITY x(e,ord) WHERE ord>(page::bigint-1)*size AND ord<=page::bigint*size;
 SELECT min((value->>'fetchedAt')::timestamptz),bool_or(now()>(value->>'fetchedAt')::timestamptz+CASE WHEN value->>'provider'='WORLD_BANK' THEN interval '30 days' ELSE interval '7 days' END) INTO fetched,stale FROM jsonb_each(v.source_metadata);
 RETURN jsonb_build_object('data',items,'meta',jsonb_build_object('catalogVersion',ver,'page',page,'pageSize',size,'total',total,'fetchedAt',fetched,'stale',coalesce(stale,false)));
END $$;
CREATE FUNCTION lastpay_private.read_rpc(action text,args jsonb) RETURNS jsonb LANGUAGE plpgsql SET search_path='' AS $$
DECLARE status integer; msg text; detail text;
BEGIN
 IF NOT lastpay_private.take_limit(CASE WHEN action LIKE '%draft%' THEN 'admin' ELSE 'public' END) THEN RETURN lastpay_private.problem(429,'Too many requests'); END IF;
 BEGIN RETURN lastpay_private.read_catalog(action,args);
 EXCEPTION WHEN OTHERS THEN
  GET STACKED DIAGNOSTICS msg=MESSAGE_TEXT,detail=PG_EXCEPTION_DETAIL;
  status:=CASE WHEN SQLSTATE LIKE 'LP%' THEN substring(SQLSTATE,3)::integer ELSE 500 END;
  RETURN lastpay_private.problem(status,CASE WHEN status=500 THEN 'Internal error' ELSE msg END,CASE WHEN status=500 THEN NULL ELSE detail END);
 END;
END $$;
CREATE FUNCTION lastpay_private.match(v lastpay.catalog_version,args jsonb) RETURNS void LANGUAGE plpgsql SET search_path='' AS $$
DECLARE expected text:=args->>'ifMatch';
BEGIN
 IF expected IS NULL OR expected !~ '^"rev-[1-9][0-9]*"$' THEN PERFORM lastpay_private.reject(400,'Valid ifMatch required'); END IF;
 IF expected<>'"rev-'||v.revision||'"' THEN PERFORM lastpay_private.reject(412,'Draft changed; reread ETag'); END IF;
END $$;
CREATE FUNCTION lastpay_private.publish(version text) RETURNS jsonb LANGUAGE plpgsql SET search_path='' AS $$
DECLARE name text; data jsonb; snapshot jsonb:='{}'; metadata jsonb; source jsonb; v lastpay.catalog_version; hash text;
BEGIN
 SELECT * INTO v FROM lastpay.catalog_version c WHERE c.version=publish.version FOR UPDATE;
 IF v.status<>'DRAFT' THEN PERFORM lastpay_private.reject(409,'Published catalog is immutable'); END IF;
 IF EXISTS(SELECT FROM lastpay.car_model m LEFT JOIN lastpay.car_balance b ON b.catalog_version=m.catalog_version AND b.model_id=m.id WHERE m.catalog_version=version AND b.model_id IS NULL) THEN PERFORM lastpay_private.reject(422,'Car balance required','car-models'); END IF;
 IF (SELECT count(*) FROM lastpay.job_definition WHERE catalog_version=version AND mechanic='WAREHOUSE')<3 THEN PERFORM lastpay_private.reject(422,'Three warehouse jobs required','jobs'); END IF;
 IF EXISTS(SELECT FROM lastpay.inflation_observation i WHERE i.catalog_version=version AND NOT EXISTS(SELECT FROM lastpay_private.iso_country c WHERE c.code=i.country_code)) THEN PERFORM lastpay_private.reject(422,'Invalid inflation country'); END IF;
 IF (SELECT array_agg(key ORDER BY key) FROM jsonb_object_keys(v.source_metadata) key) IS DISTINCT FROM (SELECT array_agg(x ORDER BY x) FROM unnest(lastpay_private.resources()) x) THEN PERFORM lastpay_private.reject(422,'Incomplete source metadata'); END IF;
 metadata:=v.source_metadata;
 FOREACH name IN ARRAY lastpay_private.resources() LOOP
  data:=lastpay_private.resource(name,version);
  IF jsonb_array_length(data)=0 THEN PERFORM lastpay_private.reject(422,'Empty required catalog',name); END IF;
  source:=metadata->name;
  PERFORM lastpay_private.shape(source,ARRAY['provider','fetchedAt','sourceVersion','sha256'],ARRAY['provider','fetchedAt','sourceVersion','sha256'],422);
  IF lastpay_private.str(source,'provider',24) NOT IN ('DEMO','ONET','WORLD_BANK','CARS_BASE') THEN PERFORM lastpay_private.reject(422,'Invalid source provider'); END IF;
  BEGIN PERFORM (lastpay_private.str(source,'fetchedAt',64))::timestamptz; EXCEPTION WHEN OTHERS THEN PERFORM lastpay_private.reject(422,'Invalid source date','sources'); END;
  PERFORM lastpay_private.str(source,'sourceVersion',120,true,true);
  hash:=encode(sha256(convert_to(lastpay_private.canonical(data),'UTF8')),'hex');
  metadata:=jsonb_set(metadata,ARRAY[name,'sha256'],to_jsonb(hash));snapshot:=snapshot||jsonb_build_object(name,data);
 END LOOP;
 snapshot:=snapshot||jsonb_build_object('source_metadata',metadata);
 hash:=encode(sha256(convert_to(lastpay_private.canonical(snapshot),'UTF8')),'hex');
 UPDATE lastpay.catalog_version c SET status='PUBLISHED',published_at=now(),revision=revision+1,source_metadata=metadata,content_sha256=hash WHERE c.version=publish.version RETURNING * INTO v;
 RETURN lastpay_private.manifest(v);
END $$;
CREATE FUNCTION lastpay_private.mutate(action text,args jsonb) RETURNS jsonb LANGUAGE plpgsql SET search_path='' AS $$
<<mutate>>
DECLARE v lastpay.catalog_version; ver text; base text; tbl text; columns text; body jsonb; id text; code text; title text; description text; source text; external text;
BEGIN
 IF NOT lastpay_private.is_member() THEN PERFORM lastpay_private.reject(401,'Sign in required'); END IF;
 IF NOT lastpay_private.is_admin() THEN PERFORM lastpay_private.reject(403,'Catalog administrator required'); END IF;
 IF action='create_draft' THEN
  PERFORM lastpay_private.shape(args,ARRAY['version','baseVersion'],ARRAY['version','baseVersion']);
  ver:=lastpay_private.str(args,'version',32);base:=lastpay_private.str(args,'baseVersion',32);
  IF ver !~ '^[a-z0-9][a-z0-9._-]{0,31}$' OR base !~ '^[a-z0-9][a-z0-9._-]{0,31}$' THEN PERFORM lastpay_private.reject(422,'Invalid catalog version'); END IF;
  PERFORM lastpay_private.header(base);
  INSERT INTO lastpay.catalog_version(version,status,revision,created_at,source_metadata) SELECT ver,'DRAFT',1,now(),source_metadata FROM lastpay.catalog_version WHERE version=base;
  FOREACH tbl IN ARRAY ARRAY['occupation','skill','occupation_skill','job_definition','inflation_observation','car_brand','car_model','car_specification','car_balance','race_track','race_configuration'] LOOP
   SELECT string_agg(quote_ident(column_name),',' ORDER BY ordinal_position) INTO columns FROM information_schema.columns WHERE table_schema='lastpay' AND table_name=tbl AND column_name<>'catalog_version';
   EXECUTE format('INSERT INTO lastpay.%I(catalog_version,%s) SELECT $1,%s FROM lastpay.%I WHERE catalog_version=$2',tbl,columns,columns,tbl) USING ver,base;
  END LOOP;
  PERFORM set_config('response.status','201',true);PERFORM lastpay_private.etag(1);
  RETURN jsonb_build_object('catalogVersion',ver,'status','DRAFT','revision',1);
 END IF;
 IF action NOT IN ('create_skill','update_skill','delete_skill','publish_catalog') THEN PERFORM lastpay_private.reject(400,'Unknown mutation'); END IF;
 PERFORM lastpay_private.shape(args,ARRAY['version','ifMatch']||CASE WHEN action IN ('update_skill','delete_skill') THEN ARRAY['id'] ELSE '{}'::text[] END||CASE WHEN action IN ('create_skill','update_skill') THEN ARRAY['body'] ELSE '{}'::text[] END,ARRAY['version','ifMatch']);
 ver:=lastpay_private.str(args,'version',32);v:=lastpay_private.header(ver,true,true);PERFORM lastpay_private.match(v,args);
 IF action='publish_catalog' THEN
  PERFORM lastpay_private.shape(args,ARRAY['version','ifMatch'],ARRAY['version','ifMatch']);
  RETURN lastpay_private.publish(ver);
 END IF;
 IF action<>'create_skill' THEN
  id:=lastpay_private.id(lastpay_private.str(args,'id',64));
  IF NOT EXISTS(SELECT FROM lastpay.skill s WHERE s.catalog_version=ver AND s.id=mutate.id) THEN PERFORM lastpay_private.reject(404,'Skill not found'); END IF;
 END IF;
 IF action='delete_skill' THEN
  PERFORM lastpay_private.shape(args,ARRAY['version','ifMatch','id'],ARRAY['version','ifMatch','id']);
  DELETE FROM lastpay.skill s WHERE s.catalog_version=ver AND s.id=mutate.id;
 ELSE
  body:=args->'body';
  PERFORM lastpay_private.shape(body,ARRAY['code','title','description','source','externalId']||CASE WHEN action='create_skill' THEN ARRAY['id'] ELSE '{}'::text[] END,ARRAY['code','title','description','source','externalId']||CASE WHEN action='create_skill' THEN ARRAY['id'] ELSE '{}'::text[] END,422);
  IF action='create_skill' THEN id:=lastpay_private.str(body,'id',64);IF id !~ '^[a-z0-9][a-z0-9._-]{0,63}$' THEN PERFORM lastpay_private.reject(422,'Invalid skill id'); END IF;END IF;
  code:=lastpay_private.str(body,'code',64);title:=lastpay_private.str(body,'title',160);description:=lastpay_private.str(body,'description',4000,false,true);source:=lastpay_private.str(body,'source',24);external:=lastpay_private.str(body,'externalId',64,true,true);
  IF code !~ '^[a-z0-9][a-z0-9._-]{0,63}$' OR source NOT IN ('DEMO','ONET') OR (source='ONET' AND coalesce(external,'') !~ '[^[:space:]   -     　]') THEN PERFORM lastpay_private.reject(422,'Invalid skill source or code'); END IF;
  IF action='create_skill' THEN
   INSERT INTO lastpay.skill VALUES(ver,id,code,title,description,source,external);
  ELSE UPDATE lastpay.skill s SET code=mutate.code,title=mutate.title,description=mutate.description,source=mutate.source,external_id=external WHERE s.catalog_version=ver AND s.id=mutate.id; END IF;
 END IF;
 UPDATE lastpay.catalog_version c SET revision=revision+1,source_metadata=jsonb_set(source_metadata,ARRAY['skills'],jsonb_build_object('provider','DEMO','fetchedAt',to_char(now() AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'sourceVersion','admin-curated','sha256',repeat('0',64))) WHERE c.version=ver RETURNING * INTO v;
 PERFORM lastpay_private.etag(v.revision);
 IF action='delete_skill' THEN PERFORM set_config('response.status','204',true);RETURN NULL; END IF;
 IF action='create_skill' THEN PERFORM set_config('response.status','201',true); END IF;
 RETURN jsonb_build_object('data',lastpay_private.resource('skills',ver,id)->0,'catalogVersion',ver);
END $$;
CREATE FUNCTION lastpay_private.admin_rpc(action text,args jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE status integer; msg text; detail text;
BEGIN
 IF NOT lastpay_private.take_limit('admin') THEN RETURN lastpay_private.problem(429,'Too many requests'); END IF;
 BEGIN RETURN lastpay_private.mutate(action,args);
 EXCEPTION WHEN OTHERS THEN
  GET STACKED DIAGNOSTICS msg=MESSAGE_TEXT,detail=PG_EXCEPTION_DETAIL;
  status:=CASE WHEN SQLSTATE LIKE 'LP%' THEN substring(SQLSTATE,3)::integer WHEN SQLSTATE IN ('23505','23503','23001') THEN 409 WHEN SQLSTATE IN ('23514','22003','22001') THEN 422 ELSE 500 END;
  RETURN lastpay_private.problem(status,CASE WHEN SQLSTATE LIKE 'LP%' THEN msg WHEN status=409 THEN 'Conflicting or referenced data' WHEN status=422 THEN 'Data constraint violation' ELSE 'Internal error' END,CASE WHEN SQLSTATE LIKE 'LP%' THEN detail END);
 END;
END $$;
-- Grants are applied after all functions are created, never via permissive defaults.
CREATE FUNCTION lastpay_private.save_rpc(args jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v lastpay.user_save; rev integer; status integer; msg text;
BEGIN
 IF NOT lastpay_private.take_limit('save') THEN RETURN lastpay_private.problem(429,'Too many requests'); END IF;
 BEGIN
  IF NOT lastpay_private.is_member() THEN PERFORM lastpay_private.reject(401,'Permanent authenticated user required'); END IF;
  PERFORM lastpay_private.shape(args,ARRAY['snapshot','expectedRevision'],ARRAY['snapshot','expectedRevision']);
  IF jsonb_typeof(args->'snapshot')<>'object' OR octet_length((args->'snapshot')::text)>262144 THEN PERFORM lastpay_private.reject(422,'Invalid save snapshot'); END IF;
  rev:=lastpay_private.integer_arg(args,'expectedRevision',NULL,0,2147483647);
  -- Serializes initial creation as well as updates for a single owner.
  PERFORM pg_advisory_xact_lock(hashtextextended(auth.uid()::text,1));
  SELECT * INTO v FROM lastpay.user_save WHERE user_id=auth.uid() FOR UPDATE;
  IF coalesce(v.revision,0)<>rev THEN PERFORM lastpay_private.reject(412,'Save revision changed'); END IF;
  INSERT INTO lastpay.user_save VALUES(auth.uid(),args->'snapshot',rev+1,now())
  ON CONFLICT(user_id) DO UPDATE SET snapshot=excluded.snapshot,revision=excluded.revision,updated_at=excluded.updated_at RETURNING * INTO v;
  RETURN to_jsonb(v);
 EXCEPTION WHEN OTHERS THEN
  status:=CASE WHEN SQLSTATE LIKE 'LP%' THEN substring(SQLSTATE,3)::integer ELSE 500 END;
  GET STACKED DIAGNOSTICS msg=MESSAGE_TEXT;
  RETURN lastpay_private.problem(status,CASE WHEN status=500 THEN 'Internal error' ELSE msg END);
 END;
END $$;
-- Only Edge service_role can reach this RPC. It has no arbitrary SQL/URL arguments.
CREATE FUNCTION lastpay_private.provider_rpc(args jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
<<provider_rpc>>
DECLARE op text; provider text; hash text; owner uuid; payload jsonb; ttl integer; lease lastpay_private.provider_lease; cached lastpay.provider_cache; allowed boolean;
BEGIN
 IF auth.role()<>'service_role' THEN PERFORM lastpay_private.reject(403,'Edge service only'); END IF;
 PERFORM lastpay_private.shape(args,ARRAY['op','provider','hash','owner','payload','ttl','userId'],ARRAY['op']);
 op:=args->>'op';
 IF op='limit' THEN
  owner:=(args->>'userId')::uuid;
  IF owner IS NULL THEN PERFORM lastpay_private.reject(400,'User id required'); END IF;
  -- Claims cannot be supplied by Android: only service_role can call this function.
  PERFORM set_config('request.jwt.claims',jsonb_build_object('role','authenticated','sub',owner)::text,true);
  allowed:=lastpay_private.take_limit('providers');
  PERFORM set_config('request.jwt.claims','{"role":"service_role"}',true);
  -- A global budget also caps upstream work, independent of the number of accounts.
  INSERT INTO lastpay_private.rate_window AS w VALUES('provider-global','all',floor(extract(epoch FROM clock_timestamp())/60),1)
  ON CONFLICT(bucket,subject) DO UPDATE SET minute=excluded.minute,hits=CASE WHEN w.minute=excluded.minute THEN w.hits+1 ELSE 1 END
  RETURNING w.hits<=300 INTO allowed;
  RETURN jsonb_build_object('allowed',allowed AND (SELECT hits<=30 FROM lastpay_private.rate_window WHERE bucket='providers' AND subject=owner::text));
 END IF;
 provider:=args->>'provider';hash:=args->>'hash';
 IF provider NOT IN ('WORLD_BANK','ONET','CARS_BASE') OR hash IS NULL OR hash !~ '^[a-f0-9]{64}$' THEN PERFORM lastpay_private.reject(400,'Invalid provider cache key'); END IF;
 IF op='lookup' THEN
  SELECT * INTO cached FROM lastpay.provider_cache c WHERE c.provider=provider_rpc.provider AND request_hash=hash;
  IF cached.id IS NULL THEN RETURN 'null'; END IF;
  RETURN jsonb_build_object('payload',cached.payload,'fresh',cached.expires_at>now());
 END IF;
 owner:=(args->>'owner')::uuid;
 IF owner IS NULL THEN PERFORM lastpay_private.reject(400,'Lease owner required'); END IF;
 IF op='acquire' THEN
  DELETE FROM lastpay_private.provider_lease WHERE expires_at<now()-interval '1 minute';
  INSERT INTO lastpay_private.provider_lease AS l VALUES(provider,hash,owner,now()+interval '55 seconds')
  ON CONFLICT ON CONSTRAINT provider_lease_pkey DO UPDATE SET owner=excluded.owner,expires_at=excluded.expires_at WHERE l.expires_at<now() RETURNING * INTO lease;
  RETURN jsonb_build_object('acquired',lease.owner=owner);
 END IF;
 SELECT * INTO lease FROM lastpay_private.provider_lease l WHERE l.provider=provider_rpc.provider AND request_hash=hash AND l.owner=provider_rpc.owner AND expires_at>now() FOR UPDATE;
 IF lease.owner IS NULL THEN RETURN jsonb_build_object('stored',false); END IF;
 IF op='store' THEN
  payload:=args->'payload';ttl:=lastpay_private.integer_arg(args,'ttl',NULL,1,30);
  IF jsonb_typeof(payload) NOT IN ('array','object') OR octet_length(payload::text)>2097152 THEN PERFORM lastpay_private.reject(422,'Invalid cache payload'); END IF;
  -- Bound raw cache size for the Free database quota. Do not expose maintenance to clients.
  PERFORM pg_advisory_xact_lock(672301);
  DELETE FROM lastpay.provider_cache WHERE id IN (SELECT id FROM lastpay.provider_cache ORDER BY fetched_at DESC OFFSET 127);
  INSERT INTO lastpay.provider_cache(id,provider,request_hash,payload,fetched_at,expires_at) VALUES(gen_random_uuid(),provider,hash,payload,now(),now()+make_interval(days=>ttl))
  ON CONFLICT ON CONSTRAINT provider_cache_provider_request_hash_key DO UPDATE SET payload=excluded.payload,fetched_at=excluded.fetched_at,expires_at=excluded.expires_at,etag=NULL,catalog_version=NULL;
 ELSIF op<>'release' THEN PERFORM lastpay_private.reject(400,'Unknown cache action'); END IF;
 DELETE FROM lastpay_private.provider_lease l WHERE l.provider=provider_rpc.provider AND request_hash=hash AND l.owner=provider_rpc.owner;
 RETURN jsonb_build_object('stored',op='store');
END $$;
CREATE FUNCTION lastpay_api.get_catalog(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.read_rpc('get_catalog',args) $$;
CREATE FUNCTION lastpay_api.list_catalog(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.read_rpc('list_catalog',args) $$;
CREATE FUNCTION lastpay_api.get_catalog_item(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.read_rpc('get_catalog_item',args) $$;
CREATE FUNCTION lastpay_api.list_draft_skills(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.read_rpc('list_draft_skills',args) $$;
CREATE FUNCTION lastpay_api.get_draft_skill(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.read_rpc('get_draft_skill',args) $$;
CREATE FUNCTION lastpay_api.create_draft(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.admin_rpc('create_draft',args) $$;
CREATE FUNCTION lastpay_api.create_skill(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.admin_rpc('create_skill',args) $$;
CREATE FUNCTION lastpay_api.update_skill(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.admin_rpc('update_skill',args) $$;
CREATE FUNCTION lastpay_api.delete_skill(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.admin_rpc('delete_skill',args) $$;
CREATE FUNCTION lastpay_api.publish_catalog(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.admin_rpc('publish_catalog',args) $$;
CREATE FUNCTION lastpay_api.save_game(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.save_rpc(args) $$;
CREATE FUNCTION lastpay_api.provider_cache(args jsonb DEFAULT '{}') RETURNS jsonb LANGUAGE sql SET search_path='' AS $$ SELECT lastpay_private.provider_rpc(args) $$;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA lastpay_private,lastpay_api FROM PUBLIC,anon,authenticated,service_role;
GRANT EXECUTE ON FUNCTION lastpay_private.is_member() TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.is_admin() TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.reject(integer,text,text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.problem(integer,text,text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.shape(jsonb,text[],text[],integer) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.str(jsonb,text,integer,boolean,boolean) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.id(text,boolean) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.integer_arg(jsonb,text,integer,integer,integer) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.table_for(text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.camel(jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.resources() TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.resource(text,text,text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.manifest(lastpay.catalog_version) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.header(text,boolean,boolean) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.etag(bigint) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.read_catalog(text,jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.read_rpc(text,jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.take_limit(text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.admin_rpc(text,jsonb),lastpay_private.save_rpc(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION lastpay_private.provider_rpc(jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION lastpay_api.get_catalog(jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.list_catalog(jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.get_catalog_item(jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.list_draft_skills(jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.get_draft_skill(jsonb) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.create_draft(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.create_skill(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.update_skill(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.delete_skill(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.publish_catalog(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.save_game(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION lastpay_api.provider_cache(jsonb) TO service_role;
NOTIFY pgrst,'reload schema';
COMMIT;

