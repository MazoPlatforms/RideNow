# ridenow-demo தனி backend schema

இந்த repository-யின் Supabase இலக்கு **ridenow-demo**, project reference `bhjuxggttifldkrcrshj`. வேறு `ridenow` என்ற Supabase திட்டம் இந்த backend-க்கு பயன்படுத்தப்படுவதில்லை. **ridenow-demo திட்டத்திற்குள் உள்ள `ridenow` என்பது schema பெயர் மட்டுமே.**

## Migrations மற்றும் அனுமதிகள்

GitHub Actions → **ridenow-demo தனி schema அமைத்தல்** workflow, `production` Environment-ன் `DATABASE_URL` மற்றும் பொது CA variable-ஐப் பயன்படுத்தும். திட்ட அடையாளம் பொருந்தாத URL என்றால் database connection-க்கு முன்பே நிறுத்தப்படும்.

Canonical migration history `ridenow.schema_migrations`-ல் node-pg-migrate மூலம் நிர்வகிக்கப்படுகிறது. `npm run migrate` Supabase URL-க்கு இந்த தனி installer-ஐப் பயன்படுத்தும்; உள்ளூர் PostgreSQL சோதனைகள் public schema-வைத் தொடர்ந்து பயன்படுத்தும். முன்பே இருக்கும் அறியப்படாத அட்டவணைகள் அல்லது migration history தானாக மாற்றப்படாது.

- அட்டவணைகள், sequences, functions அனைத்தும் தனி `ridenow` schema-வில்.
- `PUBLIC`, `anon`, `authenticated`, `service_role` ஆகியவற்றிற்கு schema/table/function அணுகல் மறுக்கப்படுகிறது.
- அனைத்து அட்டவணைகள் மற்றும் partitions-ல் RLS; trusted backend role-க்கு மட்டும் access policy.
- `ridenow_backend`: NOLOGIN, NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOBYPASSRLS. வழக்கமான application DML அனுமதிகள்; schema DDL அனுமதி இல்லை.
- Ledger/audit/fare-version பதிவுகளில் UPDATE/DELETE அனுமதி இல்லை; migration history-ல் எழுதும் அனுமதி இல்லை.
- Functions-க்கு fixed search_path; SECURITY DEFINER பயன்படுத்தப்படுவதில்லை.

இது custom WhatsApp/JWT backend என்பதால் browser பயனர்கள் நேரடியாக PostgreSQL role-ஐப் பெறுவதில்லை. Rider/Driver/Staff authorization REST/WebSocket backend-ல் அமலும்; database policies trusted backend session-க்கு மட்டுமே பொருந்தும். Supabase Auth `auth.uid()` இந்த user IDs-க்கு மாற்றாகப் பயன்படுத்தப்படாது.

## Application இணைப்பு

Supabase URL-களுக்கு connection pool தானாக `SET ROLE ridenow_backend` மற்றும் private search_path அமைக்கும். `DB_SCHEMA`, `DB_ROLE` மூலம் deployment-ல் வெளிப்படையாகவும் அமைக்கலாம். Installer/diagnostic connection மட்டும் administrative context-ஐப் பயன்படுத்தும்.

**NOLOGIN role என்பது தனி database password அல்ல.** தற்போதைய DATABASE_URL login credential அதே நிலையில் இருக்கும்; application queries குறைந்த அனுமதியுள்ள role-க்கு மாற்றப்படுகின்றன. தனி least-privilege LOGIN credential தேவைப்பட்டால் தனியாக provision செய்து role membership வழங்க வேண்டும். Schema setup செய்துவிட்டதால் owner credential-ஐ frontend-ல் பயன்படுத்தலாம் என்று பொருள்படாது.

## Verification மற்றும் பராமரிப்பு

Installer role-ல் ஒரு synthetic user/audit row எழுதிச் சரிபார்த்து transaction-ஐ rollback செய்யும். Sample fares, ஓட்டுநர்கள், admin password அல்லது WhatsApp sessions seed செய்யப்படாது.

Application role-க்கு DDL அனுமதி இல்லாததால் மாத partitions release/installer மூலம் உருவாக்கப்படும். Installer அடுத்த 12 மாதங்களுக்கு partitions தயாராக்கும்; மாதந்தோறும் migration release-ஐ இயக்கவும். Runtime retention/surge பணிகள் தொடரும்; புதிய partition உருவாக்கம் administrator-ன் பணி.

Schema அமைப்பு முடிவது application hosting/deployment முடிந்ததைக் குறிக்காது. API, worker, WhatsApp gateway, ClamAV மற்றும் client applications ஆகியவற்றைத் தனியாக இயக்க வேண்டும்.
