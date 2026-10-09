import { PC_VERSION, SYLLABUS, MARKING } from "./data.mjs";

export { PC_VERSION };
export const EXAMS = ["CET", "JEE", "NEET"];
export const GRADES = ["11", "12"];

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
    profile: { name: "Student", exam: "CET", grade: "11" },
    progress: {},
    tasks: [],
    notes: "",
    practiceBest: 0
  };
}

export function normalizeState(input) {
  const base = createDefaultState();
  const state = input && typeof input === "object" ? input : {};
  const profile = state.profile && typeof state.profile === "object" ? state.profile : {};
  return {
    ...base,
    ...state,
    profile: {
      name: typeof profile.name === "string" && profile.name.trim() ? profile.name.trim().slice(0, 60) : base.profile.name,
      exam: EXAMS.includes(profile.exam) ? profile.exam : base.profile.exam,
      grade: GRADES.includes(String(profile.grade)) ? String(profile.grade) : base.profile.grade
    },
    progress: state.progress && typeof state.progress === "object" ? state.progress : {},
    tasks: Array.isArray(state.tasks) ? state.tasks.filter(task => task && typeof task.text === "string").slice(0, 100).map(task => ({
      text: task.text.trim().slice(0, 160), done: Boolean(task.done), id: String(task.id || cryptoRandomId())
    })).filter(task => task.text) : [],
    notes: typeof state.notes === "string" ? state.notes.slice(0, 20000) : "",
    practiceBest: Number.isFinite(Number(state.practiceBest)) ? Math.max(0, Number(state.practiceBest)) : 0
  };
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

function cryptoRandomId() {
  return `task-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}