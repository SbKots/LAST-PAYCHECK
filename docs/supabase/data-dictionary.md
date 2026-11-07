# Структура PostgreSQL Supabase

Получено из отдельной локальной БД после применения миграций 001–004. Здесь только метаданные схемы, без пользовательских строк. Источник облачного развёртывания — те же неизменяемые SQL-файлы. auth.users управляется платформой и не включена в 18 таблиц приложения.

## lastpay.car_balance

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| model_id | character varying(64) | True | — |
| purchase_price_minor | bigint | True | — |
| race_max_speed_kmh | numeric(6,2) | True | — |
| acceleration_mps2 | numeric(6,3) | True | — |
| handling | numeric(4,3) | True | — |
| tank_liters | numeric(6,2) | True | — |
| consumption_l_per_100km | numeric(6,2) | True | — |
| repair_price_per_point_minor | bigint | True | — |

Ограничения:

- `car_balance_acceleration_mps2_check`: `CHECK ((acceleration_mps2 > (0)::numeric))`.
- `car_balance_consumption_l_per_100km_check`: `CHECK ((consumption_l_per_100km >= (0)::numeric))`.
- `car_balance_handling_check`: `CHECK (((handling > (0)::numeric) AND (handling <= (1)::numeric)))`.
- `car_balance_purchase_price_minor_check`: `CHECK (((purchase_price_minor >= 1) AND (purchase_price_minor <= '9007199254740991'::bigint)))`.
- `car_balance_race_max_speed_kmh_check`: `CHECK ((race_max_speed_kmh > (0)::numeric))`.
- `car_balance_repair_price_per_point_minor_check`: `CHECK (((repair_price_per_point_minor >= 0) AND (repair_price_per_point_minor <= '9007199254740991'::bigint)))`.
- `car_balance_tank_liters_check`: `CHECK ((tank_liters > (0)::numeric))`.
- `car_balance_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `car_balance_catalog_version_model_id_fkey`: `FOREIGN KEY (catalog_version, model_id) REFERENCES lastpay.car_model(catalog_version, id) ON DELETE RESTRICT`.
- `car_balance_pkey`: `PRIMARY KEY (catalog_version, model_id)`.

Индексы:

- `CREATE UNIQUE INDEX car_balance_pkey ON lastpay.car_balance USING btree (catalog_version, model_id)`.

## lastpay.car_brand

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| name | character varying(100) | True | — |
| source | character varying(24) | True | — |
| external_id | character varying(64) | False | — |

Ограничения:

- `car_brand_check`: `CHECK ((((source)::text = 'DEMO'::text) OR ((length(TRIM(BOTH FROM external_id)) > 0) AND (external_id IS NOT NULL))))`.
- `car_brand_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `car_brand_name_check`: `CHECK ((length(TRIM(BOTH FROM name)) > 0))`.
- `car_brand_source_check`: `CHECK (((source)::text = ANY ((ARRAY['CARS_BASE'::character varying, 'DEMO'::character varying])::text[])))`.
- `car_brand_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `car_brand_pkey`: `PRIMARY KEY (catalog_version, id)`.
- `car_brand_catalog_version_source_external_id_key`: `UNIQUE (catalog_version, source, external_id)`.

Индексы:

- `CREATE UNIQUE INDEX car_brand_catalog_version_source_external_id_key ON lastpay.car_brand USING btree (catalog_version, source, external_id)`.
- `CREATE UNIQUE INDEX car_brand_pkey ON lastpay.car_brand USING btree (catalog_version, id)`.

## lastpay.car_model

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| brand_id | character varying(64) | True | — |
| name | character varying(120) | True | — |
| variant | character varying(120) | True | — |
| model_year | smallint | False | — |
| source | character varying(24) | True | — |
| external_id | character varying(64) | False | — |

Ограничения:

- `car_model_check`: `CHECK ((((source)::text = 'DEMO'::text) OR ((length(TRIM(BOTH FROM external_id)) > 0) AND (external_id IS NOT NULL))))`.
- `car_model_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `car_model_model_year_check`: `CHECK (((model_year >= 1886) AND (model_year <= 2100)))`.
- `car_model_name_check`: `CHECK ((length(TRIM(BOTH FROM name)) > 0))`.
- `car_model_source_check`: `CHECK (((source)::text = ANY ((ARRAY['CARS_BASE'::character varying, 'DEMO'::character varying])::text[])))`.
- `car_model_variant_check`: `CHECK ((length(TRIM(BOTH FROM variant)) > 0))`.
- `car_model_catalog_version_brand_id_fkey`: `FOREIGN KEY (catalog_version, brand_id) REFERENCES lastpay.car_brand(catalog_version, id) ON DELETE RESTRICT`.
- `car_model_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `car_model_pkey`: `PRIMARY KEY (catalog_version, id)`.
- `car_model_catalog_version_source_external_id_key`: `UNIQUE (catalog_version, source, external_id)`.

Индексы:

- `CREATE UNIQUE INDEX car_model_catalog_version_source_external_id_key ON lastpay.car_model USING btree (catalog_version, source, external_id)`.
- `CREATE UNIQUE INDEX car_model_pkey ON lastpay.car_model USING btree (catalog_version, id)`.
- `CREATE INDEX ix_car_model_0 ON lastpay.car_model USING btree (catalog_version, brand_id, id)`.

## lastpay.car_specification

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| model_id | character varying(64) | True | — |
| mass_kg | numeric(8,2) | False | — |
| power_kw | numeric(8,2) | False | — |
| max_speed_kmh | numeric(6,2) | False | — |
| tank_liters | numeric(6,2) | False | — |
| consumption_l_per_100km | numeric(6,2) | False | — |

Ограничения:

- `car_specification_consumption_l_per_100km_check`: `CHECK ((consumption_l_per_100km >= (0)::numeric))`.
- `car_specification_mass_kg_check`: `CHECK ((mass_kg > (0)::numeric))`.
- `car_specification_max_speed_kmh_check`: `CHECK ((max_speed_kmh > (0)::numeric))`.
- `car_specification_power_kw_check`: `CHECK ((power_kw > (0)::numeric))`.
- `car_specification_tank_liters_check`: `CHECK ((tank_liters > (0)::numeric))`.
- `car_specification_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `car_specification_catalog_version_model_id_fkey`: `FOREIGN KEY (catalog_version, model_id) REFERENCES lastpay.car_model(catalog_version, id) ON DELETE RESTRICT`.
- `car_specification_pkey`: `PRIMARY KEY (catalog_version, model_id)`.

