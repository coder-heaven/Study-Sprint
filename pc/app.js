import {
  PC_VERSION, EXAMS, GRADES, PRACTICE_QUESTIONS, chapterKey, chaptersFor,
  chapterProgress, createDefaultState, normalizeState, progressPercent, scorePractice,
  setChapterProgress, markingFor
} from "./core.mjs";

const STORAGE = "study_sprint_pc_state_v1";
let state = loadState();
let currentView = "home";
let focusSeconds = 25 * 60;
let focusRunning = false;
let focusTimer = null;
let practiceAnswers = [];
let practiceSubmitted = false;
let deferredInstall = null;

const $ = selector => document.querySelector(selector);
const all = selector => [...document.querySelectorAll(selector)];
const safe = value => String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[char]));
const rich = value => safe(value).replace(/\$([^$]+)\$/g, '<span class="math">$1</span>');
const id = () => `task-${Date.now()}-${Math.random().toString(36).slice(2)}`;

function loadState() {
  try { return normalizeState(JSON.parse(localStorage.getItem(STORAGE) || "null")); }
  catch { return createDefaultState(); }
}
function saveState() {
  state = normalizeState(state);
  localStorage.setItem(STORAGE, JSON.stringify(state));
  updateProfileChrome();
}
function updateProfileChrome() {
  const name = state.profile.name || "Student";
  const initial = name.trim().charAt(0).toUpperCase() || "S";
  $("#profile-name-mini").textContent = name;
  $("#top-profile-name").textContent = name;
  $("#profile-avatar").textContent = initial;
  $("#top-avatar").textContent = initial;
  $("#profile-exam-mini").textContent = `${state.profile.exam} · Class ${state.profile.grade}`;
  $("#version-label").textContent = PC_VERSION;
}
function setError(message = "") {
  const error = $("#app-error");
  error.textContent = message;
  error.classList.toggle("hidden", !message);
}
function renderMath(root = document) {
  if (window.MathJax?.typesetPromise) window.MathJax.typesetPromise([root]).catch(() => {});
}
function renderAll() {
  updateProfileChrome();
  renderHome(); renderStudy(); renderPractice(); renderFocus(); renderNotes(); renderSettings();
  showView(currentView);
  renderMath();
}
function showView(view) {
  currentView = view;
  all(".view").forEach(item => item.classList.toggle("active-view", item.id === `view-${view}`));
  all(".nav-item").forEach(item => item.classList.toggle("active", item.dataset.view === view));
  const titles = { home: "Today", study: "Study plan", practice: "Practice", focus: "Focus timer", notes: "Notes", settings: "Settings" };
  $("#page-title").textContent = titles[view] || "Study Sprint";
  window.scrollTo({ top: 0, behavior: "smooth" });
}
function cardHeading(title, action = "") { return `<div class="card-heading"><h2>${safe(title)}</h2>${action}</div>`; }
function progressBar(value) { return `<div class="progress-track"><div class="progress-fill" style="width:${value}%"></div></div>`; }

function renderHome() {
  const percent = progressPercent(state);
  const tasks = state.tasks.slice(0, 5);
  $("#view-home").innerHTML = `
    <div class="card hero"><div><p class="eyebrow">${safe(state.profile.exam)} · CLASS ${safe(state.profile.grade)}</p><h2>Keep your next study session small and focused.</h2><p>Your PC study space keeps progress, tasks, notes and practice data on this computer. It works offline after the first visit.</p></div><div class="hero-art">✦</div></div>
    <div class="grid three" style="margin-top:18px"><div class="card"><span class="stat-number">${percent}%</span><span class="stat-label">Syllabus complete</span>${progressBar(percent)}</div><div class="card"><span class="stat-number">${state.tasks.filter(task => !task.done).length}</span><span class="stat-label">Open tasks</span><p style="margin:14px 0 0">Make one task your next win.</p></div><div class="card"><span class="stat-number">${state.practiceBest}/${PRACTICE_QUESTIONS.length}</span><span class="stat-label">Best practice score</span><p style="margin:14px 0 0">Original practice · verify answers.</p></div></div>
    <div class="grid two" style="margin-top:18px"><div class="card">${cardHeading("Today’s tasks", '<button class="button button-primary" data-action="focus">Start focus</button>')}<div class="task-list">${tasks.length ? tasks.map(taskHTML).join("") : '<div class="empty-state"><div class="empty-icon">+</div><p>Add one task below to start.</p></div>'}</div>${taskInputHTML()}</div><div class="card">${cardHeading("Continue studying", '<button class="text-button" data-action="study">Open plan</button>')}<p>Choose a chapter and mark the progress you actually completed.</p><div class="pill">${percent}% complete</div>${progressBar(percent)}<button class="button button-quiet" data-action="practice" style="margin-top:12px">Try 5 chemistry MCQs</button></div></div>`;
  bindCommonActions($("#view-home")); bindTasks($("#view-home"));
}
function taskHTML(task) { return `<label class="task ${task.done ? "done" : ""}"><input type="checkbox" data-task-id="${safe(task.id)}" ${task.done ? "checked" : ""}><span>${safe(task.text)}</span></label>`; }
function taskInputHTML() { return `<div class="add-row"><input id="new-task" maxlength="160" placeholder="Add a study task"><button class="button button-quiet" data-action="add-task">Add</button></div>`; }

