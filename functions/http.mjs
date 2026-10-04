import { createServer } from 'node:http';
import { ChatError, validate, runChat } from './chat.mjs';
const status = { 'invalid-argument': 400, unauthenticated: 401, 'permission-denied': 403, 'resource-exhausted': 429, 'failed-precondition': 503, unavailable: 503 };
export function chatServer({ verify, reserve, keys, chat = runChat }) {
  let active = 0;
  return createServer(async (req, res) => {
    const send = (code, body) => {
      if (res.destroyed) return;
      res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      res.end(JSON.stringify(body));
    };
    if (req.method === 'GET' && req.url === '/health') { send(200, { ok: true }); return; }
    if (req.url !== '/studyBuddy') { send(404, { error: { status: 'NOT_FOUND' } }); return; }
    if (req.method !== 'POST') { send(405, { error: { status: 'INVALID_ARGUMENT' } }); return; }
    if (active >= 3) { send(429, { error: { status: 'RESOURCE_EXHAUSTED' } }); return; }
    active++;
    try {
      if (!(req.headers['content-type'] ?? '').startsWith('application/json')) throw new ChatError('invalid-argument', 'Use JSON.');
      const match = /^Bearer (\S+)$/.exec(req.headers.authorization ?? '');
      const appToken = req.headers['x-firebase-appcheck'];
      if (!match || typeof appToken !== 'string' || match[1].length > 8192 || appToken.length > 8192) throw new ChatError('unauthenticated', 'Installation verification required.');
      let uid;
      try { uid = await verify(match[1], appToken); } catch { throw new ChatError('unauthenticated', 'Installation verification failed.'); }
      if (!uid) throw new ChatError('unauthenticated', 'Installation verification failed.');
      const chunks = []; let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 1500000) throw new ChatError('invalid-argument', 'Question is too large.');
        chunks.push(chunk);
      }
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new ChatError('invalid-argument', 'Invalid JSON.'); }
      const input = validate(body?.data);
      await reserve(uid);
      const result = await chat(input, keys, () => reserve(uid, true));
      send(200, { result });
    } catch (error) {
      const code = error instanceof ChatError ? error.code : 'unavailable';
      // Never expose or log provider errors, credentials, questions or photos.
      send(status[code] ?? 503, { error: { status: code.replaceAll('-', '_').toUpperCase() } });
    } finally { active--; }
  });
}
