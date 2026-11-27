exports.up = (pgm) => {
  pgm.addColumns('fare_quotes', { waypoint_labels: { type: 'jsonb' } });
};

exports.down = (pgm) => {
  pgm.dropColumns('fare_quotes', ['waypoint_labels']);
};
