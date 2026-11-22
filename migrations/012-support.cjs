exports.up = (pgm) => pgm.sql(`
CREATE TABLE support_tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id),
  app text NOT NULL CHECK(app IN ('RIDER','DRIVER')),
  kind text NOT NULL CHECK(kind IN ('TICKET','CHAT','COMPLAINT','SAFETY')),
  status text NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_PROGRESS','RESOLVED')),
  subject_encrypted text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX support_owner_list ON support_tickets(user_id,app,updated_at DESC);
CREATE INDEX support_queue ON support_tickets(app,status,updated_at DESC);
CREATE TABLE support_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), ticket_id uuid NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
  actor_id uuid NOT NULL REFERENCES users(id), sender text NOT NULL CHECK(sender IN ('CUSTOMER','SUPPORT')),
  body_encrypted text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX support_messages_thread ON support_messages(ticket_id,created_at,id);
CREATE TABLE support_attachments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id),
  app text NOT NULL CHECK(app IN ('RIDER','DRIVER')), message_id uuid REFERENCES support_messages(id) ON DELETE CASCADE,
  name text NOT NULL, mime text NOT NULL CHECK(mime IN ('image/jpeg','image/png','application/pdf')),
  bytes integer NOT NULL CHECK(bytes BETWEEN 1 AND 8388608), object_key text NOT NULL UNIQUE, object_version text,
  scan_status text NOT NULL DEFAULT 'UNSUBMITTED' CHECK(scan_status IN ('UNSUBMITTED','PENDING','CLEAN','INFECTED','FAILED')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX support_attachment_owner ON support_attachments(user_id,created_at);
CREATE INDEX support_attachment_message ON support_attachments(message_id);
CREATE INDEX support_attachment_abandoned ON support_attachments(created_at) WHERE message_id IS NULL;
`);
exports.down = false;
