import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import {
  chaptersFor, chapterKey, normalizeState, createDefaultState, progressPercent, setChapterProgress,
  scorePractice, PRACTICE_QUESTIONS, PC_VERSION, markingFor, recordEvent, totalsForDays, subjectMinutes,
  streak, remainingSeconds, beginFocusState, pauseFocusState, resumeFocusState, finishFocusState, parseBackup
} from '../core.mjs';
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
  for (const asset of ['index.html', 'styles.css', 'app.js', 'core.mjs', 'data.mjs', 'manifest.webmanifest', 'icon.svg', 'math-config.js']) assert.ok(sw.includes(asset), asset);
});

test('focus history and streak calculations survive normalized state', () => {
  const now = new Date(2026, 0, 3, 12).getTime();
  let state = createDefaultState();
  state = recordEvent(state, { kind: 'focus', at: now, durationSeconds: 1500, subject: 'Physics' });
  state = recordEvent(state, { kind: 'task', at: now, task: 'Vectors' });
  state = recordEvent(state, { kind: 'practice', at: now, attempted: 5, correct: 4 });
  state = recordEvent(state, { kind: 'focus', at: now - 86400000, durationSeconds: 900, subject: 'Chemistry' });
  const totals = totalsForDays(state, 7, now);
  assert.equal(totals.focusedMinutes, 40);
  assert.equal(totals.sessions, 2);
  assert.equal(totals.tasks, 1);
  assert.equal(totals.questions, 5);
  assert.equal(totals.correct, 4);
  assert.equal(streak(state, now), 2);
  assert.equal(remainingSeconds({ active: true, running: true, endAt: now + 2500 }, now), 3);
});

test('v1 notes migrate, v2 normalization is allowlisted, and task ids stay unique', () => {
  const dated = {};
  for (let index = 0; index < 100; index += 1) {
    const day = new Date(Date.UTC(2025, 0, index + 1)).toISOString().slice(0, 10);
    dated[day] = `note-${index}`;
  }
  const progress = JSON.parse('{"good":125,"bad":-5,"__proto__":99,"invalid":"nope"}');
  const input = {
    version: 1,
    arbitrary: 'discard me',
    profile: [],
    progress,
    tasks: Array.from({ length: 101 }, (_, index) => ({ text: `Task ${index}`, id: 'same', done: index === 1 ? 'false' : index === 2 })),
    notes: 'legacy notes',
    notesByDay: { ...dated, 'not-a-day': 'discard me', '2025-01-01': 12 },
    practiceBest: Infinity,
    dailyFocusGoal: -10,
    focus: { focusMinutes: 0, breakMinutes: 999, remainingSeconds: 0, active: true, running: true, endAt: 0 },
    events: [{ kind: 'practice', at: 0, attempted: 2, correct: 9 }, { kind: 'unknown', at: 0 }]
  };
  const state = normalizeState(input);
  assert.deepEqual(Object.keys(state).sort(), ['dailyFocusGoal', 'events', 'focus', 'notes', 'notesByDay', 'practiceBest', 'profile', 'progress', 'tasks', 'version']);
  assert.equal(state.version, 2);
  assert.equal(state.notes, 'legacy notes');
  assert.equal(state.arbitrary, undefined);
  assert.equal(state.profile.name, 'Student');
  assert.equal(state.progress.good, 100);
  assert.equal(Object.prototype.hasOwnProperty.call(state.progress, '__proto__'), false);
  assert.equal(state.tasks.length, 100);
  assert.equal(new Set(state.tasks.map(task => task.id)).size, state.tasks.length);
  assert.equal(state.tasks[1].done, false);
  assert.equal(state.notesByDay['not-a-day'], undefined);
  assert.equal(Object.keys(state.notesByDay).length, 90);
  assert.equal(state.focus.focusMinutes, 5);
  assert.equal(state.focus.breakMinutes, 60);
  assert.equal(state.focus.remainingSeconds, 0);
  assert.equal(state.focus.running, false);
  assert.equal(state.events.length, 1);
  assert.equal(state.events[0].correct, 2);
  assert.deepEqual(normalizeState(state).tasks.map(task => task.id), state.tasks.map(task => task.id));
});

test('recordEvent bounds new events and rejects unsafe kinds without losing zero values', () => {
  const at = 1767441600000;
  let state = createDefaultState();
  state = recordEvent(state, {
    kind: 'practice', at, durationSeconds: Infinity, subject: '__proto__', attempted: 3, correct: 99
  });
  state = recordEvent(state, { kind: 'not-allowed', at, attempted: 10 });
  state = recordEvent(state, { kind: 'focus', at: 0, durationSeconds: 0 });
  assert.equal(state.events.length, 2);
  assert.deepEqual(state.events[0], {
    kind: 'practice', at, durationSeconds: 0, subject: '', task: '', attempted: 3, correct: 3
  });
  assert.equal(state.events[1].at, 0);
  assert.equal(state.events[1].durationSeconds, 0);
});