Индексы:

- `CREATE UNIQUE INDEX car_specification_pkey ON lastpay.car_specification USING btree (catalog_version, model_id)`.

## lastpay.catalog_version

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| version | character varying(32) | True | — |
| status | character varying(12) | True | — |
| revision | bigint | True | — |
| created_at | timestamp with time zone | True | — |
| published_at | timestamp with time zone | False | — |
| content_sha256 | character(64) | False | — |
| source_metadata | jsonb | True | — |

Ограничения:

- `catalog_version_check`: `CHECK (((((status)::text = 'DRAFT'::text) AND (published_at IS NULL) AND (content_sha256 IS NULL)) OR (((status)::text = 'PUBLISHED'::text) AND (published_at IS NOT NULL) AND (content_sha256 IS NOT NULL) AND (content_sha256 ~ '^[a-f0-9]{64}$'::text))))`.
- `catalog_version_revision_check`: `CHECK ((revision > 0))`.
- `catalog_version_source_metadata_check`: `CHECK ((jsonb_typeof(source_metadata) = 'object'::text))`.
- `catalog_version_status_check`: `CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying])::text[])))`.
- `catalog_version_version_check`: `CHECK (((version)::text ~ '^[a-z0-9][a-z0-9._-]{0,31}$'::text))`.
- `catalog_version_pkey`: `PRIMARY KEY (version)`.

Индексы:

