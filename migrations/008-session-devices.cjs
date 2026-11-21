exports.up = (pgm) => pgm.sql(`
ALTER TABLE sessions ADD COLUMN device_key text, ADD COLUMN device_name text, ADD COLUMN device_app text,
  ADD CONSTRAINT session_device_metadata CHECK (
    (device_key IS NULL AND device_name IS NULL AND device_app IS NULL)
    OR (device_key IS NOT NULL AND device_name IS NOT NULL AND device_app IN ('RIDER','DRIVER') AND device_app IS NOT NULL)
  );
CREATE INDEX sessions_active_devices ON sessions(user_id,family_id,created_at DESC)
  WHERE revoked_at IS NULL;
CREATE INDEX sessions_active_installations ON sessions(user_id,device_app,device_key)
  WHERE revoked_at IS NULL AND device_key IS NOT NULL;
`);
exports.down = false;