test('future events do not affect totals, subjects, or streaks', () => {
  const now = new Date(2026, 0, 3, 12).getTime();
  let state = createDefaultState();
  state = recordEvent(state, { kind: 'focus', at: now, durationSeconds: 300, subject: 'Physics' });
  state = recordEvent(state, { kind: 'focus', at: now + 1, durationSeconds: 10800, subject: 'Chemistry' });
  state = recordEvent(state, { kind: 'task', at: now + 1 });
  state = recordEvent(state, { kind: 'practice', at: now + 1, attempted: 100, correct: 100 });
  state = recordEvent(state, { kind: 'task', at: now - 86400000 });
  const totals = totalsForDays(state, 1, now);
  assert.equal(totals.focusedMinutes, 5);
  assert.equal(totals.sessions, 1);
  assert.equal(totals.tasks, 0);
  assert.equal(totals.questions, 0);
  assert.deepEqual(subjectMinutes(state, 1, now), [['Physics', 5]]);
  assert.equal(streak(state, now), 2);
});

test('focus state helpers exclude paused time and record a natural deadline once', () => {
  const startedAt = 1767441600000;
  const started = beginFocusState(createDefaultState().focus, false, startedAt);
  assert.equal(started.endAt, startedAt + 25 * 60 * 1000);
  const paused = pauseFocusState(started, startedAt + 5 * 60 * 1000);
  assert.equal(paused.remainingSeconds, 20 * 60);
  const resumedAt = startedAt + 60 * 60 * 1000;
  const resumed = resumeFocusState(paused, resumedAt);
  const finished = finishFocusState({ ...createDefaultState(), focus: resumed }, resumed.endAt + 1000);
  const finishedAgain = finishFocusState(finished, resumed.endAt + 2000);
  assert.equal(finished.events.length, 1);
  assert.equal(finished.events[0].at, resumed.endAt);
  assert.equal(finished.events[0].durationSeconds, 25 * 60);
  assert.equal(finishedAgain.events.length, 1);
  assert.equal(finished.focus.active, false);
  assert.equal(finished.focus.running, false);
  assert.equal(finished.focus.remainingSeconds, 25 * 60);
});

test('backup parsing accepts v1/current envelopes and rejects arbitrary, future, or oversized data', () => {
  const legacy = parseBackup({
    version: PC_VERSION,
    exportedAt: '2026-01-03T12:00:00.000Z',
    state: { profile: { name: 'Legacy learner', exam: 'JEE', grade: '12' }, notes: 'keep this note' }
  });
  assert.equal(legacy.version, 2);
  assert.equal(legacy.profile.name, 'Legacy learner');
  assert.equal(legacy.notes, 'keep this note');

  const current = parseBackup({ schema: 2, state: createDefaultState() });
  assert.equal(current.version, 2);
  assert.throws(() => parseBackup({}), /backup/i);
  assert.throws(() => parseBackup({ profile: {}, notes: 'raw state is not an envelope' }), /backup/i);
  assert.throws(() => parseBackup({ schema: 3, state: createDefaultState() }), /schema/i);
  assert.throws(() => parseBackup({ schema: 2, state: { tasks: Array(101).fill({ text: 'too many' }) } }), /tasks/i);
  assert.throws(() => parseBackup({ schema: 2, state: { events: [{ kind: 'focus', at: 0, durationSeconds: 10801 }] } }), /duration/i);
});

test('statistics use local calendar days across a DST boundary', () => {
  const coreUrl = new URL('../core.mjs', import.meta.url).href;
  const script = `
    import { createDefaultState, recordEvent, totalsForDays, subjectMinutes } from ${JSON.stringify(coreUrl)};
    const now = new Date(2026, 2, 9, 12).getTime();
    let state = createDefaultState();
    state = recordEvent(state, { kind: 'focus', at: new Date(2026, 2, 8, 12).getTime(), durationSeconds: 60, subject: 'Physics' });
    state = recordEvent(state, { kind: 'focus', at: new Date(2026, 2, 9, 11).getTime(), durationSeconds: 60, subject: 'Physics' });
    state = recordEvent(state, { kind: 'focus', at: new Date(2026, 2, 7, 23, 30).getTime(), durationSeconds: 600, subject: 'Chemistry' });
    console.log(JSON.stringify({ two: totalsForDays(state, 2, now), one: totalsForDays(state, 1, now), subjects: subjectMinutes(state, 2, now) }));
  `;
  const result = spawnSync(process.execPath, ['--input-type=module', '--eval', script], {
    env: { ...process.env, TZ: 'America/New_York' }, encoding: 'utf8'
  });
  assert.equal(result.status, 0, result.stderr);
  const output = JSON.parse(result.stdout);
  assert.deepEqual(output.two.dailyMinutes, [1, 1]);
  assert.deepEqual(output.one.dailyMinutes, [1]);
  assert.deepEqual(output.subjects, [['Physics', 2]]);
});
