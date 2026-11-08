# API and realtime contract

Full request schemas: [OpenAPI](openapi.json). Regenerate with `npm run docs:api`; the running API exposes `/openapi.json`.

## General rules

- API prefix `/v1`; JSON; UUID identifiers; UTC ISO-8601 timestamps.
- Coordinates are `[longitude, latitude]`; distances in metres; durations in seconds; monetary values in LKR minor units.
- `Authorization: Bearer <JWT>` is required, except for public OTP/staff login/refresh endpoints.
- Protected responses must not be cached by shared proxies/CDNs.
- Important writes require `Idempotency-Key` of 8–128 characters. The same body with the same user/endpoint/key returns the original response; a different body yields `409 IDEMPOTENCY_MISMATCH`.
- Error format: `{code,message,details?,requestId}`. `400` validation; `401` session; `403` authorization; `409` state/concurrency; `422` route/GPS; `429` rate limit; `503` dependency.
- Lists use `limit` 1–100 and a UUID `cursor`. Results are ordered by descending UUID; do not treat the cursor as a timestamp. Audit uses a bigint cursor.

## Quotes and bookings

```json
{
  "waypoints": [[81.6973617, 7.7060005], [81.7005996, 7.7099703]],
  "category": "TUK",
  "tripType": "RETURN"
}
```

`POST /v1/fare-quotes` returns `id`, `estimated_minor`, `expires_at`, `route`, and `fare_snapshot`. For RETURN trips, the server appends pickup as the final waypoint; the client must not append it again.

`POST /v1/rides` body: `{"quoteId":"<uuid>"}`; response: `{rideId,state,ridePin}`. Only the rider receives the PIN. `GET /v1/rides/:id` returns the current snapshot, route, payment, cancellation charge, and active driver location. A terminal ride does not expose the driver's new location.

Driver lifecycle command: `POST /v1/rides/:id/actions`, body `{action,pin?}`. Actions: `EN_ROUTE`, `ARRIVE`, `START`, `AT_STOP`, `WAIT`, `RETURN`, `COMPLETE`, `NO_SHOW`. START requires a PIN. ARRIVE/AT_STOP/COMPLETE/NO_SHOW require fresh GPS within 300 m of the relevant location.

## Driver documents

1. Obtain metadata and a presigned upload URL using `POST /v1/drivers/documents`.
2. Upload with the original Content-Type/Content-Length.
3. Queue scanning using `POST /v1/drivers/documents/:id/submit`.
4. Scanning starts six minutes after the upload URL expires; file signatures and ClamAV results are checked.
5. Versioned S3 serves the exact scanned object version. R2 stores scanned bytes under a separate verified key that cannot be changed using the user upload URL; review downloads serve only that copy.

Driver documents: IDENTITY, LICENSE. Vehicle documents: REGISTRATION, INSURANCE. MIME: JPEG/PNG/PDF; maximum 10 MiB. Approval/availability requires all documents to be CLEAN and unexpired.

## Socket.IO

Connection: Socket.IO v4, path `/socket.io`, handshake `auth: {token: accessToken}`. This is not raw WebSocket; a Socket.IO client is required.

Client events:

- `ride.subscribe`: `{rideId}`; requires ownership or `rides.monitor` permission.
- `driver.location`: `{coordinates,accuracy,sequence,recordedAt}`; REST `/v1/locations` fallback.

Acknowledgement: `{ok:true,data}` or `{ok:false,code}`. Disconnect on an expired/revoked session and reconnect using a new JWT.

Server events: `driver.offer`, `ride.updated`, `driver.location`, `payment.updated`, `driver.reviewed`.

```json
{
  "eventId": "uuid",
  "type": "ride.updated",
  "aggregateId": "ride-uuid",
  "version": 3,
  "occurredAt": "2026-11-02T10:00:00.000Z",
  "payload": {"rideId":"ride-uuid","state":"DRIVER_ASSIGNED","version":3}
}
```

Delivery is at least once; clients must deduplicate by `eventId`. Ignore ride events with older versions and fetch the REST snapshot after a version gap or reconnection. Use sequence numbers for location streams; these are not stored in the durable notification inbox.

## Administrative finance

`CREDIT` adjustment: driver incentive/reimbursement. `SETTLEMENT`: confirmed receipt of payment from driver to platform. These APIs do not transfer external funds; a reference and reason are mandatory.

Payout: create → APPROVE by another person → bank transfer → PAID with a unique bankReference. Drivers with disputed/uncollected ride payments cannot receive payouts.

During dispute resolution, `amountMinor` is the full corrected fare. If `cashConfirmed=true`, finance must confirm the driver holds that full amount. Before removing an incorrect previous cash record, complete any actual bank refund/repayment and add its reference. Partial cash collection is not supported in this release.

Cancellation/no-show charges are platform receivables; they should only be closed through the cancellation-settlement API after the bank receipt has been verified.
