import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { decrypt, encrypt } from '../shared/crypto.js';
import { one, audit, event } from '../shared/db.js';
import { ensure } from '../shared/errors.js';

const appType = z.enum(['RIDER','DRIVER']);
const kind = z.enum(['TICKET','CHAT','COMPLAINT','SAFETY']);
const status = z.enum(['OPEN','IN_PROGRESS','RESOLVED']);
const text = n => z.string().trim().max(n);
const attachments = z.array(z.uuid()).max(5).refine(ids => new Set(ids).size === ids.length).default([]);
const message = { body: text(4000), attachments };
const hasContent = data => data.body.length > 0 || data.attachments.length > 0;
const mobileMessage = z.strictObject({ app: appType, ...message }).refine(hasContent, 'Enter a message or attach a file');
const create = z.strictObject({ app: appType, kind, subject: text(140).min(2), ...message })
  .refine(hasContent, 'Enter a message or attach a file');
const list = z.object({ app: appType, status: status.optional(), kind: kind.optional(), active: z.enum(['true']).optional(),
  before: z.iso.datetime().optional(), beforeId: z.uuid().optional() })
  .refine(p => Boolean(p.before) === Boolean(p.beforeId), 'Both cursor fields are required');
const threadQuery = z.object({ app: appType, before: z.iso.datetime().optional(), beforeId: z.uuid().optional() });
const appQuery = z.object({ app: appType });
const upload = z.strictObject({ app: appType, name: text(120).min(1).regex(/^[^\p{C}/\\]+$/u),
  mime: z.enum(['image/jpeg','image/png','application/pdf']), bytes: z.number().int().min(1).max(8 * 1024 * 1024) });
const adminPermission = 'rides.support';
const ownerAccess = (user, app) => ensure(
  !user.roles.some(role => ['ADMIN','STAFF'].includes(role)) &&
  (user.session_app ? user.session_app === app : app === 'RIDER' || user.roles.includes('DRIVER')),
  403, 'SUPPORT_APP_FORBIDDEN');
const fileFields = 'id,message_id,name,mime,bytes,scan_status';

