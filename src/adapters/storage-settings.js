import { ensure } from '../shared/errors.js';
export function storageSettings(config) {
  const endpoint = config.S3_ENDPOINT;
  let r2 = false;
  if (endpoint) {
    let url;
    try {
      url = new URL(endpoint);
    } catch {
      ensure(false, 500, 'S3_ENDPOINT_INVALID');
    }
    r2 = url.hostname.toLowerCase().endsWith('.r2.cloudflarestorage.com');
    ensure(!r2 || url.protocol === 'https:', 500, 'R2_HTTPS_REQUIRED');
  }
  const mode = config.S3_OBJECT_MODE ?? (r2 ? 'immutable' : 'versioned');
  ensure(['immutable', 'versioned'].includes(mode), 500, 'S3_OBJECT_MODE_INVALID');
  const options = {
    region: config.S3_REGION ?? (r2 ? 'auto' : 'us-east-1'),
    endpoint: endpoint || undefined,
    forcePathStyle: !!endpoint,
    ...(mode === 'immutable'
      ? { requestChecksumCalculation: 'WHEN_REQUIRED', responseChecksumValidation: 'WHEN_REQUIRED' }
      : {}),
    ...(config.S3_ACCESS_KEY
      ? {
          credentials: { accessKeyId: config.S3_ACCESS_KEY, secretAccessKey: config.S3_SECRET_KEY },
        }
      : {}),
  };
  return { r2, mode, options };
}
