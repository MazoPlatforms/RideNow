# தற்காலிக முதல் Admin அமைப்பு

இந்த வசதி முதல் admin account உருவாக்குவதற்கானது. ஏற்கெனவே வழங்கப்பட்ட public website கோப்புகளை மாற்றாது. புதிய account-access பக்கங்கள் backend-லிருந்து வழங்கப்படுகின்றன.

## இயக்குதல்

1. `003-admin-recovery` migration-ஐ நிறுவவும். Supabase-க்கு ஏற்கெனவே உள்ள `schema-install.yml` workflow பயன்படுத்தவும்; `ridenow-demo` திட்டம் மட்டும் அனுமதிக்கப்பட்டுள்ளது.
2. Render Environment-ல் `ADMIN_SETUP_KEY` என்ற 32+ random-character secret மற்றும் `ADMIN_SETUP_EXPIRES_AT` என்ற UTC ISO expiry அமைக்கவும். இந்த deployment-க்கு key 24 மணி நேரத்திற்குள் காலாவதியாகும் வகையில் வழங்கப்படுகிறது. Code-ல் expiry இல்லாமல் setup இயங்காது.
3. `/admin-setup/#key=<private-key>` என்ற தனிப்பட்ட இணைப்பைத் திறக்கவும். Fragment server request-க்கு அனுப்பப்படாது; JavaScript அதை வாசித்ததும் browser URL-லிருந்து நீக்கும். Key, password, recovery password ஆகியவற்றை chat, screenshot அல்லது Git commit-ல் பகிர வேண்டாம்.

முதல் admin ஏற்கெனவே இருந்தாலோ, setup முடிந்ததற்கான permanent marker இருந்தாலோ, key காலாவதியானாலோ setup பக்கம்/API 404 வழங்கும். Admin role பின்னர் மாற்றப்பட்டாலும் பழைய setup key மீண்டும் திறக்காது. Setup முடிந்தபின் Render Environment-லிருந்து key மற்றும் expiry-ஐயும் நீக்கலாம்.

## Account உருவாக்குதல்

- தனி admin phone number, பெயர் மற்றும் குறைந்தது 12 எழுத்துகள் கொண்ட password உள்ளிடவும். Password manager பயன்படுத்துவது பரிந்துரைக்கப்படுகிறது.
- WhatsApp OTP கோரி, 5 நிமிடங்களுக்குள் கிடைத்த code உள்ளிடவும். OTP account phone-உடன் பொருந்த வேண்டும்; ஒருமுறை மட்டுமே பயன்படுத்தலாம்.
- OTP அனுப்ப முடியாவிட்டால் அல்லது கிடைக்காவிட்டால் recovery checkbox தேர்வு செய்யலாம். **முதல் setup-ல் தனிப்பட்ட setup key தான் operator authorization**; அந்த வழியில் phone ownership சரிபார்க்கப்படாது. பொது பார்வையாளருக்கு key இல்லாமல் இந்த வழி கிடையாது.
- TOTP விருப்பம். தேர்வு செய்தால் secret-ஐ authenticator-ல் சேர்த்து அதிலுள்ள code-ஐ உறுதிப்படுத்த வேண்டும். உறுதிப்படுத்தாத secret account-ல் சேமிக்கப்படாது.
- ஏற்கெனவே சாதாரண rider ஆக உள்ள அதே எண்ணைப் பயன்படுத்தினால் account admin ஆக மாறும்; பழைய sessions revoke செய்யப்படும். Driver/staff, suspended அல்லது deleted account-களை இந்த setup மாற்றாது.
- Account உருவானதும் **தனி recovery password ஒருமுறை காட்டப்படும்**. அதை login password-இலிருந்து தனியாகப் பாதுகாப்பாகச் சேமிக்கவும். Database-ல் இரு passwords-மும் bcrypt hashes ஆகவே சேமிக்கப்படும்.

## பின்னர் Login

`/admin-access/` பக்கம்:

- TOTP இல்லாவிட்டால்: login password + WhatsApp OTP.
- TOTP அமைத்திருந்தால்: login password + புதிய TOTP code. Setup-ல் உறுதிப்படுத்திய அதே code-ஐ உடனே மீண்டும் பயன்படுத்த முடியாது; அடுத்த code வரும் வரை காத்திருக்கவும்.
- OTP/TOTP கிடைக்காவிட்டால்: login password + தனி recovery password. Recovery பயன்படுத்தியதும் பழைய sessions revoke செய்யப்படும், பழைய recovery password செல்லாது, புதிய recovery password ஒருமுறை வழங்கப்படும். புதியதைச் சேமிக்க வேண்டும்.
- தவறான recovery password 5 முறை உள்ளிடப்பட்டால் 15 நிமிட recovery lock. API rate limiting கூடுதலாக இயங்கும்.

இந்தப் பக்கம் login உறுதிப்படுத்தலும் logout வசதியும் வழங்குகிறது. Access token browser memory-ல் மட்டும் இருக்கும்; localStorage, cookies அல்லது URL-ல் சேமிக்கப்படாது. வேறு admin management UI இந்த மாற்றத்தில் உருவாக்கப்படவில்லை.

## API

- `POST /v1/admin-setup/otp`: setup key header + phone.
- `POST /v1/admin-setup/totp`: setup key header + phone; temporary encrypted enrollment envelope.
- `POST /v1/admin-setup/complete`: setup key header + name/phone/password; `verification=whatsapp` என்றால் challenge/code, `verification=setup-key` என்றால் explicit operator fallback. விருப்ப enrollment மற்றும் TOTP confirmation.
- `POST /v1/auth/staff/login`: phone/password + TOTP அல்லது phone-bound challenge/code.
- `POST /v1/auth/admin/recover`: phone/password/recoveryPassword; புதிய session மற்றும் மாற்றிய recovery password.

Setup key header: `X-Admin-Setup-Key`. Account pages, setup responses மற்றும் auth responses `Cache-Control: no-store` பயன்படுத்துகின்றன. Request bodies மற்றும் credentials logs-ல் எழுதப்படாது. Recovery password HTTPS response-ல் உரிமையாளருக்கு ஒருமுறை வழங்கப்படுகிறது; அதைச் சேமிக்காமல் response இழந்தால் சாதாரண OTP/TOTP login-ஐப் பயன்படுத்த வேண்டும்.
