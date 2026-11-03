# Cloudflare R2 ஆவணச் சேமிப்பு

GitHub `production` Environment Secrets:

- `S3_ACCESS_KEY`: R2 S3 Access Key ID.
- `S3_SECRET_KEY`: அதன் Secret Access Key; தேர்ந்தெடுத்த bucket-க்கு Object Read & Write தேவை.

Environment Variables:

- `S3_ENDPOINT=https://ACCOUNT_ID.r2.cloudflarestorage.com` — S3 API endpoint; public `r2.dev` முகவரி அல்ல.
- `S3_BUCKET` — ஏற்கெனவே உருவாக்கிய bucket பெயர்.
- `S3_REGION=auto`.

இந்த மூன்று அமைப்புகளும் Secrets-ல் இருந்தாலும் live workflow ஏற்கும். Bucket public access-ஐ அணைத்து வைத்திருக்கவும். Browser upload-க்கு பயன்பாட்டின் origin-ஐ அனுமதிக்கும் bucket CORS அமைப்பு தேவை. நேரடி Node.js சோதனை browser CORS அல்லது bucket public-access நிலையை உறுதிப்படுத்தாது.

## Versioning இல்லாத பாதுகாப்பு

[R2 S3 API](https://developers.cloudflare.com/r2/api/s3/api/) bucket versioning-ஐ ஆதரிக்கவில்லை. Backend R2 host-ஐ அடையாளம் கண்டு verified-copy முறையைத் தேர்ந்தெடுக்கும்:

1. பயனரின் presigned URL, staging key-க்கு மட்டும் upload செய்யும்.
2. Worker metadata, ETag, file signature மற்றும் ClamAV scan-ஐச் சரிபார்க்கும்.
3. CLEAN முடிவில், scan செய்த அதே bytes SHA-256 அடையாளத்துடன் புதிய `verified/` key-க்கு எழுதப்படும்.
4. Database document reference அந்த நகலுக்கு atomically மாற்றப்படும். Concurrent scans-ல் முதலில் முடிந்த reference மட்டும் publish செய்யப்படும்; பயன்படுத்தப்படாத verified நகல் நீக்கப்படும்.
5. பயனருக்கு verified key-ஐ எழுதும் presigned URL வழங்கப்படாது. Review download அந்த நகலை மட்டுமே வழங்கும்.

இது பயன்பாட்டின் immutable-key முறை; R2 object locking/versioning என்று பொருள்படாது. Privileged storage credentials-ஐ frontend-க்கு வழங்கக்கூடாது. ஏற்கெனவே உள்ள versioned S3 ஆவணங்களுக்கு object-version pin முறை தொடரும். இந்த மாற்றத்திற்கு database migration தேவையில்லை.

## நேரடிச் சோதனை

GitHub Actions → **Cloudflare R2 சேமிப்பகச் சோதனை** → Run workflow.

Workflow ஒரு சிறிய synthetic PDF-ஐ upload செய்து, deterministic scanner protocol fixture மூலம் verified copy உருவாக்கும். Staging கோப்பை மாற்றிய பிறகும் verified download மாறாமல் உள்ளதா என்று சரிபார்க்கும். அது உருவாக்கிய கோப்புகளை மட்டுமே நீக்கும்; பயனர் ஆவணங்கள் படிக்கப்படாது. Existing bucket-ன் settings/CORS மாற்றப்படாது.

அறிக்கையில் `malwareEngineVerified=false`, `browserCorsVerified=false`, `bucketVisibilityVerified=false` இருக்கும்: இவற்றிற்கு deployment-ல் தனி உண்மையான சோதனை தேவை. இந்த workflow storage integration-ஐ மட்டும் நிரூபிக்கும்.
