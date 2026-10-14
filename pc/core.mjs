import { PC_VERSION, SYLLABUS, MARKING } from "./data.mjs";

export { PC_VERSION };
export const EXAMS = ["CET", "JEE", "NEET"];
export const GRADES = ["11", "12"];
export const STATE_VERSION = 2;

const MAX_TASKS = 100;
const MAX_EVENTS = 2000;
const MAX_NOTES_DAYS = 90;
const MAX_PROGRESS_KEYS = 1000;
const MAX_EVENT_DURATION_SECONDS = 3 * 60 * 60;
const MAX_EVENT_COUNT = 100000;
const MAX_TIMESTAMP = 8640000000000000;
const MAX_NAME_LENGTH = 60;
const MAX_TASK_LENGTH = 160;
const MAX_SUBJECT_LENGTH = 80;
const MAX_CHAPTER_LENGTH = 100;
const MAX_TASK_ID_LENGTH = 120;
const MAX_NOTES_LENGTH = 20000;
const MAX_DAY_NOTE_LENGTH = 5000;
const MAX_FOCUS_GOAL = 480;
const MAX_PRACTICE_BEST = 5;
const UNSAFE_KEYS = new Set(["__proto__", "constructor", "prototype"]);
const STATE_FIELDS = new Set([
  "version", "profile", "progress", "tasks", "notes", "notesByDay",
  "practiceBest", "dailyFocusGoal", "focus", "events"
]);

export function chaptersFor(exam, grade) {
  const suffix = grade === "11" ? "Eleven" : "Twelve";
  if (exam === "CET") return SYLLABUS[`cet${suffix}`];
  const subjects = exam === "NEET" ? ["Physics", "Chemistry", "Biology"] : ["Physics", "Chemistry", "Mathematics"];
  const all = SYLLABUS[grade === "11" ? "eleven" : "twelve"];
  return Object.fromEntries(subjects.map(subject => [subject, all[subject] ?? []]));
}

export function markingFor(exam) { return MARKING[exam] ?? MARKING.JEE; }

export function chapterKey(exam, grade, subject, chapter) {
  return [exam, grade, subject, chapter].join("|");
}

export function createDefaultState() {
  return {
    version: STATE_VERSION,
    profile: { name: "Student", exam: "CET", grade: "11", onboarded: false, termsAccepted: false },
    progress: {},
    tasks: [],
    notes: "",
    notesByDay: {},
    practiceBest: 0,
    dailyFocusGoal: 120,
    focus: {
      focusMinutes: 25, breakMinutes: 5, remainingSeconds: 25 * 60,
      endAt: 0, active: false, running: false, isBreak: false,
      task: "", subject: "", softMode: true
    },
    events: []
  };
}

export function normalizeState(input) {
  const base = createDefaultState();
  const state = isRecord(input) ? input : {};
  const profile = isRecord(state.profile) ? state.profile : {};
  const rawTasks = Array.isArray(state.tasks) ? state.tasks : [];
  const tasks = [];
  const usedTaskIds = new Set();
  for (const [index, task] of rawTasks.entries()) {
    if (!isRecord(task) || typeof task.text !== "string") continue;
    const text = cleanText(task.text, MAX_TASK_LENGTH);
    if (!text) continue;
    const done = isFlag(task.done);
    const createdAt = normalizeTimestamp(task.createdAt, 0);
    const completedAt = done ? normalizeTimestamp(task.completedAt, 0) : 0;
    const id = uniqueTaskId(task.id, text, task.chapter, index, usedTaskIds);
    tasks.push({
      text,
      done,
      id,
      chapter: cleanText(task.chapter, MAX_CHAPTER_LENGTH),
      createdAt,
      completedAt
    });
    if (tasks.length === MAX_TASKS) break;
  }

  const rawNotesByDay = isRecord(state.notesByDay) ? state.notesByDay : {};
  const datedNotes = Object.entries(rawNotesByDay)
    .filter(([day, note]) => isDayKey(day) && typeof note === "string")
    .sort(([left], [right]) => left.localeCompare(right))
    .slice(-MAX_NOTES_DAYS);

  return {
    version: STATE_VERSION,
    profile: {
      name: cleanText(profile.name, MAX_NAME_LENGTH) || base.profile.name,
      exam: EXAMS.includes(profile.exam) ? profile.exam : base.profile.exam,
      grade: GRADES.includes(String(profile.grade)) ? String(profile.grade) : base.profile.grade,
      onboarded: isFlag(profile.onboarded),
      termsAccepted: isFlag(profile.termsAccepted)
    },
    progress: normalizeProgress(state.progress),
    tasks,
    notes: typeof state.notes === "string" ? state.notes.slice(0, MAX_NOTES_LENGTH) : "",
    notesByDay: Object.fromEntries(datedNotes.map(([day, note]) => [day, note.slice(0, MAX_DAY_NOTE_LENGTH)])),
    practiceBest: clampInteger(state.practiceBest, 0, MAX_PRACTICE_BEST, base.practiceBest),
    dailyFocusGoal: clampInteger(state.dailyFocusGoal, 15, MAX_FOCUS_GOAL, base.dailyFocusGoal),
    focus: normalizeFocus(state.focus, base.focus),
    events: normalizeEvents(state.events)
  };
}

