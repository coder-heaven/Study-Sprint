import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validate, runChat, GPT, NEMOTRON, KIMI, GLM, MUSE, MISTRAL } from '../chat.mjs';
import { parseMcqs } from '../mcq.mjs';
const quiz = '1. Photon energy is?\nA. h*f\nB. h/f\nC. f/h\nD. h+f\nAnswer: A';
const keys = { tavily: 'tvly-' + 't'.repeat(32), openrouter: 'sk-or-v1-' + 'a'.repeat(64), nvidia: 'nvapi-' + 'b'.repeat(64), mistral: 'm'.repeat(32) };
const provider = text => new Response(JSON.stringify({ choices: [{ message: { content: text }, finish_reason: 'stop' }] }));
const search = (title = 'MHT-CET Physics questions', raw = quiz, url = 'https://example.com/cet') =>
  new Response(JSON.stringify({ results: [{ title, url, raw_content: raw, content: raw }] }));
const photo = 'data:image/jpeg;base64,' + Buffer.from([255,216,255,224,0,255,217]).toString('base64');
const ten = Array.from({ length: 10 }, (_, i) => quiz.replace('1.', `${i + 1}.`)).join('\n\n');

test('rejects fifth option without silently deleting or remapping it', () => {
  assert.throws(() => parseMcqs(quiz.replace('Answer:', 'E. other\nAnswer:')));
  assert.throws(() => parseMcqs(quiz.replace('Answer: A', 'Answer: E')));
  assert.equal(parseMcqs(quiz)[0].options.length, 4);
});
test('five-option output is not returned by any selected study model', async () => {
  for (const model of [GPT, NEMOTRON, KIMI, GLM, MUSE, MISTRAL])
    await assert.rejects(runChat(validate({ messages: [{ role: 'user', content: 'Explain energy' }], model }), keys, async () => {}, async () => provider(quiz.replace('Answer:', 'E. extra\nAnswer:'))));
});
test('online MCQ uses Tavily only, with bounded search and no secret in body', async () => {
  for (const model of [GPT, NEMOTRON, KIMI, GLM, MUSE, MISTRAL]) {
    let calls = 0; let reserves = 0;
    const result = await runChat(validate({ messages: [{ role: 'user', content: 'Photon energy' }], model, mode: 'web_mcq' }), keys, async () => reserves++, async (url, options) => {
      calls++; assert.equal(url, 'https://api.tavily.com/search');
      assert.equal(options.headers.Authorization, `Bearer ${keys.tavily}`);
      const body = JSON.parse(options.body);
      assert.equal(body.search_depth, 'basic'); assert.equal(body.include_answer, false);
      assert.ok(body.max_results <= 5); assert.ok(!options.body.includes(keys.tavily));
      return search();
    });
    assert.equal(calls, 1); assert.equal(reserves, 0); assert.equal(result.model, 'tavily/search');
    assert.equal(result.answer, quiz); assert.equal(result.sources.length, 1); assert.equal(result.quiz, true);
  }
});
test('NEET and JEE source titles must match the selected exam', async () => {
  for (const [exam, title] of [['NEET', 'NEET UG physics questions'], ['JEE', 'JEE Main physics questions']]) {
    const result = await runChat(validate({ messages: [{ role: 'user', content: 'Photon energy' }], exam, mode: 'web_mcq' }), keys, async () => {}, async (_url, options) => {
      assert.ok(JSON.parse(options.body).query.includes(exam === 'NEET' ? 'NEET UG' : 'JEE Main'));
      return search(title);
    });
    assert.equal(result.answer, quiz);
  }
});
test('partial, wrong-exam, fifth-option and insecure sources fail closed', async () => {
  const bad = [search('NEET Physics questions'), search('generic physics'), search('MHT-CET Physics', quiz.replace('Answer:', 'E. extra\nAnswer:')),
    search('MHT-CET Physics', quiz.replace('Answer: A', '')), search('MHT-CET Physics', quiz, 'http://example.com/cet'),
    search('MHT-CET Physics', quiz, 'https://user:pass@example.com/cet')];
  for (const response of bad) {
    await assert.rejects(runChat(validate({ messages: [{ role: 'user', content: 'energy' }], mode: 'web_mcq' }), keys, async () => {}, async () => response), e => e.code === 'unavailable');
  }
});
test('missing Tavily key does not call search or fabricate questions', async () => {
  let calls = 0;
  await assert.rejects(runChat(validate({ messages: [{ role: 'user', content: 'energy' }], mode: 'web_mcq' }), {}, async () => {}, async () => { calls++; return search(); }),
    e => e.code === 'failed-precondition' && e.message.includes('TAVILY_API_KEY'));
  assert.equal(calls, 0);
});
test('photo PDF uses NVIDIA vision and rejects an incomplete ten-question reply', async () => {
  let calls = 0; let reserves = 0;
  const input = validate({ messages: [{ role: 'user', content: 'Create photo questions' }], photos: [photo], mode: 'photo_pdf' });
  const result = await runChat(input, keys, async () => reserves++, async (url, options) => {
    calls++; assert.match(url, /nvidia/); assert.equal(options.headers.Authorization, `Bearer ${keys.nvidia}`);
    const body = JSON.parse(options.body); assert.match(body.messages.at(-1).content[0].text, /exactly 10/);
    assert.equal(body.messages.at(-1).content[1].image_url.url, photo);
    return provider(ten);
  });
  assert.equal(result.model, KIMI); assert.equal(parseMcqs(result.answer, 10).length, 10);
  assert.equal(calls, 1); assert.equal(reserves, 1);
  await assert.rejects(runChat(input, keys, async () => {}, async () => provider(quiz)), e => e.code === 'unavailable');
});
test('search provider quota and auth errors are safe and distinct', async () => {
  for (const [status, code] of [[429, 'resource-exhausted'], [432, 'resource-exhausted'], [401, 'failed-precondition'], [503, 'unavailable']]) {
    await assert.rejects(runChat(validate({ messages: [{ role: 'user', content: 'Mole concept' }], mode: 'web_mcq' }), keys, async () => {}, async () => new Response(keys.tavily, { status })),
      e => e.code === code && !e.message.includes(keys.tavily));
  }
});
