import { initializeApp, applicationDefault, getApps } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
export function createPush(pool, config) {
  if (config.FCM_ENABLED !== 'true') return null;
  const firebase = getApps()[0] ?? initializeApp({ credential: applicationDefault() });
  return async (job) => {
    const tokens = (
      await pool.query('SELECT token FROM device_tokens WHERE user_id=$1', [job.userId])
    ).rows.map((r) => r.token);
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
