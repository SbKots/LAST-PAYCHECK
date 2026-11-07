-- Supabase adaptation of V1: no Flyway or legacy runtime roles.
-- Abort on a populated lastpay schema. Never overwrite an existing migration.
BEGIN;
CREATE SCHEMA IF NOT EXISTS lastpay;
CREATE SCHEMA lastpay_private;
CREATE SCHEMA lastpay_api;
REVOKE ALL ON SCHEMA lastpay, lastpay_private, lastpay_api FROM PUBLIC, anon, authenticated, service_role;
SET LOCAL search_path = lastpay, pg_catalog;
DO $$ BEGIN
 IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema='lastpay') THEN
  RAISE EXCEPTION 'lastpay is not empty; review existing objects before migration';
 END IF;
END $$;
-- Practice 9: 13 application tables from the immutable practice 8 model.

CREATE TABLE catalog_version (
  version varchar(32) NOT NULL,
  status varchar(12) NOT NULL,
  revision bigint NOT NULL,
  created_at timestamptz NOT NULL,
  published_at timestamptz,
  content_sha256 char(64),
  source_metadata jsonb NOT NULL,
  PRIMARY KEY (version),
  CHECK (version ~ '^[a-z0-9][a-z0-9._-]{0,31}$'),
  CHECK (status IN ('DRAFT','PUBLISHED')),
  CHECK (revision > 0),
  CHECK (jsonb_typeof(source_metadata)='object'),
  CHECK ((status='DRAFT' AND published_at IS NULL AND content_sha256 IS NULL) OR (status='PUBLISHED' AND published_at IS NOT NULL AND content_sha256 IS NOT NULL AND content_sha256 ~ '^[a-f0-9]{64}$'))
);

