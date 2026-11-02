import { Server } from 'socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import { z } from 'zod';
import { one, transaction } from './shared/db.js';
import { ensure } from './shared/errors.js';
import { socketRate } from './shared/rate-limit.js';
import { location } from './modules/drivers.js';
import { locationSchema } from './app.js';
import { permission } from './modules/auth.js';
export async function attachRealtime(server, { auth, pool, redis, config, logger }) {
  const io = new Server(server, {
    cors: { origin: config.CORS_ORIGINS.split(',') },
    maxHttpBufferSize: 16384,
  });
  const pub = redis.duplicate(),
    sub = redis.duplicate(),
    events = redis.duplicate();
  await Promise.all([pub.connect(), sub.connect(), events.connect()]);
  io.adapter(createAdapter(pub, sub));
  io.use(async (socket, next) => {
    try {
      socket.data.access = socket.handshake.auth.token;
      socket.data.user = await auth.authenticate(socket.data.access);
      next();
    } catch {
      next(new Error('AUTH_REQUIRED'));
    }
  });
  io.on('connection', (socket) => {
    socket.join(`user:${socket.data.user.id}`);
    const handle = (name, fn) =>
      socket.on(name, async (body, ack) => {
        try {
          const user = await auth.authenticate(socket.data.access);
          await socketRate(redis, user.id);
          const result = await fn(user, body);
          if (typeof ack === 'function') ack({ ok: true, data: result });
        } catch (e) {
          if (typeof ack === 'function') ack({ ok: false, code: e.code ?? 'INVALID_REQUEST' });
          if (e.status === 401) socket.disconnect(true);
        }
      });
    handle('ride.subscribe', async (user, body) => {
      const id = z.uuid().parse(body?.rideId);
      const r = await one(pool, 'SELECT rider_id,driver_id FROM rides WHERE id=$1', [id]);
      ensure(
        r &&
          (r.rider_id === user.id || r.driver_id === user.id || permission(user, 'rides.monitor')),
        403,
        'FORBIDDEN',
      );
      socket.join(`ride:${id}`);
      return { subscribed: true };
    });
    handle('driver.location', async (user, body) =>
      transaction(pool, (db) => location(db, user, locationSchema.parse(body))),
    );
  });
  await events.subscribe('ride.realtime', async (message) => {
    let e;
    try {
      e = JSON.parse(message);
    } catch {
      return;
    }
    for (const socket of io.of('/').sockets.values()) {
      if (
        !e.userIds.includes(socket.data.user.id) &&
        !(e.type === 'ride.updated' && socket.rooms.has(`ride:${e.aggregateId}`))
      )
        continue;
      try {
        const user = await auth.authenticate(socket.data.access);
        if (!e.userIds.includes(user.id) && !permission(user, 'rides.monitor')) continue;
        socket.emit(e.type, {
          eventId: e.eventId,
          type: e.type,
          aggregateId: e.aggregateId,
          version: e.payload.version,
          occurredAt: e.occurredAt,
          payload: e.payload,
        });
      } catch {
        socket.disconnect(true);
      }
    }
  });
  const timer = setInterval(async () => {
    for (const socket of io.of('/').sockets.values()) {
      try {
        await auth.authenticate(socket.data.access);
      } catch {
        socket.disconnect(true);
      }
    }
  }, 15000);
  timer.unref();
  return {
    io,
    async close() {
      clearInterval(timer);
      await new Promise((resolve) => io.close(resolve));
      await Promise.all([pub.quit(), sub.quit(), events.quit()]);
      logger.info('Realtime stopped');
    },
  };
}
