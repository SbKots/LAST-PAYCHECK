-- A jsonb RPC with response.status=204 is serialized as JSON null by PostgREST.
-- The resulting Content-Length: 4 is invalid for 204; Envoy retries after commit.
-- Keep the signature, invoker security and existing ACL; return a framed receipt.
CREATE OR REPLACE FUNCTION lastpay_api.delete_skill(args jsonb DEFAULT '{}')
RETURNS jsonb LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path='' AS $$
DECLARE result jsonb;
BEGIN
 result := lastpay_private.admin_rpc('delete_skill',args);
 IF result IS NULL AND current_setting('response.status',true)='204' THEN
  PERFORM set_config('response.status','200',true);
  RETURN jsonb_build_object('deleted',true,'id',args->>'id','catalogVersion',args->>'version');
 END IF;
 RETURN result;
END $$;

-- Preserve the revision validator across intermediary content transformations.
-- Do not accept or strip W/ in If-Match; weak validators remain invalid inputs.
CREATE OR REPLACE FUNCTION lastpay_private.etag(revision bigint)
RETURNS void LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
 PERFORM set_config('response.headers',jsonb_build_array(
  jsonb_build_object('ETag','"rev-'||revision||'"'),
  jsonb_build_object('Cache-Control','private, no-store, no-transform')
 )::text,true);
END $$;
