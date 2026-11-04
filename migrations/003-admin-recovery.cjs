exports.up = (pgm) => pgm.sql(`
ALTER TABLE users ADD COLUMN recovery_password_hash text;
ALTER TABLE users ADD COLUMN recovery_attempts integer NOT NULL DEFAULT 0 CHECK(recovery_attempts BETWEEN 0 AND 5);
ALTER TABLE users ADD COLUMN recovery_locked_until timestamptz;
`);
exports.down = () => { throw new Error('Forward migrations only'); };
