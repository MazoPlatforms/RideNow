import { AppError, ensure } from '../shared/errors.js';

const base = 'https://maps.sifric.workers.dev/developer/api/v1';
const modes = { TUK: 'tuk', CAR: 'drive', VAN: 'drive' };
const maxSnapMeters = 250;
const bounds = [79.16416, 5.621275, 82.64612, 10.07153];
const inCoverage = ([lon, lat]) => lon >= bounds[0] && lon <= bounds[2] && lat >= bounds[1] && lat <= bounds[3];
const metric = value => Number.isFinite(value) && value >= 0;
const point = value => Array.isArray(value) && value.length === 2 &&
  value.every(Number.isFinite) && Math.abs(value[0]) <= 180 && Math.abs(value[1]) <= 90;
const snapped = value => metric(value?.distance) && value.distance <= maxSnapMeters;

function place(item) {
  if (!item || !Number.isSafeInteger(item.id) || typeof item.name !== 'string' || !point([item.lon, item.lat]))
    return null;
  return {
    id: String(item.id), name: item.name, coordinates: [item.lon, item.lat],
    kind: item.kind ?? null, address: item.label ?? item.description ?? null,
    ...(metric(item.distanceMeters) ? { distanceMeters: item.distanceMeters } : {}),
  };
}

export function createSifricMaps(config, request = fetch) {
  const approvedCategories = (config.MAP_CATEGORY_APPROVALS ?? '').split(',').filter(category => Object.hasOwn(modes, category));
  async function call(path, { query, body, unavailable } = {}) {
    ensure(config.SIFRIC_MAPS_TOKEN, 503, 'MAPS_PROVIDER_NOT_CONFIGURED');
    const url = base + path + (query ? '?' + new URLSearchParams(query) : '');
    const headers = { Authorization: 'Bearer ' + config.SIFRIC_MAPS_TOKEN, Accept: 'application/json' };
    if (body) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await request(url, {
        method: body ? 'POST' : 'GET', body: body && JSON.stringify(body), headers,
        redirect: 'error', signal: AbortSignal.timeout(8000),
      });
    } catch {
      throw new AppError(503, 'MAPS_PROVIDER_UNAVAILABLE');
    }
    if (unavailable && [404, 409, 422].includes(response.status)) {
      await response.body?.cancel();
      throw new AppError(422, unavailable);
    }
    if (!response.ok) {
      await response.body?.cancel();
      throw new AppError(503, 'MAPS_PROVIDER_UNAVAILABLE');
    }
    try { return await response.json(); }
    catch { throw new AppError(503, 'MAPS_RESPONSE_INVALID'); }
  }

  function modeFor(category, preview = false) {
    ensure(Object.hasOwn(modes, category), 400, 'MAP_CATEGORY_UNSUPPORTED');
    if (!preview && config.NODE_ENV === 'production')
      ensure(approvedCategories.includes(category), 503, 'CATEGORY_MAP_NOT_APPROVED');
    return modes[category];
  }

  return {
    coverage: () => ({
      region: 'LK', bounds, provider: 'sifric', version: 'sifric-v1', configured: Boolean(config.SIFRIC_MAPS_TOKEN),
      limitations: ['Indexed places', 'Estimated road travel times'], approvedCategories,
    }),
    async searchPlaces({ query, near }) {
      const data = await call('/places/search', { query: {
        q: query, limit: 20, ...(near && inCoverage(near) && { lon: near[0], lat: near[1] }),
      } });
      ensure(Array.isArray(data?.items), 503, 'MAPS_RESPONSE_INVALID');
      return { items: data.items.map(place).filter(Boolean) };
    },
    async findPlace(id) {
      const result = await call('/places/' + encodeURIComponent(id), { unavailable: 'PLACE_NOT_FOUND' });
      return { item: place(result?.item ?? result?.place ?? result) };
    },
    async reverse(coordinates) {
      if (!inCoverage(coordinates)) return { item: null };
      const data = await call('/geocode/reverse', { query: { lon: coordinates[0], lat: coordinates[1], radius: 100 } });
      ensure(Array.isArray(data?.items), 503, 'MAPS_RESPONSE_INVALID');
      const items = data.items.map(place).filter(item => item && metric(item.distanceMeters) && item.distanceMeters <= 100);
      return { item: items[0] ?? null };
    },
    async route({ waypoints, vehicleCategory, preview }) {
      const mode = modeFor(vehicleCategory, preview);
      ensure(waypoints.every(inCoverage), 422, 'ROUTE_UNAVAILABLE');
      const results = await Promise.all(waypoints.slice(1).map(async (destination, index) => {
        const data = await call('/routes', { body: { mode, coordinates: [waypoints[index], destination] },
          unavailable: 'ROUTE_UNAVAILABLE' });
        const route = data?.routes?.[0];
        ensure(route && data.waypoints?.length === 2 && data.waypoints.every(snapped), 422, 'ROUTE_UNAVAILABLE');
        ensure(metric(route.distance) && metric(route.duration) && typeof data.graphVersion === 'string' &&
          route.geometry?.type === 'LineString' && route.geometry.coordinates?.length >= 2 &&
          route.geometry.coordinates.every(point), 503, 'MAPS_RESPONSE_INVALID');
        return { version: data.graphVersion, leg: {
          distanceMeters: route.distance, durationSeconds: route.duration, geometry: route.geometry,
        } };
      }));
      const graphVersion = results[0].version;
      ensure(results.every(result => result.version === graphVersion), 503, 'MAPS_VERSION_CHANGED');
      const legs = results.map(result => result.leg);
      return {
        distanceMeters: legs.reduce((total, leg) => total + leg.distanceMeters, 0),
        durationSeconds: legs.reduce((total, leg) => total + leg.durationSeconds, 0),
        legs, provider: 'sifric', graphVersion,
      };
    },
    async matrix({ origins, destination, vehicleCategory }) {
      if (!origins.length) return [];
      const mode = modeFor(vehicleCategory);
      const position = origins.map((origin, index) => ({ origin, index })).filter(({ origin }) => inCoverage(origin));
      const results = origins.map(() => null);
      if (!position.length || !inCoverage(destination)) return results;
      try {
        for (let offset = 0; offset < position.length; offset += 24) {
          const batch = position.slice(offset, offset + 24);
          const data = await call('/matrix', { body: {
            mode, coordinates: [...batch.map(({ origin }) => origin), destination],
            sources: batch.map((_, index) => index),
            destinations: [batch.length], allowUnsnapped: true,
          }, unavailable: 'ROUTE_UNAVAILABLE' });
          if (!snapped(data?.destinations?.[0])) continue;
          batch.forEach(({ index }, row) => {
            const distanceMeters = data.distances?.[row]?.[0];
            const durationSeconds = data.durations?.[row]?.[0];
            if (metric(distanceMeters) && metric(durationSeconds) && snapped(data.sources?.[row]))
              results[index] = { distanceMeters, durationSeconds };
          });
        }
        return results;
      } catch (error) {
        if (error instanceof AppError) return results;
        throw error;
      }
    },
  };
}
