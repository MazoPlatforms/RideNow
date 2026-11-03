import { randomUUID } from 'node:crypto';
import { one, transaction } from './shared/db.js';
import { dispatchTick } from './modules/dispatch.js';
export async function publishOutbox({ pool, redis, config }) {
  for (let n = 0; n < 100; n++) {
    const found = await transaction(pool, async (db) => {
      const e = await one(
        db,
        'SELECT * FROM outbox_events WHERE published_at IS NULL AND next_attempt_at<=now() AND attempts<10 ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1',
      );
      if (!e) return false;
      try {
        if (e.type !== 'driver.location')
          for (const userId of e.user_ids) {
            const notification = await one(
              db,
              'INSERT INTO notifications(user_id,event_id,type,payload) VALUES($1,$2,$3,$4) ON CONFLICT DO NOTHING RETURNING id',
              [userId, e.id, e.type, e.payload],
            );
            if (notification && config?.FCM_ENABLED === 'true')
              await db.query("INSERT INTO jobs(kind,payload) VALUES('push',$1)", [
                { notificationId: notification.id, userId, type: e.type, payload: e.payload },
              ]);
          }
        await redis.publish(
          'ride.realtime',
          JSON.stringify({
            eventId: e.id,
            type: e.type,
            aggregateId: e.aggregate_id,
            userIds: e.user_ids,
            occurredAt: e.created_at,
            payload: e.payload,
          }),
        );
        await db.query('UPDATE outbox_events SET published_at=now() WHERE id=$1', [e.id]);
      } catch (err) {
        await db.query(
          "UPDATE outbox_events SET attempts=attempts+1,next_attempt_at=now()+interval '10 seconds',last_error=$2 WHERE id=$1",
          [e.id, err.code ?? 'PUBLISH_FAILED'],
        );
      }
      return true;
    });
    if (!found) break;
  }
}
export async function claimJob(pool, kinds) {
  return transaction(pool, (db) =>
    one(
      db,
      `UPDATE jobs SET lease_until=now()+interval '60 seconds',lease_token=$2,attempts=attempts+1 WHERE id=(
    SELECT id FROM jobs WHERE kind=ANY($1) AND done_at IS NULL AND NOT dead AND run_at<=now() AND (lease_until IS NULL OR lease_until<now()) ORDER BY run_at FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *`,
      [kinds, randomUUID()],
    ),
  );
}
export async function finishJob(pool, job, error) {
  if (error)
    await pool.query(
      "UPDATE jobs SET lease_until=NULL,lease_token=NULL,dead=attempts>=8,run_at=now()+LEAST(300,power(2,attempts)) * interval '1 second',last_error=$3 WHERE id=$1 AND lease_token=$2",
      [job.id, job.lease_token, error.code ?? 'PROVIDER_FAILURE'],
    );
  else
    await pool.query(
      "UPDATE jobs SET done_at=now(),lease_until=NULL,lease_token=NULL,payload=CASE WHEN kind='otp' THEN '{}'::jsonb ELSE payload END WHERE id=$1 AND lease_token=$2",
      [job.id, job.lease_token],
    );
}
export async function processJobs(deps) {
  const { pool, storage, push, logger } = deps;
  for (let i = 0; i < 20; i++) {
    // Disabled external push must not consume retries or falsely record delivery.
    // Existing jobs remain pending until an external push provider is enabled.
    const job = await claimJob(pool, push ? ['scan', 'push'] : ['scan']);
    if (!job) break;
    try {
      if (job.kind === 'scan') {
        const doc = await one(pool, 'SELECT * FROM documents WHERE id=$1', [
          job.payload.documentId,
        ]);
        if (doc?.scan_status === 'PENDING') {
          const result = await storage.scan(doc);
          const updated = await pool.query(
            "UPDATE documents SET scan_status=$2,object_version=$3,object_key=COALESCE($4,object_key) WHERE id=$1 AND scan_status='PENDING'",
            [doc.id, result.status, result.version ?? null, result.verifiedKey ?? null],
          );
          if (!updated.rowCount && result.verifiedKey) {
            try {
              await storage.discardVerified(result.verifiedKey);
            } catch {
              logger.warn({ documentId: doc.id }, 'Unreferenced verified copy requires cleanup');
            }
          }
        }
      } else if (push) await push(job.payload);
      else throw Object.assign(new Error('Push unavailable'), { code: 'PUSH_DISABLED' });
      await finishJob(pool, job);
    } catch (e) {
      logger.warn(
        { jobId: job.id, kind: job.kind, code: e.code ?? 'PROVIDER_FAILURE' },
        'Job retry',
      );
      await finishJob(pool, job, e);
    }
  }
}
export async function maintenance(pool) {
  await transaction(pool, async (db) => {
    // One maintenance runner per minute across all workers.
    if (!(await one(db, 'SELECT pg_try_advisory_xact_lock(876234) AS locked')).locked) return;
    const state = await one(db, "SELECT updated_at FROM system_state WHERE key='maintenance'");
    if (state && Date.now() - state.updated_at < 60000) return;
    await db.query(
      "UPDATE driver_profiles d SET availability='OFFLINE' WHERE availability='AVAILABLE' AND (NOT EXISTS(SELECT 1 FROM driver_locations l WHERE l.driver_id=d.user_id AND l.received_at>now()-interval '30 seconds') OR NOT EXISTS(SELECT 1 FROM vehicles v WHERE v.driver_id=d.user_id AND v.status='APPROVED' AND EXISTS(SELECT 1 FROM documents WHERE driver_id=d.user_id AND vehicle_id IS NULL AND kind='IDENTITY' AND scan_status='CLEAN' AND expires_at>now()) AND EXISTS(SELECT 1 FROM documents WHERE driver_id=d.user_id AND vehicle_id IS NULL AND kind='LICENSE' AND scan_status='CLEAN' AND expires_at>now()) AND EXISTS(SELECT 1 FROM documents WHERE vehicle_id=v.id AND kind='REGISTRATION' AND scan_status='CLEAN' AND expires_at>now()) AND EXISTS(SELECT 1 FROM documents WHERE vehicle_id=v.id AND kind='INSURANCE' AND scan_status='CLEAN' AND expires_at>now())))",
    );
    const month = (await one(db, "SELECT date_trunc('month',now())+interval '2 months' AS start"))
      .start;
    const end = new Date(month);
    end.setUTCMonth(end.getUTCMonth() + 1);
    const label = `ride_location_points_${month.getUTCFullYear()}_${String(month.getUTCMonth() + 1).padStart(2, '0')}`;
    if (
      (
        await one(
          db,
          "SELECT has_schema_privilege(current_user,current_schema(),'CREATE') AS allowed",
        )
      ).allowed
    ) {
      await db.query(
        `CREATE TABLE IF NOT EXISTS ${label} PARTITION OF ride_location_points FOR VALUES FROM ('${month.toISOString()}') TO ('${end.toISOString()}')`,
      );
    }
    await db.query("DELETE FROM ride_location_points WHERE recorded_at<now()-interval '30 days'");
    await db.query(
      "DELETE FROM ride_events e USING rides r WHERE e.ride_id=r.id AND r.state IN ('COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW') AND e.created_at<now()-interval '90 days'",
    );
    await db.query(
      "UPDATE fare_quotes q SET route=q.route-'legs' WHERE created_at<now()-interval '90 days' AND route ? 'legs' AND EXISTS(SELECT 1 FROM rides r WHERE r.quote_id=q.id AND r.state IN ('COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW'))",
    );
    await db.query(
      "DELETE FROM outbox_events WHERE published_at IS NOT NULL AND ((type='driver.location' AND created_at<now()-interval '30 days') OR created_at<now()-interval '90 days')",
    );
    await db.query(
      "DELETE FROM jobs WHERE (done_at IS NOT NULL OR dead) AND run_at<now()-interval '90 days'",
    );
    await db.query("DELETE FROM notifications WHERE created_at<now()-interval '90 days'");
    await db.query(
      "DELETE FROM fare_quotes q WHERE q.created_at<now()-interval '90 days' AND NOT EXISTS(SELECT 1 FROM rides r WHERE r.quote_id=q.id)",
    );
    await db.query("DELETE FROM driver_locations WHERE received_at<now()-interval '30 days'");
    await db.query(
      "DELETE FROM otp_challenges WHERE created_at<now()-interval '1 day' AND NOT EXISTS(SELECT 1 FROM jobs j WHERE j.kind='otp' AND j.payload->>'challengeId'=otp_challenges.id::text AND j.done_at IS NULL AND NOT j.dead)",
    );
    await db.query(
      "UPDATE jobs SET payload='{}' WHERE kind='otp' AND (dead OR done_at IS NOT NULL OR NOT EXISTS(SELECT 1 FROM otp_challenges c WHERE c.id::text=payload->>'challengeId' AND c.expires_at>now()))",
    );
    await db.query(`INSERT INTO surge_state(area_id,category,multiplier)
      SELECT a.id,c.category,CASE WHEN rules.enabled=false THEN 1 ELSE LEAST(COALESCE(rules.maximum,2),GREATEST(1,COALESCE(demand.n,0)::numeric/GREATEST(COALESCE(supply.n,0),1))) END
      FROM service_areas a CROSS JOIN (VALUES ('TUK'),('CAR'),('VAN')) c(category)
      LEFT JOIN surge_rules rules ON rules.area_id=a.id AND rules.category=c.category
      LEFT JOIN LATERAL(SELECT count(*) n FROM rides r JOIN fare_quotes q ON q.id=r.quote_id WHERE r.state='SEARCHING' AND q.area_id=a.id AND q.category=c.category) demand ON true
      LEFT JOIN LATERAL(SELECT count(*) n FROM driver_profiles d JOIN vehicles v ON v.driver_id=d.user_id AND v.status='APPROVED' JOIN driver_locations l ON l.driver_id=d.user_id WHERE d.status='APPROVED' AND d.availability='AVAILABLE' AND v.category=c.category AND l.received_at>now()-interval '30 seconds' AND ST_Covers(a.polygon,l.point::geometry)) supply ON true
      WHERE a.active ON CONFLICT(area_id,category) DO UPDATE SET multiplier=EXCLUDED.multiplier,updated_at=now()`);
    await db.query(
      "INSERT INTO system_state(key,value) VALUES('maintenance','true') ON CONFLICT(key) DO UPDATE SET updated_at=now()",
    );
  });
}
export async function workerTick(deps) {
  await deps.pool.query(
    "INSERT INTO system_state(key,value) VALUES('worker.heartbeat','true') ON CONFLICT(key) DO UPDATE SET updated_at=now()",
  );
  await dispatchTick(deps.pool, deps.maps);
  await publishOutbox(deps);
  await processJobs(deps);
  await maintenance(deps.pool);
}
