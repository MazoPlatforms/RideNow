exports.up = (pgm) =>
  pgm.sql(`
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE users (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), phone text UNIQUE NOT NULL, name text NOT NULL DEFAULT '',
 roles text[] NOT NULL DEFAULT '{RIDER}', permissions text[] NOT NULL DEFAULT '{}', suspended boolean NOT NULL DEFAULT false,
 password_hash text, totp_secret text, created_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz,
 CHECK (roles <@ ARRAY['RIDER','DRIVER','STAFF','ADMIN']::text[])
);
CREATE TABLE sessions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),user_id uuid NOT NULL REFERENCES users, family_id uuid NOT NULL DEFAULT gen_random_uuid(),
 token_hash text UNIQUE NOT NULL,expires_at timestamptz NOT NULL,revoked_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sessions_user ON sessions(user_id);
CREATE TABLE otp_challenges (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),phone text NOT NULL,code_hash text NOT NULL,attempts int NOT NULL DEFAULT 0,
 expires_at timestamptz NOT NULL,used_at timestamptz,created_at timestamptz NOT NULL DEFAULT now(),CHECK(attempts BETWEEN 0 AND 5)
);
CREATE INDEX otp_phone_created ON otp_challenges(phone,created_at DESC);
CREATE TABLE device_tokens (user_id uuid REFERENCES users,token text PRIMARY KEY,created_at timestamptz DEFAULT now());
CREATE TABLE driver_profiles (
 user_id uuid PRIMARY KEY REFERENCES users,status text NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING','APPROVED','REJECTED','SUSPENDED')),
 availability text NOT NULL DEFAULT 'OFFLINE' CHECK(availability IN ('OFFLINE','AVAILABLE','RESERVED','ON_TRIP')),
 idle_since timestamptz NOT NULL DEFAULT now(),review_reason text,created_at timestamptz DEFAULT now()
);
CREATE TABLE vehicles (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),driver_id uuid NOT NULL REFERENCES driver_profiles(user_id),registration text UNIQUE NOT NULL,
 category text NOT NULL CHECK(category IN ('TUK','CAR','VAN')),capacity int NOT NULL CHECK(capacity BETWEEN 1 AND 15),
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),created_at timestamptz DEFAULT now()
);
CREATE UNIQUE INDEX one_approved_vehicle ON vehicles(driver_id) WHERE status='APPROVED';
CREATE TABLE documents (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),driver_id uuid NOT NULL REFERENCES driver_profiles(user_id),vehicle_id uuid REFERENCES vehicles,
 kind text NOT NULL CHECK(kind IN ('IDENTITY','LICENSE','REGISTRATION','INSURANCE')),object_key text UNIQUE NOT NULL,
 mime text NOT NULL CHECK(mime IN ('image/jpeg','image/png','application/pdf')),bytes int NOT NULL CHECK(bytes BETWEEN 1 AND 10485760),
 expires_at timestamptz NOT NULL,scan_status text NOT NULL DEFAULT 'PENDING' CHECK(scan_status IN ('PENDING','CLEAN','INFECTED')),
 created_at timestamptz DEFAULT now()
);
CREATE TABLE service_areas (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),name text NOT NULL,polygon geometry(Polygon,4326) NOT NULL,
 active boolean NOT NULL DEFAULT false,created_at timestamptz DEFAULT now(),CHECK(ST_IsValid(polygon))
);
CREATE INDEX areas_geo ON service_areas USING gist(polygon);
CREATE TABLE fare_plans (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),area_id uuid NOT NULL REFERENCES service_areas,category text NOT NULL CHECK(category IN ('TUK','CAR','VAN')),
 version int NOT NULL,base_minor int NOT NULL CHECK(base_minor>=0),per_km_minor int NOT NULL CHECK(per_km_minor>=0),
 per_minute_minor int NOT NULL CHECK(per_minute_minor>=0),waiting_minor int NOT NULL CHECK(waiting_minor>=0),
 minimum_minor int NOT NULL CHECK(minimum_minor>=0),commission_bps int NOT NULL CHECK(commission_bps BETWEEN 0 AND 10000),
 cancellation_minor int NOT NULL DEFAULT 0 CHECK(cancellation_minor>=0),created_at timestamptz DEFAULT now(),UNIQUE(area_id,category,version)
);
CREATE TABLE surge_state (area_id uuid REFERENCES service_areas, category text NOT NULL,multiplier numeric NOT NULL DEFAULT 1 CHECK(multiplier BETWEEN 1 AND 2),updated_at timestamptz DEFAULT now(),PRIMARY KEY(area_id,category));
CREATE TABLE driver_locations (
 driver_id uuid PRIMARY KEY REFERENCES driver_profiles(user_id),point geography(Point,4326) NOT NULL,
 accuracy real NOT NULL,sequence bigint NOT NULL,recorded_at timestamptz NOT NULL,received_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX driver_geo ON driver_locations USING gist(point);
CREATE TABLE fare_quotes (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),rider_id uuid NOT NULL REFERENCES users,area_id uuid NOT NULL REFERENCES service_areas,
 category text NOT NULL CHECK(category IN ('TUK','CAR','VAN')),trip_type text NOT NULL CHECK(trip_type IN ('ONE_WAY','RETURN')),
 waypoints jsonb NOT NULL,route jsonb NOT NULL,fare_snapshot jsonb NOT NULL,estimated_minor int NOT NULL CHECK(estimated_minor>=0),
 scheduled_at timestamptz,expires_at timestamptz NOT NULL,created_at timestamptz DEFAULT now()
);
CREATE TABLE rides (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),rider_id uuid NOT NULL REFERENCES users,quote_id uuid NOT NULL UNIQUE REFERENCES fare_quotes,
 driver_id uuid REFERENCES driver_profiles(user_id),vehicle_id uuid REFERENCES vehicles,
 state text NOT NULL CHECK(state IN ('SCHEDULED','SEARCHING','DRIVER_ASSIGNED','DRIVER_ARRIVING','DRIVER_ARRIVED','IN_PROGRESS','AT_STOP','WAITING','RETURNING','COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW')),
 version int NOT NULL DEFAULT 1,scheduled_at timestamptz,search_started_at timestamptz,search_deadline timestamptz,
 pin_hash text NOT NULL,pin_encrypted text NOT NULL,pin_attempts int NOT NULL DEFAULT 0,arrived_at timestamptz,started_at timestamptz,
 stop_at timestamptz,return_at timestamptz,completed_at timestamptz,cancel_reason text,final_minor int CHECK(final_minor>=0),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX rider_active ON rides(rider_id) WHERE state NOT IN ('SCHEDULED','COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW');
CREATE UNIQUE INDEX driver_active ON rides(driver_id) WHERE state NOT IN ('COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW');
CREATE INDEX rides_due ON rides(scheduled_at) WHERE state='SCHEDULED';
CREATE TABLE dispatch_offers (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),ride_id uuid NOT NULL REFERENCES rides,driver_id uuid NOT NULL REFERENCES driver_profiles(user_id),
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','CANCELLED')),
 expires_at timestamptz NOT NULL,created_at timestamptz DEFAULT now(),UNIQUE(ride_id,driver_id)
);
CREATE UNIQUE INDEX one_pending_offer_ride ON dispatch_offers(ride_id) WHERE status='PENDING';
CREATE UNIQUE INDEX one_pending_offer_driver ON dispatch_offers(driver_id) WHERE status='PENDING';
CREATE TABLE ride_events (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,ride_id uuid NOT NULL REFERENCES rides,version int NOT NULL,
 actor_id uuid REFERENCES users,state text NOT NULL,details jsonb NOT NULL DEFAULT '{}',created_at timestamptz DEFAULT now(),UNIQUE(ride_id,version)
);
CREATE TABLE ride_location_points (
 ride_id uuid NOT NULL REFERENCES rides,driver_id uuid NOT NULL REFERENCES users,point geography(Point,4326) NOT NULL,
 recorded_at timestamptz NOT NULL,sequence bigint NOT NULL,PRIMARY KEY(ride_id,recorded_at,sequence)
) PARTITION BY RANGE(recorded_at);
CREATE TABLE ride_location_points_default PARTITION OF ride_location_points DEFAULT;
CREATE TABLE payments (
 ride_id uuid PRIMARY KEY REFERENCES rides,amount_minor int NOT NULL CHECK(amount_minor>=0),status text NOT NULL DEFAULT 'DUE' CHECK(status IN ('DUE','COLLECTED','DISPUTED')),
 collected_at timestamptz,collected_by uuid REFERENCES users
);
CREATE TABLE ledger_accounts (id uuid PRIMARY KEY DEFAULT gen_random_uuid(),code text NOT NULL,user_id uuid REFERENCES users,currency text NOT NULL DEFAULT 'LKR' CHECK(currency='LKR'));
CREATE UNIQUE INDEX ledger_account_identity ON ledger_accounts(code,COALESCE(user_id,'00000000-0000-0000-0000-000000000000'::uuid));
CREATE TABLE ledger_transactions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),reference text UNIQUE NOT NULL,kind text NOT NULL,ride_id uuid REFERENCES rides,
 reversal_of uuid UNIQUE REFERENCES ledger_transactions,created_at timestamptz DEFAULT now()
);
CREATE TABLE ledger_entries (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,transaction_id uuid NOT NULL REFERENCES ledger_transactions,account_id uuid NOT NULL REFERENCES ledger_accounts,
 debit bigint NOT NULL DEFAULT 0 CHECK(debit>=0),credit bigint NOT NULL DEFAULT 0 CHECK(credit>=0),CHECK((debit=0)<>(credit=0))
);
CREATE FUNCTION check_balanced_ledger() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE tx uuid; total numeric;
BEGIN
 tx := NEW.transaction_id;
 SELECT sum(debit-credit) INTO total FROM ledger_entries WHERE transaction_id=tx;
 IF total<>0 THEN RAISE EXCEPTION 'Unbalanced ledger' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER balanced_ledger AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_balanced_ledger();
CREATE FUNCTION immutable_ledger() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Ledger is immutable'; END $$;
CREATE TRIGGER no_ledger_edit BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION immutable_ledger();
CREATE TRIGGER no_transaction_edit BEFORE UPDATE OR DELETE ON ledger_transactions FOR EACH ROW EXECUTE FUNCTION immutable_ledger();
CREATE TABLE payouts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),driver_id uuid NOT NULL REFERENCES driver_profiles(user_id),amount_minor int NOT NULL CHECK(amount_minor>0),
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','PAID','REJECTED')),
 maker_id uuid NOT NULL REFERENCES users,approver_id uuid REFERENCES users,bank_reference text UNIQUE,reason text NOT NULL,created_at timestamptz DEFAULT now(),
 CHECK(approver_id IS NULL OR approver_id<>maker_id)
);
CREATE TABLE notifications (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),user_id uuid NOT NULL REFERENCES users,event_id uuid NOT NULL,type text NOT NULL,payload jsonb NOT NULL,
 read_at timestamptz,created_at timestamptz DEFAULT now(),UNIQUE(user_id,event_id)
);
CREATE TABLE outbox_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),type text NOT NULL,aggregate_id uuid NOT NULL,payload jsonb NOT NULL,user_ids uuid[] NOT NULL DEFAULT '{}',
 created_at timestamptz DEFAULT now(),published_at timestamptz,attempts int NOT NULL DEFAULT 0,next_attempt_at timestamptz DEFAULT now(),last_error text
);
CREATE INDEX outbox_pending ON outbox_events(next_attempt_at) WHERE published_at IS NULL;
CREATE TABLE jobs (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),kind text NOT NULL,payload jsonb NOT NULL,run_at timestamptz NOT NULL DEFAULT now(),
 attempts int NOT NULL DEFAULT 0,lease_until timestamptz,lease_token uuid,done_at timestamptz,last_error text,dead boolean NOT NULL DEFAULT false
);
CREATE INDEX jobs_due ON jobs(run_at) WHERE done_at IS NULL AND dead=false;
CREATE TABLE idempotency_records (
 actor_id uuid NOT NULL REFERENCES users,operation text NOT NULL,key text NOT NULL,request_hash text NOT NULL,
 response jsonb NOT NULL,created_at timestamptz DEFAULT now(),PRIMARY KEY(actor_id,operation,key)
);
CREATE TABLE audit_logs (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,actor_id uuid REFERENCES users,action text NOT NULL,target_id uuid,details jsonb NOT NULL DEFAULT '{}',created_at timestamptz DEFAULT now());
CREATE TABLE gateway_credentials (id text PRIMARY KEY,value text NOT NULL,updated_at timestamptz DEFAULT now());
CREATE TABLE system_state (key text PRIMARY KEY,value jsonb NOT NULL,updated_at timestamptz DEFAULT now());
`);
exports.down = () => {
  throw new Error('Destructive rollback disabled. Restore backup or apply a forward migration.');
};
