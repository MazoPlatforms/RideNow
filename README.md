# Mazo பயணச் சேவை backend

Node.js 24, Vanilla JavaScript ES Modules, PostgreSQL/PostGIS, Redis, REST மற்றும் Socket.IO அடிப்படையிலான backend. இலங்கை, LKR, `Asia/Colombo`; உடனடிப் பயணம், நேர முன்பதிவு, அதே driver உடன் திரும்பும் பயணம்; Tuk/Car/Van.

## உள்ளூரில் இயக்குதல்

தேவை: Node.js 24, npm, Docker Compose. செயலியை host-ல் இயக்குவது தொடக்கத்திற்கு எளிதான வழி.

```sh
npm ci
cp .env.example .env
```

`.env`-ல் `JWT_SECRET`, `OTP_PEPPER` ஆகியவற்றிற்கு வெவ்வேறு குறைந்தது 32-character random மதிப்புகளும் `ENCRYPTION_KEY`-க்கு 32-byte hex மதிப்பும் அமைக்கவும். உதாரண random மதிப்பு உருவாக்க:

```sh
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
docker compose up -d postgres redis storage
npm run migrate
npm run seed
npm run storage:init
npm start
```

வேறொரு terminal-ல் `npm run worker`. ஆவணப் பரிசோதனைக்கு `docker compose --profile scanner up -d clamav` இயக்கவும். ClamAV signature database தயாராக நேரம் தேவைப்படலாம். `seed` development சேவைப் பகுதி மற்றும் **சோதனை விகிதங்களை** மட்டுமே உருவாக்கும்; production-ல் அனுமதிக்கப்படாது.

API: `http://localhost:3000`; `GET /health/live`, `GET /health/ready`, `GET /openapi.json`. `.env` மற்றும் private keys-ஐ commit செய்யக்கூடாது.

### Development OTP

`OTP_PROVIDER=database` நிலையில் WhatsApp அனுப்பப்படாது. API பதிலில் OTP வெளியிடப்படாது.

```sh
curl -X POST http://localhost:3000/v1/auth/otp/request \
  -H 'Content-Type: application/json' -d '{"phone":"0771234567"}'
npm run otp:dev
```

பெற்ற `challengeId`, code மூலம் `POST /v1/auth/otp/verify`. பிற API-களுக்கு `Authorization: Bearer <accessToken>`. Booking, cash confirmation உள்ளிட்ட முக்கிய writes-க்கு `Idempotency-Key` header தேவை. அனைத்து booking quotes-க்கும் 2 நிமிட expiry.

### WhatsApp மற்றும் நிர்வாகி

உண்மையான செய்தி அனுப்பும் GitHub சோதனை: [WhatsApp சோதனை வழிமுறைகள்](docs/whatsapp-testing.md). `production` Environment-ன் `WA_AUTH_CREDS` secret-ஐப் பயன்படுத்தும் தனி manual workflow வழங்கப்பட்டுள்ளது.

`OTP_PROVIDER=baileys` அமைத்து `npm run whatsapp` இயக்கவும். Terminal-ல் வரும் pairing QR payload-ஐ நம்பகமான local QR renderer-ல் காட்டி account-ஐ இணைக்கவும். QR payload-ஐ public QR இணையதளங்களில் பகிரக்கூடாது. Gateway credentials database-ல் AES-256-GCM மூலம் encrypt செய்யப்படும்; ஒரே gateway மட்டுமே advisory lock பெறும்.

Baileys அதிகாரப்பூர்வ WhatsApp Business API அல்ல. இணைப்பு இல்லையெனில் OTP request `503 OTP_PROVIDER_UNAVAILABLE`; மாற்று provider இணைக்க authentication code-ஐ மாற்றாமல் gateway adapter-ஐ மாற்றலாம். SMS adapter இந்த வெளியீட்டில் இல்லை.

```sh
npm run admin:create -- 0771234567
```

தற்காலிக browser setup, விருப்ப TOTP மற்றும் OTP இல்லாத நேரத்திற்கான recovery login விவரங்கள்: [Admin setup வழிகாட்டி](docs/admin-setup.md).

