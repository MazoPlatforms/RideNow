# Pilot முன்னோட்டச் சரிபார்ப்பு — 2026-11-04

**முடிவு: backend அடிப்படைச் சோதனைகள் வெற்றி; உண்மையான பயணிகளுடன் pilot தொடங்க இன்னும் தயாரில்லை.** இந்த ஆய்வு `437bc3dee540decb13d024a786163fd62fddbc59` பதிப்பைச் சரிபார்த்தது. Website கோப்புகள் மாற்றப்படவில்லை. Production-ல் புதிய OTP, driver, ஆவணம், booking அல்லது financial transaction உருவாக்கப்படவில்லை. Cloud storage probe மட்டும் தனிப்பட்ட temporary objects உருவாக்கி அவற்றைச் சுத்தம் செய்தது.

## வெற்றியடைந்த சோதனைகள்

| பகுதி | முடிவு |
| --- | --- |
| JavaScript syntax | `npm run check` வெற்றி |
| Unit மற்றும் website routes | 17/17 வெற்றி |
| PostgreSQL/PostGIS, Redis, API, WebSocket integration | 32/32 வெற்றி |
| Versioned S3 மற்றும் immutable storage | 2/2 வெற்றி; scanner ஒரு கட்டுப்படுத்தப்பட்ட protocol fixture |
| Dependency audit | `npm audit --omit=dev --audit-level=moderate`: 0 vulnerabilities |
| API documentation | 56 API paths; generated OpenAPI-ல் மாற்றம் இல்லை |
| Production HTTP | 13 checks வெற்றி: website, assets, health, OpenAPI, authentication தடுப்பு, invalid challenge, API JSON 404, அனுமதிக்காத CORS origin |
| Production WebSocket | உண்மையான WebSocket transport-ல் தவறான token `AUTH_REQUIRED` என மறுக்கப்பட்டது |
| Supabase | Session pooler 5432, verified TLS, session lock, pgcrypto, PostGIS மற்றும் geospatial query வெற்றி |
| Upstash | TLS, PING, read/write, Lua மற்றும் Pub/Sub வெற்றி |
| R2 | Upload, scan-fixture வழியாக verified copy, download, staging overwrite isolation மற்றும் temporary object cleanup வெற்றி |
| Supabase security advisor | Security lints இல்லை; இது முழுமையான penetration test அல்ல |
| Production processes | Render deployment live; worker மற்றும் WhatsApp readiness heartbeats fresh |

Production HTTP ஆய்வில் புதிய login token உருவாக்கப்படவில்லை. அதற்கு முன் இதே நாளில் உரிமையாளர் WhatsApp-ல் பெற்ற OTP மூலம் நடத்தப்பட்ட live authentication சோதனையில் 11 checks வெற்றியடைந்தன: login, OTP reuse rejection, profile access, rider/admin isolation, token rotation, logout மற்றும் refresh reuse rejection. சோதனை session logout செய்யப்பட்டது; tokens வெளியிடப்படவில்லை.

Cloud சோதனை ஆதாரங்கள்:

