exports.up = (pgm) => pgm.sql(`
CREATE TABLE saved_addresses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind text NOT NULL CHECK (kind IN ('HOME','WORK','OTHER')),
  details_encrypted text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX saved_address_slots ON saved_addresses(user_id,kind) WHERE kind IN ('HOME','WORK');
CREATE INDEX saved_addresses_owner ON saved_addresses(user_id,updated_at DESC);
CREATE TABLE trusted_contacts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  phone_key text NOT NULL,
  details_encrypted text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id,phone_key)
);
CREATE TABLE notification_preferences (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  app text NOT NULL CHECK (app IN ('RIDER','DRIVER')),
  preferences jsonb NOT NULL DEFAULT '{}',
  inbox_cleared_at timestamptz,
  PRIMARY KEY(user_id,app)
);
ALTER TABLE device_tokens ADD COLUMN device_app text CHECK (device_app IN ('RIDER','DRIVER'));
`);
exports.down = false;
