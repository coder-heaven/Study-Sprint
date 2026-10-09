import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chaptersFor, chapterKey, normalizeState, createDefaultState, progressPercent, setChapterProgress, scorePractice, PRACTICE_QUESTIONS, PC_VERSION, markingFor } from '../core.mjs';
import { androidData, sync } from '../scripts/sync.mjs';

test('PC version, syllabus and marking are synchronized to Android source', async () => {
  const source = await androidData();
  assert.equal(PC_VERSION, source.version);
  assert.equal(await sync({ check: true }), source.version);
  assert.deepEqual(chaptersFor('CET', '11'), source.syllabus.cetEleven);
  assert.deepEqual(chaptersFor('CET', '12'), source.syllabus.cetTwelve);
  assert.deepEqual(chaptersFor('JEE', '11').Mathematics, source.syllabus.eleven.Mathematics);
  assert.deepEqual(chaptersFor('NEET', '12').Biology, source.syllabus.twelve.Biology);
  assert.equal(chaptersFor('NEET', '11').Mathematics, undefined);
  assert.deepEqual(markingFor('JEE'), { correct: 4, incorrect: -1 });
});

test('local state normalizes invalid profile and keeps safe bounds', () => {
  const state = normalizeState({ profile: { name: '  Learner  ', exam: 'MBA', grade: 12 }, tasks: [{ text: 'Read chapter', id: 'a' }, null, { text: 12 }], notes: 'x'.repeat(22000), practiceBest: -2 });
  assert.equal(state.profile.name, 'Learner');
  assert.equal(state.profile.exam, 'CET');
  assert.equal(state.profile.grade, '12');
  assert.equal(state.tasks.length, 1);
  assert.equal(state.notes.length, 20000);
  assert.equal(state.practiceBest, 0);
});

test('progress is scoped by exam, grade and chapter and survives JSON backup', () => {
  let state = createDefaultState();
  const chapters = chaptersFor('CET', '11');
  for (const [subject, list] of Object.entries(chapters)) for (const chapter of list) state = setChapterProgress(state, 'CET', '11', subject, chapter, 100);
  assert.equal(progressPercent(state), 100);
  assert.equal(progressPercent(state, 'CET', '12'), 0);
  assert.equal(progressPercent(normalizeState(JSON.parse(JSON.stringify(state)))), 100);
  assert.equal(state.progress[chapterKey('CET', '11', 'Chemistry', 'Basic Concepts')], 100);
});

test('original chemistry practice has four options and scores answers', () => {
  assert.equal(PRACTICE_QUESTIONS.length, 5);
  assert.ok(PRACTICE_QUESTIONS.every(q => q.options.length === 4 && q.answer >= 0 && q.answer <= 3));
  assert.equal(scorePractice(PRACTICE_QUESTIONS, PRACTICE_QUESTIONS.map(q => q.answer)), 5);
  assert.equal(scorePractice(PRACTICE_QUESTIONS, []), 0);
});

test('PC entry and service worker include offline install assets', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  const sw = await readFile(new URL('../sw.js', import.meta.url), 'utf8');
  const manifest = JSON.parse(await readFile(new URL('../manifest.webmanifest', import.meta.url), 'utf8'));
  assert.match(html, /type="module" src="app.js"/);
  assert.equal(manifest.display, 'standalone');
  for (const asset of ['index.html', 'styles.css', 'app.js', 'core.mjs', 'data.mjs', 'manifest.webmanifest', 'icon.svg']) assert.ok(sw.includes(asset), asset);
});
