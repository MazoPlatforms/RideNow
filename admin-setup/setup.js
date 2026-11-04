const $ = (id) => document.getElementById(id);
const setup = location.pathname.startsWith('/admin-setup');
let challenge, enrollment, accessToken, enrollmentPhone;
const fragment = new URLSearchParams(location.hash.slice(1));
$('key').value = fragment.get('key') ?? '';
if (location.hash) history.replaceState(null, '', location.pathname + location.search);
const messages = {
  OTP_PROVIDER_UNAVAILABLE: 'WhatsApp இணைப்பு கிடைக்கவில்லை. மீண்டும் முயற்சிக்கவும் அல்லது recovery வழியைத் தேர்வு செய்யவும்.',
  OTP_RATE_LIMIT: 'OTP கோரிக்கைகளுக்கு இடையில் குறைந்தது 60 விநாடிகள் காத்திருக்கவும். ஒரு மணி நேரத்திற்கு 10 கோரிக்கைகள் மட்டுமே.',
  OTP_INVALID: 'OTP தவறானது, காலாவதியானது அல்லது ஏற்கெனவே பயன்படுத்தப்பட்டது.',
  SETUP_UNAVAILABLE: 'Setup key தவறானது, காலாவதியானது அல்லது admin ஏற்கெனவே உருவாக்கப்பட்டுள்ளார்.',
  INVALID_CREDENTIALS: 'Login விவரங்கள் பொருந்தவில்லை. TOTP அமைத்திருந்தால் அதன் code-ஐ உள்ளிடவும்.',
  WHATSAPP_OTP_REQUIRED: 'WhatsApp OTP தேவை. OTP அனுப்பிய பிறகு கிடைத்த code-ஐ உள்ளிடவும்.',
  INVALID_TOTP: 'Authenticator code பொருந்தவில்லை.',
  TOTP_REUSED: 'இந்த authenticator code பயன்படுத்தப்பட்டது. அடுத்த புதிய code வரும் வரை காத்திருக்கவும்.',
  INVALID_TOTP_ENROLLMENT: 'Authenticator setup காலாவதியானது. புதிய secret உருவாக்கவும்.',
  RECOVERY_LOCKED: 'Recovery முயற்சிகள் அதிகம். 15 நிமிடங்கள் கழித்து மீண்டும் முயற்சிக்கவும்.',
  RATE_LIMITED: 'கோரிக்கைகள் அதிகம். சிறிது நேரம் கழித்து மீண்டும் முயற்சிக்கவும்.',
};
async function api(path, body, useKey = false) {
  const headers = { 'Content-Type': 'application/json' };
  if (useKey) headers['X-Admin-Setup-Key'] = $('key').value.trim();
  if (accessToken && path.endsWith('/logout')) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetch(path, { method: 'POST', headers, body: JSON.stringify(body), cache: 'no-store', credentials: 'omit' });
  const result = await response.json();
  if (!response.ok) throw new Error(messages[result.code] ?? `கோரிக்கை தோல்வி: ${result.code ?? response.status}`);
  return result;
}
async function run(button, fn) {
  button.disabled = true;
  $('status').textContent = 'செயல்படுகிறது…';
  try { await fn(); }
  catch (error) { $('status').textContent = error.message; }
  finally { button.disabled = false; }
}
function update() {
  const fallback = $('fallback').checked;
  $('otp-field').hidden = fallback;
  $('fallback-note').hidden = !fallback || !setup;
  $('recovery-field').hidden = !fallback || setup;
  $('totp-fields').hidden = !$('enable-totp').checked;
  $('login-totp-field').hidden = setup || fallback;
}
if (!setup) {
  $('title').textContent = 'Admin login';
  $('intro').textContent = 'Password உடன் WhatsApp OTP அல்லது அமைக்கப்பட்ட TOTP code பயன்படுத்தவும். OTP/TOTP கிடைக்காவிட்டால் தனி recovery password பயன்படுத்தலாம்.';
  for (const id of ['key-field', 'name-field', 'phone-note', 'totp-section']) $(id).hidden = true;
  $('password').autocomplete = 'current-password';
  $('submit').textContent = 'Login';
}
update();
$('fallback').addEventListener('change', update);
$('enable-totp').addEventListener('change', update);
$('phone').addEventListener('input', () => { challenge = null; enrollment = null; $('totp-secret').value = ''; });
$('send').addEventListener('click', () => run($('send'), async () => {
  const phone = $('phone').value.trim();
  const result = await api(setup ? '/v1/admin-setup/otp' : '/v1/auth/otp/request', { phone }, setup);
  challenge = { id: result.challengeId, phone };
  $('status').textContent = 'OTP கோரிக்கை ஏற்கப்பட்டது. WhatsApp-ல் கிடைத்த 6 இலக்க code-ஐ 5 நிமிடங்களுக்குள் உள்ளிடவும்.';
}));
$('enroll').addEventListener('click', () => run($('enroll'), async () => {
  const phone = $('phone').value.trim();
  const result = await api('/v1/admin-setup/totp', { phone }, true);
  enrollment = result.enrollment; enrollmentPhone = phone;
  $('totp-secret').value = result.secret;
  $('status').textContent = 'Secret-ஐ authenticator-ல் சேர்த்து அதன் code-ஐ உள்ளிடவும்.';
}));
$('form').addEventListener('submit', (event) => {
  event.preventDefault();
  run($('submit'), async () => {
    const phone = $('phone').value.trim(), password = $('password').value;
    const fallback = $('fallback').checked;
    let result;
    if (setup) {
      if (!fallback && (!challenge || challenge.phone !== phone)) throw new Error('முதலில் இந்த எண்ணுக்கு OTP கோரவும்.');
      const body = { phone, password, name: $('name').value.trim(), verification: fallback ? 'setup-key' : 'whatsapp' };
      if (!fallback) { body.challengeId = challenge.id; body.code = $('otp').value; }
      if ($('enable-totp').checked) {
        if (!enrollment || enrollmentPhone !== phone) throw new Error('முதலில் authenticator secret உருவாக்கவும்.');
        body.enrollment = enrollment; body.totp = $('totp').value;
      }
      result = await api('/v1/admin-setup/complete', body, true);
      $('key').value = ''; $('totp-secret').value = ''; enrollment = null;
      $('form').hidden = true;
      $('status').textContent = 'Admin உருவாக்கப்பட்டார். Setup மூடப்பட்டது. Recovery password-ஐ இப்போது சேமிக்கவும்.';
    } else {
      if (fallback) result = await api('/v1/auth/admin/recover', { phone, password, recoveryPassword: $('recovery').value.trim() });
      else {
        const body = { phone, password };
        if ($('login-totp').value) body.totp = $('login-totp').value;
        else {
          if (!challenge || challenge.phone !== phone) throw new Error('முதலில் OTP கோரவும் அல்லது TOTP code உள்ளிடவும்.');
          body.challengeId = challenge.id; body.code = $('otp').value;
        }
        result = await api('/v1/auth/staff/login', body);
      }
      accessToken = result.accessToken;
      $('form').hidden = true; $('logout').hidden = false;
      $('status').textContent = 'Admin login வெற்றி.';
    }
    $('password').value = ''; $('otp').value = ''; $('totp').value = ''; $('login-totp').value = ''; $('recovery').value = '';
    if (result.recoveryPassword) {
      $('recovery-result').value = result.recoveryPassword;
      $('result').hidden = false;
      $('saved-link').hidden = !setup;
    }
  });
});
$('copy').addEventListener('click', () => run($('copy'), async () => {
  await navigator.clipboard.writeText($('recovery-result').value);
  $('status').textContent = 'Recovery password நகலெடுக்கப்பட்டது. Password manager-ல் பாதுகாப்பாகச் சேமிக்கவும்.';
}));
$('logout').addEventListener('click', () => run($('logout'), async () => {
  await api('/v1/auth/logout', {}); accessToken = null;
  $('recovery-result').value = ''; $('result').hidden = true;
  $('logout').hidden = true; $('form').hidden = false;
  $('status').textContent = 'Logout முடிந்தது.';
}));
