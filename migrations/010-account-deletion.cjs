exports.up = (pgm) => pgm.sql(`
ALTER TABLE otp_challenges ADD COLUMN session_family_id uuid,
  DROP CONSTRAINT otp_challenge_scope,
  ADD CONSTRAINT otp_challenge_scope CHECK (
    (purpose='LOGIN' AND owner_id IS NULL AND change_id IS NULL AND session_family_id IS NULL)
    OR (purpose IN ('PHONE_CURRENT','PHONE_NEW','TWO_STEP_SETUP') AND owner_id IS NOT NULL
      AND change_id IS NOT NULL AND session_family_id IS NULL)
    OR (purpose='ACCOUNT_DELETION' AND owner_id IS NOT NULL AND change_id IS NOT NULL AND session_family_id IS NOT NULL)
  );
`);
exports.down = false;
