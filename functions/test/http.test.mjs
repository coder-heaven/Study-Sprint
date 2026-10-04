import test from 'node:test';
import assert from 'node:assert/strict';
import { chatServer } from '../http.mjs';
import { ChatError, NEMOTRON } from '../chat.mjs';
async function withServer(overrides, fn) {
  const calls = [];
  const server = chatServer({ verify: async () => 'guest', reserve: async (...args) => calls.push(args), keys: {}, chat: async () => ({ answer: 'OK', model: NEMOTRON }), ...overrides });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`, calls); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
const question = { data: { messages: [{ role: 'user', content: 'Help' }], photos: [] } };
const post = (url, body = question, headers = {}) => fetch(`${url}/studyBuddy`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer auth-token', 'X-Firebase-AppCheck': 'app-token', ...headers }, body: typeof body === 'string' ? body : JSON.stringify(body) });
test('authenticated request returns callable result and reserves durable quota', async () => withServer({}, async (url, calls) => {
  const res = await post(url); assert.equal(res.status, 200); assert.equal((await res.json()).result.answer, 'OK'); assert.deepEqual(calls, [['guest']]);
}));
test('verification failure blocks quota and provider without leaking secrets', async () => withServer({ verify: async () => { throw new Error('private credential'); } }, async (url, calls) => {
  const res = await post(url); assert.equal(res.status, 401); assert.equal((await res.text()).includes('private'), false); assert.equal(calls.length, 0);
}));
test('malformed body and five photos rejected before quota', async () => withServer({}, async (url, calls) => {
  assert.equal((await post(url, '{broken')).status, 400);
  assert.equal((await post(url, { data: { ...question.data, photos: Array(5).fill('image') } })).status, 400); assert.equal(calls.length, 0);
}));
test('quota exhaustion preserved and raw unexpected errors sanitized', async () => {
  await withServer({ reserve: async () => { throw new ChatError('resource-exhausted', 'limit'); } }, async url => assert.equal((await post(url)).status, 429));
  await withServer({ chat: async () => { throw new Error('private provider body'); } }, async url => {
    const res = await post(url); assert.equal(res.status, 503); assert.equal(await res.text(), '{"error":{"status":"UNAVAILABLE"}}');
  });
});
test('health endpoint public; unknown route and methods rejected', async () => withServer({}, async url => {
  assert.equal((await fetch(`${url}/health`)).status, 200);
  assert.equal((await fetch(`${url}/studyBuddy`)).status, 405);
  assert.equal((await fetch(`${url}/other`)).status, 404);
}));
