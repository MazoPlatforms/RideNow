import { one, event, audit } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
import { permission } from './auth.js';
export async function eligible(db, id) {
  return one(
    db,
    `SELECT d.*,v.id AS vehicle_id,v.category FROM driver_profiles d JOIN users u ON u.id=d.user_id JOIN vehicles v ON v.driver_id=d.user_id AND v.status='APPROVED'
    WHERE d.user_id=$1 AND d.status='APPROVED' AND NOT u.suspended AND u.deleted_at IS NULL
    AND EXISTS(SELECT 1 FROM documents WHERE driver_id=d.user_id AND vehicle_id IS NULL AND kind='IDENTITY' AND scan_status='CLEAN' AND expires_at>now())
    AND EXISTS(SELECT 1 FROM documents WHERE driver_id=d.user_id AND vehicle_id IS NULL AND kind='LICENSE' AND scan_status='CLEAN' AND expires_at>now())
    AND EXISTS(SELECT 1 FROM documents WHERE vehicle_id=v.id AND kind='REGISTRATION' AND scan_status='CLEAN' AND expires_at>now())
    AND EXISTS(SELECT 1 FROM documents WHERE vehicle_id=v.id AND kind='INSURANCE' AND scan_status='CLEAN' AND expires_at>now())`,
    [id],
  );
}
export async function onboard(db, user, body) {
  await db.query(
    "UPDATE users SET roles=array(SELECT DISTINCT unnest(roles||ARRAY['DRIVER'])),name=$2 WHERE id=$1",
    [user.id, body.name],
  );
  return one(
    db,
    'INSERT INTO driver_profiles(user_id) VALUES($1) ON CONFLICT(user_id) DO UPDATE SET review_reason=NULL RETURNING *',
    [user.id],
  );
}
export async function availability(db, user, value) {
  const p = ensure(
    await one(db, 'SELECT * FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [user.id]),
    404,
    'DRIVER_NOT_FOUND',
  );
  ensure(!['RESERVED', 'ON_TRIP'].includes(p.availability), 409, 'DRIVER_BUSY');
  if (value === 'AVAILABLE') {
    ensure(await eligible(db, user.id), 409, 'DRIVER_NOT_ELIGIBLE');
    ensure(
      await one(
        db,
        "SELECT 1 FROM driver_locations l JOIN service_areas a ON a.active AND ST_Covers(a.polygon,l.point::geometry) WHERE driver_id=$1 AND received_at>now()-interval '30 seconds'",
        [user.id],
      ),
      409,
      'FRESH_SERVICE_AREA_LOCATION_REQUIRED',
    );
  }
  return one(
    db,
    'UPDATE driver_profiles SET availability=$2,idle_since=now() WHERE user_id=$1 RETURNING *',
    [user.id, value],
  );
}
export async function location(db, user, body) {
  ensure(user.roles.includes('DRIVER'), 403, 'DRIVER_REQUIRED');
  await db.query('SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [user.id]);
  const previous = await one(
    db,
    'SELECT *,ST_X(point::geometry) AS lng,ST_Y(point::geometry) AS lat FROM driver_locations WHERE driver_id=$1',
    [user.id],
  );
  const date = new Date(body.recordedAt),
    age = Date.now() - date;
  ensure(age >= -5000 && age < 30000, 400, 'STALE_LOCATION');
  if (previous) {
    ensure(
      body.sequence > Number(previous.sequence) && date > previous.recorded_at,
      409,
      'LOCATION_OUT_OF_ORDER',
    );
    const delta = (date - previous.recorded_at) / 1000;
    const dist = (
      await one(
        db,
        'SELECT ST_Distance(ST_SetSRID(ST_MakePoint($1,$2),4326)::geography,ST_SetSRID(ST_MakePoint($3,$4),4326)::geography) AS d',
        [previous.lng, previous.lat, ...body.coordinates],
      )
    ).d;
    ensure(dist <= delta * 70 + body.accuracy + previous.accuracy, 422, 'IMPLAUSIBLE_LOCATION');
  }
  await db.query(
    `INSERT INTO driver_locations(driver_id,point,accuracy,sequence,recorded_at) VALUES($1,ST_SetSRID(ST_MakePoint($2,$3),4326)::geography,$4,$5,$6)
    ON CONFLICT(driver_id) DO UPDATE SET point=EXCLUDED.point,accuracy=EXCLUDED.accuracy,sequence=EXCLUDED.sequence,recorded_at=EXCLUDED.recorded_at,received_at=now()`,
    [user.id, ...body.coordinates, body.accuracy, body.sequence, date],
  );
  const ride = await one(
    db,
    "SELECT * FROM rides WHERE driver_id=$1 AND state NOT IN ('COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW')",
    [user.id],
  );
  if (ride) {
    await db.query(
      'INSERT INTO ride_location_points(ride_id,driver_id,point,recorded_at,sequence) VALUES($1,$2,ST_SetSRID(ST_MakePoint($3,$4),4326)::geography,$5,$6)',
      [ride.id, user.id, ...body.coordinates, date, body.sequence],
    );
    await event(
      db,
      'driver.location',
      ride.id,
      {
        rideId: ride.id,
        coordinates: body.coordinates,
        sequence: body.sequence,
        recordedAt: body.recordedAt,
      },
      [ride.rider_id],
    );
  }
  return { accepted: true };
}
export async function review(db, user, id, body) {
  ensure(permission(user, 'drivers.review'), 403, 'FORBIDDEN');
  ensure(
    await one(db, 'SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [id]),
    404,
    'DRIVER_NOT_FOUND',
  );
  if (body.status === 'APPROVED') {
    const docs = (
      await db.query(
        "SELECT kind,vehicle_id FROM documents WHERE driver_id=$1 AND scan_status='CLEAN' AND expires_at>now()",
        [id],
      )
    ).rows;
    ensure(
      ['IDENTITY', 'LICENSE'].every((k) => docs.some((d) => d.kind === k && !d.vehicle_id)),
      409,
      'DRIVER_DOCUMENTS_INCOMPLETE',
    );
    const vehicle = ensure(
      await one(db, 'SELECT * FROM vehicles WHERE id=$1 AND driver_id=$2', [body.vehicleId, id]),
      409,
      'VEHICLE_REQUIRED',
    );
    ensure(
      ['REGISTRATION', 'INSURANCE'].every((k) =>
        docs.some((d) => d.kind === k && d.vehicle_id === vehicle.id),
      ),
      409,
      'VEHICLE_DOCUMENTS_INCOMPLETE',
    );
    ensure(
      !(await one(
        db,
        "SELECT id FROM rides WHERE driver_id=$1 AND vehicle_id<>$2 AND state NOT IN ('COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW')",
        [id, vehicle.id],
      )),
      409,
      'DRIVER_BUSY_WITH_DIFFERENT_VEHICLE',
    );
    await db.query(
      "UPDATE vehicles SET status='PENDING' WHERE driver_id=$1 AND status='APPROVED'",
      [id],
    );
    await db.query("UPDATE vehicles SET status='APPROVED' WHERE id=$1", [vehicle.id]);
  } else ensure(body.reason, 400, 'REASON_REQUIRED');
  const result = await one(
    db,
    "UPDATE driver_profiles SET status=$2,review_reason=$3,availability=CASE WHEN $2='APPROVED' THEN availability WHEN availability='AVAILABLE' THEN 'OFFLINE' ELSE availability END WHERE user_id=$1 RETURNING *",
    [id, body.status, body.reason ?? null],
  );
  await audit(db, user.id, 'DRIVER_REVIEW', id, body);
  await event(db, 'driver.reviewed', id, { status: body.status }, [id]);
  return result;
}