English admin dashboard: `/admin/`. Multi-page login, permissions, build commands மற்றும் browser checks: [Admin dashboard guide](docs/admin-dashboard.md).

கடவுச்சொல் stdin-ல் பெறப்படும்; `ADMIN_PASSWORD` மூலமும் வழங்கலாம். கிடைக்கும் TOTP secret-ஐ authenticator-ல் பதிவு செய்யவும். `POST /v1/auth/staff/login` வழியாக password + TOTP பயன்படுத்தவும். Rider OTP மூலம் Staff/Admin login அனுமதிக்கப்படாது. முதல் admin உருவாக்கியபின் Staff provision மற்றும் permissions API வழியாக நிர்வகிக்கலாம்.

## பயண ஓட்டம்

1. Rider `POST /v1/fare-quotes` மூலம் server உறுதிப்படுத்திய route quote பெறுதல்.
2. `POST /v1/rides` மூலம் booking. Rider-க்கு மட்டும் 4-digit ride PIN கிடைக்கும்.
3. Driver onboarding → vehicle/documents → scan → staff approval → fresh location → availability.
4. Worker nearby drivers-ஐ தேர்வு செய்து offer அனுப்பும்; `GET /v1/driver-offers` அல்லது WebSocket மூலம் பெறலாம்.
5. Offer accept → `ARRIVE` → rider PIN உடன் `START` → `COMPLETE`.
6. Return trip-ல் `AT_STOP` → விருப்பமாக `WAIT` → `RETURN` → pickup இடத்தில் `COMPLETE`.
7. Driver cash பெற்றபின் `POST /v1/rides/:id/cash`. Completion மட்டும் cash collection ஆகாது.

Scheduled quote-ல் `scheduledAt` UTC ISO நேரம் தேவை. குறைந்தது 30 நிமிடம் முன், அதிகபட்சம் 30 நாட்கள். புறப்பாட்டுக்கு 15 நிமிடம் முன் dispatch; pickup +5 நிமிடம் வரை தேடல். Driver கிடைக்கும்வரை assignment உறுதி கிடையாது. Reschedule-க்கு புதிய quote தேவை; dispatch தொடங்கிய பிறகு அனுமதியில்லை.

இறுதிக் கட்டணம்: booking route distance + உண்மையான driving நேரம் + stop waiting. Stop-ல் முதல் 5 நிமிடம் இலவசம்; waiting நேரம் driving நேரத்தில் மீண்டும் சேராது. Surge base/distance பகுதிகளுக்கு மட்டும். Fare-plan version மற்றும் surge booking-ல் நிலைநிறுத்தப்படும்.

Cash-only நிலையில் driver பெற்ற முழுப் பணத்திலிருந்து platform commission நிலுவை உருவாகும். உதாரணம்: LKR 1,000 fare, 20% commission என்றால் driver platform-க்கு LKR 200 செலுத்த வேண்டும்; LKR 800 மீண்டும் payout செய்யப்படாது. Driver credit இருந்தால்தான் payout உருவாகும்; maker/checker வெவ்வேறு நபர்களாக இருக்க வேண்டும்.

## சோதனைகள்

Render Free Tier-ல் API, worker, WhatsApp gateway ஆகியவற்றை ஒரே instance-ல் இயக்கவும் UptimeRobot கண்காணிப்பைச் சேர்க்கவும் [முன்னோட்ட deployment வழிகாட்டி](docs/render-free-pilot.md) பார்க்கவும்.

```sh
npm test
npm run test:storage
TEST_DATABASE_URL=postgres://ride:ride@localhost:5432/ride_test npm run test:integration
LOAD_DATABASE_URL=postgres://ride:ride@localhost:5432/ride_load_test npm run test:load
TEST_DATABASE_URL=postgres://ride:ride@localhost:5432/ride_test npm run demo
npm run check
npm run docs:api
npm audit --omit=dev --audit-level=moderate
```

