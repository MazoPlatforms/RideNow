# API மற்றும் realtime ஒப்பந்தம்

முழு request schemas: [OpenAPI](openapi.json). `npm run docs:api` மூலம் மீண்டும் உருவாக்கலாம்; இயங்கும் API-ல் `/openapi.json` கிடைக்கும்.

## பொதுவான விதிகள்

- API prefix `/v1`; JSON; UUID அடையாளங்கள்; timestamps UTC ISO-8601.
- Coordinates `[longitude, latitude]`; distances metres; durations seconds; பணம் LKR minor units.
- `Authorization: Bearer <JWT>` தேவை; OTP/staff login/refresh மட்டும் public.
- Protected responses-ஐ shared proxy/CDN cache-ல் சேமிக்கக்கூடாது.
- முக்கிய writes-க்கு `Idempotency-Key`: 8–128 characters. ஒரே user/endpoint/key-க்கு அதே body என்றால் முதற்பதில்; வேறு body என்றால் `409 IDEMPOTENCY_MISMATCH`.
- Error வடிவம்: `{code,message,details?,requestId}`. `400` validation; `401` session; `403` authorization; `409` state/concurrency; `422` route/GPS; `429` rate limit; `503` dependency.
- பட்டியல்களுக்கு `limit` 1–100, `cursor` UUID. UUID descending வரிசை; cursor-ஐ நேரமாகப் பொருள்படுத்தக்கூடாது. Audit-க்கு bigint cursor.

## Quote மற்றும் booking

```json
{
  "waypoints": [[81.6973617, 7.7060005], [81.7005996, 7.7099703]],
  "category": "TUK",
  "tripType": "RETURN"
}
```

`POST /v1/fare-quotes` பதிலில் `id`, `estimated_minor`, `expires_at`, `route`, `fare_snapshot`. RETURN-க்கு pickup server-ல் இறுதி waypoint ஆக இணைக்கப்படும்; client மீண்டும் pickup சேர்க்க வேண்டாம்.

`POST /v1/rides` body: `{"quoteId":"<uuid>"}`; பதில்: `{rideId,state,ridePin}`. PIN rider-க்கு மட்டும். `GET /v1/rides/:id` மூலம் தற்போதைய snapshot, route, payment, cancellation charge, active driver location பெறலாம். Terminal ride-ல் driver-ன் புதிய location வெளியிடப்படாது.

Driver lifecycle command: `POST /v1/rides/:id/actions`, body `{action,pin?}`. Actions: `EN_ROUTE`, `ARRIVE`, `START`, `AT_STOP`, `WAIT`, `RETURN`, `COMPLETE`, `NO_SHOW`. START-க்கு PIN; ARRIVE/AT_STOP/COMPLETE/NO_SHOW-க்கு தொடர்புடைய இடத்திலிருந்து 300 m-க்குள் fresh GPS தேவை.

## Driver documents

1. `POST /v1/drivers/documents` மூலம் metadata மற்றும் presigned upload URL பெறுதல்.
2. அசல் Content-Type/Content-Length-உடன் upload செய்தல்.
3. `POST /v1/drivers/documents/:id/submit` மூலம் scan queue-ல் சேர்த்தல்.
4. Upload URL expiry-க்கு அப்பால் 6 நிமிடம் கழித்து scan நடைபெறும்; file signature மற்றும் ClamAV முடிவு சரிபார்க்கப்படும்.
5. Versioned S3-ல் scan செய்த exact object version வழங்கப்படும். R2-ல் scan செய்த bytes, பயனர் upload URL மூலம் மாற்ற முடியாத தனி verified key-ல் சேமிக்கப்படும்; review download அந்த நகலை மட்டுமே வழங்கும்.

Driver: IDENTITY, LICENSE. Vehicle: REGISTRATION, INSURANCE. MIME: JPEG/PNG/PDF; அதிகபட்சம் 10 MiB. அனைத்து ஆவணங்களும் CLEAN, காலாவதியாகாத நிலையில் இருந்தால்தான் approval/availability செல்லும்.

## Socket.IO

இணைப்பு: Socket.IO v4; path `/socket.io`; handshake `auth: {token: accessToken}`. Raw WebSocket protocol அல்ல; Socket.IO client தேவை.

Client events:

- `ride.subscribe`: `{rideId}`; ownership அல்லது `rides.monitor` permission தேவை.
- `driver.location`: `{coordinates,accuracy,sequence,recordedAt}`; REST `/v1/locations` fallback.

Acknowledgement: `{ok:true,data}` அல்லது `{ok:false,code}`. Expired/revoked session-ல் disconnect; புதிய JWT-உடன் மீண்டும் இணைக்கவும்.

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

At-least-once delivery; client `eventId` மூலம் duplicates நீக்க வேண்டும். Ride version குறைந்த event-ஐப் புறக்கணிக்கவும்; version gap/reconnect-ல் REST snapshot பெறவும். Location stream-க்கு sequence பயன்படுத்தவும்; அது durable notification inbox-ல் சேமிக்கப்படாது.

## நிர்வாகப் பணம்

`CREDIT` adjustment: driver incentive/reimbursement. `SETTLEMENT`: driver platform-க்கு பணம் செலுத்தியதற்கான உறுதிப்படுத்தப்பட்ட receipt. External transfer இந்த API-களால் நடைபெறாது; reference மற்றும் reason கட்டாயம்.

Payout: create → வேறு நபர் APPROVE → bank transfer → PAID + unique bankReference. Disputed/uncollected ride payments இருக்கும் driver-க்கு payout உருவாக்க முடியாது.

Dispute resolution-ல் கொடுக்கப்படும் `amountMinor` திருத்தப்பட்ட முழு fare; `cashConfirmed=true` என்றால் அதே முழுத் தொகை driver கையில் இருப்பதை finance உறுதிப்படுத்த வேண்டும். தவறான பழைய cash பதிவை நீக்கும்போது bank refund/repayment போன்ற உண்மையான செயல்களை முதலில் முடித்து reference சேர்க்கவும். Partial cash collection இந்த வெளியீட்டில் இல்லை.

Cancellation/no-show charge platform receivable; bank receipt சரிபார்க்கப்பட்ட பிறகே cancellation-settlement API மூலம் முடிக்க வேண்டும்.
