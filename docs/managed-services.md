# Supabase மற்றும் Upstash இணைப்பு

`production` GitHub Environment-ல் `DATABASE_URL`, `REDIS_URL` secrets தேவை. GitHub Actions-ன் **Supabase மற்றும் Upstash இணைப்புச் சோதனை** workflow-ஐ `main` கிளையில் கையால் இயக்கலாம்.

- Supabase: நீண்ட நேரம் இயங்கும் backend/gateway-க்கு நேரடி PostgreSQL அல்லது Session pooler இணைப்பு. IPv4 runner-க்கு Session pooler port 5432 வழக்கமான தேர்வு. Transaction pooler port 6543 session-level locks-க்கு பொருந்தாது; backend அதை மறுக்கும்.
- Supabase host-களில் TLS certificate/hostname verification கட்டாயம். `TLS_CERTIFICATE_VERIFICATION_FAILED` என்றால் Dashboard → Database Settings → SSL Configuration-ல் கிடைக்கும் பொது CA certificate-ஐ `production` Environment Variable `SUPABASE_CA_CERT`-ல் PEM வடிவில் வைக்கலாம். Certificate verification அணைக்கப்படாது.
- Upstash: நேரடி Redis/TCP URL தேவை. CLI எடுத்துக்காட்டில் `redis://` உடன் `--tls` இருக்கலாம்; backend Upstash host-களின் URL-ஐ தானாக `rediss://` ஆக மாற்றும். Secret மதிப்பை வெளியிடாது. REST URL/token இந்த client-க்கு பொருந்தாது.

சோதனை PostgreSQL/TLS, session advisory lock, PostGIS தூரக் கணக்கு, Redis read/write, Lua rate-limit commands, மற்றும் Pub/Sub ஆகியவற்றைச் சரிபார்க்கும். PostgreSQL-ல் schema/migrations அல்லது பயனர் தரவை மாற்றாது. Redis-ல் UUID பெயருள்ள சோதனை keys மட்டும் உருவாக்கி நீக்கும்; அவை 30 விநாடிகளில் தானாகவும் காலாவதியாகும். `FLUSHDB` பயன்படுத்தப்படாது.

பதிவுகள் மற்றும் `services-smoke-report` artifact-ல் connection URLs, passwords, tokens அல்லது பயனர் தரவு இருக்காது. இயங்கும் பயன்பாட்டிற்கு இணைத்துவிட்டதாகவோ, pilot வெளியீடு முடிந்ததாகவோ இந்தச் சோதனையைப் பொருள்படுத்தக்கூடாது.

## Schema ஒத்திசைவு

Supabase-ல் ஏற்கெனவே அட்டவணைகள் இருந்தால், repository migrations-உடன் அவற்றின் columns/version history பொருந்துகிறதா என்று முதலில் பரிசீலிக்க வேண்டும். இந்த workflow தானாக migrations இயக்காது. Application tables-ஐ Data API-ல் வெளிப்படாத schema/அனுமதிகளுடன் அமைத்து, PostgreSQL role மற்றும் RLS access model-ஐ உறுதிசெய்த பிறகே deployment migration செய்ய வேண்டும்.

Cloudflare R2-க்கு versioning தேவையில்லாத verified-copy முறை வழங்கப்பட்டுள்ளது; [R2 வழிமுறைகள்](r2-storage.md). Supabase Storage தனியாக இணைத்துச் சோதிக்கப்படவில்லை. PostgreSQL இணைப்பு வெற்றி என்பது Storage இணைப்பு வெற்றி அல்ல.
