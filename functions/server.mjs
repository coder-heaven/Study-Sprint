import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getAppCheck } from 'firebase-admin/app-check';
import { reserve } from './quota.mjs';
import { chatServer } from './http.mjs';
import { googleKey } from './environment.mjs';
try {
  const credentials = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON ?? '');
  if (!process.env.FIREBASE_APP_ID) throw new Error('missing');
  initializeApp({ credential: cert(credentials), projectId: credentials.project_id });
} catch {
  // Fail closed without printing potentially secret JSON parser errors.
  console.error('Configure FIREBASE_SERVICE_ACCOUNT_JSON and FIREBASE_APP_ID privately in Render.');
  process.exit(1);
}
const server = chatServer({
  keys: { openrouter: process.env.OPENROUTER_API_KEY, nvidia: process.env.NVIDIA_API_KEY, gemini: googleKey(), mistral: process.env.MISTRAL_API_KEY?.trim() },
  reserve,
  verify: async (token, appToken) => {
    const [user, app] = await Promise.all([getAuth().verifyIdToken(token), getAppCheck().verifyToken(appToken)]);
    if (app.appId !== process.env.FIREBASE_APP_ID) throw new Error('wrong app');
    return user.uid;
  }
});
server.requestTimeout = 300000;
server.headersTimeout = 15000;
server.listen(Number(process.env.PORT ?? 10000), '0.0.0.0');
process.on('SIGTERM', () => { server.close(); });
