import { token, hash } from '../shared/crypto.js';
import { one, transaction } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
import { TERMINAL } from './rides.js';

const shareable = ['SEARCHING', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED',
  'IN_PROGRESS', 'AT_STOP', 'WAITING', 'RETURNING'];

export async function createShareLink(db, user, rideId, publicBaseUrl, pepper) {
  return transaction(db, async (tx) => {
    const ride = ensure(await one(tx,
      'SELECT id,rider_id,driver_id,state FROM rides WHERE id=$1 FOR UPDATE', [rideId]),
    404, 'RIDE_NOT_FOUND');
    ensure(ride.rider_id === user.id || ride.driver_id === user.id, 403, 'FORBIDDEN');
    ensure(shareable.includes(ride.state) && !TERMINAL.includes(ride.state), 409, 'RIDE_NOT_SHAREABLE');
    await tx.query('UPDATE ride_share_links SET revoked_at=now() WHERE ride_id=$1 AND revoked_at IS NULL', [rideId]);
    const secret = token();
    const record = await one(tx,
      `INSERT INTO ride_share_links(ride_id,created_by,token_hash,expires_at)
       VALUES($1,$2,$3,now()+interval '24 hours') RETURNING expires_at`,
      [rideId, user.id, hash(secret, pepper)],
    );
    return {
      url: `${publicBaseUrl.replace(/\/$/, '')}/trip/#${secret}`,
      expiresAt: record.expires_at,
    };
  });
}

export async function revokeShareLinks(db, user, rideId) {
  const ride = ensure(await one(db,
    'SELECT id,rider_id,driver_id FROM rides WHERE id=$1', [rideId]), 404, 'RIDE_NOT_FOUND');
  ensure(ride.rider_id === user.id || ride.driver_id === user.id, 403, 'FORBIDDEN');
  await db.query('UPDATE ride_share_links SET revoked_at=now() WHERE ride_id=$1 AND revoked_at IS NULL', [rideId]);
  return { revoked: true };
}

export async function sharedTrip(db, secret, pepper) {
  const item = await one(db,
    `SELECT r.id,r.state,r.scheduled_at,q.waypoint_labels,q.waypoints,
            CASE WHEN l.received_at>now()-interval '30 seconds'
                 THEN ST_Y(l.point::geometry) END AS driver_latitude,
            CASE WHEN l.received_at>now()-interval '30 seconds'
                 THEN ST_X(l.point::geometry) END AS driver_longitude,
            CASE WHEN l.received_at>now()-interval '30 seconds'
                 THEN l.received_at END AS driver_location_updated,
            u.name AS driver_name,v.make,v.model,v.color,v.registration
       FROM ride_share_links s
       JOIN rides r ON r.id=s.ride_id
       JOIN fare_quotes q ON q.id=r.quote_id
       LEFT JOIN driver_locations l ON l.driver_id=r.driver_id
       LEFT JOIN users u ON u.id=r.driver_id
       LEFT JOIN vehicles v ON v.id=r.vehicle_id
      WHERE s.token_hash=$1 AND s.revoked_at IS NULL AND s.expires_at>now()
        AND r.state=ANY($2::text[]) LIMIT 1`,
    [hash(secret, pepper), shareable],
  );
  ensure(item, 404, 'SHARED_TRIP_NOT_FOUND');
  const labels = item.waypoint_labels ?? [];
  const points = item.waypoints ?? [];
  return {
    state: item.state,
    scheduledAt: item.scheduled_at,
    pickup: labels[0] ?? null,
    destination: labels.at(-1) ?? null,
    driverName: item.driver_name?.split(/\s+/)[0] ?? null,
    vehicle: [item.color, item.make, item.model, item.registration].filter(Boolean).join(' · ') || null,
    driverLocation: item.driver_latitude == null ? null : [item.driver_longitude, item.driver_latitude],
    locationUpdated: item.driver_location_updated,
    updatedAt: new Date().toISOString(),
  };
}

