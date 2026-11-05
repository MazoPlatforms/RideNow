# Render Free + UptimeRobot முன்னோட்டம்

இந்த அமைப்பு கட்டுப்படுத்தப்பட்ட demo / உள் சோதனைக்கானது. தொடர்ச்சியான பயணச் சேவைக்கு கிடைக்கும் நேர உத்தரவாதம் இல்லை. Render-ல் ஒரு Free Web Service மட்டும் உருவாக்கவும்; புதிய database, Redis, paid worker அல்லது disk தேவையில்லை. ஏற்கெனவே உள்ள Supabase **ridenow-demo** (`bhjuxggttifldkrcrshj`), Upstash, R2 பயன்படுத்தப்படுகின்றன. வேறு `ridenow` Supabase திட்டத்தைப் பயன்படுத்தக்கூடாது. `ridenow` என்ற database schema மட்டும் ridenow-demo உள்ளே உள்ளது.

## சேவை அமைப்பு

Render workspace-ஐ உரிமையாளர் உறுதிப்படுத்திய பிறகே சேவை உருவாக்க வேண்டும்.

| அமைப்பு | மதிப்பு |
| --- | --- |
| வகை | Web Service |
| பெயர் | ridenow-demo-api (பெயர் கிடைப்பதை உறுதிப்படுத்தவும்) |
| Repository | https://github.com/MazoPlatforms/RideNow |
| Branch | main |
| Runtime | Node |
| Compute plan | Free |
| Build command | `npm ci --omit=dev` |
| Start command | `npm run start:pilot` |
| Health check path | `/health/ready` |
| Auto-deploy | முதலில் முடக்கவும்; சரிபார்த்த commit-ஐ deploy செய்யவும் |

`start:pilot` API, worker, WhatsApp gateway ஆகியவற்றை மூன்று child processes-ஆக ஒரே instance-ல் இயக்குகிறது. ஒன்று நின்றாலும் மற்றவற்றை நிறுத்தி service-ஐ தோல்வி exit code-உடன் முடிக்கும்; hosting platform மீண்டும் தொடங்கலாம். SIGTERM/SIGINT எல்லா child processes-க்கும் அனுப்பப்படும்; 20 விநாடிக்கு மேலாக நிற்காத process கட்டாயமாக நிறுத்தப்படும். ஒவ்வொரு process-க்கும் PostgreSQL pool இயல்பாக 4 connections; மொத்தம் அதிகபட்சம் 12. Instance memory/CPU போதுமா என்பதை உண்மையான deployment metrics-ல் அளக்க வேண்டும்; இன்னும் capacity நிரூபிக்கப்படவில்லை.

Deploy overlap நேரத்தில் புதிய WhatsApp gateway பழைய gateway-ன் database lock விடுவிக்கப்படும் வரை காத்திருக்கும். பழைய instance ஓய்ந்ததும் அதே encrypted database session-ஐத் தொடரும். Build அல்லது startup-ல் migrations/seed/admin creation தானாக இயங்காது. Private schema installer workflow-ஐ தனியாக இயக்க வேண்டும்; அது ஏற்கெனவே வெற்றியடைந்துள்ளது.

## Render Environment

GitHub production Environment secrets தானாக Render-க்கு வராது. கீழ்க்கண்ட ஏற்கெனவே உள்ள மதிப்புகளை Render Environment-ல் தனியாகச் சேர்க்கவும்; chat, commit, build logs-ல் வெளியிட வேண்டாம்.

| ரகசியம் | பயன்பாடு |
| --- | --- |
| `DATABASE_URL` | ridenow-demo session pooler, port 5432; password URL encoded |
| `REDIS_URL` | Upstash TLS connection |
| `JWT_SECRET` | ஏற்கெனவே பயன்படுத்தும் signing secret |
| `OTP_PEPPER` | ஏற்கெனவே பயன்படுத்தும் தனி OTP secret |
| `ENCRYPTION_KEY` | அதே 64 hex key; மாற்றினால் பழைய credentials படிக்க முடியாது |
| `WA_AUTH_CREDS` | ஏற்கெனவே paired Baileys JSON; காலியான auth database-க்கு மட்டும் import |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | R2 credentials |

பொது configuration:

```dotenv
NODE_VERSION=24
NODE_ENV=production
OTP_PROVIDER=baileys
DB_SCHEMA=ridenow
DB_ROLE=ridenow_backend
DB_POOL_MAX=4
S3_REGION=auto
FCM_ENABLED=false
NODE_EXTRA_CA_CERTS=/etc/secrets/supabase-ca.crt
```

Production-ல் `OTP_PROVIDER` code மூலம் `baileys` ஆக நிர்ணயிக்கப்படுகிறது. Render-ல் இந்த variable இல்லாவிட்டாலும் அல்லது வேறு மதிப்பாக இருந்தாலும் Baileys மட்டுமே பயன்படுத்தப்படும். Development/test adapter தேர்வு தொடர்ந்து கிடைக்கும்.