export function mountSupport(route, config, storage) {
  const encryptText = value => encrypt(value, config.ENCRYPTION_KEY);
  const decryptText = value => decrypt(value, config.ENCRYPTION_KEY);
  function ticketView(ticket) {
    const { subject_encrypted, ...safe } = ticket;
    return { ...safe, subject: decryptText(subject_encrypted) };
  }
  const lockActive = async (db, id) => ensure(await one(db,
    'SELECT id FROM users WHERE id=$1 AND NOT suspended AND deleted_at IS NULL FOR UPDATE', [id]),
    401, 'SESSION_REVOKED');
  const ticketById = async (db, id, app, owner, lock = false) => ensure(await one(db,
    `SELECT id,user_id,app,kind,status,subject_encrypted,created_at,updated_at FROM support_tickets
     WHERE id=$1 AND app=$2 AND ($3::uuid IS NULL OR user_id=$3)${lock ? ' FOR UPDATE' : ''}`,
    [id, app, owner]), 404, 'SUPPORT_TICKET_NOT_FOUND');

  async function tickets({ db, user, req }, admin) {
    const query = list.parse(req.query);
    if (!admin) ownerAccess(user, query.app);
    const rows = (await db.query(`SELECT t.id,t.user_id,t.app,t.kind,t.status,t.subject_encrypted,t.created_at,t.updated_at,
      to_char(t.updated_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS cursor_time,
      CASE WHEN $2::uuid IS NULL THEN u.name ELSE NULL END AS customer_name
      FROM support_tickets t JOIN users u ON u.id=t.user_id
      WHERE t.app=$1 AND ($2::uuid IS NULL OR t.user_id=$2) AND ($3::text IS NULL OR t.status=$3)
      AND ($4::text IS NULL OR t.kind=$4) AND (NOT $7::boolean OR t.status<>'RESOLVED')
      AND ($5::timestamptz IS NULL OR (t.updated_at,t.id)<($5,$6::uuid))
      ORDER BY t.updated_at DESC,t.id DESC LIMIT 51`,
    [query.app, admin ? null : user.id, query.status ?? null, query.kind ?? null, query.before ?? null, query.beforeId ?? null, query.active === 'true'])).rows;
    const last = rows.length > 50 ? rows[49] : null;
    return { items: rows.slice(0,50).map(({ cursor_time, ...row }) => ticketView(row)), next: last ? { before: last.cursor_time, beforeId: last.id } : null };
  }

  async function thread({ db, user, req }, admin) {
    const { app, before, beforeId } = threadQuery.parse(req.query);
    ensure(Boolean(before) === Boolean(beforeId), 400, 'SUPPORT_CURSOR_INVALID');
    if (!admin) ownerAccess(user, app);
    const ticket = await ticketById(db, req.params.id, app, admin ? null : user.id);
    const rows = (await db.query(`SELECT id,sender,body_encrypted,created_at,
      to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS cursor_time FROM support_messages
      WHERE ticket_id=$1 AND ($2::timestamptz IS NULL OR (created_at,id)<($2,$3::uuid))
      ORDER BY created_at DESC,id DESC LIMIT 101`, [ticket.id,before ?? null,beforeId ?? null])).rows;
    const recent = rows.slice(0,100);
    const files = (await db.query(`SELECT ${fileFields} FROM support_attachments WHERE message_id=ANY($1::uuid[]) ORDER BY created_at,id`,
      [recent.map(row => row.id)])).rows;
    const last = rows.length > 100 ? recent.at(-1) : null;
    return { ticket: ticketView(ticket), messages: recent.reverse().map(({ body_encrypted, cursor_time, ...entry }) =>
      ({ ...entry, created_at: cursor_time, body: decryptText(body_encrypted), attachments: files.filter(v => v.message_id === entry.id) })),
    next: last ? { before: last.cursor_time, beforeId: last.id } : null };
  }

  async function writeMessage(db, ticket, actor, sender, body, ids) {
    const entry = await one(db, 'INSERT INTO support_messages(ticket_id,actor_id,sender,body_encrypted) VALUES($1,$2,$3,$4) RETURNING id',
      [ticket.id, actor.id, sender, encryptText(body)]);
    if (ids.length) {
      const bound = await db.query(`UPDATE support_attachments SET message_id=$4 WHERE user_id=$1 AND app=$2
        AND id=ANY($3::uuid[]) AND message_id IS NULL AND scan_status IN ('PENDING','CLEAN')`,
      [actor.id, ticket.app, ids, entry.id]);
      ensure(bound.rowCount === ids.length, 409, 'SUPPORT_ATTACHMENT_UNAVAILABLE');
    }
    await db.query("UPDATE support_tickets SET updated_at=now(),status=CASE WHEN $2='SUPPORT' AND status='OPEN' THEN 'IN_PROGRESS' ELSE status END WHERE id=$1",
      [ticket.id, sender]);
    if (sender === 'SUPPORT') {
      await audit(db, actor.id, 'SUPPORT_REPLIED', ticket.id);
      await event(db, 'support.updated', ticket.id, { ticketId: ticket.id, app: ticket.app }, [ticket.user_id]);
    }
    return { id: entry.id };
  }

  async function download({ db, user, req }, admin) {
    const { app } = appQuery.parse(req.query);
    if (!admin) ownerAccess(user, app);
    const file = ensure(await one(db, `SELECT a.name,a.object_key,a.object_version,a.scan_status FROM support_attachments a
      JOIN support_messages m ON m.id=a.message_id JOIN support_tickets t ON t.id=m.ticket_id
      WHERE a.id=$1 AND t.app=$2 AND ($3::uuid IS NULL OR t.user_id=$3)`,
    [req.params.id, app, admin ? null : user.id]), 404, 'SUPPORT_ATTACHMENT_NOT_FOUND');
    ensure(file.scan_status === 'CLEAN', 409, 'DOCUMENT_NOT_AVAILABLE');
    return { downloadUrl: await storage.download(file.object_key, file.object_version, { attachment: file.name }) };
  }

  const mobile = '/v1/me/support', staff = '/v1/admin/support';
  const ownerWrite = { authorize: (user, body) => ownerAccess(user, body.app), noStore: true, idem: true };
  const staffAccess = { permission: adminPermission, noStore: true };
  route('get', mobile + '/tickets', { query: list, noStore: true }, ctx => tickets(ctx, false));
  route('get', mobile + '/tickets/:id', { query: threadQuery, noStore: true }, ctx => thread(ctx, false));
  route('post', mobile + '/tickets', { ...ownerWrite, body: create, status: 201 }, async ({ db, user, body }) => {
    await lockActive(db, user.id);
    if (body.kind === 'CHAT') {
      const active = await one(db, `SELECT * FROM support_tickets WHERE user_id=$1 AND app=$2 AND kind='CHAT'
        AND status<>'RESOLVED' ORDER BY updated_at DESC LIMIT 1 FOR UPDATE`, [user.id,body.app]);
      if (active) {
        await writeMessage(db, active, user, 'CUSTOMER', body.body, body.attachments);
        return ticketView(active);
      }
    }
    const count = await one(db, "SELECT count(*)::int AS n FROM support_tickets WHERE user_id=$1 AND created_at>now()-interval '1 hour'", [user.id]);
    ensure(count.n < 20, 429, 'SUPPORT_TICKET_LIMIT');
    const ticket = await one(db, 'INSERT INTO support_tickets(user_id,app,kind,subject_encrypted) VALUES($1,$2,$3,$4) RETURNING *',
      [user.id, body.app, body.kind, encryptText(body.subject)]);
    await writeMessage(db, ticket, user, 'CUSTOMER', body.body, body.attachments);
    return ticketView(ticket);
  });
  route('post', mobile + '/tickets/:id/messages', { ...ownerWrite, body: mobileMessage }, async ({ db, user, body, req }) => {
    await lockActive(db, user.id);
    const ticket = await ticketById(db, req.params.id, body.app, user.id, true);
    ensure(ticket.status !== 'RESOLVED', 409, 'SUPPORT_TICKET_RESOLVED');
    return writeMessage(db, ticket, user, 'CUSTOMER', body.body, body.attachments);
  });
  route('post', mobile + '/attachments', { ...ownerWrite, body: upload, status: 201 }, async ({ db, user, body }) => {
    await lockActive(db, user.id);
    const recent = await one(db, "SELECT count(*)::int AS n FROM support_attachments WHERE user_id=$1 AND created_at>now()-interval '1 day'", [user.id]);
    ensure(recent.n < 60, 429, 'SUPPORT_ATTACHMENT_LIMIT');
    const id = randomUUID(), key = `support/${user.id}/${id}/upload`;
    await db.query('INSERT INTO support_attachments(id,user_id,app,name,mime,bytes,object_key) VALUES($1,$2,$3,$4,$5,$6,$7)',
      [id, user.id, body.app, body.name, body.mime, body.bytes, key]);
    return { id, uploadUrl: await storage.upload(key, body.mime, body.bytes) };
  });
  route('post', mobile + '/attachments/:id/submit', { ...ownerWrite, body: z.strictObject({ app: appType }) }, async ({ db, user, body, req }) => {
    await lockActive(db, user.id);
    const file = ensure(await one(db, `SELECT id,scan_status FROM support_attachments WHERE id=$1 AND user_id=$2 AND app=$3
      AND created_at>now()-interval '15 minutes' FOR UPDATE`, [req.params.id,user.id,body.app]), 404, 'SUPPORT_ATTACHMENT_NOT_FOUND');
    if (file.scan_status === 'UNSUBMITTED') {
      await db.query("UPDATE support_attachments SET scan_status='PENDING' WHERE id=$1", [file.id]);
      await db.query("INSERT INTO jobs(kind,payload) VALUES('scan_support',$1)", [{ documentId: file.id }]);
    }
    return { queued: true };
  });
  route('get', mobile + '/attachments/:id', { query: appQuery, noStore: true }, ctx => download(ctx,false));

  route('get', staff + '/tickets', { ...staffAccess, query: list }, ctx => tickets(ctx,true));
  route('get', staff + '/tickets/:id', { ...staffAccess, query: threadQuery }, ctx => thread(ctx,true));
  route('get', staff + '/attachments/:id', { ...staffAccess, query: appQuery }, ctx => download(ctx,true));
  route('post', staff + '/tickets/:id/messages', { ...staffAccess, idem: true,
    body: z.strictObject({ app: appType, body: text(4000).min(1) }) }, async ({ db, user, body, req }) => {
    const ticket = await ticketById(db, req.params.id, body.app, null, true);
    ensure(ticket.status !== 'RESOLVED', 409, 'SUPPORT_TICKET_RESOLVED');
    return writeMessage(db, ticket, user, 'SUPPORT', body.body, []);
  });
  route('patch', staff + '/tickets/:id', { ...staffAccess, idem: true,
    body: z.strictObject({ app: appType, status }) }, async ({ db, user, body, req }) => {
    const ticket = await ticketById(db, req.params.id, body.app, null, true);
    if (ticket.status !== body.status) {
      await db.query('UPDATE support_tickets SET status=$2,updated_at=now() WHERE id=$1', [ticket.id, body.status]);
      await audit(db, user.id, 'SUPPORT_STATUS_CHANGED', ticket.id, { status: body.status });
      await event(db, 'support.updated', ticket.id, { ticketId: ticket.id, app: ticket.app }, [ticket.user_id]);
    }
    return { status: body.status };
  });
}
