exports.up = (pgm) => pgm.sql(`
ALTER TABLE users ADD COLUMN profile_completed_at timestamptz;
UPDATE users SET profile_completed_at=created_at WHERE btrim(name) <> '';
`);

exports.down = () => {
  throw new Error('Forward migrations only');
};
