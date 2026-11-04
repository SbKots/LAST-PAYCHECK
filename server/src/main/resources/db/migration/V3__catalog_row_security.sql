-- Runtime logins are cluster-wide. Passwords are provisioned separately.
DO $$
DECLARE login_name text;
BEGIN
  FOREACH login_name IN ARRAY ARRAY['lastpay_reader', 'lastpay_writer'] LOOP
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = login_name) THEN
      EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', login_name);
    END IF;
    IF EXISTS (SELECT FROM pg_roles r WHERE r.rolname = login_name AND
      (NOT r.rolcanlogin OR r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls
       OR EXISTS (SELECT FROM pg_auth_members m WHERE m.member = r.oid))) THEN
      RAISE EXCEPTION 'Unsafe runtime role %', login_name;
    END IF;
  END LOOP;
END $$;

-- FOR SHARE is filtered by the UPDATE policy. Fail closed when a parent
-- cannot be locked, including when it was just published by another transaction.
CREATE OR REPLACE FUNCTION guard_published_catalog() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_status text;
BEGIN
  IF TG_TABLE_NAME = 'catalog_version' THEN
    IF OLD.status = 'PUBLISHED' THEN
      RAISE EXCEPTION 'Published catalog is immutable' USING ERRCODE = '23514';
    END IF;
  ELSE
    IF TG_OP <> 'INSERT' THEN
      SELECT status INTO v_status FROM catalog_version WHERE version = OLD.catalog_version FOR SHARE;
      IF NOT FOUND THEN
        RAISE EXCEPTION 'Cannot lock catalog version' USING ERRCODE = '23503';
      END IF;
      IF v_status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'Published catalog is immutable' USING ERRCODE = '23514';
      END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
      SELECT status INTO v_status FROM catalog_version WHERE version = NEW.catalog_version FOR SHARE;
      IF NOT FOUND THEN
        RAISE EXCEPTION 'Cannot lock catalog version' USING ERRCODE = '23503';
      END IF;
      IF v_status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'Published catalog is immutable' USING ERRCODE = '23514';
      END IF;
    END IF;
  END IF;
  IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;

ALTER TABLE catalog_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE catalog_version FORCE ROW LEVEL SECURITY;
GRANT SELECT ON catalog_version TO lastpay_reader, lastpay_writer;
GRANT INSERT, UPDATE (status, revision, published_at, content_sha256, source_metadata)
  ON catalog_version TO lastpay_writer;
CREATE POLICY published_read ON catalog_version FOR SELECT TO lastpay_reader USING (status = 'PUBLISHED');
CREATE POLICY editor_read ON catalog_version FOR SELECT TO lastpay_writer USING (status IN ('DRAFT', 'PUBLISHED'));
CREATE POLICY draft_insert ON catalog_version FOR INSERT TO lastpay_writer WITH CHECK (status = 'DRAFT');
CREATE POLICY draft_update ON catalog_version FOR UPDATE TO lastpay_writer
  USING (status = 'DRAFT') WITH CHECK (status IN ('DRAFT', 'PUBLISHED'));

GRANT SELECT, INSERT, UPDATE ON provider_cache TO lastpay_writer;

DO $$
DECLARE schema_name text := current_schema();
table_name text;
parent_query text;
BEGIN
  EXECUTE format('GRANT USAGE ON SCHEMA %I TO lastpay_reader, lastpay_writer', schema_name);
  EXECUTE format('ALTER FUNCTION %I.guard_published_catalog() SET search_path TO pg_catalog, %I, pg_temp', schema_name, schema_name);
  FOREACH table_name IN ARRAY ARRAY[
    'occupation', 'skill', 'occupation_skill', 'job_definition',
    'inflation_observation', 'car_brand', 'car_model', 'car_specification',
    'car_balance', 'race_track', 'race_configuration'
  ] LOOP
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', schema_name, table_name);
    EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', schema_name, table_name);
    EXECUTE format('GRANT SELECT ON %I.%I TO lastpay_reader, lastpay_writer', schema_name, table_name);
    EXECUTE format('GRANT INSERT ON %I.%I TO lastpay_writer', schema_name, table_name);
    parent_query := format('SELECT FROM %I.catalog_version v WHERE v.version = %I.catalog_version', schema_name, table_name);
    EXECUTE format('CREATE POLICY published_read ON %I.%I FOR SELECT TO lastpay_reader USING (EXISTS (%s AND v.status = %L))',
      schema_name, table_name, parent_query, 'PUBLISHED');
    EXECUTE format('CREATE POLICY editor_read ON %I.%I FOR SELECT TO lastpay_writer USING (EXISTS (%s))',
      schema_name, table_name, parent_query);
    EXECUTE format('CREATE POLICY draft_insert ON %I.%I FOR INSERT TO lastpay_writer WITH CHECK (EXISTS (%s AND v.status = %L))',
      schema_name, table_name, parent_query, 'DRAFT');
    IF table_name = 'skill' THEN
      EXECUTE format('GRANT UPDATE (code, title, description, source, external_id), DELETE ON %I.skill TO lastpay_writer', schema_name);
      EXECUTE format('CREATE POLICY draft_update ON %I.skill FOR UPDATE TO lastpay_writer USING (EXISTS (%s AND v.status = %L))',
        schema_name, parent_query, 'DRAFT');
      EXECUTE format('CREATE POLICY draft_delete ON %I.skill FOR DELETE TO lastpay_writer USING (EXISTS (%s AND v.status = %L))',
        schema_name, parent_query, 'DRAFT');
    END IF;
  END LOOP;
END $$;