function normalizeFocus(input, fallback) {
  const value = isRecord(input) ? input : {};
  const focusMinutes = clampInteger(value.focusMinutes, 5, 180, fallback.focusMinutes);
  const breakMinutes = clampInteger(value.breakMinutes, 1, 60, fallback.breakMinutes);
  const isBreak = isFlag(value.isBreak);
  const defaultSeconds = (isBreak ? breakMinutes : focusMinutes) * 60;
  const rawRemaining = finiteNumber(value.remainingSeconds);
  const remaining = rawRemaining === null ? defaultSeconds : Math.floor(rawRemaining);
  let active = isFlag(value.active);
  let running = active && isFlag(value.running);
  let endAt = normalizeTimestamp(value.endAt, 0);
  if (!running || endAt <= 0) {
    running = false;
    endAt = 0;
  }
  return {
    focusMinutes, breakMinutes,
    remainingSeconds: Math.max(0, Math.min(defaultSeconds, Number.isFinite(remaining) ? remaining : defaultSeconds)),
    endAt, active, running, isBreak,
    softMode: value.softMode === undefined ? true : isFlag(value.softMode),
    task: cleanText(value.task, MAX_TASK_LENGTH),
    subject: cleanSubject(value.subject)
  };
}

export function dayKey(at = Date.now()) {
  const timestamp = normalizeTimestamp(at instanceof Date ? at.getTime() : at, Date.now());
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function remainingSeconds(focus, now = Date.now()) {
  const value = isRecord(focus) ? focus : {};
  const rawRemaining = finiteNumber(value.remainingSeconds);
  const focusMinutes = clampInteger(value.focusMinutes, 5, 180, 25);
  const breakMinutes = clampInteger(value.breakMinutes, 1, 60, 5);
  const maximum = (isFlag(value.isBreak) ? breakMinutes : focusMinutes) * 60;
  if (!isFlag(value.active) || !isFlag(value.running)) return Math.max(0, Math.min(maximum, Math.floor(rawRemaining ?? 0)));
  const endAt = normalizeTimestamp(value.endAt, 0);
  const current = normalizeTimestamp(now, Date.now());
  if (!endAt) return Math.max(0, Math.min(maximum, Math.floor(rawRemaining ?? 0)));
  return Math.max(0, Math.min(maximum, Math.ceil((endAt - current) / 1000)));
}

export function recordEvent(state, event) {
  const next = normalizeState(state);
  const candidate = isRecord(event) ? event : {};
  const fallbackAt = Date.now();
  const normalized = normalizeEvent({
    kind: candidate.kind,
    at: finiteTimestamp(candidate.at) === null ? fallbackAt : candidate.at,
    durationSeconds: candidate.durationSeconds,
    subject: candidate.subject,
    task: candidate.task,
    attempted: candidate.attempted,
    correct: candidate.correct
  });
  if (normalized) next.events = [...next.events, normalized].slice(-MAX_EVENTS);
  return next;
}

export function totalsForDays(state, days = 1, now = Date.now()) {
  const count = normalizeDays(days);
  const current = normalizeTimestamp(now, Date.now());
  const start = shiftLocalDays(localMidnight(current), -(count - 1));
  const events = eventsInRange(state, start.getTime(), current);
  const focusSecondsByDay = new Map();
  for (const event of events) {
    if (event.kind !== "focus") continue;
    const key = dayKey(event.at);
    focusSecondsByDay.set(key, (focusSecondsByDay.get(key) || 0) + event.durationSeconds);
  }
  return {
    focusedMinutes: Math.floor(events.filter(event => event.kind === "focus").reduce((sum, event) => sum + event.durationSeconds, 0) / 60),
    sessions: events.filter(event => event.kind === "focus").length,
    tasks: events.filter(event => event.kind === "task").length,
    questions: events.filter(event => event.kind === "practice").reduce((sum, event) => sum + event.attempted, 0),
    correct: events.filter(event => event.kind === "practice").reduce((sum, event) => sum + event.correct, 0),
    dailyMinutes: Array.from({ length: count }, (_, index) => {
      const key = dayKey(shiftLocalDays(start, index).getTime());
      return Math.floor((focusSecondsByDay.get(key) || 0) / 60);
    })
  };
}

export function subjectMinutes(state, days = 30, now = Date.now()) {
  const count = normalizeDays(days);
  const current = normalizeTimestamp(now, Date.now());
  const start = shiftLocalDays(localMidnight(current), -(count - 1));
  const secondsBySubject = new Map();
  for (const event of eventsInRange(state, start.getTime(), current)) {
    if (event.kind !== "focus") continue;
    const subject = event.subject || "General";
    secondsBySubject.set(subject, (secondsBySubject.get(subject) || 0) + event.durationSeconds);
  }
  return [...secondsBySubject.entries()]
    .map(([subject, seconds]) => [subject, Math.floor(seconds / 60)])
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));
}