CA-க்கு மாற்று அமைப்பு: `SUPABASE_CA_CERT` environment variable-ல் public PEM வைத்திருந்தால் `start:pilot` தற்காலிக CA file உருவாக்கி, child processes தொடங்கும் முன்பே `NODE_EXTRA_CA_CERTS` அமைக்கும். இந்த முறையில் மேலுள்ள `/etc/secrets/supabase-ca.crt` அமைப்பு தேவையில்லை. இரண்டையும் அமைத்தால் ஏற்கெனவே உள்ள CA file-உடன் புதிய CA சேர்க்கப்படும்; குறிப்பிடும் file உண்மையாக இருக்க வேண்டும். TLS certificate verification தொடர்ந்து இயங்கும்.

மேலும் `S3_ENDPOINT`, `S3_BUCKET` சேர்க்கவும். `CORS_ORIGINS`-ல் உண்மையான HTTPS frontend origin-களை comma-separated-ஆக சேர்க்கவும். அங்கீகரிக்கப்பட்ட vehicle categories-ஐ மட்டும் `MAP_CATEGORY_APPROVALS`-ல் குறிப்பிடவும்; சாலைச் சரிபார்ப்புக்கு முன் `TUK,CAR,VAN` என்று அனுமதி கொடுக்க வேண்டாம். `PORT` Render வழங்கும் மதிப்பிலேயே இருக்கட்டும்.

`TRUST_PROXY_HOPS`-ஐ actual proxy chain சரிபார்த்த பிறகே அமைக்கவும். தவறான அமைப்பு rate limiting-ஐ பாதிக்கும். நேரடி onrender.com ingress-ல் ஓர் proxy hop இருப்பதை உறுதிப்படுத்தினால் `1`; பின்னர் Cloudflare proxy சேர்த்தால் மீண்டும் சோதிக்கவும்.

Render Secret Files:

- `supabase-ca.crt`: GitHub Variable `SUPABASE_CA_CERT`-ல் இருக்கும் public CA PEM. TLS verification-ஐ முடக்க வேண்டாம்.
- Firebase credential இந்த self-hosted notification அமைப்புக்குத் தேவையில்லை. பின்னர் FCM சேர்த்தால் மட்டும் `FCM_ENABLED=true` மற்றும் `GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-service-account.json` அமைத்து உண்மையான push delivery-ஐச் சோதிக்கவும்.

ஆவண malware scanning-க்கு Render-இலிருந்து அணுகக்கூடிய உண்மையான ClamAV endpoint தேவை: `CLAMAV_HOST`, `CLAMAV_PORT`. ClamAV TCP protocol-ல் authentication/TLS இல்லை; public internet-க்கு scanner port-ஐ நேரடியாகத் திறக்க வேண்டாம். பாதுகாக்கப்பட்ட network/tunnel அமைக்க வேண்டும். Scanner இல்லாமல் driver documents CLEAN ஆகாது. Free instance-க்குள் ClamAV-ஐயும் சேர்த்து இயக்குவது இந்த deployment அமைப்பின் பகுதி அல்ல. Rider profile avatars மட்டும் JPEG-ஆக decode/re-encode செய்யப்பட்டு metadata நீக்கப்படும்; அவற்றுக்கு ClamAV endpoint தேவையில்லை.

## UptimeRobot

Service live ஆன பிறகு மட்டுமே அதன் Render dashboard காட்டும் உண்மையான URL-ஐப் பயன்படுத்தவும்; service பெயரிலிருந்து URL-ஐ ஊகிக்க வேண்டாம்.

1. Add New Monitor → HTTP(s).
2. பெயர்: `RideNow demo readiness`.
3. URL: `https://<உண்மையான-render-host>/health/ready`.
4. Interval: Free திட்டத்தில் 5 minutes.
5. Alert contact-ஐத் தேர்ந்து down/recovery alerts இயங்குவதைச் சோதிக்கவும்.

இந்த endpoint PostgreSQL, Redis, production worker heartbeat ஆகியவற்றைச் சோதிக்கும். WhatsApp delivery, FCM delivery, R2 permissions, ClamAV ஆகியவற்றின் முழுச் செயல்பாட்டை இது நிரூபிக்காது. அவற்றைத் தனியாக end-to-end சோதிக்க வேண்டும். Monitor-க்கு JWT, OTP, API credentials சேர்க்க வேண்டாம். UptimeRobot account access/API credential இந்த repository-ல் இல்லை; monitor இன்னும் உருவாக்கப்படவில்லை.

## Deployment சரிபார்ப்பு

