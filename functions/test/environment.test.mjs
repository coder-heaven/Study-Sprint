import test from 'node:test';
import assert from 'node:assert/strict';
import { googleKey } from '../environment.mjs';

const first = 'test_google_key_1234567890';
const second = 'test_google_key_0987654321';
test('Google environment accepts either documented name and surrounding whitespace', () => {
  assert.equal(googleKey({ GEMINI_API_KEY: ` ${first}\n` }), first);
  assert.equal(googleKey({ GOOGLE_API_KEY: second }), second);
  assert.equal(googleKey({ GEMINI_API_KEY: first, GOOGLE_API_KEY: second }), first);
  assert.equal(googleKey({ GEMINI_API_KEY: '', GOOGLE_API_KEY: second }), second);
  assert.equal(googleKey({ GEMINI_API_KEY: `Bearer ${first}` }), undefined);
  assert.equal(googleKey({}), undefined);
});
