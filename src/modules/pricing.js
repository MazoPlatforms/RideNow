import Decimal from 'decimal.js';
import { one } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
export function calculateFare(plan, distanceMeters, drivingSeconds, waitingSeconds = 0) {
  const route = new Decimal(plan.base_minor)
    .plus(new Decimal(distanceMeters).div(1000).mul(plan.per_km_minor))
    .mul(plan.surge ?? 1);
  const time = new Decimal(Math.max(0, drivingSeconds)).div(60).mul(plan.per_minute_minor);
  const waiting = new Decimal(Math.max(0, waitingSeconds - 300)).div(60).mul(plan.waiting_minor);
  return Decimal.max(plan.minimum_minor, route.plus(time).plus(waiting))
    .toDecimalPlaces(0, Decimal.ROUND_HALF_UP)
    .toNumber();
}
export async function quote(db, maps, user, body) {
  const points =
    body.tripType === 'RETURN' ? [...body.waypoints, body.waypoints[0]] : body.waypoints;
  const area = await one(
    db,
    `SELECT * FROM service_areas a WHERE active AND
    NOT EXISTS(SELECT 1 FROM jsonb_array_elements($1::jsonb) p WHERE NOT ST_Covers(a.polygon,ST_SetSRID(ST_MakePoint((p->>0)::float,(p->>1)::float),4326))) LIMIT 1`,
    [JSON.stringify(points)],
  );
  ensure(area, 422, 'OUTSIDE_SERVICE_AREA');
  const plan = await one(
    db,
    'SELECT * FROM fare_plans WHERE area_id=$1 AND category=$2 ORDER BY version DESC LIMIT 1',
    [area.id, body.category],
  );
  ensure(plan, 422, 'FARE_PLAN_UNAVAILABLE');
  if (body.scheduledAt) {
    const delay = new Date(body.scheduledAt) - Date.now();
    ensure(delay >= 30 * 60000 && delay <= 30 * 86400000, 400, 'SCHEDULE_OUT_OF_RANGE');
  }
  const surge = await one(
    db,
    'SELECT multiplier FROM surge_state WHERE area_id=$1 AND category=$2',
    [area.id, body.category],
  );
  const snapshot = { ...plan, surge: Number(surge?.multiplier ?? 1) };
  const route = await maps.route({ waypoints: points, vehicleCategory: body.category });
  const amount = calculateFare(snapshot, route.distanceMeters, route.durationSeconds);
  return one(
    db,
    `INSERT INTO fare_quotes(rider_id,area_id,category,trip_type,waypoints,route,fare_snapshot,estimated_minor,scheduled_at,expires_at)
    VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,now()+interval '2 minutes') RETURNING *`,
    [
      user.id,
      area.id,
      body.category,
      body.tripType,
      JSON.stringify(points),
      route,
      snapshot,
      amount,
      body.scheduledAt ?? null,
    ],
  );
}