1. Render deploy `live` ஆக முடிந்திருக்க வேண்டும்; build success மட்டும் போதாது.
2. `/health/live` → 200; `/health/ready` → 200 மற்றும் `status=ready`.
3. Worker heartbeat, WhatsApp connection ஆகியவற்றைச் சரிபார்க்கவும். உரிமையாளர் ஒப்புக்கொண்ட எண்ணுக்கு ஒரு OTP அனுப்பி verify/replay rejection சோதிக்கவும்; ஒவ்வொரு deploy-க்கும் தானாக OTP அனுப்ப வேண்டாம்.
4. Restart/redeploy செய்த பிறகும் WhatsApp session மீளவும், pending jobs தொடரவும் வேண்டும்.
5. WebSocket notifications, reconnect பிறகு notification inbox, scanner வழியாக R2 upload/download, driver approval, booking → dispatch → accept → start → complete → cash ledger ஆகியவற்றைச் சோதிக்கவும்.
6. Free memory/CPU, connection counts, job backlog, latency அளந்த பிறகே குறைந்த எண்ணிக்கையிலான pilot பயனர்களை அனுமதிக்கவும்.

## இலவசத் திட்ட வரம்புகள்

2026-11-03 அன்று அதிகாரப்பூர்வ ஆவணங்கள் சரிபார்க்கப்பட்டன:

- [Render Free](https://render.com/docs/free): inbound HTTP/WebSocket traffic இல்லாமல் 15 நிமிடங்கள் கழிந்தால் sleep; மீண்டும் தொடங்க சுமார் ஒரு நிமிடம் ஆகலாம். எந்த நேரத்திலும் restart நிகழலாம்.
- ஒவ்வொரு workspace-க்கும் மாதம் 750 free instance hours பகிரப்படும். அதே workspace-ல் வேறு Free services இருந்தால் அவையும் நேரத்தைப் பயன்படுத்தும்.
- Free web instance: 0.1 CPU / 512 MB. External database/API/storage traffic அதிகமாக இருந்தாலும் Render suspend செய்யலாம்.
- Bandwidth/build usage தனி வரம்புகள்; payment method இருந்தால் கூடுதல் usage billing ஏற்படலாம். Budget/spend settings-ஐ உரிமையாளர் சரிபார்க்க வேண்டும்.
- [UptimeRobot Free](https://uptimerobot.com/pricing/): 5-minute monitoring. HTTP checks idle நேரத்தைக் குறைக்கலாம்; hosting suspension, restart, quota அல்லது downtime-ஐத் தடுக்க உத்தரவாதம் இல்லை.

உண்மையான பயணங்களின் dispatch, scheduled rides, live tracking, WhatsApp OTP ஆகியவற்றிற்கு தொடர்ந்து இயங்கும் paid infrastructure-க்கு மாறுவது pilot readiness மதிப்பீட்டின் பகுதி.

## அதே சேவையில் self-hosted notifications

Notification worker ஏற்கெனவே `start:pilot`-க்குள் இயங்குகிறது; தனி Render service தேவையில்லை. PostgreSQL outbox → notifications inbox → Redis → authenticated Socket.IO இணைப்பு என்ற வழியில் அறிவிப்புகள் செல்கின்றன. FCM இல்லாவிட்டாலும் inbox மற்றும் WebSocket தொடர்ந்து இயங்கும். `driver.location` போன்ற அதிக அதிர்வெண் tracking நிகழ்வுகள் inbox-ல் சேமிக்கப்படாது.

Android/iOS client Socket.IO protocol பயன்படுத்த வேண்டும்; raw WebSocket client மட்டும் போதாது. Handshake-ல் `auth: { token: accessToken }` அனுப்பவும். பயன்பாட்டுக்குரிய `ride.updated` போன்ற event-களைக் கேட்டு `eventId` மூலம் duplicate-களைத் தவிர்க்கவும். ஒவ்வொரு reconnect-க்கும் JWT உடன் `GET /v1/me/notifications` அழைத்து தவறவிட்ட அறிவிப்புகளைப் பெறவும்; வாசித்தபின் `POST /v1/me/notifications/:id/read`. Socket event வராத நேரத்திலும் PostgreSQL inbox இருக்கும்; 90 நாள் retention தற்போதைய worker policy.

`FCM_ENABLED=false` நிலையில் புதிய external push jobs உருவாகாது. பழைய push jobs இருந்தால் அவற்றின் attempts அதிகரிக்காமல் pending-ல் இருக்கும்; பின்னர் FCM இயக்கினால் அவை தொடரலாம். Push provider-ஐ இயக்குவதற்கு முன் பழைய அறிவிப்புகளின் பொருத்தத்தை ஆய்வு செய்யவும். Inbox read status என்பது சாதன OS push delivery receipt அல்ல.

இந்த முறை செயலி இணைந்திருக்கும் நேரத்தில் உடனடி அறிவிப்புகளையும், மீண்டும் திறந்தபின் inbox-ஐயும் வழங்கும். செயலி background/suspended/மூடப்பட்ட நிலையில் உடனடி OS notification உத்தரவாதம் இல்லை; iOS-க்கு APNs, வழக்கமான Android சாதனங்களுக்கு FCM போன்ற push transport தனியாகத் தேவை. Self-hosted worker மட்டும் அவற்றை மாற்றாது. Render Free sleep/restart நேரத்தில் உடனடி அனுப்புதலும் நிற்கும்; மீண்டும் தொடங்கியதும் pending outbox தொடரும்.
