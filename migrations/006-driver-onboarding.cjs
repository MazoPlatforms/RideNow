exports.up = (pgm) => pgm.sql(`
ALTER TABLE driver_profiles
  ADD COLUMN application_encrypted text,
  ADD COLUMN application_revision uuid,
  ADD COLUMN application_vehicle_id uuid REFERENCES vehicles(id),
  ADD COLUMN application_document_ids uuid[],
  ADD COLUMN submitted_at timestamptz;
ALTER TABLE vehicles DROP CONSTRAINT vehicles_category_check,
  DROP CONSTRAINT vehicles_registration_key,
  ADD CONSTRAINT vehicles_driver_registration_key UNIQUE(driver_id, registration),
  ADD CONSTRAINT vehicles_category_check CHECK (category IN ('TUK','CAR','VAN','BIKE')),
  ADD COLUMN make text, ADD COLUMN model text, ADD COLUMN model_year smallint, ADD COLUMN color text;
CREATE UNIQUE INDEX vehicle_approved_registration ON vehicles (
  (regexp_replace(upper(normalize(registration, NFKC)), '[^[:alnum:]]', '', 'g'))
) WHERE status='APPROVED';
ALTER TABLE documents DROP CONSTRAINT documents_kind_check,
  DROP CONSTRAINT documents_scan_status_check,
  ADD CONSTRAINT documents_scan_status_check CHECK (scan_status IN ('PENDING','CLEAN','INFECTED','FAILED')),
  ALTER COLUMN expires_at DROP NOT NULL,
  ADD COLUMN submitted_at timestamptz,
  ADD CONSTRAINT documents_kind_check CHECK (kind IN (
    'IDENTITY','LICENSE','REGISTRATION','INSURANCE','REVENUE_LICENSE','EMISSION',
    'PROFILE_PHOTO','IDENTITY_FRONT','IDENTITY_BACK','LICENSE_FRONT','LICENSE_BACK',
    'VEHICLE_FRONT','VEHICLE_REAR','VEHICLE_LEFT','VEHICLE_RIGHT','VEHICLE_INTERIOR','VEHICLE_EXTRA'
  ));
CREATE INDEX driver_document_lookup ON documents(driver_id, created_at);
`);
exports.down = false;
