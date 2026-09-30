import { before, after, beforeEach, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, updateDoc, getDoc, getDocs, collection, query, where, limit, writeBatch, serverTimestamp } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-study-sprint', firestore: {
    rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8')
  }});
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });
const db = (uid) => env.authenticatedContext(uid).firestore();
const student = () => ({ name: 'Student', photo: '', accountType: 'local', visible: true, updatedAt: serverTimestamp(),
  focusMinutes: 0, quizAttempts: 0, quizWins: 0, quizCorrect: 0, quizQuestions: 0, lastEventId: '' });
const focusId = 'focus_11111111-1111-4111-8111-111111111111';
const quizId = 'quiz_2026-09-30_' + 'a'.repeat(64);
const focus = { focusMinutes: 25, quizAttempts: 0, quizWins: 0, quizCorrect: 0, quizQuestions: 0 };
const quiz = { focusMinutes: 0, quizAttempts: 1, quizWins: 1, quizCorrect: 8, quizQuestions: 10 };
async function create(uid = 'alice') { await setDoc(doc(db(uid), 'leaderboardStudents', uid), student()); }
function result(uid, id, delta, totals = delta) {
  const firestore = db(uid); const entry = doc(firestore, 'leaderboardStudents', uid);
  const batch = writeBatch(firestore);
  batch.set(doc(entry, 'events', id), { ...delta, createdAt: serverTimestamp() });
  batch.update(entry, { ...totals, lastEventId: id, updatedAt: serverTimestamp() });
  return batch.commit();
}
test('local and Google profiles may join with zero scores and thumbnails', async () => {
  await assertSucceeds(create());
  await assertSucceeds(setDoc(doc(db('bob'), 'leaderboardStudents', 'bob'), { ...student(), accountType: 'google', photo: 'jpegThumbnail' }));
  await assertFails(setDoc(doc(db('eve'), 'leaderboardStudents', 'eve'), { ...student(), focusMinutes: 100 }));
});
test('private fields and writes to another student are denied', async () => {
  await create();
  await assertFails(updateDoc(doc(db('alice'), 'leaderboardStudents', 'alice'), { email: 'private@example.com', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db('bob'), 'leaderboardStudents', 'alice'), { name: 'Changed', updatedAt: serverTimestamp() }));
});
test('authenticated students can view visible profiles; guests cannot', async () => {
  await create();
  await assertSucceeds(getDoc(doc(db('bob'), 'leaderboardStudents', 'alice')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'leaderboardStudents', 'alice')));
  await assertSucceeds(getDocs(query(collection(db('bob'), 'leaderboardStudents'), where('visible', '==', true), limit(100))));
});
test('focus results add only their validated delta once', async () => {
  await create();
  await assertSucceeds(result('alice', focusId, focus));
  await assertFails(result('alice', focusId, focus, { ...focus, focusMinutes: 50 }));
  await assertFails(updateDoc(doc(db('alice'), 'leaderboardStudents', 'alice'), { focusMinutes: 500, updatedAt: serverTimestamp() }));
});
test('quiz wins require at least 80 percent and cannot replay an event', async () => {
  await create();
  await assertFails(result('alice', quizId, { ...quiz, quizCorrect: 7 }));
  await assertSucceeds(result('alice', quizId, quiz));
  await assertFails(result('alice', quizId, quiz, { ...quiz, quizWins: 2, quizAttempts: 2, quizCorrect: 16, quizQuestions: 20 }));
});
test('an event cannot be stored without matching totals or with inflated totals', async () => {
  await create();
  await assertFails(setDoc(doc(db('alice'), 'leaderboardStudents', 'alice', 'events', focusId), { ...focus, createdAt: serverTimestamp() }));
  await assertFails(result('alice', focusId, focus, { ...focus, focusMinutes: 100 }));
});
test('leaving hides a profile from others and stops new score writes', async () => {
  await create();
  await assertSucceeds(updateDoc(doc(db('alice'), 'leaderboardStudents', 'alice'), { visible: false, name: 'Student', photo: '', updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(db('bob'), 'leaderboardStudents', 'alice')));
  await assertSucceeds(getDoc(doc(db('alice'), 'leaderboardStudents', 'alice')));
  await assertFails(result('alice', focusId, focus));
});