function renderStudy() {
  const chapters = chaptersFor(state.profile.exam, state.profile.grade);
  const subjects = Object.keys(chapters);
  $("#view-study").innerHTML = `<div class="card"><div class="card-heading"><div><p class="eyebrow">${safe(state.profile.exam)} · CLASS ${safe(state.profile.grade)}</p><h2>Study plan</h2></div><span class="pill">${progressPercent(state)}% complete</span></div><p>These chapter lists are generated from the Android app’s syllabus data. Future PC releases sync the source data automatically.</p><div class="filters"><select id="subject-filter"><option value="all">All subjects</option>${subjects.map(subject => `<option>${safe(subject)}</option>`).join("")}</select><button class="button button-quiet" data-action="mark-visible">Mark visible complete</button></div><div id="chapter-container">${subjects.map(subject => subjectHTML(subject, chapters[subject])).join("")}</div></div>`;
  $("#subject-filter").addEventListener("change", event => {
    all(".subject-block").forEach(block => block.classList.toggle("hidden", event.target.value !== "all" && block.dataset.subject !== event.target.value));
  });
  all("input[data-chapter-key]").forEach(input => input.addEventListener("change", event => {
    const [exam, grade, subject, ...rest] = event.target.dataset.chapterKey.split("|");
    state = setChapterProgress(state, exam, grade, subject, rest.join("|"), event.target.value);
    saveState(); renderStudy(); renderHome();
  }));
  $("[data-action=mark-visible]").addEventListener("click", () => {
    const filter = $("#subject-filter").value;
    for (const [subject, list] of Object.entries(chapters)) if (filter === "all" || filter === subject) for (const chapter of list) state = setChapterProgress(state, state.profile.exam, state.profile.grade, subject, chapter, 100);
    saveState(); renderAll();
  });
}
function subjectHTML(subject, list) { return `<div class="subject-block" data-subject="${safe(subject)}"><div class="subject-title"><span>${safe(subject)}</span><span>${list.length} chapters</span></div><div class="chapter-list">${list.map(chapter => { const value = chapterProgress(state, state.profile.exam, state.profile.grade, subject, chapter); const key = chapterKey(state.profile.exam, state.profile.grade, subject, chapter); return `<div class="chapter-row"><span>${safe(chapter)}</span><input type="range" min="0" max="100" step="25" value="${value}" data-chapter-key="${safe(key)}" aria-label="${safe(chapter)} progress"><strong>${value}%</strong></div>`; }).join("")}</div></div>`; }

