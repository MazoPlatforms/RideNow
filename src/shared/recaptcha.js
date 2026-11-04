import { AppError, ensure } from './errors.js';

const endpoint = 'https://www.google.com/recaptcha/api/siteverify';

export function createRecaptchaVerifier(config, fetchImpl = fetch) {
  return async ({ token, hostname }) => {
    if (!config.RECAPTCHA_V2_SECRET_KEY) {
      ensure(config.NODE_ENV !== 'production', 503, 'CAPTCHA_UNAVAILABLE');
      return true;
    }
    ensure(typeof token === 'string' && token.length >= 20 && token.length <= 4096, 400, 'CAPTCHA_REQUIRED');
    let result;
    try {
      const response = await fetchImpl(endpoint, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ secret: config.RECAPTCHA_V2_SECRET_KEY, response: token }),
        signal: AbortSignal.timeout(5000),
      });
      ensure(response.ok, 503, 'CAPTCHA_UNAVAILABLE');
      result = await response.json();
    } catch (error) {
      if (error instanceof AppError) throw error;
      throw new AppError(503, 'CAPTCHA_UNAVAILABLE');
    }
    ensure(result.success === true && typeof result.hostname === 'string' &&
      result.hostname.toLowerCase() === String(hostname).toLowerCase(), 403, 'CAPTCHA_INVALID');
    return true;
  };
}
