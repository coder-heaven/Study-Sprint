import { createHash } from 'node:crypto';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { defineSecret, defineInt } from 'firebase-functions/params';
import { ChatError, validate, runChat } from './chat.mjs';
initializeApp();
const apiKey = defineSecret('OPENROUTER_API_KEY');
const nvidiaKey = defineSecret('NVIDIA_API_KEY');
const daily = defineInt('CHAT_DAILY_LIMIT', { default: 100 });
const userDaily = defineInt('CHAT_USER_DAILY_LIMIT', { default: 20 });
const kimiDaily = defineInt('KIMI_DAILY_LIMIT', { default: 10 });
async function reserve(uid, kimi = false) {
  const db = getFirestore();
  const day = new Date().toISOString().slice(0, 10);
  const global = db.doc(`_chatQuota/${day}_${kimi ? 'kimi' : 'all'}`);
  const user = db.doc(`_chatQuota/${day}_${createHash('sha256').update(uid).digest('hex')}`);
  await db.runTransaction(async transaction => {
    const refs = kimi ? [global] : [global, user];
    const rows = await transaction.getAll(...refs);
    const now = Date.now();
    for (let index = 0; index < rows.length; index++) {
      const row = rows[index].data() ?? {};
      const limit = kimi ? kimiDaily.value() : index === 0 ? daily.value() : userDaily.value();
      if ((row.count ?? 0) >= limit) throw new ChatError('resource-exhausted', 'The study chat daily allowance is reached. Please try tomorrow.');
      if (!kimi && index === 1 && now - (row.lastAttempt ?? 0) < 5000) throw new ChatError('resource-exhausted', 'Wait a few seconds before sending another question.');
    }
    for (let index = 0; index < rows.length; index++) transaction.set(refs[index], { count: (rows[index].data()?.count ?? 0) + 1, lastAttempt: now, expiresAt: new Date(now + 7 * 86400000) });
  });
}
export const studyBuddy = onCall({
  region: 'us-central1', secrets: [apiKey, nvidiaKey], enforceAppCheck: true,
  timeoutSeconds: 300, memory: '256MiB', maxInstances: 3, concurrency: 1
}, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Reconnect to Study Sprint and retry.');
  try {
    const input = validate(request.data);
    await reserve(request.auth.uid);
    return await runChat(input, { openrouter: apiKey.value(), nvidia: nvidiaKey.value() }, () => reserve(request.auth.uid, true));
  } catch (error) {
    if (error instanceof ChatError) throw new HttpsError(error.code, error.message);
    // Deliberately no raw request, photo, key, or provider exception logging.
    throw new HttpsError('unavailable', 'Study buddy could not respond. Please try later.');
  }
});
