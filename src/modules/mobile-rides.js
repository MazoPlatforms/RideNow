import { one } from '../shared/db.js';
import { decrypt } from '../shared/crypto.js';
import { ensure } from '../shared/errors.js';
import { TERMINAL } from './rides.js';

export async function mobileRide(db, user, { app, id }, key) {
  const driver = app === 'DRIVER';
  const availability = driver ? (await one(db,
    'SELECT availability FROM driver_profiles WHERE user_id=$1', [user.id],
  ))?.availability ?? 'OFFLINE' : undefined;
  const record = await one(db,
    `SELECT r.id,r.state,r.version,r.scheduled_at,r.arrived_at,r.pin_encrypted,r.driver_id,r.final_minor,
            q.waypoints,q.waypoint_labels,q.route,q.trip_type,q.category,q.estimated_minor,
            CASE WHEN r.driver_id IS NULL THEN 0 ELSE (q.fare_snapshot->>'cancellation_minor')::int END AS cancellation_minor,
            c.amount_minor AS charged_minor,p.status AS payment_status,
            CASE WHEN $3 THEN rider.name ELSE operator.name END AS counterpart,
            v.registration,v.make,v.model,v.color,
            CASE WHEN l.received_at > now()-interval '30 seconds' THEN ST_Y(l.point::geometry) END AS driver_latitude,
            CASE WHEN l.received_at > now()-interval '30 seconds' THEN ST_X(l.point::geometry) END AS driver_longitude
       FROM rides r
       JOIN fare_quotes q ON q.id=r.quote_id
       JOIN users rider ON rider.id=r.rider_id
       LEFT JOIN users operator ON operator.id=r.driver_id
       LEFT JOIN vehicles v ON v.id=r.vehicle_id
       LEFT JOIN driver_locations l ON l.driver_id=r.driver_id
       LEFT JOIN payments p ON p.ride_id=r.id
       LEFT JOIN cancellation_charges c ON c.ride_id=r.id
      WHERE (CASE WHEN $3 THEN r.driver_id ELSE r.rider_id END)=$1
        AND (($2::uuid IS NOT NULL AND r.id=$2) OR ($2 IS NULL AND
          (r.state NOT IN ('SCHEDULED','COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW')
           OR (r.state='COMPLETED' AND p.status='DUE'))))
      ORDER BY (r.state='COMPLETED'),r.created_at DESC,r.id DESC LIMIT 1`,
    [user.id, id ?? null, driver],
  );
  if (id) ensure(record, 404, 'RIDE_NOT_FOUND');
  if (!record) return { ride: null, availability };
  const { pin_encrypted, driver_id, driver_latitude, driver_longitude, ...ride } = record;
  const terminal = TERMINAL.includes(ride.state);
  if (!driver && !terminal && driver_id && ['DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED'].includes(ride.state))
    ride.ridePin = decrypt(pin_encrypted, key);
  if (!driver && !terminal && driver_latitude !== null)
    ride.driverLocation = [driver_longitude, driver_latitude];
  return { ride, availability };
}

export async function mobileOffers(db, user) {
  return { items: (await db.query(
    `SELECT o.id,o.expires_at,q.trip_type,q.waypoints,q.waypoint_labels,q.category,q.estimated_minor,
            q.route->'distanceMeters' AS distance_meters
       FROM dispatch_offers o JOIN rides r ON r.id=o.ride_id JOIN fare_quotes q ON q.id=r.quote_id
       JOIN driver_profiles d ON d.user_id=o.driver_id
      WHERE o.driver_id=$1 AND o.status='PENDING' AND o.expires_at>now()
        AND r.state='SEARCHING' AND d.availability='AVAILABLE'
        AND NOT EXISTS (SELECT 1 FROM rides outstanding JOIN payments p ON p.ride_id=outstanding.id
                         WHERE outstanding.driver_id=$1 AND p.status='DUE')
      ORDER BY o.created_at DESC`,
    [user.id],
  )).rows };
}