- `CREATE UNIQUE INDEX catalog_version_pkey ON lastpay.catalog_version USING btree (version)`.
- `CREATE INDEX ix_catalog_version_0 ON lastpay.catalog_version USING btree (status, published_at DESC, version)`.

## lastpay.inflation_observation

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| country_code | character(3) | True | — |
| year | smallint | True | — |
| indicator_code | character varying(32) | True | — |
| value_percent | numeric(12,6) | True | — |
| source | character varying(24) | True | — |
| fetched_at | timestamp with time zone | True | — |

Ограничения:

- `inflation_observation_country_code_check`: `CHECK ((country_code ~ '^[A-Z]{3}$'::text))`.
- `inflation_observation_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `inflation_observation_indicator_code_check`: `CHECK (((indicator_code)::text = 'FP.CPI.TOTL.ZG'::text))`.
- `inflation_observation_source_check`: `CHECK (((source)::text = ANY ((ARRAY['WORLD_BANK'::character varying, 'DEMO'::character varying])::text[])))`.
- `inflation_observation_value_percent_check`: `CHECK ((value_percent > ('-100'::integer)::numeric))`.
- `inflation_observation_year_check`: `CHECK (((year >= 1900) AND (year <= 2100)))`.
- `inflation_observation_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `inflation_observation_pkey`: `PRIMARY KEY (catalog_version, id)`.
- `inflation_observation_catalog_version_country_code_indicato_key`: `UNIQUE (catalog_version, country_code, indicator_code, year)`.

Индексы:

- `CREATE UNIQUE INDEX inflation_observation_catalog_version_country_code_indicato_key ON lastpay.inflation_observation USING btree (catalog_version, country_code, indicator_code, year)`.
- `CREATE UNIQUE INDEX inflation_observation_pkey ON lastpay.inflation_observation USING btree (catalog_version, id)`.

## lastpay.job_definition

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| occupation_id | character varying(64) | False | — |
| required_skill_id | character varying(64) | True | — |
| title | character varying(160) | True | — |
| required_level | smallint | True | — |
| duration_hours | numeric(5,2) | True | — |
| base_pay_minor | bigint | True | — |
| bonus_cap_minor | bigint | True | — |
| task_plan | integer | True | — |
| extra_energy_per_hour | numeric(5,2) | True | — |
| mechanic | character varying(16) | True | — |

Ограничения:

- `job_definition_base_pay_minor_check`: `CHECK (((base_pay_minor >= 0) AND (base_pay_minor <= '9007199254740991'::bigint)))`.
- `job_definition_bonus_cap_minor_check`: `CHECK (((bonus_cap_minor >= 0) AND (bonus_cap_minor <= '9007199254740991'::bigint)))`.
- `job_definition_duration_hours_check`: `CHECK (((duration_hours > (0)::numeric) AND (duration_hours <= (24)::numeric)))`.
- `job_definition_extra_energy_per_hour_check`: `CHECK (((extra_energy_per_hour >= (0)::numeric) AND (extra_energy_per_hour <= (100)::numeric)))`.
- `job_definition_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `job_definition_mechanic_check`: `CHECK (((mechanic)::text = 'WAREHOUSE'::text))`.
- `job_definition_required_level_check`: `CHECK (((required_level >= 0) AND (required_level <= 100)))`.
- `job_definition_task_plan_check`: `CHECK ((task_plan > 0))`.
- `job_definition_title_check`: `CHECK ((length(TRIM(BOTH FROM title)) > 0))`.
- `job_definition_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `job_definition_catalog_version_occupation_id_fkey`: `FOREIGN KEY (catalog_version, occupation_id) REFERENCES lastpay.occupation(catalog_version, id) ON DELETE RESTRICT`.
- `job_definition_catalog_version_required_skill_id_fkey`: `FOREIGN KEY (catalog_version, required_skill_id) REFERENCES lastpay.skill(catalog_version, id) ON DELETE RESTRICT`.
- `job_definition_pkey`: `PRIMARY KEY (catalog_version, id)`.

Индексы:

