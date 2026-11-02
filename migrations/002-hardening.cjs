exports.up = (pgm) =>
  pgm.sql(`
ALTER TABLE fare_quotes ADD COLUMN consumed_at timestamptz;
UPDATE fare_quotes q SET consumed_at=r.created_at FROM rides r WHERE r.quote_id=q.id;
ALTER TABLE documents ADD COLUMN object_version text;
ALTER TABLE users ADD COLUMN totp_counter bigint NOT NULL DEFAULT -1;
CREATE TABLE surge_rules (
 area_id uuid REFERENCES service_areas,category text CHECK(category IN ('TUK','CAR','VAN')),enabled boolean NOT NULL DEFAULT true,
 maximum numeric NOT NULL DEFAULT 2 CHECK(maximum BETWEEN 1 AND 2),PRIMARY KEY(area_id,category)
);
CREATE TABLE cancellation_charges (
 ride_id uuid PRIMARY KEY REFERENCES rides,rider_id uuid NOT NULL REFERENCES users,amount_minor int NOT NULL CHECK(amount_minor>0),
 status text NOT NULL DEFAULT 'DUE' CHECK(status IN ('DUE','SETTLED')),reference text UNIQUE,settled_by uuid REFERENCES users,created_at timestamptz DEFAULT now()
);
CREATE TABLE payment_disputes (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),ride_id uuid NOT NULL REFERENCES rides,reason text NOT NULL,opened_by uuid NOT NULL REFERENCES users,
 status text NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED')),resolution text,created_at timestamptz DEFAULT now(),resolved_at timestamptz
);
CREATE UNIQUE INDEX one_open_dispute ON payment_disputes(ride_id) WHERE status='OPEN';
CREATE TABLE reconciliations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),reference text UNIQUE NOT NULL,driver_id uuid REFERENCES driver_profiles(user_id),
 ride_id uuid REFERENCES rides,kind text NOT NULL,amount_minor int NOT NULL CHECK(amount_minor>=0),actor_id uuid NOT NULL REFERENCES users,reason text NOT NULL,created_at timestamptz DEFAULT now()
);
CREATE FUNCTION immutable_fare() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Publish a new fare plan version'; END $$;
CREATE TRIGGER fare_versions_immutable BEFORE UPDATE OR DELETE ON fare_plans FOR EACH ROW EXECUTE FUNCTION immutable_fare();
DO $$ DECLARE period date; label text; BEGIN
 FOR i IN -1..2 LOOP
  period:=date_trunc('month',now())::date + (i||' months')::interval;
  label:='ride_location_points_'||to_char(period,'YYYY_MM');
  EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF ride_location_points FOR VALUES FROM (%L) TO (%L)',label,period,period+interval '1 month');
 END LOOP;
END $$;
`);
exports.down = () => {
  throw new Error('Forward migrations only');
};
