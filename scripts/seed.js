import { database, transaction, one } from '../src/shared/db.js';
if (process.env.NODE_ENV === 'production')
  throw new Error('Development seed forbidden in production');
const pool = database(process.env.DATABASE_URL);
try {
  await transaction(pool, async (db) => {
    let a = await one(db, "SELECT id FROM service_areas WHERE name='Kattankudy development'");
    a ??= await one(
      db,
      "INSERT INTO service_areas(name,polygon,active) VALUES('Kattankudy development',ST_MakeEnvelope(81.6973617,7.5101429,81.7912717,7.7249628,4326),true) RETURNING id",
    );
    for (const [category, base, km] of [
      ['TUK', 10000, 8000],
      ['CAR', 20000, 12000],
      ['VAN', 30000, 18000],
    ])
      await db.query(
        'INSERT INTO fare_plans(area_id,category,version,base_minor,per_km_minor,per_minute_minor,waiting_minor,minimum_minor,commission_bps) VALUES($1,$2,1,$3,$4,1000,1000,$3,2000) ON CONFLICT DO NOTHING',
        [a.id, category, base, km],
      );
  });
  console.log('Development area and sample fares seeded. Not production tariffs.');
} finally {
  await pool.end();
}