export function streak(state, now = Date.now()) {
  const current = normalizeTimestamp(now, Date.now());
  const studyDays = new Set(eventsInRange(state, -MAX_TIMESTAMP, current)
    .filter(event => (event.kind === "focus" && event.durationSeconds >= 5 * 60) || event.kind === "task" || event.kind === "practice")
    .map(event => dayKey(event.at)));
  let date = new Date(current);
  if (!studyDays.has(dayKey(date))) date = shiftLocalDays(date, -1);
  let count = 0;
  while (studyDays.has(dayKey(date))) { count += 1; date = shiftLocalDays(date, -1); }
  return count;
}

export function beginFocusState(focus, isBreak = false, now = Date.now()) {
  const current = normalizeFocus(focus, createDefaultState().focus);
  const start = normalizeTimestamp(now, Date.now());
  const breakPhase = isFlag(isBreak);
  const seconds = (breakPhase ? current.breakMinutes : current.focusMinutes) * 60;
  return {
    ...current,
    active: true,
    running: true,
    isBreak: breakPhase,
    remainingSeconds: seconds,
    endAt: Math.min(MAX_TIMESTAMP, start + seconds * 1000)
  };
}

export function pauseFocusState(focus, now = Date.now()) {
  const current = normalizeFocus(focus, createDefaultState().focus);
  if (!current.active || !current.running) return { ...current, running: false, endAt: 0 };
  return {
    ...current,
    remainingSeconds: remainingSeconds(current, now),
    running: false,
    endAt: 0
  };
}

export function resumeFocusState(focus, now = Date.now()) {
  const current = normalizeFocus(focus, createDefaultState().focus);
  if (!current.active || current.running) return current;
  const start = normalizeTimestamp(now, Date.now());
  return {
    ...current,
    running: true,
    endAt: Math.min(MAX_TIMESTAMP, start + current.remainingSeconds * 1000)
  };
}

export function finishFocusState(state, now = Date.now()) {
  const next = normalizeState(state);
  const current = next.focus;
  if (!current.active) return next;

  const currentTime = normalizeTimestamp(now, Date.now());
  const left = remainingSeconds(current, currentTime);
  const phaseSeconds = (current.isBreak ? current.breakMinutes : current.focusMinutes) * 60;
  const elapsed = Math.max(0, phaseSeconds - Math.min(phaseSeconds, left));
  const naturallyPastDeadline = current.running && current.endAt > 0 && currentTime >= current.endAt;
  if (!current.isBreak && elapsed >= 60) {
    next.events = [...next.events, {
      kind: "focus",
      at: naturallyPastDeadline ? current.endAt : currentTime,
      durationSeconds: elapsed,
      subject: current.subject,
      task: current.task,
      attempted: 0,
      correct: 0
    }].slice(-MAX_EVENTS);
  }
  next.focus = {
    ...current,
    active: false,
    running: false,
    isBreak: false,
    endAt: 0,
    remainingSeconds: current.focusMinutes * 60
  };
  return normalizeState(next);
}

