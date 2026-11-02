import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { findLocalRoutes, validateRoutingGraph } from '../../vendor/mazo/routing-graph.mjs';
import { decodeSearchIndex } from '../../vendor/mazo/place-delivery.mjs';
import { ensure } from '../shared/errors.js';
export async function createMaps(config) {
  const bytes = await readFile(config.MAP_GRAPH_PATH),
    manifest = JSON.parse(await readFile(config.MAP_MANIFEST_PATH, 'utf8'));
  ensure(
    createHash('sha256').update(bytes).digest('hex') === manifest.graph_sha256,
    503,
    'GRAPH_CHECKSUM_MISMATCH',
  );
  const graph = validateRoutingGraph(JSON.parse(bytes));
  const searchBytes = await readFile('data/search-index.json');
  ensure(
    createHash('sha256').update(searchBytes).digest('hex') ===
      'bdedf275e0a3457fe2ee66db8fe692357b153cdc4a01e7b3eba9f078fd2edda9',
    503,
    'SEARCH_CHECKSUM_MISMATCH',
  );
  const places = decodeSearchIndex(JSON.parse(searchBytes));
  return {
    coverage: () => ({
      bounds: manifest.bounds,
      version: manifest.graph_sha256,
      limitations: manifest.limitations,
      approvedCategories: config.MAP_CATEGORY_APPROVALS.split(',').filter(Boolean),
    }),
    async route({ waypoints, vehicleCategory }) {
      if (config.NODE_ENV === 'production')
        ensure(
          config.MAP_CATEGORY_APPROVALS.split(',').includes(vehicleCategory),
          503,
          'CATEGORY_MAP_NOT_APPROVED',
        );
      const legs = [];
      for (let i = 1; i < waypoints.length; i++) {
        const result = findLocalRoutes(graph, waypoints[i - 1], waypoints[i], 'driving', {
          alternatives: 1,
        });
        ensure(result.routes?.length, 422, 'ROUTE_UNAVAILABLE');
        const r = result.routes[0];
        legs.push({
          distanceMeters: r.distance,
          durationSeconds: r.duration,
          geometry: r.geometry,
        });
      }
      return {
        distanceMeters: legs.reduce((s, l) => s + l.distanceMeters, 0),
        durationSeconds: legs.reduce((s, l) => s + l.durationSeconds, 0),
        legs,
        provider: 'mazo',
        graphVersion: manifest.graph_sha256,
      };
    },
    async matrix({ origins, destination, vehicleCategory }) {
      const result = [];
      for (const origin of origins) {
        try {
          result.push(await this.route({ waypoints: [origin, destination], vehicleCategory }));
        } catch {
          result.push(null);
        }
      }
      return result;
    },
    async searchPlaces({ query }) {
      const q = query.toLocaleLowerCase();
      return {
        items: places
          .filter(
            (p) => p.coordinates && `${p.name} ${p.address ?? ''}`.toLocaleLowerCase().includes(q),
          )
          .slice(0, 20),
      };
    },
  };
}
