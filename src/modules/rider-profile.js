import { one } from '../shared/db.js';

// A completed rider profile remains complete even when driving privileges change.
export const riderProfileCompleteSql = `(
  u.profile_completed_at IS NOT NULL OR (
    NOT u.suspended AND u.deleted_at IS NULL
    AND 'DRIVER'=ANY(u.roles)
    AND EXISTS (
      SELECT 1 FROM driver_profiles d WHERE d.user_id=u.id AND d.status='APPROVED'
    )
  )
)`;

export async function riderProfileComplete(db, user) {
  if (user.profile_completed_at) return true;
  if (!user.roles.includes('DRIVER')) return false;
  const result = await one(db,
    `SELECT ${riderProfileCompleteSql} AS complete FROM users u WHERE u.id=$1`, [user.id]);
  return result?.complete === true;
}