- [Supabase மற்றும் Upstash live run](https://github.com/MazoPlatforms/RideNow/actions/runs/41806521716)
- [Cloudflare R2 live run](https://github.com/MazoPlatforms/RideNow/actions/runs/41806526426)
- [ஆய்வு செய்யப்பட்ட பதிப்பின் backend CI](https://github.com/MazoPlatforms/RideNow/actions/runs/41636507050)

## உள்ளூர் load சோதனை

தனி உள்ளூர் test database மற்றும் Redis பயன்படுத்தப்பட்டன; Render production instance-க்கு இந்த load அனுப்பப்படவில்லை.

| அளவு | மதிப்பு |
| --- | --- |
| உருவகப்படுத்தப்பட்ட drivers | 1,000 |
| Active rides | 100 |
| Socket connections | 1,100 |
| Location updates | 2,000 |
| Errors | 0 |
| REST p95 | 33 ms |
| Location acknowledgement p95 | 251 ms |
| Realtime p95 | 941 ms |

இவை உள்ளூர் அளவீடுகள் மட்டுமே. Render Free capacity, mobile network latency, WhatsApp delivery, production dispatch throughput, storage அல்லது malware scanning capacity-ஐ இந்த அளவீடுகள் நிரூபிக்கவில்லை. [முழு load report](load-report.json).

## Production-ல் கண்டறிந்த தடைகள்

`ridenow-demo` திட்டத்தின் `ridenow` schema மட்டும் read-only queries மூலம் ஆய்வு செய்யப்பட்டது. வேறு Supabase திட்டம் பயன்படுத்தப்படவில்லை.

| அமைப்பு | தற்போதைய எண்ணிக்கை | தேவைப்படும் நடவடிக்கை |
| --- | --- | --- |
| Active admin accounts | 0 | சரியான operator account, password மற்றும் TOTP enrollment அமைத்தல் |
| Active service areas | 0 | ஒப்புக்கொண்ட செயல்பாட்டுப் பகுதியின் polygon அமைத்தல் |
| Fare plans | 0 | Vehicle category-களுக்கான அங்கீகரிக்கப்பட்ட கட்டணங்கள் அமைத்தல் |
| Approved drivers | 0 | உண்மையான onboarding, document review மற்றும் vehicle approval |
| Pending documents | 0 | Production scanner செயல்பாடு இன்னும் நிரூபிக்கப்படவில்லை |
| Dead jobs | 0 | இந்த snapshot-ல் dead jobs இல்லை |
| Pending non-OTP jobs | 0 | இந்த snapshot-ல் backlog இல்லை |

கூடுதல் தேவைகள்:

1. **உண்மையான malware scanner:** R2 probe-ன் `malwareEngineVerified=false`. பாதுகாக்கப்பட்ட scanner இணைப்பு, CLEAN/rejected/timeout வழிகள் மற்றும் fail-closed நடத்தையைச் சரிபார்க்க வேண்டும். Protocol fixture-ஐ உண்மையான engine சோதனையாகக் கருதக்கூடாது.
2. **R2 policy உறுதிப்படுத்தல்:** probe-ன் `browserCorsVerified=false`, `bucketVisibilityVerified=false`. Bucket private என்பதை உறுதிப்படுத்தவும். Website browser upload பயன்படுத்தினால் browser CORS-ஐயும் சோதிக்கவும்; native Android client-க்கு browser CORS கட்டுப்பாடு பொருந்தாது.
3. **Routing அங்கீகாரம்:** vehicle category approvals மற்றும் உள்ளூர் சாலைப் பாதைகள் சரிபார்க்கப்பட வேண்டும். தற்போதைய graph-ல் turn restrictions இல்லை; எல்லா vehicle வகைகளுக்கும் சரியான பாதை என்று கருத முடியாது.
4. **Android end-to-end:** உண்மையான சாதனத்தில் booking → offer → accept → GPS → complete → cash ledger; network interruption, reconnect, notification inbox மற்றும் session expiry சோதிக்கப்பட வேண்டும்.
5. **Background notifications:** தற்போதைய self-hosted inbox/WebSocket அமைப்பு foreground மற்றும் reconnect பயன்பாட்டை ஆதரிக்கிறது. செயலி suspended/மூடப்பட்ட நிலையில் உடனடி OS push delivery நிரூபிக்கப்படவில்லை; FCM/APNs transport இந்த அமைப்பில் இயக்கப்படவில்லை.
6. **Hosting மற்றும் operations:** Render Free sleep/restart/quota வரம்புகள் தொடர்கின்றன. UptimeRobot down/recovery alert delivery, backup restore, operator நடைமுறைகள் மற்றும் உண்மையான pilot load தனியாகச் சோதிக்கப்பட வேண்டும்.

இந்தத் தடைகளுக்கான business configuration அல்லது admin account-ஐ ஆய்வின் போது தானாக உருவாக்கவில்லை. அடிப்படை authentication மற்றும் backend செயல்பாடுகள் சரிபார்க்கப்பட்டுள்ளன; service-area/fare/driver/scanner அமைப்புகள் இல்லாமல் முழுமையான production booking readiness உறுதிப்படுத்த முடியாது.
