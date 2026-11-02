# உண்மையான WhatsApp சோதனை

GitHub Actions-ல் **WhatsApp நேரடிச் சோதனை** workflow-ஐ கையால் இயக்கலாம். `production` GitHub Environment-ல் `WA_AUTH_CREDS` secret-க்கு இணைக்கப்பட்ட Baileys `creds.json`-ன் முழு JSON உள்ளடக்கம் தேவை. அந்த Environment-ன் approval விதிகள் வழக்கம்போல் பொருந்தும்.

பெறுநர் எண்ணை `94` உடன் உள்ளிடவும். ஒவ்வொரு இயக்கமும் ஒரே OTP அனுப்பும் முயற்சியை மட்டும் மேற்கொள்ளும்; வழக்கமான push/PR சோதனைகள் WhatsApp செய்தி அனுப்பாது. Workflow input-ல் பெறுநர் எண் GitHub-க்கு வழங்கப்படும்; குறியீட்டில் சேமிக்கப்படாது; runner பதிவுகளில் மறைக்கப்படும்.

இந்த workflow, `production` Environment-ஐ secret அணுகலுக்காகப் பயன்படுத்துகிறது. பயன்பாட்டின் `NODE_ENV=test`; PostgreSQL/Redis தனியான தற்காலிகச் சேவைகள். Production பயன்பாட்டின் தரவுத்தளம், பயனர்கள் அல்லது sessions பயன்படுத்தப்படுவதில்லை. JWT/encryption/OTP keys ஒவ்வொரு இயக்கத்திற்கும் புதிதாக memory-ல் உருவாக்கப்படும்; production keys தேவையில்லை.

சோதனை வரிசை:

1. Credential JSON மற்றும் binary keys சரிபார்ப்பு; encrypted database import.
2. உண்மையான WhatsApp இணைப்பு; pairing தேவைப்பட்டால் QR-ஐ வெளியிடாமல் நிறுத்துதல்.
3. உண்மையான OTP request API → PostgreSQL queue → Baileys மூலம் ஒரு சோதனைச் செய்தி.
4. Queue-ல் உள்ள அதே OTP மூலம் verification, மீள்பயன்பாடு மறுப்பு, authenticated request, logout மற்றும் session revocation.
5. WhatsApp வழங்கும் சாதன delivery acknowledgement-க்கு அதிகபட்சம் 60 விநாடிகள் காத்திருத்தல்.

`otpVerification=true` என்பது server flow சோதனை; பெறுநர் OTP-ஐ கைமுறையாக உள்ளிட்டதாகக் கருதக்கூடாது. `sendAccepted=true` என்பது provider அனுப்புதலை ஏற்றுக்கொண்டது. `delivery=delivered` என்றால் WhatsApp சாதன delivery acknowledgement கிடைத்துள்ளது; பயனர் படித்ததற்கான உறுதி அல்ல. Delivery உறுதி இல்லையெனில் workflow தோல்வியாகக் காட்டும்; தானாக வேறு OTP அனுப்பாது.

`whatsapp-smoke-report` artifact-ல் முடிவுகள் மட்டும் இருக்கும். OTP, recipient, credentials, JWT, Signal keys அல்லது database dump இருக்காது. Artifact மூன்று நாட்கள் மட்டும் வைக்கப்படும்.

## பொதுவான முடிவுகள்

- `WA_AUTH_CREDS_MISSING`: Environment secret கிடைக்கவில்லை அல்லது காலியாக உள்ளது.
- `WA_AUTH_CREDS_INVALID_JSON`: raw `creds.json` JSON வழங்க வேண்டும்; கோப்பின் பெயர் மட்டும் போதாது.
- `WA_AUTH_CREDS_NOT_PAIRED` / `WA_PAIRING_REQUIRED`: செயல்படும் paired session export தேவை.
- `WA_SESSION_LOGGED_OUT`: session செல்லுபடியாகவில்லை; புதிய pairing தேவை.
- `WA_CONNECT_CLOSED`: report-ல் உள்ள numeric connectionStatus மூலம் காரணம் அறியலாம்; புதிய pairing தானாக முயற்சிக்கப்படாது.
- `WA_DEVICE_DELIVERY_UNCONFIRMED`: send ஏற்றுக்கொள்ளப்பட்டிருக்கலாம்; சாதன delivery உறுதி வரவில்லை. கைபேசியில் செய்தி வந்ததா என்று பார்க்கவும்; உடனடியாக rerun செய்ய வேண்டாம்.

## நிரந்தர gateway

`npm run whatsapp` முதன்முதலில் database காலியாக இருந்தால் `WA_AUTH_CREDS`-ஐ import செய்யும். ஏற்கெனவே உள்ள session-ஐ environment export மாற்றாது. புதிய credentials மற்றும் Signal keys அதே database-ல் encrypt செய்து சேமிக்கப்படும். சாதாரண பல-கோப்பு Baileys session-ல் `creds.json` தவிர பிற key files-மும் இருக்கலாம்; credentials மட்டும் உள்ள export, முழு session backup-க்கு மாற்றாகாது. விருப்பமாக `{creds,keys}` bundle-யும் importer ஏற்கும்.

CI-யின் session database தற்காலிகமானது; இது தொடர்ந்து இயங்கும் gateway அல்ல. நிரந்தர gateway-க்கு persistent PostgreSQL மற்றும் அதே `ENCRYPTION_KEY` அவசியம்.
