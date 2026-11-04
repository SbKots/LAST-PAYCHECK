-- Defense in depth. The application also locks the version row before any mutation.
CREATE FUNCTION guard_published_catalog() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_status text;
BEGIN
 IF TG_TABLE_NAME='catalog_version' THEN
  IF OLD.status='PUBLISHED' THEN RAISE EXCEPTION 'Published catalog is immutable' USING ERRCODE='23514'; END IF;
 ELSE
  IF TG_OP<>'INSERT' THEN
   SELECT status INTO v_status FROM catalog_version WHERE version=OLD.catalog_version FOR SHARE;
   IF v_status='PUBLISHED' THEN RAISE EXCEPTION 'Published catalog is immutable' USING ERRCODE='23514'; END IF;
  END IF;
  IF TG_OP<>'DELETE' THEN
   SELECT status INTO v_status FROM catalog_version WHERE version=NEW.catalog_version FOR SHARE;
   IF v_status='PUBLISHED' THEN RAISE EXCEPTION 'Published catalog is immutable' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;
CREATE TRIGGER immutable_catalog BEFORE UPDATE OR DELETE ON catalog_version FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_occupation BEFORE INSERT OR UPDATE OR DELETE ON occupation FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_skill BEFORE INSERT OR UPDATE OR DELETE ON skill FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_occupation_skill BEFORE INSERT OR UPDATE OR DELETE ON occupation_skill FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_job_definition BEFORE INSERT OR UPDATE OR DELETE ON job_definition FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_inflation_observation BEFORE INSERT OR UPDATE OR DELETE ON inflation_observation FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_car_brand BEFORE INSERT OR UPDATE OR DELETE ON car_brand FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_car_model BEFORE INSERT OR UPDATE OR DELETE ON car_model FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_car_specification BEFORE INSERT OR UPDATE OR DELETE ON car_specification FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_car_balance BEFORE INSERT OR UPDATE OR DELETE ON car_balance FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_race_track BEFORE INSERT OR UPDATE OR DELETE ON race_track FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
CREATE TRIGGER immutable_race_configuration BEFORE INSERT OR UPDATE OR DELETE ON race_configuration FOR EACH ROW EXECUTE FUNCTION guard_published_catalog();
