import { test } from 'node:test';
import assert from 'node:assert/strict';
import { reserve } from '../quota.mjs';
function database(rows = []) {
  const writes = [];
  return { writes, doc: path => ({ path }), runTransaction: async action => action({
    getAll: async (...refs) => refs.map((ref, i) => ({ data: () => rows[i] })),
    set: (ref, value) => writes.push({ ref, value })
  }) };
}
test('first authenticated request reserves both allowances without ReferenceError', async () => {
  const db = database(); await reserve('test-user', false, db);
  assert.equal(db.writes.length, 2); assert(db.writes.every(w => w.value.count === 1));
});
test('Kimi request reserves backup allowance without ReferenceError', async () => {
  const db = database(); await reserve('test-user', true, db);
  assert.equal(db.writes.length, 1); assert.match(db.writes[0].ref.path, /_kimi$/);
});
test('exhausted quota and cooldown reject before writing', async () => {
  for (const rows of [[{count:100}], [{count:0},{count:1,lastAttempt:Date.now()}]]) {
    const db = database(rows);
    await assert.rejects(reserve('test-user', false, db), e => e.code === 'resource-exhausted');
    assert.equal(db.writes.length, 0);
  }
});