export function parseBackup(payload, now = Date.now()) {
  const root = isRecord(payload) ? payload : null;
  if (!root) throw new Error("Invalid Study Sprint PC backup");

  let state;
  const hasSchema = Object.prototype.hasOwnProperty.call(root, "schema");
  if (hasSchema) {
    const schema = finiteNumber(root.schema);
    if (schema === null || !Number.isInteger(schema) || schema < 1 || schema > STATE_VERSION) {
      throw new Error("Unsupported Study Sprint PC backup schema");
    }
    assertEnvelope(root, ["schema", "version", "exportedAt", "state"]);
    state = root.state;
  } else if (Object.prototype.hasOwnProperty.call(root, "state")) {
    assertEnvelope(root, ["version", "exportedAt", "state"]);
    if (!isBackupVersion(root.version)) throw new Error("Invalid Study Sprint PC backup version");
    state = root.state;
  } else {
    throw new Error("Invalid Study Sprint PC backup");
  }

  assertBackupState(state);
  // Keep the argument in the API so callers can use one clock for import and timer reconciliation.
  normalizeTimestamp(now, Date.now());
  return normalizeState(state);
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isFlag(value) {
  return value === true;
}

function finiteNumber(value) {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value === "string" && value.trim()) {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  return null;
}

function finiteTimestamp(value) {
  const number = finiteNumber(value);
  if (number === null || Math.abs(number) > MAX_TIMESTAMP) return null;
  return Math.trunc(number);
}

function normalizeTimestamp(value, fallback = 0) {
  return finiteTimestamp(value) ?? finiteTimestamp(fallback) ?? 0;
}

function clampNumber(value, min, max, fallback) {
  const number = finiteNumber(value);
  if (number === null) return fallback;
  return Math.max(min, Math.min(max, number));
}

function clampInteger(value, min, max, fallback) {
  return Math.trunc(clampNumber(value, min, max, fallback));
}

function cleanText(value, maxLength) {
  if (typeof value !== "string") return "";
  return value.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, maxLength);
}

function cleanSubject(value) {
  const subject = cleanText(value, MAX_SUBJECT_LENGTH);
  return subject && safePropertyKey(subject, MAX_SUBJECT_LENGTH) ? subject : "";
}

function safePropertyKey(value, maxLength = MAX_CHAPTER_LENGTH) {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength &&
    !UNSAFE_KEYS.has(value) && !/[\u0000-\u001f\u007f]/.test(value);
}

