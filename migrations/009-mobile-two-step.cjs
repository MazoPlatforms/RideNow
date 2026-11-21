exports.up = (pgm) => pgm.sql(`
ALTER TABLE users ADD COLUMN mobile_totp_secret text,
  ADD COLUMN mobile_totp_counter bigint NOT NULL DEFAULT -1,
  ADD COLUMN mobile_totp_pending jsonb,
  ADD COLUMN mobile_recovery_hashes text[] NOT NULL DEFAULT '{}';
ALTER TABLE otp_challenges DROP CONSTRAINT otp_challenge_scope,
  ADD CONSTRAINT otp_challenge_scope CHECK (
    (purpose='LOGIN' AND owner_id IS NULL AND change_id IS NULL)
    OR (purpose IN ('PHONE_CURRENT','PHONE_NEW','TWO_STEP_SETUP') AND owner_id IS NOT NULL AND change_id IS NOT NULL)
  );
CREATE TABLE mobile_login_steps (
  token_hash text PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  phone text NOT NULL,
  device jsonb,
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
  expires_at timestamptz NOT NULL
);
CREATE INDEX mobile_login_steps_expiry ON mobile_login_steps(expires_at);
CREATE INDEX mobile_login_steps_user ON mobile_login_steps(user_id);
`);
exports.down = false;
