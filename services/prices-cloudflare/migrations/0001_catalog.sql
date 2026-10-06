CREATE TABLE sources(
  id TEXT PRIMARY KEY,
  status TEXT NOT NULL CHECK(status IN ('candidate','active','suspended','unsupported')),
  priority INTEGER NOT NULL CHECK(priority>0),
  metadata TEXT NOT NULL CHECK(json_valid(metadata)),
  current_batch TEXT,
  last_successful_at TEXT,
  digest TEXT,
  CHECK(status!='active' OR json_extract(metadata,'$.reuse_verified')=1)
);
-- statement boundary
CREATE TABLE offer_batches(
  id TEXT PRIMARY KEY,
  source TEXT NOT NULL REFERENCES sources(id),
  digest TEXT NOT NULL,
  expected_count INTEGER NOT NULL CHECK(expected_count BETWEEN 1 AND 2000),
  status TEXT NOT NULL CHECK(status IN ('staging','published')),
  created_at TEXT NOT NULL,
  observed_at TEXT NOT NULL,
  published_at TEXT,
  UNIQUE(source,digest)
);
-- statement boundary
CREATE TABLE offers(
  batch_id TEXT NOT NULL REFERENCES offer_batches(id) ON DELETE CASCADE,
  source TEXT NOT NULL REFERENCES sources(id),
  sku TEXT NOT NULL,
  scope TEXT NOT NULL CHECK(scope IN ('generic','postcode')),
  postcode TEXT NOT NULL,
  offer_id TEXT NOT NULL,
  product_id TEXT NOT NULL,
  gtin TEXT,
  name_key TEXT NOT NULL,
  payload TEXT NOT NULL CHECK(json_valid(payload)),
  PRIMARY KEY(batch_id,offer_id),
  UNIQUE(batch_id,source,sku,scope,postcode),
  CHECK((scope='generic' AND postcode='') OR (scope='postcode' AND length(postcode)=5 AND postcode NOT GLOB '*[^0-9]*'))
);
-- statement boundary
CREATE INDEX offers_product ON offers(product_id,batch_id);
-- statement boundary
CREATE INDEX offers_gtin ON offers(gtin,batch_id);
-- statement boundary
CREATE TABLE catalog(id INTEGER PRIMARY KEY CHECK(id=1),version INTEGER NOT NULL CHECK(version BETWEEN 0 AND 9007199254740991));
-- statement boundary
INSERT INTO catalog VALUES(1,0);
-- statement boundary
CREATE TABLE runs(source TEXT NOT NULL REFERENCES sources(id),day TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('running','success','failed')),checked_at TEXT NOT NULL,PRIMARY KEY(source,day));
-- statement boundary
CREATE TRIGGER offer_source BEFORE INSERT ON offers
BEGIN
  SELECT (CASE WHEN NOT EXISTS(SELECT 1 FROM offer_batches WHERE id=NEW.batch_id AND source=NEW.source AND status='staging')
    THEN RAISE(ABORT,'offer batch unavailable') END);
END;
-- statement boundary
CREATE TRIGGER immutable_offer BEFORE UPDATE ON offers
BEGIN SELECT RAISE(ABORT,'immutable offer'); END;
-- statement boundary
CREATE TRIGGER guard_publication BEFORE UPDATE OF current_batch ON sources
WHEN NEW.current_batch IS NOT OLD.current_batch
BEGIN
  SELECT (CASE WHEN NEW.status!='active' OR NOT EXISTS(
    SELECT 1 FROM offer_batches b WHERE b.id=NEW.current_batch AND b.source=NEW.id
      AND b.digest=NEW.digest AND b.observed_at=NEW.last_successful_at
      AND b.expected_count=(SELECT COUNT(*) FROM offers WHERE batch_id=b.id)
  ) THEN RAISE(ABORT,'batch incomplete or source inactive') END);
  SELECT (CASE WHEN (SELECT COUNT(*) FROM offers o JOIN sources s ON o.batch_id=s.current_batch WHERE s.id!=NEW.id)
    +(SELECT COUNT(*) FROM offers WHERE batch_id=NEW.current_batch)>2000
    THEN RAISE(ABORT,'catalog limit') END);
END;
-- statement boundary
CREATE TRIGGER publish_version AFTER UPDATE OF current_batch ON sources
WHEN NEW.current_batch IS NOT OLD.current_batch
BEGIN
  UPDATE offer_batches SET status='published',published_at=NEW.last_successful_at WHERE id=NEW.current_batch;
  UPDATE catalog SET version=version+1 WHERE id=1;
END;
-- statement boundary
CREATE TRIGGER source_version AFTER UPDATE OF status ON sources
WHEN NEW.status!=OLD.status
BEGIN UPDATE catalog SET version=version+1 WHERE id=1; END;
-- statement boundary
CREATE TRIGGER protect_current BEFORE DELETE ON offer_batches
WHEN EXISTS(SELECT 1 FROM sources WHERE current_batch=OLD.id)
BEGIN SELECT RAISE(ABORT,'current batch'); END;
