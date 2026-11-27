import { randomInt, randomUUID } from 'node:crypto';
import { one, event } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
import { hash, encrypt, equal } from '../shared/crypto.js';
import { calculateFare } from './pricing.js';
import { bookFare, chargeCancellation } from './ledger.js';
export const TERMINAL = ['COMPLETED', 'CANCELLED', 'NO_DRIVER', 'NO_SHOW'];
export async function rideEvent(db, ride, actor, details = {}) {
  await db.query(
    'INSERT INTO ride_events(ride_id,version,actor_id,state,details) VALUES($1,$2,$3,$4,$5)',
    [ride.id, ride.version, actor, ride.state, details],
  );
  await event(
    db,
    'ride.updated',
    ride.id,
    { rideId: ride.id, state: ride.state, version: ride.version, details },
    [ride.rider_id, ride.driver_id].filter(Boolean),
  );
}
export async function setState(db, ride, state, actor, extra = {}) {
  const changed = await one(
    db,
    'UPDATE rides SET state=$2,version=version+1,updated_at=now() WHERE id=$1 RETURNING *',
    [ride.id, state],
  );
  if (TERMINAL.includes(changed.state))
    await db.query('UPDATE rides SET passenger_phone_encrypted=NULL WHERE id=$1', [ride.id]);
  if (TERMINAL.includes(changed.state))
    await db.query('UPDATE ride_share_links SET revoked_at=now() WHERE ride_id=$1 AND revoked_at IS NULL', [ride.id]);
  await rideEvent(db, changed, actor, extra);
  return changed;
}
export async function book(db, user, quoteId, config, passenger = null) {
  // Lock rider to serialize scheduled-spacing and active-ride checks.
  const rider = ensure(await one(db, 'SELECT id,phone FROM users WHERE id=$1 AND deleted_at IS NULL AND NOT suspended FOR UPDATE',
    [user.id]), 401, 'SESSION_REVOKED');
  if (passenger) ensure(passenger.phone !== rider.phone, 400, 'PASSENGER_MUST_DIFFER');
  const q = await one(db, 'SELECT * FROM fare_quotes WHERE id=$1 AND rider_id=$2 FOR UPDATE', [
    quoteId,
    user.id,
  ]);
  ensure(q, 404, 'QUOTE_NOT_FOUND');
  ensure(q.expires_at > Date.now(), 409, 'QUOTE_EXPIRED');
  ensure(!q.consumed_at, 409, 'QUOTE_USED');
  ensure(!(await one(db, 'SELECT id FROM rides WHERE quote_id=$1', [q.id])), 409, 'QUOTE_USED');
  const repeatedAt = q.scheduled_at ? (q.repeat_at?.length ? q.repeat_at : [q.scheduled_at.toISOString()]) : [];
  if (q.scheduled_at) {
    ensure(q.scheduled_at - Date.now() >= 30 * 60000, 409, 'SCHEDULE_TOO_SOON');
    for (const when of repeatedAt) ensure(!(await one(db,
      "SELECT id FROM rides WHERE rider_id=$1 AND state='SCHEDULED' AND abs(extract(epoch from scheduled_at-$2::timestamptz))<3600",
      [user.id, when])), 409, 'SCHEDULE_CONFLICT');
  } else
    ensure(
      !(await one(
        db,
        "SELECT id FROM rides WHERE rider_id=$1 AND state NOT IN ('SCHEDULED','COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW')",
        [user.id],
      )),
      409,
      'ACTIVE_RIDE_EXISTS',
    );
  const dates = q.scheduled_at ? repeatedAt : [null];
  const group = dates.length > 1 ? randomUUID() : null;
  const rideIds = [];
  let firstPin;
  for (let index = 0; index < dates.length; index++) {
    const when = dates[index];
    const pin = String(randomInt(1000, 10000));
    const quoteForRide = index === 0 ? q : await one(db,
      `INSERT INTO fare_quotes(rider_id,area_id,category,trip_type,waypoints,route,fare_snapshot,estimated_minor,
        scheduled_at,expires_at,waypoint_labels,repeat_rule,repeat_at,reminder_minutes,consumed_at)
       VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,now()+interval '2 minutes',$10,'NONE',jsonb_build_array($9::text),$11,now()) RETURNING id`,
      [q.rider_id,q.area_id,q.category,q.trip_type,q.waypoints,q.route,q.fare_snapshot,q.estimated_minor,
        when,q.waypoint_labels,q.reminder_minutes]);
    const ride = await one(db,
      `INSERT INTO rides(rider_id,quote_id,state,scheduled_at,search_started_at,search_deadline,pin_hash,pin_encrypted,
        repeat_group,repeat_index,passenger_name,passenger_phone_encrypted,reminder_minutes)
       VALUES($1,$2,$3,$4,CASE WHEN $4::timestamptz IS NULL THEN now() END,
        CASE WHEN $4::timestamptz IS NULL THEN now()+interval '2 minutes' END,$5,$6,$7,$8,$9,$10,$11) RETURNING *`,
      [user.id,quoteForRide.id,q.scheduled_at?'SCHEDULED':'SEARCHING',when,hash(pin,config.OTP_PEPPER),
        encrypt(pin,config.ENCRYPTION_KEY),group,index,passenger?.name ?? null,
        passenger ? encrypt(passenger.phone,config.ENCRYPTION_KEY) : null,q.reminder_minutes ?? 0]);
    if (index === 0) firstPin = pin;
    rideIds.push(ride.id);
    if (ride.scheduled_at && ride.reminder_minutes > 0) {
      await db.query(`INSERT INTO jobs(kind,payload,run_at) VALUES('scheduled_ride_reminder',$1,
        GREATEST(now(),$2::timestamptz-($3::int*interval '1 minute')))` ,
      [{ rideId: ride.id, scheduledAt: ride.scheduled_at.toISOString() },ride.scheduled_at,ride.reminder_minutes]);
    }
    await rideEvent(db,ride,user.id,index > 0 ? { repeatGroup: group, occurrence: index + 1, count: dates.length } : {});
  }
  await db.query('UPDATE fare_quotes SET consumed_at=now() WHERE id=$1', [q.id]);
  return { rideId: rideIds[0], rideIds, state: q.scheduled_at ? 'SCHEDULED' : 'SEARCHING', ...(firstPin ? { ridePin: firstPin } : {}) };
}
export async function releaseDriver(db, id) {
  if (!id) return;
  await db.query(
    "UPDATE driver_profiles SET availability='OFFLINE',idle_since=now() WHERE user_id=$1",
    [id],
  );
}
export async function cancel(db, user, id, reason, expectedState) {
  const ride = ensure(
    await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE', [id]),
    404,
    'RIDE_NOT_FOUND',
  );
  ensure(ride.rider_id === user.id || ride.driver_id === user.id, 403, 'FORBIDDEN');
  ensure(!expectedState || ride.state === expectedState, 409, 'RIDE_STATE_CHANGED');
  ensure(
    !TERMINAL.includes(ride.state) &&
      !['IN_PROGRESS', 'AT_STOP', 'WAITING', 'RETURNING'].includes(ride.state),
    409,
    'CANNOT_CANCEL',
  );
  const previousDriver = ride.driver_id;
  await db.query(
    "UPDATE dispatch_offers SET status='CANCELLED' WHERE ride_id=$1 AND status='PENDING'",
    [id],
  );
  await releaseDriver(db, previousDriver);
  if (user.id === ride.driver_id) {
    await db.query(
      "UPDATE rides SET search_started_at=now(),search_deadline=CASE WHEN scheduled_at IS NULL THEN now()+interval '2 minutes' ELSE GREATEST(now()+interval '2 minutes',scheduled_at+interval '5 minutes') END WHERE id=$1",
      [id],
    );
    await db.query(
      'UPDATE rides SET driver_id=NULL,vehicle_id=NULL,arrived_at=NULL,cancel_reason=$2 WHERE id=$1',
      [id, reason],
    );
    const changed = await setState(db, { ...ride, driver_id: null }, 'SEARCHING', user.id, {
      reason,
      previousDriver,
    });
    if (previousDriver)
      await event(
        db,
        'ride.updated',
        id,
        { rideId: id, state: 'SEARCHING', version: changed.version },
        [previousDriver],
      );
    return { rideId: id, state: changed.state };
  }
  if (ride.driver_id && user.id === ride.rider_id) await chargeCancellation(db, ride);
  await db.query('UPDATE rides SET cancel_reason=$2 WHERE id=$1', [id, reason]);
  const changed = await setState(db, ride, 'CANCELLED', user.id, { reason });
  return { rideId: id, state: changed.state };
}
export async function action(db, user, id, body, config) {
  const ride = ensure(
    await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE', [id]),
    404,
    'RIDE_NOT_FOUND',
  );
  ensure(ride.driver_id === user.id, 403, 'FORBIDDEN');
  const q = await one(db, 'SELECT * FROM fare_quotes WHERE id=$1', [ride.quote_id]);
  const move = async (state) => {
    const r = await setState(db, ride, state, user.id);
    return { rideId: id, state: r.state, version: r.version };
  };
  switch (body.action) {
    case 'EN_ROUTE':
      ensure(ride.state === 'DRIVER_ASSIGNED', 409, 'INVALID_TRANSITION');
      return move('DRIVER_ARRIVING');
    case 'ARRIVE': {
      ensure(
        ['DRIVER_ASSIGNED', 'DRIVER_ARRIVING'].includes(ride.state),
        409,
        'INVALID_TRANSITION',
      );
      await nearPoint(db, user.id, q.waypoints[0]);
      await db.query('UPDATE rides SET arrived_at=now() WHERE id=$1', [id]);
      return move('DRIVER_ARRIVED');
    }
    case 'START': {
      ensure(ride.state === 'DRIVER_ARRIVED', 409, 'INVALID_TRANSITION');
      ensure(!ride.scheduled_at || ride.scheduled_at <= Date.now(), 409, 'PICKUP_TIME_NOT_REACHED');
      ensure(ride.pin_attempts < 5, 429, 'RIDE_PIN_LOCKED');
      if (!body.pin || !equal(ride.pin_hash, hash(body.pin, config.OTP_PEPPER))) {
        // Return an error marker so this failed attempt is committed by idempotency transaction.
        await db.query('UPDATE rides SET pin_attempts=pin_attempts+1 WHERE id=$1', [id]);
        return { error: 'INVALID_RIDE_PIN' };
      }
      await db.query('UPDATE rides SET started_at=now() WHERE id=$1', [id]);
      await db.query("UPDATE driver_profiles SET availability='ON_TRIP' WHERE user_id=$1", [
        user.id,
      ]);
      return move('IN_PROGRESS');
    }
    case 'AT_STOP':
      ensure(q.trip_type === 'RETURN' && ride.state === 'IN_PROGRESS', 409, 'INVALID_TRANSITION');
      await nearPoint(db, user.id, q.waypoints[1]);
      await db.query('UPDATE rides SET stop_at=now() WHERE id=$1', [id]);
      return move('AT_STOP');
    case 'WAIT':
      ensure(ride.state === 'AT_STOP', 409, 'INVALID_TRANSITION');
      return move('WAITING');
    case 'RETURN':
      ensure(['AT_STOP', 'WAITING'].includes(ride.state), 409, 'INVALID_TRANSITION');
      await db.query('UPDATE rides SET return_at=now() WHERE id=$1', [id]);
      return move('RETURNING');
    case 'COMPLETE': {
      ensure(
        (q.trip_type === 'RETURN' && ride.state === 'RETURNING') ||
          (q.trip_type === 'ONE_WAY' && ride.state === 'IN_PROGRESS'),
        409,
        'INVALID_TRANSITION',
      );
      await nearPoint(db, user.id, q.waypoints.at(-1));
      const now = (await one(db, 'SELECT now() AS time')).time;
      const waiting = ride.stop_at && ride.return_at ? (ride.return_at - ride.stop_at) / 1000 : 0;
      const driving = Math.max(0, (now - ride.started_at) / 1000 - waiting);
      const amount = calculateFare(q.fare_snapshot, q.route.distanceMeters, driving, waiting);
      await db.query('UPDATE rides SET completed_at=$2,final_minor=$3 WHERE id=$1', [
        id,
        now,
        amount,
      ]);
      const completed = await setState(db, { ...ride, final_minor: amount }, 'COMPLETED', user.id, {
        drivingSeconds: driving,
        waitingSeconds: waiting,
        amountMinor: amount,
      });
      await bookFare(db, completed, q.fare_snapshot);
      await db.query('INSERT INTO payments(ride_id,amount_minor) VALUES($1,$2)', [id, amount]);
      await releaseDriver(db, user.id);
      return { rideId: id, state: 'COMPLETED', finalMinor: amount, currency: 'LKR' };
    }
    case 'NO_SHOW': {
      ensure(ride.state === 'DRIVER_ARRIVED', 409, 'INVALID_TRANSITION');
      const begins = Math.max(ride.arrived_at.getTime(), ride.scheduled_at?.getTime() ?? 0);
      ensure(Date.now() - begins >= 300000, 409, 'NO_SHOW_TOO_SOON');
      await nearPoint(db, user.id, q.waypoints[0]);
      await chargeCancellation(db, ride);
      await releaseDriver(db, user.id);
      return move('NO_SHOW');
    }
    default:
      ensure(false, 400, 'INVALID_ACTION');
  }
}
async function nearPoint(db, driverId, point) {
  ensure(
    await one(
      db,
      "SELECT 1 FROM driver_locations WHERE driver_id=$1 AND received_at>now()-interval '30 seconds' AND ST_DWithin(point,ST_SetSRID(ST_MakePoint($2,$3),4326)::geography,300)",
      [driverId, ...point],
    ),
    409,
    'FRESH_LOCATION_NEAR_STOP_REQUIRED',
  );
}