Integration/load scripts தங்களுக்கான database-ஐ உருவாக்கி fixtures-ஐ மீளமைக்கும். **Production database URL பயன்படுத்தக்கூடாது.** பெயர் `_test` / `_load_test` என்று முடிவதை scripts கட்டாயப்படுத்துகின்றன. `CREATE DATABASE` அனுமதி இல்லாவிட்டால் databases-ஐ முன்பே உருவாக்கவும்.

Default integration port `5432`; default Redis integration DB `15`, load DB `14`. `TEST_REDIS_URL`, `LOAD_REDIS_URL` மூலம் மாற்றலாம். இந்த Redis databases சோதனையில் flush செய்யப்படும்; தனி test Redis பயன்படுத்தவும்.

PostgreSQL/PostGIS, transactions, locks, constraints, migrations, Redis மற்றும் WebSocket உண்மையாக இயக்கப்படுகின்றன. WhatsApp/FCM delivery-க்கு deterministic adapters பயன்படுத்தப்படுகின்றன. தனி `test:storage` suite உண்மையான versioned S3-compatible storage மற்றும் கட்டுப்படுத்தப்பட்ட ClamAV TCP fixture-ஐப் பயன்படுத்துகிறது; உண்மையான malware-engine signatures தனியாக smoke-test செய்யப்பட வேண்டும். Mazo graph உண்மையானது. [அளவீட்டு அறிக்கை](docs/load-report.json) உள்ளூர் 1,000-driver சோதனையின் முடிவு; production SLA உத்தரவாதம் அல்ல.

## Production தயார்நிலை

`ridenow-demo` தனி schema, migrations மற்றும் role அனுமதிகள்: [தரவுத்தள அமைப்பு](docs/private-database.md).

Supabase/Upstash secrets மற்றும் நேரடி இணைப்புச் சோதனை: [வழிமுறைகள்](docs/managed-services.md). Cloudflare R2: [சேமிப்பக வழிமுறைகள்](docs/r2-storage.md).

Production-க்கு முன் [இயக்கக் கையேடு](docs/operations.md), [API ஒப்பந்தம்](docs/api.md), [பாதுகாப்பு மற்றும் தரவு விதிகள்](docs/security.md) ஆகியவற்றைப் பின்பற்றவும்.

- உண்மையான fare/commission விகிதங்கள், service-area polygons, தேவையான ஆவணங்கள் மற்றும் retention சட்ட விதிகள் உறுதிசெய்யப்பட வேண்டும்.
- Private S3/R2 bucket, ClamAV, இணைக்கப்பட்ட Baileys account தேவை. Self-hosted notification inbox/WebSocket-க்கு Firebase தேவையில்லை; FCM push தேர்ந்தெடுத்தால் மட்டும் அதன் credentials தேவை. R2-க்கு scan செய்த bytes தனி verified நகலாகப் பாதுகாக்கப்படும்; versioned S3-க்கு object version pin பயன்படுத்தப்படும்.
- `MAP_CATEGORY_APPROVALS`-ல் உறுதிசெய்யப்பட்ட வகைகளை மட்டும் குறிப்பிடவும். தற்போதைய graph-ல் turn-restriction relations இல்லை; பொதுவான driving பாதையை எல்லா வகைகளுக்கும் சட்டபூர்வமானதாகக் கருதக்கூடாது.
- Production config HTTPS origins, Baileys, S3 ஆகியவற்றைக் கட்டாயப்படுத்தும்; FCM விருப்ப அமைப்பு. Routing coverage-க்கு வெளியே quote மறுக்கப்படும்; public demo OSRM fallback இல்லை.
- Dockerfile வழங்கப்பட்டுள்ளது; இந்த பணிச்சூழலில் முழு application image build மற்றும் வெளிச்சேவை delivery smoke test இயக்கப்படவில்லை.

Mazo routing core மற்றும் place decoder மூலத்துடன் upstream GPL-3.0 உரிமம் `vendor/mazo/LICENSE`-ல் உள்ளது. தரவு மூலங்கள் மற்றும் checksum விவரங்கள் [NOTICE](NOTICE.md)-ல் உள்ளன.
