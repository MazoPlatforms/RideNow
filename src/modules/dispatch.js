import { transaction, one, event } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
import { eligible } from './drivers.js';
import { setState } from './rides.js';
export async function acceptOffer(db, user, id, accept) {
  // Always lock ride before offer, consistent with cancellation/dispatch.
  const info = ensure(
    await one(db, 'SELECT ride_id FROM dispatch_offers WHERE id=$1 AND driver_id=$2', [
      id,
      user.id,
    ]),
    404,
    'OFFER_NOT_FOUND',
  );
  const ride = await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE', [info.ride_id]);
  const offer = await one(db, 'SELECT * FROM dispatch_offers WHERE id=$1 FOR UPDATE', [id]);
  ensure(
    ride.state === 'SEARCHING' && offer.status === 'PENDING' && offer.expires_at > Date.now(),
    409,
    'OFFER_EXPIRED',
  );
  await db.query('SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [user.id]);
  if (!accept) {
    await db.query("UPDATE dispatch_offers SET status='REJECTED' WHERE id=$1", [id]);
    return { accepted: false };
  }
  ensure(ride.rider_id !== user.id, 409, 'CANNOT_DRIVE_OWN_RIDE');
  const driver = await eligible(db, user.id);
  const quote = await one(db, 'SELECT category FROM fare_quotes WHERE id=$1', [ride.quote_id]);
  ensure(
    driver?.availability === 'AVAILABLE' && driver.category === quote.category,
    409,
    'DRIVER_NOT_ELIGIBLE',
  );
  ensure(
    await one(
      db,
      "SELECT 1 FROM driver_locations WHERE driver_id=$1 AND received_at>now()-interval '30 seconds'",
      [user.id],
    ),
    409,
    'STALE_DRIVER_LOCATION',
  );
  await db.query("UPDATE dispatch_offers SET status='ACCEPTED' WHERE id=$1", [id]);
  await db.query('UPDATE rides SET driver_id=$2,vehicle_id=$3 WHERE id=$1', [
    ride.id,
    user.id,
    driver.vehicle_id,
  ]);
  await db.query("UPDATE driver_profiles SET availability='RESERVED' WHERE user_id=$1", [user.id]);
  const updated = await setState(db, { ...ride, driver_id: user.id }, 'DRIVER_ASSIGNED', user.id);
  return { rideId: ride.id, state: updated.state };
}
export async function dispatchTick(pool, maps) {
  const ids = (
    await pool.query(
      "SELECT id FROM rides WHERE state IN ('SCHEDULED','SEARCHING') AND (scheduled_at IS NULL OR scheduled_at<=now()+interval '15 minutes') ORDER BY created_at LIMIT 100",
    )
  ).rows;
  for (const { id } of ids)
    await transaction(pool, async (db) => {
      let ride = await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE SKIP LOCKED', [id]);
      if (!ride || !['SCHEDULED', 'SEARCHING'].includes(ride.state)) return;
      if (ride.state === 'SCHEDULED') {
        await db.query('SELECT id FROM users WHERE id=$1 FOR UPDATE', [ride.rider_id]);
        const busy = await one(
          db,
          "SELECT id FROM rides WHERE rider_id=$1 AND id<>$2 AND state NOT IN ('SCHEDULED','COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW')",
          [ride.rider_id, id],
        );
        if (busy) {
          if (Date.now() - ride.scheduled_at >= 300000) {
            await db.query("UPDATE rides SET cancel_reason='RIDER_BUSY' WHERE id=$1", [id]);
            await setState(db, ride, 'CANCELLED', null, { reason: 'RIDER_BUSY' });
          }
          return;
        }
        await db.query(
          "UPDATE rides SET search_started_at=now(),search_deadline=scheduled_at+interval '5 minutes' WHERE id=$1",
          [id],
        );
        ride = await setState(db, ride, 'SEARCHING', null);
      }
      await db.query(
        "UPDATE dispatch_offers SET status='EXPIRED' WHERE ride_id=$1 AND status='PENDING' AND expires_at<=now()",
        [id],
      );
      if (ride.search_deadline <= Date.now()) {
        await db.query(
          "UPDATE dispatch_offers SET status='EXPIRED' WHERE ride_id=$1 AND status='PENDING'",
          [id],
        );
        await setState(db, ride, 'NO_DRIVER', null);
        return;
      }
      if (
        await one(db, "SELECT id FROM dispatch_offers WHERE ride_id=$1 AND status='PENDING'", [id])
      )
        return;
      const q = await one(db, 'SELECT * FROM fare_quotes WHERE id=$1', [ride.quote_id]);
      const age = (Date.now() - ride.search_started_at) / 1000,
        radius = age < 40 ? 2000 : age < 80 ? 4000 : 6000;
      const candidates = (
        await db.query(
          `SELECT d.user_id,v.category,ST_X(l.point::geometry) AS lng,ST_Y(l.point::geometry) AS lat,d.idle_since
      FROM driver_profiles d JOIN users u ON u.id=d.user_id JOIN vehicles v ON v.driver_id=d.user_id AND v.status='APPROVED'
      JOIN driver_locations l ON l.driver_id=d.user_id JOIN service_areas a ON a.id=$5 AND a.active
      WHERE d.user_id<>$7 AND d.status='APPROVED' AND d.availability='AVAILABLE' AND NOT u.suspended AND u.deleted_at IS NULL AND v.category=$3
      AND l.received_at>now()-interval '30 seconds' AND ST_Covers(a.polygon,l.point::geometry)
      AND ST_DWithin(l.point,ST_SetSRID(ST_MakePoint($1,$2),4326)::geography,$4)
      AND NOT EXISTS(SELECT 1 FROM dispatch_offers WHERE driver_id=d.user_id AND (ride_id=$6 OR status='PENDING'))
      ORDER BY l.point <-> ST_SetSRID(ST_MakePoint($1,$2),4326)::geography LIMIT 10`,
          [...q.waypoints[0], q.category, radius, q.area_id, id, ride.rider_id],
        )
      ).rows;
      const matrix = await maps.matrix({
        origins: candidates.map((c) => [c.lng, c.lat]),
        destination: q.waypoints[0],
        vehicleCategory: q.category,
      });
      const ranked = candidates
        .map((c, i) => ({ ...c, eta: matrix[i]?.durationSeconds ?? Infinity }))
        .filter((c) => Number.isFinite(c.eta))
        .sort((a, b) => a.eta - b.eta || a.idle_since - b.idle_since);
      for (const candidate of ranked) {
        const locked = await one(
          db,
          'SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE SKIP LOCKED',
          [candidate.user_id],
        );
        if (!locked) continue;
        const valid = await eligible(db, candidate.user_id);
        if (valid?.availability !== 'AVAILABLE') continue;
        if (
          await one(db, "SELECT id FROM dispatch_offers WHERE driver_id=$1 AND status='PENDING'", [
            candidate.user_id,
          ])
        )
          continue;
        const offer = await one(
          db,
          "INSERT INTO dispatch_offers(ride_id,driver_id,expires_at) VALUES($1,$2,LEAST(now()+interval '15 seconds',$3)) ON CONFLICT DO NOTHING RETURNING *",
          [id, candidate.user_id, ride.search_deadline],
        );
        if (offer) {
          await event(
            db,
            'driver.offer',
            id,
            {
              offerId: offer.id,
              rideId: id,
              expiresAt: offer.expires_at,
              pickup: q.waypoints[0],
              category: q.category,
            },
            [candidate.user_id],
          );
          break;
        }
      }
    });
}
