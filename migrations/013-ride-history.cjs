exports.up = (pgm) => {
  pgm.sql(`
    CREATE INDEX rides_rider_history ON rides(rider_id, created_at DESC, id DESC);
    CREATE INDEX rides_driver_history ON rides(driver_id, created_at DESC, id DESC) WHERE driver_id IS NOT NULL;
    CREATE INDEX ledger_entries_account ON ledger_entries(account_id);
  `);
};

exports.down = (pgm) => {
  pgm.sql(`
    DROP INDEX ledger_entries_account;
    DROP INDEX rides_driver_history;
    DROP INDEX rides_rider_history;
  `);
};