- `CREATE INDEX ix_job_definition_0 ON lastpay.job_definition USING btree (catalog_version, occupation_id)`.
- `CREATE INDEX ix_job_definition_1 ON lastpay.job_definition USING btree (catalog_version, required_skill_id)`.
- `CREATE UNIQUE INDEX job_definition_pkey ON lastpay.job_definition USING btree (catalog_version, id)`.

## lastpay.occupation

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| source | character varying(24) | True | — |
| external_id | character varying(64) | False | — |
| title | character varying(160) | True | — |
| description | text | True | — |

Ограничения:

- `occupation_check`: `CHECK ((((source)::text = 'DEMO'::text) OR ((length(TRIM(BOTH FROM external_id)) > 0) AND (external_id IS NOT NULL))))`.
- `occupation_description_check`: `CHECK ((length(description) <= 4000))`.
- `occupation_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `occupation_source_check`: `CHECK (((source)::text = ANY ((ARRAY['ONET'::character varying, 'DEMO'::character varying])::text[])))`.
- `occupation_title_check`: `CHECK ((length(TRIM(BOTH FROM title)) > 0))`.
- `occupation_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `occupation_pkey`: `PRIMARY KEY (catalog_version, id)`.
- `occupation_catalog_version_source_external_id_key`: `UNIQUE (catalog_version, source, external_id)`.

Индексы:

- `CREATE UNIQUE INDEX occupation_catalog_version_source_external_id_key ON lastpay.occupation USING btree (catalog_version, source, external_id)`.
- `CREATE UNIQUE INDEX occupation_pkey ON lastpay.occupation USING btree (catalog_version, id)`.

## lastpay.occupation_skill

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| occupation_id | character varying(64) | True | — |
| skill_id | character varying(64) | True | — |
| importance | numeric(5,2) | False | — |

Ограничения:

- `occupation_skill_importance_check`: `CHECK (((importance >= (0)::numeric) AND (importance <= (100)::numeric)))`.
- `occupation_skill_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `occupation_skill_catalog_version_occupation_id_fkey`: `FOREIGN KEY (catalog_version, occupation_id) REFERENCES lastpay.occupation(catalog_version, id) ON DELETE RESTRICT`.
- `occupation_skill_catalog_version_skill_id_fkey`: `FOREIGN KEY (catalog_version, skill_id) REFERENCES lastpay.skill(catalog_version, id) ON DELETE RESTRICT`.
- `occupation_skill_pkey`: `PRIMARY KEY (catalog_version, occupation_id, skill_id)`.

Индексы:

- `CREATE INDEX ix_occupation_skill_0 ON lastpay.occupation_skill USING btree (catalog_version, skill_id, occupation_id)`.
- `CREATE UNIQUE INDEX occupation_skill_pkey ON lastpay.occupation_skill USING btree (catalog_version, occupation_id, skill_id)`.

## lastpay.provider_cache

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| id | uuid | True | — |
| provider | character varying(24) | True | — |
| request_hash | character(64) | True | — |
| payload | jsonb | True | — |
| fetched_at | timestamp with time zone | True | — |
| expires_at | timestamp with time zone | True | — |
| etag | character varying(256) | False | — |
| catalog_version | character varying(32) | False | — |

Ограничения:

- `provider_cache_check`: `CHECK ((expires_at >= fetched_at))`.
- `provider_cache_payload_check`: `CHECK ((jsonb_typeof(payload) = ANY (ARRAY['object'::text, 'array'::text])))`.
- `provider_cache_payload_check1`: `CHECK ((octet_length((payload)::text) <= 2097152))`.
- `provider_cache_provider_check`: `CHECK (((provider)::text = ANY ((ARRAY['ONET'::character varying, 'WORLD_BANK'::character varying, 'CARS_BASE'::character varying])::text[])))`.
- `provider_cache_request_hash_check`: `CHECK ((request_hash ~ '^[a-f0-9]{64}$'::text))`.
- `provider_cache_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `provider_cache_pkey`: `PRIMARY KEY (id)`.
- `provider_cache_provider_request_hash_key`: `UNIQUE (provider, request_hash)`.

Индексы:

- `CREATE INDEX ix_provider_cache_0 ON lastpay.provider_cache USING btree (expires_at)`.
- `CREATE UNIQUE INDEX provider_cache_pkey ON lastpay.provider_cache USING btree (id)`.
- `CREATE UNIQUE INDEX provider_cache_provider_request_hash_key ON lastpay.provider_cache USING btree (provider, request_hash)`.

## lastpay.race_configuration

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| track_id | character varying(64) | True | — |
| title | character varying(160) | True | — |
| laps | smallint | True | — |
| time_limit_seconds | integer | True | — |
| entry_fee_minor | bigint | True | — |
| finish_reward_minor | bigint | True | — |
| required_car_condition | smallint | True | — |

Ограничения:

- `race_configuration_entry_fee_minor_check`: `CHECK (((entry_fee_minor >= 0) AND (entry_fee_minor <= '9007199254740991'::bigint)))`.
- `race_configuration_finish_reward_minor_check`: `CHECK (((finish_reward_minor >= 0) AND (finish_reward_minor <= '9007199254740991'::bigint)))`.
- `race_configuration_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `race_configuration_laps_check`: `CHECK (((laps >= 1) AND (laps <= 100)))`.
- `race_configuration_required_car_condition_check`: `CHECK (((required_car_condition >= 0) AND (required_car_condition <= 100)))`.
- `race_configuration_time_limit_seconds_check`: `CHECK ((time_limit_seconds > 0))`.
- `race_configuration_title_check`: `CHECK ((length(TRIM(BOTH FROM title)) > 0))`.
- `race_configuration_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `race_configuration_catalog_version_track_id_fkey`: `FOREIGN KEY (catalog_version, track_id) REFERENCES lastpay.race_track(catalog_version, id) ON DELETE RESTRICT`.
- `race_configuration_pkey`: `PRIMARY KEY (catalog_version, id)`.

Индексы:

- `CREATE INDEX ix_race_configuration_0 ON lastpay.race_configuration USING btree (catalog_version, track_id, id)`.
- `CREATE UNIQUE INDEX race_configuration_pkey ON lastpay.race_configuration USING btree (catalog_version, id)`.

## lastpay.race_track

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| title | character varying(160) | True | — |
| length_m | integer | True | — |
| asset_key | character varying(120) | True | — |
| asset_sha256 | character(64) | True | — |
| layout_revision | integer | True | — |

Ограничения:

- `race_track_asset_sha256_check`: `CHECK ((asset_sha256 ~ '^[a-f0-9]{64}$'::text))`.
- `race_track_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `race_track_layout_revision_check`: `CHECK ((layout_revision > 0))`.
- `race_track_length_m_check`: `CHECK ((length_m > 0))`.
- `race_track_title_check`: `CHECK ((length(TRIM(BOTH FROM title)) > 0))`.
- `race_track_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `race_track_pkey`: `PRIMARY KEY (catalog_version, id)`.
- `race_track_catalog_version_asset_key_key`: `UNIQUE (catalog_version, asset_key)`.

Индексы:

- `CREATE UNIQUE INDEX race_track_catalog_version_asset_key_key ON lastpay.race_track USING btree (catalog_version, asset_key)`.
- `CREATE UNIQUE INDEX race_track_pkey ON lastpay.race_track USING btree (catalog_version, id)`.

## lastpay.skill

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| catalog_version | character varying(32) | True | — |
| id | character varying(64) | True | — |
| code | character varying(64) | True | — |
| title | character varying(160) | True | — |
| description | text | True | — |
| source | character varying(24) | True | — |
| external_id | character varying(64) | False | — |

Ограничения:

