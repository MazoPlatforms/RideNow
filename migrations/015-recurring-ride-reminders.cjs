exports.up = (pgm) => pgm.sql(`
ALTER TABLE fare_quotes
  ADD COLUMN repeat_at jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN repeat_rule text NOT NULL DEFAULT 'NONE' CHECK (repeat_rule IN ('NONE','DAILY','WEEKDAYS','WEEKLY','MONTHLY')),
  ADD COLUMN reminder_minutes smallint NOT NULL DEFAULT 0 CHECK (reminder_minutes IN (0,5,15,30,60)),
  ADD CONSTRAINT fare_quotes_repeat_at_array CHECK (jsonb_typeof(repeat_at)='array' AND jsonb_array_length(repeat_at)<=31);
ALTER TABLE rides
  ADD COLUMN repeat_group uuid,
  ADD COLUMN repeat_index smallint NOT NULL DEFAULT 0 CHECK (repeat_index BETWEEN 0 AND 30),
  ADD COLUMN passenger_name text,
  ADD COLUMN passenger_phone_encrypted text,
  ADD COLUMN reminder_minutes smallint NOT NULL DEFAULT 0 CHECK (reminder_minutes IN (0,5,15,30,60));
CREATE INDEX rides_repeat_group ON rides(repeat_group) WHERE repeat_group IS NOT NULL;
CREATE TABLE ride_reminders_sent (
  ride_id uuid NOT NULL REFERENCES rides ON DELETE CASCADE,
  scheduled_at timestamptz NOT NULL,
  sent_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (ride_id,scheduled_at)
);
`);

exports.down = () => {
  throw new Error('Destructive rollback disabled. Restore backup or apply a forward migration.');
};
