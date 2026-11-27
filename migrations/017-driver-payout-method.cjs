exports.up = (pgm) => pgm.sql(`
ALTER TABLE driver_profiles ADD COLUMN payout_bank_encrypted text;
`);

exports.down = () => {
  throw new Error('Destructive rollback disabled. Restore backup or apply a forward migration.');
};