- `skill_check`: `CHECK ((((source)::text = 'DEMO'::text) OR ((length(TRIM(BOTH FROM external_id)) > 0) AND (external_id IS NOT NULL))))`.
- `skill_code_check`: `CHECK (((code)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `skill_description_check`: `CHECK ((length(description) <= 4000))`.
- `skill_id_check`: `CHECK (((id)::text ~ '^[a-z0-9][a-z0-9._-]{0,63}$'::text))`.
- `skill_source_check`: `CHECK (((source)::text = ANY ((ARRAY['ONET'::character varying, 'DEMO'::character varying])::text[])))`.
- `skill_title_check`: `CHECK ((length(TRIM(BOTH FROM title)) > 0))`.
- `skill_catalog_version_fkey`: `FOREIGN KEY (catalog_version) REFERENCES lastpay.catalog_version(version) ON DELETE RESTRICT`.
- `skill_pkey`: `PRIMARY KEY (catalog_version, id)`.
- `skill_catalog_version_code_key`: `UNIQUE (catalog_version, code)`.
- `skill_catalog_version_source_external_id_key`: `UNIQUE (catalog_version, source, external_id)`.

Индексы:

- `CREATE UNIQUE INDEX skill_catalog_version_code_key ON lastpay.skill USING btree (catalog_version, code)`.
- `CREATE UNIQUE INDEX skill_catalog_version_source_external_id_key ON lastpay.skill USING btree (catalog_version, source, external_id)`.
- `CREATE UNIQUE INDEX skill_pkey ON lastpay.skill USING btree (catalog_version, id)`.

## lastpay.user_save

RLS: True; FORCE RLS: True. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| user_id | uuid | True | auth.uid() |
| snapshot | jsonb | True | — |
| revision | bigint | True | — |
| updated_at | timestamp with time zone | True | now() |

Ограничения:

- `user_save_revision_check`: `CHECK (((revision >= 1) AND (revision <= '9007199254740991'::bigint)))`.
- `user_save_snapshot_check`: `CHECK (((jsonb_typeof(snapshot) = 'object'::text) AND (octet_length((snapshot)::text) <= 262144)))`.
- `user_save_user_id_fkey`: `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`.
- `user_save_pkey`: `PRIMARY KEY (user_id)`.

Индексы:

- `CREATE UNIQUE INDEX user_save_pkey ON lastpay.user_save USING btree (user_id)`.

## lastpay_private.admin_membership

RLS: True; FORCE RLS: False. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| user_id | uuid | True | — |
| created_at | timestamp with time zone | True | now() |

Ограничения:

- `admin_membership_user_id_fkey`: `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`.
- `admin_membership_pkey`: `PRIMARY KEY (user_id)`.

Индексы:

- `CREATE UNIQUE INDEX admin_membership_pkey ON lastpay_private.admin_membership USING btree (user_id)`.

## lastpay_private.iso_country

RLS: True; FORCE RLS: False. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| code | text | True | — |

Ограничения:

- `iso_country_pkey`: `PRIMARY KEY (code)`.

Индексы:

- `CREATE UNIQUE INDEX iso_country_pkey ON lastpay_private.iso_country USING btree (code)`.

## lastpay_private.provider_lease

RLS: True; FORCE RLS: False. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| provider | text | True | — |
| request_hash | text | True | — |
| owner | uuid | True | — |
| expires_at | timestamp with time zone | True | — |

Ограничения:

- `provider_lease_pkey`: `PRIMARY KEY (provider, request_hash)`.

Индексы:

- `CREATE UNIQUE INDEX provider_lease_pkey ON lastpay_private.provider_lease USING btree (provider, request_hash)`.

## lastpay_private.rate_window

RLS: True; FORCE RLS: False. Private-служебные таблицы дополнительно закрыты отсутствием schema/table GRANT у клиентских ролей.

| Поле | Тип | NOT NULL | Default |
|---|---|---|---|
| bucket | text | True | — |
| subject | text | True | — |
| minute | bigint | True | — |
| hits | integer | True | — |

Ограничения:

- `rate_window_pkey`: `PRIMARY KEY (bucket, subject)`.

Индексы:

- `CREATE UNIQUE INDEX rate_window_pkey ON lastpay_private.rate_window USING btree (bucket, subject)`.

## Представление API

lastpay_api.user_save использует security_invoker=true; читает только данные владельца через RLS. Прямая запись клиенту запрещена. Для записи применяется save_game с auth.uid() и expectedRevision.
