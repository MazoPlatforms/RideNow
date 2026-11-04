const $ = (id) => document.getElementById(id);
const setup = location.pathname.startsWith('/admin-setup');
let challenge, enrollment, accessToken, enrollmentPhone;
const fragment = new URLSearchParams(location.hash.slice(1));
$('key').value = fragment.get('key') ?? '';
if (location.hash) history.replaceState(null, '', location.pathname + location.search);
const messages = {
  OTP_PROVIDER_UNAVAILABLE: 'WhatsApp is unavailable. Try again or choose recovery.',
  OTP_RATE_LIMIT: 'Wait 60 seconds between code requests. There is also a limit of 10 requests per hour.',
  OTP_INVALID: 'The code is incorrect, expired, or already used.',
  SETUP_UNAVAILABLE: 'The setup key is invalid, expired, or an administrator already exists.',
  INVALID_CREDENTIALS: 'The sign-in details are incorrect. If an authenticator is enabled, enter its code.',
  WHATSAPP_OTP_REQUIRED: 'Request a WhatsApp code and enter the code you receive.',
  INVALID_TOTP: 'The authenticator code is incorrect.',
  TOTP_REUSED: 'This authenticator code was already used. Wait for the next code.',
  INVALID_TOTP_ENROLLMENT: 'Authenticator enrollment expired. Generate a new secret.',
  RECOVERY_LOCKED: 'Recovery is locked. Try again in 15 minutes.',
  RATE_LIMITED: 'Too many requests. Please wait and try again.',
};
async function api(path, body, useKey = false) {
  const headers = { 'Content-Type': 'application/json' };
  if (useKey) headers['X-Admin-Setup-Key'] = $('key').value.trim();
  if (accessToken && path.endsWith('/logout')) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetch(path, { method: 'POST', headers, body: JSON.stringify(body), cache: 'no-store', credentials: 'omit' });
  const result = await response.json();
  if (!response.ok) throw new Error(messages[result.code] ?? `Request failed: ${result.code ?? response.status}`);
  return result;
}
async function run(button, fn) {
  button.disabled = true;
  $('status').textContent = 'Please wait…';
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
  $('intro').textContent = 'Use your password and a WhatsApp or authenticator code. Use your separate recovery password if verification is unavailable.';
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
  $('status').textContent = 'Code requested. Enter the 6-digit WhatsApp code within 5 minutes.';
}));
$('enroll').addEventListener('click', () => run($('enroll'), async () => {
  const phone = $('phone').value.trim();
  const result = await api('/v1/admin-setup/totp', { phone }, true);
  enrollment = result.enrollment; enrollmentPhone = phone;
  $('totp-secret').value = result.secret;
  $('status').textContent = 'Add the secret to your authenticator, then enter its code.';
}));
$('form').addEventListener('submit', (event) => {
  event.preventDefault();
  run($('submit'), async () => {
    const phone = $('phone').value.trim(), password = $('password').value;
    const fallback = $('fallback').checked;
    let result;
    if (setup) {
      if (!fallback && (!challenge || challenge.phone !== phone)) throw new Error('Request a code for this phone number first.');
      const body = { phone, password, name: $('name').value.trim(), verification: fallback ? 'setup-key' : 'whatsapp' };
      if (!fallback) { body.challengeId = challenge.id; body.code = $('otp').value; }
      if ($('enable-totp').checked) {
        if (!enrollment || enrollmentPhone !== phone) throw new Error('Generate an authenticator secret first.');
        body.enrollment = enrollment; body.totp = $('totp').value;
      }
      result = await api('/v1/admin-setup/complete', body, true);
      $('key').value = ''; $('totp-secret').value = ''; enrollment = null;
      $('form').hidden = true;
      $('status').textContent = 'Administrator created. Setup is closed. Save your recovery password now.';
    } else {
      if (fallback) result = await api('/v1/auth/admin/recover', { phone, password, recoveryPassword: $('recovery').value.trim() });
      else {
        const body = { phone, password };
        if ($('login-totp').value) body.totp = $('login-totp').value;
        else {
          if (!challenge || challenge.phone !== phone) throw new Error('Request a WhatsApp code or enter an authenticator code.');
          body.challengeId = challenge.id; body.code = $('otp').value;
        }
        result = await api('/v1/auth/staff/login', body);
      }
      accessToken = result.accessToken;
      $('form').hidden = true; $('logout').hidden = false;
      $('status').textContent = 'Administrator sign-in successful.';
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
  $('status').textContent = 'Recovery password copied. Save it securely in a password manager.';
}));
$('logout').addEventListener('click', () => run($('logout'), async () => {
  await api('/v1/auth/logout', {}); accessToken = null;
  $('recovery-result').value = ''; $('result').hidden = true;
  $('logout').hidden = true; $('form').hidden = false;
  $('status').textContent = 'Signed out.';
}));
