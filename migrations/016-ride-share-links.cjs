exports.up = (pgm) => pgm.sql(`
CREATE TABLE ride_share_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id uuid NOT NULL REFERENCES rides ON DELETE CASCADE,
  created_by uuid NOT NULL REFERENCES users ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ride_share_links_ride_active ON ride_share_links(ride_id,expires_at)
  WHERE revoked_at IS NULL;
CREATE INDEX ride_share_links_creator ON ride_share_links(created_by);
`);

exports.down = () => {
  throw new Error('Destructive rollback disabled. Restore backup or apply a forward migration.');
};
