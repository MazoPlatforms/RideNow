exports.up = (pgm) =>
  pgm.sql("ALTER TABLE users ADD COLUMN email text NOT NULL DEFAULT '';\nALTER TABLE users ADD COLUMN profile_photo_key text;\nALTER TABLE users ADD COLUMN profile_photo_version text;\nCREATE TABLE profile_photo_uploads (id uuid PRIMARY KEY DEFAULT gen_random_uuid(),user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,object_key text UNIQUE NOT NULL,mime text NOT NULL CHECK(mime IN ('image/jpeg','image/png')),bytes int NOT NULL CHECK(bytes BETWEEN 1 AND 5242880),status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','CLEAN','REJECTED')),object_version text,expires_at timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT now());\nCREATE INDEX profile_photo_uploads_user ON profile_photo_uploads(user_id,created_at DESC);");

exports.down = () => {
  throw new Error('Forward migrations only');
};