CREATE TABLE provider_cache (
  id uuid NOT NULL,
  provider varchar(24) NOT NULL,
  request_hash char(64) NOT NULL,
  payload jsonb NOT NULL,
  fetched_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  etag varchar(256),
  catalog_version varchar(32),
  PRIMARY KEY (id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  UNIQUE (provider, request_hash),
  CHECK (provider IN ('ONET','WORLD_BANK','CARS_BASE')),
  CHECK (request_hash ~ '^[a-f0-9]{64}$'),
  CHECK (jsonb_typeof(payload) IN ('object','array')),
  CHECK (octet_length(payload::text)<=2097152),
  CHECK (expires_at>=fetched_at)
);

CREATE TABLE occupation (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  source varchar(24) NOT NULL,
  external_id varchar(64),
  title varchar(160) NOT NULL,
  description text NOT NULL,
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  UNIQUE (catalog_version, source, external_id),
  CHECK (source IN ('ONET','DEMO')),
  CHECK (source='DEMO' OR length(trim(external_id))>0 AND external_id IS NOT NULL),
  CHECK (length(trim(title))>0),
  CHECK (length(description)<=4000),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE TABLE skill (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  code varchar(64) NOT NULL,
  title varchar(160) NOT NULL,
  description text NOT NULL,
  source varchar(24) NOT NULL,
  external_id varchar(64),
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  UNIQUE (catalog_version, code),
  UNIQUE (catalog_version, source, external_id),
  CHECK (source IN ('ONET','DEMO')),
  CHECK (source='DEMO' OR length(trim(external_id))>0 AND external_id IS NOT NULL),
  CHECK (code ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
  CHECK (length(trim(title))>0),
  CHECK (length(description)<=4000),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE TABLE occupation_skill (
  catalog_version varchar(32) NOT NULL,
  occupation_id varchar(64) NOT NULL,
  skill_id varchar(64) NOT NULL,
  importance numeric(5,2),
  PRIMARY KEY (catalog_version, occupation_id, skill_id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, occupation_id) REFERENCES occupation(catalog_version, id) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, skill_id) REFERENCES skill(catalog_version, id) ON DELETE RESTRICT,
  CHECK (importance BETWEEN 0 AND 100)
);

CREATE TABLE job_definition (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  occupation_id varchar(64),
  required_skill_id varchar(64) NOT NULL,
  title varchar(160) NOT NULL,
  required_level smallint NOT NULL,
  duration_hours numeric(5,2) NOT NULL,
  base_pay_minor bigint NOT NULL,
  bonus_cap_minor bigint NOT NULL,
  task_plan integer NOT NULL,
  extra_energy_per_hour numeric(5,2) NOT NULL,
  mechanic varchar(16) NOT NULL,
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, occupation_id) REFERENCES occupation(catalog_version, id) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, required_skill_id) REFERENCES skill(catalog_version, id) ON DELETE RESTRICT,
  CHECK (required_level BETWEEN 0 AND 100),
  CHECK (duration_hours>0 AND duration_hours<=24),
  CHECK (base_pay_minor BETWEEN 0 AND 9007199254740991),
  CHECK (bonus_cap_minor BETWEEN 0 AND 9007199254740991),
  CHECK (task_plan>0),
  CHECK (extra_energy_per_hour BETWEEN 0 AND 100),
  CHECK (mechanic='WAREHOUSE'),
  CHECK (length(trim(title))>0),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE TABLE inflation_observation (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  country_code char(3) NOT NULL,
  year smallint NOT NULL,
  indicator_code varchar(32) NOT NULL,
  value_percent numeric(12,6) NOT NULL,
  source varchar(24) NOT NULL,
  fetched_at timestamptz NOT NULL,
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  UNIQUE (catalog_version, country_code, indicator_code, year),
  CHECK (country_code ~ '^[A-Z]{3}$'),
  CHECK (year BETWEEN 1900 AND 2100),
  CHECK (indicator_code='FP.CPI.TOTL.ZG'),
  CHECK (value_percent > -100),
  CHECK (source IN ('WORLD_BANK','DEMO')),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE TABLE car_brand (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  source varchar(24) NOT NULL,
  external_id varchar(64),
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  UNIQUE (catalog_version, source, external_id),
  CHECK (source IN ('CARS_BASE','DEMO')),
  CHECK (source='DEMO' OR length(trim(external_id))>0 AND external_id IS NOT NULL),
  CHECK (length(trim(name))>0),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE TABLE car_model (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  brand_id varchar(64) NOT NULL,
  name varchar(120) NOT NULL,
  variant varchar(120) NOT NULL,
  model_year smallint,
  source varchar(24) NOT NULL,
  external_id varchar(64),
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, brand_id) REFERENCES car_brand(catalog_version, id) ON DELETE RESTRICT,
  UNIQUE (catalog_version, source, external_id),
  CHECK (source IN ('CARS_BASE','DEMO')),
  CHECK (source='DEMO' OR length(trim(external_id))>0 AND external_id IS NOT NULL),
  CHECK (model_year BETWEEN 1886 AND 2100),
  CHECK (length(trim(name))>0),
  CHECK (length(trim(variant))>0),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE TABLE car_specification (
  catalog_version varchar(32) NOT NULL,
  model_id varchar(64) NOT NULL,
  mass_kg numeric(8,2),
  power_kw numeric(8,2),
  max_speed_kmh numeric(6,2),
  tank_liters numeric(6,2),
  consumption_l_per_100km numeric(6,2),
  PRIMARY KEY (catalog_version, model_id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, model_id) REFERENCES car_model(catalog_version, id) ON DELETE RESTRICT,
  CHECK (mass_kg>0),
  CHECK (power_kw>0),
  CHECK (max_speed_kmh>0),
  CHECK (tank_liters>0),
  CHECK (consumption_l_per_100km>=0)
);

CREATE TABLE car_balance (
  catalog_version varchar(32) NOT NULL,
  model_id varchar(64) NOT NULL,
  purchase_price_minor bigint NOT NULL,
  race_max_speed_kmh numeric(6,2) NOT NULL,
  acceleration_mps2 numeric(6,3) NOT NULL,
  handling numeric(4,3) NOT NULL,
  tank_liters numeric(6,2) NOT NULL,
  consumption_l_per_100km numeric(6,2) NOT NULL,
  repair_price_per_point_minor bigint NOT NULL,
  PRIMARY KEY (catalog_version, model_id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, model_id) REFERENCES car_model(catalog_version, id) ON DELETE RESTRICT,
  CHECK (purchase_price_minor BETWEEN 1 AND 9007199254740991),
  CHECK (race_max_speed_kmh>0),
  CHECK (acceleration_mps2>0),
  CHECK (handling>0 AND handling<=1),
  CHECK (tank_liters>0),
  CHECK (consumption_l_per_100km>=0),
  CHECK (repair_price_per_point_minor BETWEEN 0 AND 9007199254740991)
);

CREATE TABLE race_track (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  title varchar(160) NOT NULL,
  length_m integer NOT NULL,
  asset_key varchar(120) NOT NULL,
  asset_sha256 char(64) NOT NULL,
  layout_revision integer NOT NULL,
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  UNIQUE (catalog_version, asset_key),
  CHECK (length(trim(title))>0),
  CHECK (length_m>0),
  CHECK (asset_sha256 ~ '^[a-f0-9]{64}$'),
  CHECK (layout_revision>0),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE TABLE race_configuration (
  catalog_version varchar(32) NOT NULL,
  id varchar(64) NOT NULL,
  track_id varchar(64) NOT NULL,
  title varchar(160) NOT NULL,
  laps smallint NOT NULL,
  time_limit_seconds integer NOT NULL,
  entry_fee_minor bigint NOT NULL,
  finish_reward_minor bigint NOT NULL,
  required_car_condition smallint NOT NULL,
  PRIMARY KEY (catalog_version, id),
  FOREIGN KEY (catalog_version) REFERENCES catalog_version(version) ON DELETE RESTRICT,
  FOREIGN KEY (catalog_version, track_id) REFERENCES race_track(catalog_version, id) ON DELETE RESTRICT,
  CHECK (length(trim(title))>0),
  CHECK (laps BETWEEN 1 AND 100),
  CHECK (time_limit_seconds>0),
  CHECK (entry_fee_minor BETWEEN 0 AND 9007199254740991),
  CHECK (finish_reward_minor BETWEEN 0 AND 9007199254740991),
  CHECK (required_car_condition BETWEEN 0 AND 100),
  CHECK (id ~ '^[a-z0-9][a-z0-9._-]{0,63}$')
);

CREATE INDEX ix_catalog_version_0 ON catalog_version (status, published_at DESC, version);

CREATE INDEX ix_provider_cache_0 ON provider_cache (expires_at);

CREATE INDEX ix_occupation_skill_0 ON occupation_skill (catalog_version, skill_id, occupation_id);

CREATE INDEX ix_job_definition_0 ON job_definition (catalog_version, occupation_id);

CREATE INDEX ix_job_definition_1 ON job_definition (catalog_version, required_skill_id);

CREATE INDEX ix_car_model_0 ON car_model (catalog_version, brand_id, id);

CREATE INDEX ix_race_configuration_0 ON race_configuration (catalog_version, track_id, id);

CREATE TABLE lastpay_private.admin_membership (
 user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE lastpay.user_save (
 user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
 snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot)='object' AND octet_length(snapshot::text)<=262144),
 revision bigint NOT NULL CHECK (revision BETWEEN 1 AND 9007199254740991),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE lastpay_private.rate_window (
 bucket text NOT NULL, subject text NOT NULL, minute bigint NOT NULL, hits integer NOT NULL,
 PRIMARY KEY(bucket,subject)
);
CREATE TABLE lastpay_private.provider_lease (
 provider text NOT NULL, request_hash text NOT NULL, owner uuid NOT NULL, expires_at timestamptz NOT NULL,
 PRIMARY KEY(provider,request_hash)
);
CREATE TABLE lastpay_private.iso_country (code text PRIMARY KEY);
INSERT INTO lastpay_private.iso_country VALUES ('ABW'),('AFG'),('AGO'),('AIA'),('ALA'),('ALB'),('AND'),('ARE'),('ARG'),('ARM'),('ASM'),('ATA'),('ATF'),('ATG'),('AUS'),('AUT'),('AZE'),('BDI'),('BEL'),('BEN'),('BES'),('BFA'),('BGD'),('BGR'),('BHR'),('BHS'),('BIH'),('BLM'),('BLR'),('BLZ'),('BMU'),('BOL'),('BRA'),('BRB'),('BRN'),('BTN'),('BVT'),('BWA'),('CAF'),('CAN'),('CCK'),('CHE'),('CHL'),('CHN'),('CIV'),('CMR'),('COD'),('COG'),('COK'),('COL'),('COM'),('CPV'),('CRI'),('CUB'),('CUW'),('CXR'),('CYM'),('CYP'),('CZE'),('DEU'),('DJI'),('DMA'),('DNK'),('DOM'),('DZA'),('ECU'),('EGY'),('ERI'),('ESH'),('ESP'),('EST'),('ETH'),('FIN'),('FJI'),('FLK'),('FRA'),('FRO'),('FSM'),('GAB'),('GBR'),('GEO'),('GGY'),('GHA'),('GIB'),('GIN'),('GLP'),('GMB'),('GNB'),('GNQ'),('GRC'),('GRD'),('GRL'),('GTM'),('GUF'),('GUM'),('GUY'),('HKG'),('HMD'),('HND'),('HRV'),('HTI'),('HUN'),('IDN'),('IMN'),('IND'),('IOT'),('IRL'),('IRN'),('IRQ'),('ISL'),('ISR'),('ITA'),('JAM'),('JEY'),('JOR'),('JPN'),('KAZ'),('KEN'),('KGZ'),('KHM'),('KIR'),('KNA'),('KOR'),('KWT'),('LAO'),('LBN'),('LBR'),('LBY'),('LCA'),('LIE'),('LKA'),('LSO'),('LTU'),('LUX'),('LVA'),('MAC'),('MAF'),('MAR'),('MCO'),('MDA'),('MDG'),('MDV'),('MEX'),('MHL'),('MKD'),('MLI'),('MLT'),('MMR'),('MNE'),('MNG'),('MNP'),('MOZ'),('MRT'),('MSR'),('MTQ'),('MUS'),('MWI'),('MYS'),('MYT'),('NAM'),('NCL'),('NER'),('NFK'),('NGA'),('NIC'),('NIU'),('NLD'),('NOR'),('NPL'),('NRU'),('NZL'),('OMN'),('PAK'),('PAN'),('PCN'),('PER'),('PHL'),('PLW'),('PNG'),('POL'),('PRI'),('PRK'),('PRT'),('PRY'),('PSE'),('PYF'),('QAT'),('REU'),('ROU'),('RUS'),('RWA'),('SAU'),('SDN'),('SEN'),('SGP'),('SGS'),('SHN'),('SJM'),('SLB'),('SLE'),('SLV'),('SMR'),('SOM'),('SPM'),('SRB'),('SSD'),('STP'),('SUR'),('SVK'),('SVN'),('SWE'),('SWZ'),('SXM'),('SYC'),('SYR'),('TCA'),('TCD'),('TGO'),('THA'),('TJK'),('TKL'),('TKM'),('TLS'),('TON'),('TTO'),('TUN'),('TUR'),('TUV'),('TWN'),('TZA'),('UGA'),('UKR'),('UMI'),('URY'),('USA'),('UZB'),('VAT'),('VCT'),('VEN'),('VGB'),('VIR'),('VNM'),('VUT'),('WLF'),('WSM'),('YEM'),('ZAF'),('ZMB'),('ZWE');

CREATE FUNCTION lastpay_private.is_member() RETURNS boolean
LANGUAGE sql STABLE SET search_path='' AS $$
 SELECT auth.uid() IS NOT NULL AND auth.role()='authenticated'
 AND coalesce(auth.jwt()->>'is_anonymous','false')='false'
$$;
CREATE FUNCTION lastpay_private.is_admin() RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT lastpay_private.is_member() AND EXISTS (
 SELECT FROM lastpay_private.admin_membership WHERE user_id=auth.uid())
$$;
CREATE FUNCTION lastpay_private.guard_published() RETURNS trigger
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE state text;
BEGIN
 IF TG_TABLE_NAME='catalog_version' THEN
  IF OLD.status='PUBLISHED' THEN RAISE EXCEPTION 'Published catalog is immutable' USING ERRCODE='23514'; END IF;
 ELSE
  IF TG_OP<>'INSERT' THEN
   SELECT status INTO state FROM lastpay.catalog_version WHERE version=OLD.catalog_version FOR SHARE;
   IF NOT FOUND OR state='PUBLISHED' THEN RAISE EXCEPTION 'Catalog is immutable or missing' USING ERRCODE='23503'; END IF;
  END IF;
  IF TG_OP<>'DELETE' THEN
   SELECT status INTO state FROM lastpay.catalog_version WHERE version=NEW.catalog_version FOR SHARE;
   IF NOT FOUND OR state='PUBLISHED' THEN RAISE EXCEPTION 'Catalog is immutable or missing' USING ERRCODE='23503'; END IF;
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;
CREATE TRIGGER immutable_catalog BEFORE UPDATE OR DELETE ON lastpay.catalog_version
FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_occupation BEFORE INSERT OR UPDATE OR DELETE ON lastpay.occupation FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_skill BEFORE INSERT OR UPDATE OR DELETE ON lastpay.skill FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_occupation_skill BEFORE INSERT OR UPDATE OR DELETE ON lastpay.occupation_skill FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_job_definition BEFORE INSERT OR UPDATE OR DELETE ON lastpay.job_definition FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_inflation_observation BEFORE INSERT OR UPDATE OR DELETE ON lastpay.inflation_observation FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_car_brand BEFORE INSERT OR UPDATE OR DELETE ON lastpay.car_brand FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_car_model BEFORE INSERT OR UPDATE OR DELETE ON lastpay.car_model FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_car_specification BEFORE INSERT OR UPDATE OR DELETE ON lastpay.car_specification FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_car_balance BEFORE INSERT OR UPDATE OR DELETE ON lastpay.car_balance FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_race_track BEFORE INSERT OR UPDATE OR DELETE ON lastpay.race_track FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
CREATE TRIGGER immutable_race_configuration BEFORE INSERT OR UPDATE OR DELETE ON lastpay.race_configuration FOR EACH ROW EXECUTE FUNCTION lastpay_private.guard_published();
ALTER TABLE lastpay.catalog_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.catalog_version FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.provider_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.provider_cache FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.occupation ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.occupation FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.skill ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.skill FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.occupation_skill ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.occupation_skill FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.job_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.job_definition FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.inflation_observation ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.inflation_observation FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_brand ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_brand FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_model ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_model FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_specification ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_specification FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_balance ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.car_balance FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.race_track ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.race_track FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.race_configuration ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.race_configuration FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay.user_save ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay.user_save FORCE ROW LEVEL SECURITY;
ALTER TABLE lastpay_private.admin_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay_private.rate_window ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay_private.provider_lease ENABLE ROW LEVEL SECURITY;
ALTER TABLE lastpay_private.iso_country ENABLE ROW LEVEL SECURITY;
CREATE POLICY country_read ON lastpay_private.iso_country FOR SELECT TO anon,authenticated USING(true);
CREATE POLICY catalog_read ON lastpay.catalog_version FOR SELECT TO anon,authenticated
USING (status='PUBLISHED' OR lastpay_private.is_admin());
CREATE POLICY save_read ON lastpay.user_save FOR SELECT TO authenticated
USING (user_id=(SELECT auth.uid()) AND lastpay_private.is_member());
CREATE POLICY catalog_read ON lastpay.occupation FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.skill FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.occupation_skill FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.job_definition FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.inflation_observation FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.car_brand FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.car_model FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.car_specification FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.car_balance FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.race_track FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));
CREATE POLICY catalog_read ON lastpay.race_configuration FOR SELECT TO anon,authenticated USING (EXISTS (SELECT FROM lastpay.catalog_version v WHERE v.version=catalog_version AND (v.status='PUBLISHED' OR lastpay_private.is_admin())));

-- No client DML policies/grants: mutations occur only through checked atomic RPC.
REVOKE ALL ON ALL TABLES IN SCHEMA lastpay,lastpay_private FROM PUBLIC,anon,authenticated,service_role;
GRANT USAGE ON SCHEMA lastpay,lastpay_private,lastpay_api TO anon,authenticated,service_role;
GRANT SELECT ON lastpay_private.iso_country TO anon,authenticated;
GRANT SELECT ON lastpay.catalog_version TO anon,authenticated;
GRANT SELECT ON lastpay.occupation TO anon,authenticated;
GRANT SELECT ON lastpay.skill TO anon,authenticated;
GRANT SELECT ON lastpay.occupation_skill TO anon,authenticated;
GRANT SELECT ON lastpay.job_definition TO anon,authenticated;
GRANT SELECT ON lastpay.inflation_observation TO anon,authenticated;
GRANT SELECT ON lastpay.car_brand TO anon,authenticated;
GRANT SELECT ON lastpay.car_model TO anon,authenticated;
GRANT SELECT ON lastpay.car_specification TO anon,authenticated;
GRANT SELECT ON lastpay.car_balance TO anon,authenticated;
GRANT SELECT ON lastpay.race_track TO anon,authenticated;
GRANT SELECT ON lastpay.race_configuration TO anon,authenticated;
GRANT SELECT ON lastpay.user_save TO authenticated;
CREATE VIEW lastpay_api.user_save WITH (security_invoker=true) AS
SELECT user_id,snapshot,revision,updated_at FROM lastpay.user_save;
GRANT SELECT ON lastpay_api.user_save TO authenticated;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA lastpay_private,lastpay_api FROM PUBLIC,anon,authenticated,service_role;
GRANT EXECUTE ON FUNCTION lastpay_private.is_admin(),lastpay_private.is_member() TO anon,authenticated;
COMMIT;
