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