function renderPractice() {
  const answered = practiceAnswers.filter(answer => answer !== undefined).length;
  const score = practiceSubmitted ? scorePractice(PRACTICE_QUESTIONS, practiceAnswers) : 0;
  $("#view-practice").innerHTML = `<div class="card"><div class="card-heading"><div><p class="eyebrow">ORIGINAL PC PRACTICE</p><h2>Basic concepts of chemistry</h2></div><span class="pill">${answered}/${PRACTICE_QUESTIONS.length} answered</span></div><p>These are original practice questions, not past-paper questions. Equations use LaTeX-style notation and are shown without changing your saved study data.</p>${PRACTICE_QUESTIONS.map((question, index) => `<div class="practice-question"><h3>${index + 1}. ${rich(question.question)}</h3><div class="options">${question.options.map((option, optionIndex) => { const chosen = practiceAnswers[index] === optionIndex; const correct = practiceSubmitted && question.answer === optionIndex; const wrong = practiceSubmitted && chosen && !correct; return `<button class="option ${chosen ? "selected" : ""} ${correct ? "correct" : ""} ${wrong ? "wrong" : ""}" data-practice="${index}:${optionIndex}">${String.fromCharCode(65 + optionIndex)}. ${rich(option)}</button>`; }).join("")}</div>${practiceSubmitted ? `<p class="answer-note">Correct answer: ${String.fromCharCode(65 + question.answer)}</p>` : ""}</div>`).join("")}<div class="dialog-actions"><button class="button button-quiet" data-action="reset-practice">Reset</button><button class="button button-primary" data-action="submit-practice">${practiceSubmitted ? `Score ${score}/${PRACTICE_QUESTIONS.length}` : "Check answers"}</button></div>${practiceSubmitted ? `<div class="notice">You scored ${score}/${PRACTICE_QUESTIONS.length}. Your best score is ${state.practiceBest}/${PRACTICE_QUESTIONS.length}.</div>` : ""}</div>`;
  all("[data-practice]").forEach(button => button.addEventListener("click", () => { const [question, option] = button.dataset.practice.split(":").map(Number); if (!practiceSubmitted) { practiceAnswers[question] = option; renderPractice(); renderMath(); } }));
  $("[data-action=reset-practice]").addEventListener("click", () => { practiceAnswers = []; practiceSubmitted = false; renderPractice(); });
  $("[data-action=submit-practice]").addEventListener("click", () => { practiceSubmitted = true; const result = scorePractice(PRACTICE_QUESTIONS, practiceAnswers); state.practiceBest = Math.max(state.practiceBest, result); saveState(); renderPractice(); renderMath(); });
}

function renderFocus() {
  const display = `${String(Math.floor(focusSeconds / 60)).padStart(2, "0")}:${String(focusSeconds % 60).padStart(2, "0")}`;
  $("#view-focus").innerHTML = `<div class="card timer"><p class="eyebrow">FOCUSED STUDY</p><h2>${focusRunning ? "Focus session in progress" : "Ready for a focused session?"}</h2><div class="timer-face">${display}</div><div class="timer-actions"><button class="button button-primary" data-action="toggle-focus">${focusRunning ? "Pause" : "Start 25 minutes"}</button><button class="button button-quiet" data-action="reset-focus">Reset</button></div><p style="margin:22px auto 0;max-width:550px">PC focus timer tracks this session in this browser. It does not block other desktop programs or read your screen.</p></div>`;
  $("[data-action=toggle-focus]").addEventListener("click", () => { focusRunning = !focusRunning; if (focusRunning) startFocusTimer(); else stopFocusTimer(); renderFocus(); });
  $("[data-action=reset-focus]").addEventListener("click", () => { stopFocusTimer(); focusSeconds = 25 * 60; renderFocus(); });
}
function startFocusTimer() { clearInterval(focusTimer); focusTimer = setInterval(() => { focusSeconds = Math.max(0, focusSeconds - 1); if (!focusSeconds) { stopFocusTimer(); alert("Focus session complete. Nice work."); } renderFocus(); }, 1000); }
function stopFocusTimer() { clearInterval(focusTimer); focusTimer = null; focusRunning = false; }

