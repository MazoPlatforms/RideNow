# பாதுகாப்பு மற்றும் தரவு விதிகள்

- JWT 15 நிமிடம்; rotating refresh session 30 நாட்கள்; reuse குடும்பத்தை revoke செய்யும். ஒவ்வொரு HTTP request மற்றும் socket command-லும் database session சரிபார்ப்பு.
- Socket events அனுப்புவதற்கு முன்பும் session மறுசரிபார்ப்பு; idle connection-கள் 15 விநாடிக்கு ஒருமுறை சோதிக்கப்படும்.
- OTP 5 நிமிடம், challenge-க்கு 5 attempts, resend 60 விநாடி, phone-க்கு மணிக்கு 10 requests; encrypted delivery payload; API-ல் OTP இல்லை.
- Admin/Staff bcrypt password + single-use TOTP; TOTP அமைக்காத account-க்கு password + phone-bound single-use WhatsApp OTP. TOTP அமைத்திருந்தால் சாதாரண OTP மூலம் அதைத் தவிர்க்க முடியாது. Secret AES-256-GCM; role elevation admin-க்கு மட்டும்; resource ownership backend-ல் கட்டாயம்.
- முதல் admin web setup தனிப்பட்ட காலாவதியாகும் setup key மூலம் பாதுகாக்கப்படும்; admin உருவானதும் நிரந்தர completion marker மூலம் மூடப்படும். OTP கிடைக்காவிட்டால் உரிமையாளர் setup key வழியைத் தெளிவாகத் தேர்வு செய்யலாம்; அந்த வழியில் phone ownership உறுதிப்படுத்தப்படாது. பின்னர் recovery login-க்கு login password + தனி rotating recovery password இரண்டும் தேவை; 5 தவறான recovery முயற்சிகளுக்குப் பின் 15 நிமிட lock.
- Rate limiter Redis தோல்வியில் fail closed. Edge IP limit 10,000/min, authenticated user 180/min, auth endpoint IP 10/min, socket user 120 commands/min.
- SQL parameterized; state transitions, assignment, idempotency, ledger transactions lock செய்யப்பட்ட database transactions-ல்.
- Ledger entries/transactions, fare versions immutable; திருத்தத்திற்கு reversal/புதிய version.
- Document scan CLEAN இல்லாமல் review download/driver approval கிடையாது. S3-ல் exact scanned version pin; R2-ல் SHA-256 அடையாளத்துடன் புதிய server-only key-க்கு scanned bytes சேமிக்கப்படும். Verified key-க்கு user upload URL வழங்கப்படாது.
- Rider history-ல் driver-ன் தற்போதைய இடம் வெளியிடப்படாது; realtime GPS தற்போதைய assignment-க்கு மட்டும்.
- OTP, password, JWT, Baileys credentials, முழு documents ஆகியவை logs-ல் எழுதப்படக்கூடாது. QR/TOTP enrollment secrets operator-க்கு ஒருமுறை வழங்கப்படுகின்றன.

## Retention

Raw ride GPS மற்றும் location outbox: 30 நாட்கள். Terminal ride events, route geometry, notifications: 90 நாட்கள். Canonical ride/fare snapshot, ledger, audit மற்றும் reconciliation பதிவுகள் அழிக்கப்படாது; நிதி/சட்ட retention கொள்கை உறுதியாகும் வரை பாதுகாக்கப்படும். Expired unused quote-கள் 90 நாட்களில் நீக்கப்படும்.

Location history மாத partition-களுடன் இயங்கும்; worker வரவிருக்கும் partition-ஐ உருவாக்கும். Default partition எதிர்பாராத காலத் தரவை ஏற்கும் பாதுகாப்பு. Old records cleanup செய்யப்படுகிறது; காலியான பழைய partitions-ஐ DBA கண்காணித்து நீக்கலாம்.

Production-க்கு முன் இலங்கையின் privacy, driver licensing, retention மற்றும் நிதி தேவைகளை உறுதிசெய்து configuration/செயல்முறைகளை மாற்றவும். பயனர் data deletion வேண்டுகோளில் account disable, session revocation, நிதிப்பதிவுகளுக்கான கட்டாய retention ஆகியவற்றை ஒருங்கிணைக்க வேண்டும்; destructive account deletion API இந்த வெளியீட்டில் இல்லை.
