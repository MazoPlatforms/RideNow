function invalid(code) {
  return Object.assign(new Error(code), { code });
}
function parse(value, protocols, code) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw invalid(code);
  }
  if (!protocols.includes(url.protocol) || !url.hostname) throw invalid(code);
  return url;
}
export function isSupabaseHost(host) {
  host = host.toLowerCase();
  return host.endsWith('.supabase.co') || host.endsWith('.pooler.supabase.com');
}
export function postgresConnectionString(value) {
  const url = parse(value, ['postgres:', 'postgresql:'], 'DATABASE_TCP_URL_REQUIRED');
  if (isSupabaseHost(url.hostname)) {
    if (url.port === '6543') throw invalid('SUPABASE_SESSION_POOLER_REQUIRED');
    // Session locks are used by the gateway. Verify TLS for hosted PostgreSQL.
    url.searchParams.set('sslmode', 'verify-full');
    url.searchParams.delete('uselibpqcompat');
  }
  return url.toString();
}
export function redisConnectionString(value) {
  const url = parse(value, ['redis:', 'rediss:'], 'REDIS_TCP_URL_REQUIRED');
  if (url.hostname.toLowerCase().endsWith('.upstash.io')) url.protocol = 'rediss:';
  return url.toString();
}