function isDayKey(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(0);
  date.setUTCHours(0, 0, 0, 0);
  date.setUTCFullYear(year, month - 1, day);
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

function normalizeProgress(input) {
  if (!isRecord(input)) return {};
  const result = {};
  for (const [key, value] of Object.entries(input)) {
    if (Object.keys(result).length >= MAX_PROGRESS_KEYS || !safePropertyKey(key, 200)) continue;
    const number = finiteNumber(value);
    if (number === null) continue;
    result[key] = Math.max(0, Math.min(100, number));
  }
  return result;
}

function stableHash(value) {
  let hash = 2166136261;
  for (const character of String(value)) {
    hash ^= character.codePointAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}

function uniqueTaskId(rawId, text, chapter, index, used) {
  const supplied = typeof rawId === "string" ? cleanText(rawId, MAX_TASK_ID_LENGTH) : "";
  let id = supplied && safePropertyKey(supplied, MAX_TASK_ID_LENGTH) ? supplied : "";
  if (!id || used.has(id)) id = `task-${stableHash(`${id}|${text}|${cleanText(chapter, MAX_CHAPTER_LENGTH)}|${index}`)}`;
  let suffix = 2;
  const original = id;
  while (used.has(id) || !safePropertyKey(id, MAX_TASK_ID_LENGTH)) id = `${original}-${suffix++}`.slice(0, MAX_TASK_ID_LENGTH);
  used.add(id);
  return id;
}

function normalizeEvent(event) {
  if (!isRecord(event)) return null;
  const kind = typeof event.kind === "string" ? event.kind.trim() : "";
  if (!new Set(["focus", "task", "practice"]).has(kind)) return null;
  const at = finiteTimestamp(event.at);
  if (at === null) return null;
  const attempted = clampInteger(event.attempted, 0, MAX_EVENT_COUNT, 0);
  return {
    kind,
    at,
    durationSeconds: clampInteger(event.durationSeconds, 0, MAX_EVENT_DURATION_SECONDS, 0),
    subject: cleanSubject(event.subject),
    task: cleanText(event.task, MAX_TASK_LENGTH),
    attempted,
    correct: clampInteger(event.correct, 0, attempted, 0)
  };
}

function normalizeEvents(input) {
  if (!Array.isArray(input)) return [];
  return input.map(normalizeEvent).filter(Boolean).slice(-MAX_EVENTS);
}

function normalizeDays(value) {
  return clampInteger(value, 1, 30, 1);
}

function localMidnight(at) {
  const date = new Date(at);
  date.setHours(0, 0, 0, 0);
  return date;
}

function shiftLocalDays(date, amount) {
  const shifted = new Date(date.getTime());
  shifted.setDate(shifted.getDate() + amount);
  return shifted;
}

function eventsInRange(state, start, end) {
  return normalizeState(state).events.filter(event => event.at >= start && event.at <= end);
}

function isVersionString(value) {
  return typeof value === "string" && value.length <= 32 && /^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(value);
}

function isBackupVersion(value) {
  return value === 1 || isVersionString(value);
}

function assertEnvelope(root, allowedKeys) {
  const allowed = new Set(allowedKeys);
  if (Object.keys(root).some(key => !allowed.has(key))) throw new Error("Invalid Study Sprint PC backup envelope");
  if (root.version !== undefined && !isBackupVersion(root.version)) throw new Error("Invalid Study Sprint PC backup version");
  if (root.exportedAt !== undefined && (typeof root.exportedAt !== "string" || root.exportedAt.length > 64)) {
    throw new Error("Invalid Study Sprint PC backup date");
  }
}

function assertOptionalString(value, maxLength, label) {
  if (value !== undefined && (typeof value !== "string" || value.length > maxLength)) {
    throw new Error(`Invalid ${label}`);
  }
}

function assertOptionalNumber(value, min, max, label, integer = false) {
  if (value === undefined) return;
  const number = finiteNumber(value);
  if (number === null || number < min || number > max || (integer && !Number.isInteger(number))) {
    throw new Error(`Invalid ${label}`);
  }
}

function assertOptionalTimestamp(value, label) {
  if (value !== undefined && finiteTimestamp(value) === null) throw new Error(`Invalid ${label}`);
}

function assertBackupState(state) {
  if (!isRecord(state) || !Object.keys(state).some(key => STATE_FIELDS.has(key) && key !== "version")) {
    throw new Error("Invalid Study Sprint PC backup state");
  }
  const stateVersion = finiteNumber(state.version);
  if (stateVersion !== null && (!Number.isInteger(stateVersion) || stateVersion > STATE_VERSION || stateVersion < 1)) {
    throw new Error("Unsupported Study Sprint PC state version");
  }

  if (state.profile !== undefined) {
    if (!isRecord(state.profile)) throw new Error("Invalid profile");
    assertOptionalString(state.profile.name, MAX_NAME_LENGTH, "profile name");
    if (state.profile.exam !== undefined && typeof state.profile.exam !== "string") throw new Error("Invalid profile exam");
    if (state.profile.grade !== undefined && typeof state.profile.grade !== "string" && finiteNumber(state.profile.grade) === null) throw new Error("Invalid profile grade");
  }
  if (state.progress !== undefined) {
    if (!isRecord(state.progress)) throw new Error("Invalid progress");
    if (Object.keys(state.progress).length > MAX_PROGRESS_KEYS) throw new Error("Progress is too large");
    for (const [key, value] of Object.entries(state.progress)) {
      if (!safePropertyKey(key, 200)) throw new Error("Invalid progress key");
      assertOptionalNumber(value, 0, 100, "progress value");
    }
  }
  if (state.tasks !== undefined) {
    if (!Array.isArray(state.tasks) || state.tasks.length > MAX_TASKS) throw new Error("Too many tasks");
    for (const task of state.tasks) {
      if (!isRecord(task)) continue;
      assertOptionalString(task.text, MAX_TASK_LENGTH, "task text");
      assertOptionalString(task.id, MAX_TASK_ID_LENGTH, "task id");
      assertOptionalString(task.chapter, MAX_CHAPTER_LENGTH, "task chapter");
      assertOptionalTimestamp(task.createdAt, "task timestamp");
      assertOptionalTimestamp(task.completedAt, "task completion timestamp");
    }
  }
  assertOptionalString(state.notes, MAX_NOTES_LENGTH, "notes");
  if (state.notesByDay !== undefined) {
    if (!isRecord(state.notesByDay)) throw new Error("Invalid dated notes");
    const days = Object.entries(state.notesByDay);
    if (days.length > MAX_NOTES_DAYS) throw new Error("Too many dated notes");
    for (const [day, note] of days) {
      if (!isDayKey(day) || typeof note !== "string" || note.length > MAX_DAY_NOTE_LENGTH) throw new Error("Invalid dated note");
    }
  }
  assertOptionalNumber(state.practiceBest, 0, MAX_PRACTICE_BEST, "practice score", true);
  assertOptionalNumber(state.dailyFocusGoal, 15, MAX_FOCUS_GOAL, "daily focus goal", true);

  if (state.focus !== undefined) {
    if (!isRecord(state.focus)) throw new Error("Invalid focus state");
    assertOptionalNumber(state.focus.focusMinutes, 5, 180, "focus minutes", true);
    assertOptionalNumber(state.focus.breakMinutes, 1, 60, "break minutes", true);
    const focusMinutes = clampInteger(state.focus.focusMinutes, 5, 180, 25);
    const breakMinutes = clampInteger(state.focus.breakMinutes, 1, 60, 5);
    const maxRemaining = state.focus.isBreak === true ? breakMinutes * 60 : focusMinutes * 60;
    assertOptionalNumber(state.focus.remainingSeconds, 0, maxRemaining, "remaining seconds", true);
    assertOptionalTimestamp(state.focus.endAt, "focus deadline");
    assertOptionalString(state.focus.task, MAX_TASK_LENGTH, "focus task");
    assertOptionalString(state.focus.subject, MAX_SUBJECT_LENGTH, "focus subject");
  }
  if (state.events !== undefined) {
    if (!Array.isArray(state.events) || state.events.length > MAX_EVENTS) throw new Error("Too many events");
    for (const event of state.events) {
      if (!isRecord(event)) continue;
      if (typeof event.kind !== "string" || !["focus", "task", "practice"].includes(event.kind.trim())) throw new Error("Invalid event kind");
      assertOptionalTimestamp(event.at, "event timestamp");
      assertOptionalNumber(event.durationSeconds, 0, MAX_EVENT_DURATION_SECONDS, "event duration", true);
      assertOptionalString(event.subject, MAX_SUBJECT_LENGTH, "event subject");
      assertOptionalString(event.task, MAX_TASK_LENGTH, "event task");
      assertOptionalNumber(event.attempted, 0, MAX_EVENT_COUNT, "event attempts", true);
      assertOptionalNumber(event.correct, 0, MAX_EVENT_COUNT, "event correct", true);
      if (finiteNumber(event.correct) !== null && finiteNumber(event.attempted) !== null && Number(event.correct) > Number(event.attempted)) {
        throw new Error("Event correct count exceeds attempts");
      }
    }
  }
}

export function progressPercent(state, exam = state.profile.exam, grade = state.profile.grade) {
  const all = Object.entries(chaptersFor(exam, grade)).flatMap(([subject, list]) => list.map(chapter => chapterKey(exam, grade, subject, chapter)));
  if (!all.length) return 0;
  const completed = all.filter(key => Number(state.progress[key]) >= 100).length;
  return Math.round((completed / all.length) * 100);
}

export function chapterProgress(state, exam, grade, subject, chapter) {
  return Math.max(0, Math.min(100, Number(state.progress[chapterKey(exam, grade, subject, chapter)] || 0)));
}

export function setChapterProgress(state, exam, grade, subject, chapter, value) {
  const next = normalizeState(state);
  next.progress[chapterKey(exam, grade, subject, chapter)] = Math.max(0, Math.min(100, Number(value) || 0));
  return next;
}

export function scorePractice(questions, answers) {
  return questions.reduce((score, question, index) => score + (answers[index] === question.answer ? 1 : 0), 0);
}

export const PRACTICE_QUESTIONS = [
  { question: "How many moles are present in 18 g of water? Use $n = m/M$.", options: ["0.5 mol", "1 mol", "2 mol", "18 mol"], answer: 1 },
  { question: "The number of particles in one mole is approximately:", options: ["$6.022 \\times 10^{23}$", "$3.011 \\times 10^{8}$", "$9.81$", "$1.602 \\times 10^{-19}$"], answer: 0 },
  { question: "The empirical formula of a compound with molecular formula $C_6H_{12}O_6$ is:", options: ["$C_6H_{12}O_6$", "$C_3H_6O_3$", "$CH_2O$", "$C_2H_4O_2$"], answer: 2 },
  { question: "What is the molar mass of $CO_2$ approximately?", options: ["12 g mol⁻¹", "16 g mol⁻¹", "28 g mol⁻¹", "44 g mol⁻¹"], answer: 3 },
  { question: "A balanced chemical equation must conserve the number of:", options: ["Molecules only", "Atoms of each element", "Containers", "Products only"], answer: 1 }
];
