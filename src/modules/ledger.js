import { one, audit } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
export async function account(db, code, userId = null) {
  // Serialize identity creation even when NULL would otherwise race.
  await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,2))', [`${code}:${userId}`]);
  return (
    (await one(
      db,
      'SELECT id FROM ledger_accounts WHERE code=$1 AND user_id IS NOT DISTINCT FROM $2::uuid',
      [code, userId],
    )) ??
    (await one(db, 'INSERT INTO ledger_accounts(code,user_id) VALUES($1,$2) RETURNING id', [
      code,
      userId,
    ]))
  ).id;
}
export async function post(db, { reference, kind, rideId = null, reversalOf = null, entries }) {
  ensure(
    entries.length >= 2 && entries.reduce((s, e) => s + e.debit - e.credit, 0) === 0,
    500,
    'LEDGER_UNBALANCED',
  );
  const t = await one(
    db,
    'INSERT INTO ledger_transactions(reference,kind,ride_id,reversal_of) VALUES($1,$2,$3,$4) ON CONFLICT(reference) DO NOTHING RETURNING id',
    [reference, kind, rideId, reversalOf],
  );
  if (!t) return;
  for (const e of entries) {
    if (!e.debit && !e.credit) continue;
    const a = await account(db, e.code, e.userId);
    await db.query(
      'INSERT INTO ledger_entries(transaction_id,account_id,debit,credit) VALUES($1,$2,$3,$4)',
      [t.id, a, e.debit, e.credit],
    );
  }
  return t.id;
}
const entry = (code, userId, debit = 0, credit = 0) => ({ code, userId, debit, credit });
export async function bookFare(db, ride, plan) {
  const commission = Math.round((ride.final_minor * plan.commission_bps) / 10000),
    net = ride.final_minor - commission;
  await post(db, {
    reference: `fare:${ride.id}`,
    kind: 'FARE',
    rideId: ride.id,
    entries: [
      entry('RIDER_RECEIVABLE', ride.rider_id, ride.final_minor),
      entry('DRIVER_PAYABLE', ride.driver_id, 0, net),
      entry('PLATFORM_REVENUE', null, 0, commission),
    ],
  });
}
export async function collectCash(db, ride, user) {
  ensure(ride.driver_id === user.id, 403, 'FORBIDDEN');
  ensure(ride.state === 'COMPLETED', 409, 'RIDE_NOT_COMPLETED');
  const payment = await one(db, 'SELECT * FROM payments WHERE ride_id=$1 FOR UPDATE', [ride.id]);
  ensure(payment, 404, 'PAYMENT_NOT_FOUND');
  if (payment.status === 'COLLECTED') return payment;
  ensure(payment.status === 'DUE', 409, 'PAYMENT_DISPUTED');
  await post(db, {
    reference: `cash:${ride.id}:${ride.version}`,
    kind: 'CASH_COLLECTION',
    rideId: ride.id,
    entries: [
      entry('DRIVER_CASH_RECEIVABLE', user.id, ride.final_minor),
      entry('RIDER_RECEIVABLE', ride.rider_id, 0, ride.final_minor),
    ],
  });
  return one(
    db,
    "UPDATE payments SET status='COLLECTED',collected_at=now(),collected_by=$2 WHERE ride_id=$1 RETURNING *",
    [ride.id, user.id],
  );
}
export async function balance(db, driverId) {
  return one(
    db,
    `SELECT COALESCE(sum(CASE WHEN a.code='DRIVER_PAYABLE' THEN e.credit-e.debit WHEN a.code='DRIVER_CASH_RECEIVABLE' THEN e.credit-e.debit ELSE 0 END),0)::bigint AS net_minor,
    COALESCE(sum(CASE WHEN a.code='DRIVER_PAYABLE' THEN e.credit-e.debit ELSE 0 END),0)::bigint AS earned_payable_minor,
    COALESCE(sum(CASE WHEN a.code='DRIVER_CASH_RECEIVABLE' THEN e.debit-e.credit ELSE 0 END),0)::bigint AS cash_receivable_minor
    FROM ledger_accounts a LEFT JOIN ledger_entries e ON e.account_id=a.id WHERE a.user_id=$1`,
    [driverId],
  );
}
export async function adjustment(db, user, body) {
  ensure(
    await one(db, 'SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [
      body.driverId,
    ]),
    404,
    'DRIVER_NOT_FOUND',
  );
  const entries =
    body.kind === 'CREDIT'
      ? [
          entry('DRIVER_EXPENSE', null, body.amountMinor),
          entry('DRIVER_PAYABLE', body.driverId, 0, body.amountMinor),
        ]
      : [
          entry('BANK', null, body.amountMinor),
          entry('DRIVER_CASH_RECEIVABLE', body.driverId, 0, body.amountMinor),
        ];
  await post(db, { reference: `adjustment:${body.reference}`, kind: body.kind, entries });
  await db.query(
    'INSERT INTO reconciliations(reference,driver_id,kind,amount_minor,actor_id,reason) VALUES($1,$2,$3,$4,$5,$6)',
    [body.reference, body.driverId, body.kind, body.amountMinor, user.id, body.reason],
  );
  await audit(db, user.id, 'FINANCE_ADJUSTMENT', body.driverId, { ...body });
  return balance(db, body.driverId);
}
export async function createPayout(db, user, body) {
  ensure(
    await one(db, 'SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [
      body.driverId,
    ]),
    404,
    'DRIVER_NOT_FOUND',
  );
  const b = await balance(db, body.driverId),
    pending = await one(
      db,
      "SELECT COALESCE(sum(amount_minor),0)::bigint AS total FROM payouts WHERE driver_id=$1 AND status IN ('PENDING','APPROVED')",
      [body.driverId],
    );
  ensure(
    Number(b.net_minor) - Number(pending.total) >= body.amountMinor,
    409,
    'INSUFFICIENT_DRIVER_CREDIT',
  );
  // Do not payout earnings for rides whose rider cash is still uncollected.
  ensure(
    !(await one(
      db,
      "SELECT 1 FROM payments p JOIN rides r ON r.id=p.ride_id WHERE r.driver_id=$1 AND p.status<>'COLLECTED' LIMIT 1",
      [body.driverId],
    )),
    409,
    'UNSETTLED_RIDE_PAYMENTS',
  );
  const payout = await one(
    db,
    'INSERT INTO payouts(driver_id,amount_minor,maker_id,reason) VALUES($1,$2,$3,$4) RETURNING *',
    [body.driverId, body.amountMinor, user.id, body.reason],
  );
  await audit(db, user.id, 'PAYOUT_CREATED', payout.id, {});
  return payout;
}
export async function payoutAction(db, user, id, body) {
  const initial = ensure(
    await one(db, 'SELECT driver_id FROM payouts WHERE id=$1', [id]),
    404,
    'PAYOUT_NOT_FOUND',
  );
  await db.query('SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [
    initial.driver_id,
  ]);
  const p = await one(db, 'SELECT * FROM payouts WHERE id=$1 FOR UPDATE', [id]);
  if (body.action === 'APPROVE') {
    ensure(p.status === 'PENDING' && p.maker_id !== user.id, 409, 'PAYOUT_APPROVAL_INVALID');
    await db.query("UPDATE payouts SET status='APPROVED',approver_id=$2 WHERE id=$1", [
      id,
      user.id,
    ]);
  } else if (body.action === 'REJECT') {
    ensure(p.status === 'PENDING' || p.status === 'APPROVED', 409, 'PAYOUT_STATE_INVALID');
    await db.query("UPDATE payouts SET status='REJECTED' WHERE id=$1", [id]);
  } else {
    ensure(p.status === 'APPROVED', 409, 'PAYOUT_STATE_INVALID');
    ensure(body.bankReference, 400, 'BANK_REFERENCE_REQUIRED');
    const b = await balance(db, p.driver_id);
    ensure(Number(b.net_minor) >= p.amount_minor, 409, 'INSUFFICIENT_DRIVER_CREDIT');
    await post(db, {
      reference: `payout:${id}`,
      kind: 'PAYOUT',
      entries: [
        entry('DRIVER_PAYABLE', p.driver_id, p.amount_minor),
        entry('BANK', null, 0, p.amount_minor),
      ],
    });
    await db.query("UPDATE payouts SET status='PAID',bank_reference=$2 WHERE id=$1", [
      id,
      body.bankReference,
    ]);
  }
  await audit(db, user.id, `PAYOUT_${body.action}`, id, {});
  return one(db, 'SELECT * FROM payouts WHERE id=$1', [id]);
}
export async function chargeCancellation(db, ride) {
  const q = await one(db, 'SELECT fare_snapshot FROM fare_quotes WHERE id=$1', [ride.quote_id]);
  const amount = q.fare_snapshot.cancellation_minor ?? 0;
  if (!amount) return;
  await db.query(
    'INSERT INTO cancellation_charges(ride_id,rider_id,amount_minor) VALUES($1,$2,$3) ON CONFLICT DO NOTHING',
    [ride.id, ride.rider_id, amount],
  );
  await post(db, {
    reference: `cancel:${ride.id}`,
    kind: 'CANCELLATION',
    rideId: ride.id,
    entries: [
      entry('RIDER_RECEIVABLE', ride.rider_id, amount),
      entry('PLATFORM_REVENUE', null, 0, amount),
    ],
  });
}
export async function settleCancellation(db, user, id, body) {
  const c = ensure(
    await one(db, 'SELECT * FROM cancellation_charges WHERE ride_id=$1 FOR UPDATE', [id]),
    404,
    'CHARGE_NOT_FOUND',
  );
  ensure(c.status === 'DUE', 409, 'CHARGE_ALREADY_SETTLED');
  await post(db, {
    reference: `cancellation-settlement:${body.reference}`,
    kind: 'CANCELLATION_SETTLEMENT',
    rideId: id,
    entries: [
      entry('BANK', null, c.amount_minor),
      entry('RIDER_RECEIVABLE', c.rider_id, 0, c.amount_minor),
    ],
  });
  await db.query(
    "UPDATE cancellation_charges SET status='SETTLED',reference=$2,settled_by=$3 WHERE ride_id=$1",
    [id, body.reference, user.id],
  );
  await db.query(
    "INSERT INTO reconciliations(reference,ride_id,kind,amount_minor,actor_id,reason) VALUES($1,$2,'CANCELLATION',$3,$4,$5)",
    [body.reference, id, c.amount_minor, user.id, body.reason],
  );
  await audit(db, user.id, 'CANCELLATION_SETTLEMENT', id, body);
  return { settled: true };
}
export async function reverse(db, transactionId, reference) {
  const t = ensure(
    await one(db, 'SELECT * FROM ledger_transactions WHERE id=$1', [transactionId]),
    404,
    'TRANSACTION_NOT_FOUND',
  );
  ensure(!t.reversal_of, 409, 'CANNOT_REVERSE_REVERSAL');
  const entries = (
    await db.query(
      'SELECT a.code,a.user_id AS "userId",e.credit::float8 AS debit,e.debit::float8 AS credit FROM ledger_entries e JOIN ledger_accounts a ON a.id=e.account_id WHERE transaction_id=$1',
      [transactionId],
    )
  ).rows;
  return post(db, {
    reference,
    kind: 'REVERSAL',
    rideId: t.ride_id,
    reversalOf: transactionId,
    entries,
  });
}
export async function openDispute(db, user, id, reason) {
  ensure(await one(db, 'SELECT id FROM rides WHERE id=$1 FOR UPDATE', [id]), 404, 'RIDE_NOT_FOUND');
  const payment = ensure(
    await one(db, 'SELECT * FROM payments WHERE ride_id=$1 FOR UPDATE', [id]),
    404,
    'PAYMENT_NOT_FOUND',
  );
  const dispute = await one(
    db,
    'INSERT INTO payment_disputes(ride_id,reason,opened_by) VALUES($1,$2,$3) RETURNING *',
    [id, reason, user.id],
  );
  await db.query("UPDATE payments SET status='DISPUTED' WHERE ride_id=$1", [id]);
  await audit(db, user.id, 'PAYMENT_DISPUTED', id, { reason, previousStatus: payment.status });
  return dispute;
}
export async function resolveDispute(db, user, id, body) {
  const d = ensure(
    await one(db, 'SELECT * FROM payment_disputes WHERE id=$1 FOR UPDATE', [id]),
    404,
    'DISPUTE_NOT_FOUND',
  );
  ensure(d.status === 'OPEN', 409, 'DISPUTE_RESOLVED');
  const r = await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE', [d.ride_id]);
  await db.query('SELECT user_id FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [r.driver_id]);
  const q = await one(db, 'SELECT fare_snapshot FROM fare_quotes WHERE id=$1', [r.quote_id]);
  const current = (
    await db.query(
      "SELECT t.id FROM ledger_transactions t WHERE ride_id=$1 AND kind IN ('FARE','FARE_ADJUSTMENT','CASH_COLLECTION','CASH_ADJUSTMENT') AND NOT EXISTS(SELECT 1 FROM ledger_transactions x WHERE x.reversal_of=t.id) ORDER BY created_at,id",
      [r.id],
    )
  ).rows;
  for (const t of current) await reverse(db, t.id, `resolution:${id}:reverse:${t.id}`);
  const commission = Math.round((body.amountMinor * q.fare_snapshot.commission_bps) / 10000);
  await post(db, {
    reference: `resolution:${id}:fare`,
    kind: 'FARE_ADJUSTMENT',
    rideId: r.id,
    entries: [
      entry('RIDER_RECEIVABLE', r.rider_id, body.amountMinor),
      entry('DRIVER_PAYABLE', r.driver_id, 0, body.amountMinor - commission),
      entry('PLATFORM_REVENUE', null, 0, commission),
    ],
  });
  if (body.cashConfirmed)
    await post(db, {
      reference: `resolution:${id}:cash`,
      kind: 'CASH_ADJUSTMENT',
      rideId: r.id,
      entries: [
        entry('DRIVER_CASH_RECEIVABLE', r.driver_id, body.amountMinor),
        entry('RIDER_RECEIVABLE', r.rider_id, 0, body.amountMinor),
      ],
    });
  await db.query('UPDATE rides SET final_minor=$2,version=version+1,updated_at=now() WHERE id=$1', [
    r.id,
    body.amountMinor,
  ]);
  await db.query(
    "UPDATE payments SET amount_minor=$2,status=$3,collected_at=CASE WHEN $3='COLLECTED' THEN now() END,collected_by=CASE WHEN $3='COLLECTED' THEN $4::uuid END WHERE ride_id=$1",
    [r.id, body.amountMinor, body.cashConfirmed ? 'COLLECTED' : 'DUE', user.id],
  );
  await db.query(
    "UPDATE payment_disputes SET status='RESOLVED',resolution=$2,resolved_at=now() WHERE id=$1",
    [id, body.reason],
  );
  await db.query(
    "INSERT INTO reconciliations(reference,ride_id,driver_id,kind,amount_minor,actor_id,reason) VALUES($1,$2,$3,'DISPUTE',$4,$5,$6)",
    [body.reference, r.id, r.driver_id, body.amountMinor, user.id, body.reason],
  );
  await audit(db, user.id, 'DISPUTE_RESOLVED', id, body);
  return { resolved: true, rideId: r.id, amountMinor: body.amountMinor };
}
