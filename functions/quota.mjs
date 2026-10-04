import { createHash } from 'node:crypto';
import { getFirestore } from 'firebase-admin/firestore';
import { ChatError } from './chat.mjs';
function limit(name, fallback) {
  const n = Number(process.env[name] ?? fallback);
  if (!Number.isSafeInteger(n) || n < 1) throw new ChatError('failed-precondition', 'Chat quota configuration needs attention.');
  return n;
}
export async function reserve(uid, kimi = false, db = getFirestore()) {
  const day = new Date().toISOString().slice(0, 10);
  const global = db.doc(`_chatQuota/${day}_${kimi ? 'kimi' : 'all'}`);
  const user = db.doc(`_chatQuota/${day}_${createHash('sha256').update(uid).digest('hex')}`);
  await db.runTransaction(async transaction => {
    const refs = kimi ? [global] : [global, user];
    const rows = await transaction.getAll(...refs);
    const now = Date.now();
    for (let index = 0; index < rows.length; index++) {
      const row = rows[index].data() ?? {};
      const dailyLimit = kimi ? limit('NVIDIA_DAILY_LIMIT', 100) : index === 0 ? limit('CHAT_DAILY_LIMIT', 1000) : limit('CHAT_USER_DAILY_LIMIT', 100);
      if ((row.count ?? 0) >= dailyLimit) throw new ChatError('resource-exhausted', 'The study chat daily allowance is reached. Please try tomorrow.');
      if (!kimi && index === 1 && now - (row.lastAttempt ?? 0) < 5000) throw new ChatError('resource-exhausted', 'Wait a few seconds before sending another question.');
    }
    for (let index = 0; index < rows.length; index++) transaction.set(refs[index], { count: (rows[index].data()?.count ?? 0) + 1, lastAttempt: now, expiresAt: new Date(now + 7 * 86400000) });
  });
}
