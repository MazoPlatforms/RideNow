exports.up = (pgm) => pgm.sql(`
ALTER TABLE users ADD COLUMN rider_gender text CHECK (rider_gender IN ('FEMALE','MALE','NON_BINARY','UNDISCLOSED')),
  ADD COLUMN rider_birth_date date,
  ADD COLUMN rider_city text CHECK (length(rider_city) <= 100);
ALTER TABLE driver_profiles ADD COLUMN contact_encrypted text,
  ADD COLUMN profile_photo_key text, ADD COLUMN profile_photo_version text;
ALTER TABLE profile_photo_uploads ADD COLUMN purpose text NOT NULL DEFAULT 'RIDER'
  CHECK (purpose IN ('RIDER','DRIVER'));
ALTER TABLE otp_challenges ADD COLUMN purpose text NOT NULL DEFAULT 'LOGIN',
  ADD COLUMN owner_id uuid REFERENCES users(id) ON DELETE CASCADE,
  ADD COLUMN change_id uuid,
  ADD CONSTRAINT otp_challenge_scope CHECK (
    (purpose='LOGIN' AND owner_id IS NULL AND change_id IS NULL)
    OR (purpose IN ('PHONE_CURRENT','PHONE_NEW') AND owner_id IS NOT NULL AND change_id IS NOT NULL)
  );
CREATE UNIQUE INDEX otp_change_scope ON otp_challenges(change_id,purpose) WHERE change_id IS NOT NULL;
`);

exports.down = false;