function renderNotes() { $("#view-notes").innerHTML = `<div class="card"><div class="card-heading"><div><p class="eyebrow">PRIVATE ON THIS COMPUTER</p><h2>Study notes</h2></div><span class="pill">Auto-saved</span></div><p>Notes are stored locally in this PC browser. They are not uploaded to the Android app or cloud.</p><textarea id="notes-editor" placeholder="Write your revision notes here...">${safe(state.notes)}</textarea>`; $("#notes-editor").addEventListener("input", event => { state.notes = event.target.value; saveState(); }); }
function renderSettings() { $("#view-settings").innerHTML = `<div class="grid two"><div class="card"><p class="eyebrow">PROFILE</p><h2>PC preferences</h2><p>Your selected exam controls the chapter plan and practice labels.</p><button class="button button-primary" data-action="profile">Edit profile</button></div><div class="card"><p class="eyebrow">LOCAL DATA</p><h2>Backup & restore</h2><p>Export your PC profile, tasks, notes and progress before changing computers.</p><div class="dialog-actions"><button class="button button-quiet" data-action="export">Export backup</button><label class="button button-quiet" style="display:inline-flex;align-items:center;justify-content:center;margin:0">Import backup<input type="file" id="import-file" accept="application/json" class="hidden"></label></div><button class="button button-danger" data-action="reset-data" style="margin-top:12px">Reset PC data</button></div></div><div class="card" style="margin-top:18px"><p class="eyebrow">PC LIMITS</p><h2>What this version includes</h2><p>Study plan, local progress, tasks, notes, focus timer, chemistry practice, backup and offline installation. Android-only features such as Accessibility app blocking, Firebase leaderboard sign-in and protected Study buddy chat remain in the Android app until a separate secure desktop identity flow is deployed.</p><p>PC version ${PC_VERSION} updates automatically when the published PC site is refreshed. If an update banner appears, click Reload.</p></div>`; bindCommonActions($("#view-settings")); $("[data-action=export]").addEventListener("click", exportBackup); $("#import-file").addEventListener("change", importBackup); $("[data-action=reset-data]").addEventListener("click", () => { if (confirm("Reset all Study Sprint PC data on this computer?")) { state = createDefaultState(); saveState(); renderAll(); } }); }
function bindCommonActions(root) { root.querySelectorAll("[data-action]").forEach(button => { if (["add-task", "focus", "study", "practice", "profile", "export", "reset-data", "submit-practice", "reset-practice", "toggle-focus", "reset-focus", "mark-visible"].includes(button.dataset.action) && !button.dataset.bound) { button.dataset.bound = "true"; button.addEventListener("click", () => { const action = button.dataset.action; if (action === "focus") showView("focus"); else if (action === "study") showView("study"); else if (action === "practice") showView("practice"); else if (action === "profile") openProfile(); else if (action === "add-task") addTask(root); }); } }); }
function bindTasks(root) { root.querySelectorAll("[data-task-id]").forEach(input => input.addEventListener("change", event => { const task = state.tasks.find(item => item.id === event.target.dataset.taskId); if (task) task.done = event.target.checked; saveState(); renderHome(); })); }
function addTask(root) { const input = root.querySelector("#new-task"); const text = input?.value.trim(); if (!text) return input?.focus(); state.tasks.unshift({ id: id(), text, done: false }); saveState(); renderHome(); }
function openProfile() { $("#profile-name-input").value = state.profile.name; $("#profile-exam-input").value = state.profile.exam; $("#profile-grade-input").value = state.profile.grade; $("#profile-dialog").showModal(); }
function exportBackup() { const blob = new Blob([JSON.stringify({ version: PC_VERSION, exportedAt: new Date().toISOString(), state }, null, 2)], { type: "application/json" }); const link = document.createElement("a"); link.href = URL.createObjectURL(blob); link.download = `study-sprint-pc-${PC_VERSION}-backup.json`; link.click(); URL.revokeObjectURL(link.href); }
async function importBackup(event) { const file = event.target.files?.[0]; if (!file) return; try { const payload = JSON.parse(await file.text()); state = normalizeState(payload.state || payload); saveState(); renderAll(); setError(""); } catch { setError("That backup could not be imported. Choose a Study Sprint PC JSON backup."); } event.target.value = ""; }

$("#navigation").addEventListener("click", event => { const button = event.target.closest("[data-view]"); if (button) showView(button.dataset.view); });
$("#top-profile").addEventListener("click", openProfile);
$("#profile-form").addEventListener("submit", event => { event.preventDefault(); state.profile = { name: $("#profile-name-input").value.trim() || "Student", exam: $("#profile-exam-input").value, grade: $("#profile-grade-input").value }; saveState(); $("#profile-dialog").close(); practiceAnswers = []; practiceSubmitted = false; renderAll(); });
$("#install-button").addEventListener("click", async () => { if (!deferredInstall) return; deferredInstall.prompt(); await deferredInstall.userChoice; deferredInstall = null; $("#install-button").classList.add("hidden"); });
window.addEventListener("beforeinstallprompt", event => { event.preventDefault(); deferredInstall = event; $("#install-button").classList.remove("hidden"); });
window.addEventListener("appinstalled", () => { deferredInstall = null; $("#install-button").classList.add("hidden"); });

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").then(registration => {
    registration.addEventListener("updatefound", () => {
      const worker = registration.installing;
      worker?.addEventListener("statechange", () => { if (worker.state === "installed" && navigator.serviceWorker.controller) $("#update-banner").classList.remove("hidden"); });
    });
  }).catch(() => {});
  navigator.serviceWorker.addEventListener("message", event => { if (event.data?.type === "PC_UPDATE_READY") $("#update-banner").classList.remove("hidden"); });
}
$("#reload-update").addEventListener("click", () => location.reload());

renderAll();