export async function reschedule(db, user, id, quoteId) {
  const r = ensure(
    await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE', [id]),
    404,
    'RIDE_NOT_FOUND',
  );
  ensure(r.rider_id === user.id, 403, 'FORBIDDEN');
  ensure(
    r.state === 'SCHEDULED' && r.scheduled_at - Date.now() > 15 * 60000,
    409,
    'DISPATCH_ALREADY_STARTED',
  );
  const q = ensure(
    await one(db, 'SELECT * FROM fare_quotes WHERE id=$1 AND rider_id=$2 FOR UPDATE', [
      quoteId,
      user.id,
    ]),
    404,
    'QUOTE_NOT_FOUND',
  );
  ensure(
    q.scheduled_at &&
      q.repeat_rule === 'NONE' &&
      q.scheduled_at - Date.now() >= 30 * 60000 &&
      q.expires_at > Date.now() &&
      !q.consumed_at,
    409,
    'QUOTE_UNAVAILABLE',
  );
  await db.query('SELECT id FROM users WHERE id=$1 FOR UPDATE', [user.id]);
  ensure(
    !(await one(
      db,
      "SELECT 1 FROM rides WHERE rider_id=$1 AND id<>$2 AND state='SCHEDULED' AND abs(extract(epoch from scheduled_at-$3::timestamptz))<3600",
      [user.id, id, q.scheduled_at],
    )),
    409,
    'SCHEDULE_CONFLICT',
  );
  await db.query('UPDATE fare_quotes SET consumed_at=now() WHERE id=$1', [q.id]);
  await db.query('UPDATE rides SET quote_id=$2,scheduled_at=$3,reminder_minutes=$4 WHERE id=$1', [
    id,
    q.id,
    q.scheduled_at,
    q.reminder_minutes ?? 0,
  ]);
  if ((q.reminder_minutes ?? 0) > 0) await db.query(`INSERT INTO jobs(kind,payload,run_at)
    VALUES('scheduled_ride_reminder',$1,GREATEST(now(),$2::timestamptz-($3::int*interval '1 minute')))` ,
    [{ rideId: id, scheduledAt: q.scheduled_at.toISOString() },q.scheduled_at,q.reminder_minutes]);
  const changed = await setState(db, r, 'SCHEDULED', user.id, { rescheduledAt: q.scheduled_at });
  return { rideId: id, state: changed.state, scheduledAt: q.scheduled_at };
}
export async function interrupt(db, user, id, body) {
  const r = ensure(
    await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE', [id]),
    404,
    'RIDE_NOT_FOUND',
  );
  ensure(
    ['IN_PROGRESS', 'AT_STOP', 'WAITING', 'RETURNING'].includes(r.state),
    409,
    'RIDE_NOT_IN_PROGRESS',
  );
  const q = await one(db, 'SELECT * FROM fare_quotes WHERE id=$1', [r.quote_id]);
  await db.query(
    'UPDATE rides SET final_minor=$2,completed_at=now(),cancel_reason=$3 WHERE id=$1',
    [id, body.amountMinor, body.reason],
  );
  const result = await setState(db, r, 'COMPLETED', user.id, {
    interrupted: true,
    reason: body.reason,
    amountMinor: body.amountMinor,
  });
  await bookFare(db, { ...result, final_minor: body.amountMinor }, q.fare_snapshot);
  await db.query('INSERT INTO payments(ride_id,amount_minor) VALUES($1,$2)', [
    id,
    body.amountMinor,
  ]);
  await releaseDriver(db, r.driver_id);
  return { rideId: id, state: 'COMPLETED', finalMinor: body.amountMinor };
}
