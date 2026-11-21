import { initializeApp, applicationDefault, getApps } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { allowsNotification, notificationCategory } from '../modules/mobile-preferences.js';
export function createPush(pool, config) {
  if (config.FCM_ENABLED !== 'true') return null;
  const firebase = getApps()[0] ?? initializeApp({ credential: applicationDefault() });
  return async (job) => {
    const tokens = (
      await pool.query(`SELECT dt.token,dt.device_app,rider.preferences AS rider,driver.preferences AS driver
        FROM device_tokens dt JOIN users u ON u.id=dt.user_id
        LEFT JOIN notification_preferences rider ON rider.user_id=u.id AND rider.app='RIDER'
        LEFT JOIN notification_preferences driver ON driver.user_id=u.id AND driver.app='DRIVER'
        WHERE u.id=$1 AND NOT u.suspended AND u.deleted_at IS NULL`, [job.userId])
    ).rows.filter(row => {
      const app = row.device_app ?? (notificationCategory(job.type, job.payload) === 'offers' ||
        job.type.startsWith('driver.') ? 'DRIVER' : 'RIDER');
      return allowsNotification(row[app.toLowerCase()], app, job.type, job.payload, 'push');
    }).map(row => row.token);
    for (let i = 0; i < tokens.length; i += 500) {
      const batch = tokens.slice(i, i + 500),
        result = await getMessaging(firebase).sendEachForMulticast({
          tokens: batch,
          data: {
            type: job.type,
            notificationId: job.notificationId,
            payload: JSON.stringify(job.payload),
          },
        });
      for (let j = 0; j < result.responses.length; j++) {
        const error = result.responses[j].error;
        if (
          [
            'messaging/registration-token-not-registered',
            'messaging/invalid-registration-token',
          ].includes(error?.code)
        )
          await pool.query('DELETE FROM device_tokens WHERE token=$1', [batch[j]]);
        else if (error) throw error;
      }
    }
  };
}
