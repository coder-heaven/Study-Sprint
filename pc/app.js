import {
  PC_VERSION, EXAMS, GRADES, PRACTICE_QUESTIONS, chapterKey, chaptersFor,
  chapterProgress, createDefaultState, normalizeState, progressPercent, scorePractice,
  setChapterProgress, markingFor, dayKey, remainingSeconds, recordEvent,
  totalsForDays, subjectMinutes, streak, beginFocusState, pauseFocusState,
  resumeFocusState, finishFocusState, parseBackup
} from "./core.mjs";

const STORAGE = "study_sprint_pc_state_v2";
let state = loadState();
let currentView = "home";
let progressDays = 7;
let focusTimer = null;
let practiceAnswers = [];
let practiceSubmitted = false;
let deferredInstall = null;
let editingTaskId = null;
let confirmAction = null;
let previousDay = dayKey();

const $ = selector => document.querySelector(selector);
const all = selector => [...document.querySelectorAll(selector)];
const safe = value => String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[char]));
const rich = value => safe(value);
const id = () => `task-${Date.now()}-${Math.random().toString(36).slice(2)}`;

function loadState() {
  try { return normalizeState(JSON.parse(localStorage.getItem(STORAGE) || localStorage.getItem("study_sprint_pc_state_v1") || "null")); }
  catch { return createDefaultState(); }
}
function saveState() {
  state = normalizeState(state);
  try { localStorage.setItem(STORAGE, JSON.stringify(state)); }
  catch { setError("Study data could not be saved. Export a backup before closing and check free storage or browser permissions."); }
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
  reconcileFocus();
  updateProfileChrome();
  const onboarded = state.profile.onboarded && state.profile.termsAccepted;
  $("#welcome-screen").classList.toggle("hidden", onboarded);
  $(".app-shell").classList.toggle("hidden", !onboarded);
  if (!onboarded) {
    $("#welcome-name").value = state.profile.name === "Student" ? "" : state.profile.name;
    $("#welcome-exam").value = state.profile.exam;
    $("#welcome-grade").value = state.profile.grade;
    return;
  }
  renderHome(); renderStudy(); renderPlanner(); renderPractice(); renderFocus(); renderProgress(); renderNotes(); renderSettings();
  showView(currentView);
  renderMath();
}
function showView(view) {
  if (!(state.profile.onboarded && state.profile.termsAccepted)) return;
  currentView = view;
  if (view === "focus") renderFocus();
  if (view === "progress") renderProgress();
  if (view === "notes") renderNotes();
  all(".view").forEach(item => item.classList.toggle("active-view", item.id === `view-${view}`));
  all(".nav-item").forEach(item => item.classList.toggle("active", item.dataset.view === view));
  const titles = { home: "Today", study: "Study plan", planner: "Planner", practice: "Practice", focus: "Focus timer", progress: "Progress", notes: "Notes", settings: "Settings" };
  $("#page-title").textContent = titles[view] || "Study Sprint";
  window.scrollTo({ top: 0, behavior: "smooth" });
}
function cardHeading(title, action = "") { return `<div class="card-heading"><h2>${safe(title)}</h2>${action}</div>`; }
function progressBar(value) { return `<div class="progress-track"><div class="progress-fill" style="width:${Math.max(0, Math.min(100, value))}%"></div></div>`; }

function renderHome() {
  const percent = progressPercent(state);
  const totals = totalsForDays(state, 1);
  const focus = state.focus;
  const focusLeft = remainingSeconds(focus);
  const tasks = state.tasks.filter(task => !task.done).slice(0, 5);
  const goalProgress = Math.min(100, Math.round((totals.focusedMinutes / state.dailyFocusGoal) * 100));
  $("#view-home").innerHTML = `
    <div class="card hero"><div><p class="eyebrow">${safe(state.profile.exam)} · CLASS ${safe(state.profile.grade)}</p><h2>Keep your next study session small and focused.</h2><p>Your PC study space keeps progress, tasks, notes and focus history on this computer. It works offline after the first visit.</p></div><div class="hero-art">✦</div></div>
    <div class="grid four" style="margin-top:18px"><div class="card"><span class="stat-number">${percent}%</span><span class="stat-label">Syllabus complete</span>${progressBar(percent)}</div><div class="card"><span class="stat-number">${totals.focusedMinutes}m</span><span class="stat-label">Focused today</span>${progressBar(goalProgress)}<small>${goalProgress}% of ${state.dailyFocusGoal} min goal</small></div><div class="card"><span class="stat-number">${state.tasks.filter(task => !task.done).length}</span><span class="stat-label">Open tasks</span><p style="margin:14px 0 0">Make one task your next win.</p></div><div class="card"><span class="stat-number">${streak(state)}</span><span class="stat-label">Day streak</span><p style="margin:14px 0 0">${focus.active ? `${Math.ceil(focusLeft / 60)} min left in focus` : "Start a session today."}</p></div></div>
    <div class="grid two" style="margin-top:18px"><div class="card">${cardHeading("Today’s tasks", '<button class="button button-primary" data-action="focus">Start focus</button>')}<div class="task-list">${tasks.length ? tasks.map(taskHTML).join("") : '<div class="empty-state"><div class="empty-icon">+</div><p>Add one task below to start.</p></div>'}</div>${taskInputHTML()}</div><div class="card">${cardHeading("Continue studying", '<button class="text-button" data-action="study">Open plan</button>')}<p>Choose a chapter and mark the progress you actually completed.</p><div class="pill">${percent}% complete</div>${progressBar(percent)}<button class="button button-quiet" data-action="practice" style="margin-top:12px">Try 5 chemistry MCQs</button><button class="button button-quiet" data-action="progress" style="margin:12px 0 0 8px">View progress</button></div></div>`;
  bindCommonActions($("#view-home")); bindTasks($("#view-home"));
}
function taskHTML(task) {
  return `<div class="task ${task.done ? "done" : ""}"><input type="checkbox" data-task-id="${safe(task.id)}" ${task.done ? "checked" : ""} aria-label="Complete ${safe(task.text)}"><span class="task-copy"><strong>${safe(task.text)}</strong>${task.chapter ? `<small>${safe(task.chapter)}</small>` : ""}</span><button class="icon-button task-action" data-task-action="focus" data-task-id="${safe(task.id)}" aria-label="Focus on ${safe(task.text)}">◷</button><button class="icon-button task-action" data-task-action="edit" data-task-id="${safe(task.id)}" aria-label="Edit ${safe(task.text)}">✎</button><button class="icon-button task-action" data-task-action="up" data-task-id="${safe(task.id)}" aria-label="Move ${safe(task.text)} up">↑</button><button class="icon-button task-action" data-task-action="delete" data-task-id="${safe(task.id)}" aria-label="Delete ${safe(task.text)}">×</button></div>`;
}
function taskInputHTML() { return `<div class="add-row"><input id="new-task" maxlength="160" placeholder="Add a study task"><input id="new-task-chapter" maxlength="100" placeholder="Chapter (optional)"><button class="button button-quiet" data-action="add-task">Add</button></div>`; }

function renderStudy() {
  const chapters = chaptersFor(state.profile.exam, state.profile.grade);
  const subjects = Object.keys(chapters);
  $("#view-study").innerHTML = `<div class="card"><div class="card-heading"><div><p class="eyebrow">${safe(state.profile.exam)} · CLASS ${safe(state.profile.grade)}</p><h2>Study plan</h2></div><span class="pill">${progressPercent(state)}% complete</span></div><p>These chapter lists are generated from the Android app’s syllabus data. Future PC releases sync the source data automatically.</p><div class="filters"><select id="subject-filter"><option value="all">All subjects</option>${subjects.map(subject => `<option>${safe(subject)}</option>`).join("")}</select><button class="button button-quiet" data-action="mark-visible">Mark visible complete</button></div><div id="chapter-container">${subjects.map(subject => subjectHTML(subject, chapters[subject])).join("")}</div></div>`;
  $("#subject-filter").addEventListener("change", event => all(".subject-block").forEach(block => block.classList.toggle("hidden", event.target.value !== "all" && block.dataset.subject !== event.target.value)));
  all("input[data-chapter-key]").forEach(input => input.addEventListener("change", event => {
    const [exam, grade, subject, ...rest] = event.target.dataset.chapterKey.split("|");
    state = setChapterProgress(state, exam, grade, subject, rest.join("|"), event.target.value); saveState(); renderStudy(); renderHome();
  }));
  $("[data-action=mark-visible]").addEventListener("click", () => {
    const filter = $("#subject-filter").value;
    for (const [subject, list] of Object.entries(chapters)) if (filter === "all" || filter === subject) for (const chapter of list) state = setChapterProgress(state, state.profile.exam, state.profile.grade, subject, chapter, 100);
    saveState(); renderAll();
  });
}
function subjectHTML(subject, list) { return `<div class="subject-block" data-subject="${safe(subject)}"><div class="subject-title"><span>${safe(subject)}</span><span>${list.length} chapters</span></div><div class="chapter-list">${list.map(chapter => { const value = chapterProgress(state, state.profile.exam, state.profile.grade, subject, chapter); const key = chapterKey(state.profile.exam, state.profile.grade, subject, chapter); return `<div class="chapter-row"><span>${safe(chapter)}</span><input type="range" min="0" max="100" step="25" value="${value}" data-chapter-key="${safe(key)}" aria-label="${safe(chapter)} progress"><strong>${value}%</strong></div>`; }).join("")}</div></div>`; }

function renderPlanner() {
  const active = state.tasks.filter(task => !task.done);
  const completed = state.tasks.filter(task => task.done);
  $("#view-planner").innerHTML = `<div class="grid two"><div class="card"><p class="eyebrow">YOUR NEXT STEPS</p><h2>Planner</h2><p>Organize tasks, connect them to chapters and launch a focus session from any task.</p><form id="planner-form" class="planner-form"><input id="planner-task" maxlength="160" placeholder="What will you study?" required><input id="planner-chapter" maxlength="100" placeholder="Chapter (optional)"><button class="button button-primary" type="submit">Add task</button></form><div class="task-list planner-list">${active.length ? active.map(taskHTML).join("") : '<div class="empty-state"><div class="empty-icon">✦</div><p>No active tasks. Add one clear next step.</p></div>'}</div></div><div class="card"><p class="eyebrow">COMPLETED</p><h2>${completed.length} finished</h2><p>Completed tasks contribute to your local progress history.</p><div class="task-list">${completed.length ? completed.map(taskHTML).join("") : '<div class="empty-state"><p>Finish a task to see it here.</p></div>'}</div></div></div>`;
  $("#planner-form").addEventListener("submit", event => { event.preventDefault(); addTask($("#view-planner"), "planner"); });
  bindTasks($("#view-planner"));
}

function renderPractice() {
  const answered = practiceAnswers.filter(answer => answer !== undefined).length;
  const score = practiceSubmitted ? scorePractice(PRACTICE_QUESTIONS, practiceAnswers) : 0;
  $("#view-practice").innerHTML = `<div class="card"><div class="card-heading"><div><p class="eyebrow">ORIGINAL PC PRACTICE</p><h2>Basic concepts of chemistry</h2></div><span class="pill">${answered}/${PRACTICE_QUESTIONS.length} answered</span></div><p>These are original practice questions, not past-paper questions. Equations use LaTeX-style notation and are shown without changing your saved study data.</p>${PRACTICE_QUESTIONS.map((question, index) => `<div class="practice-question"><h3>${index + 1}. ${rich(question.question)}</h3><div class="options">${question.options.map((option, optionIndex) => { const chosen = practiceAnswers[index] === optionIndex; const correct = practiceSubmitted && question.answer === optionIndex; const wrong = practiceSubmitted && chosen && !correct; return `<button class="option ${chosen ? "selected" : ""} ${correct ? "correct" : ""} ${wrong ? "wrong" : ""}" data-practice="${index}:${optionIndex}">${String.fromCharCode(65 + optionIndex)}. ${rich(option)}</button>`; }).join("")}</div>${practiceSubmitted ? `<p class="answer-note">Correct answer: ${String.fromCharCode(65 + question.answer)}</p>` : ""}</div>`).join("")}<div class="dialog-actions"><button class="button button-quiet" data-action="reset-practice">Reset</button><button class="button button-primary" data-action="submit-practice">${practiceSubmitted ? `Score ${score}/${PRACTICE_QUESTIONS.length}` : "Check answers"}</button></div>${practiceSubmitted ? `<div class="notice">You scored ${score}/${PRACTICE_QUESTIONS.length}. Your best score is ${state.practiceBest}/${PRACTICE_QUESTIONS.length}.</div>` : ""}</div>`;
  all("[data-practice]").forEach(button => button.addEventListener("click", () => { const [question, option] = button.dataset.practice.split(":").map(Number); if (!practiceSubmitted) { practiceAnswers[question] = option; renderPractice(); renderMath(); } }));
  $("[data-action=reset-practice]").addEventListener("click", () => { practiceAnswers = []; practiceSubmitted = false; renderPractice(); });
  $("[data-action=submit-practice]").addEventListener("click", () => { if (!practiceSubmitted) { practiceSubmitted = true; const result = scorePractice(PRACTICE_QUESTIONS, practiceAnswers); state.practiceBest = Math.max(state.practiceBest, result); const attempted = practiceAnswers.filter(answer => answer !== undefined).length; const correct = PRACTICE_QUESTIONS.reduce((count, question, index) => count + (practiceAnswers[index] === question.answer ? 1 : 0), 0); state = recordEvent(state, { kind: "practice", attempted, correct }); saveState(); } renderPractice(); renderMath(); });
}

function renderFocus() {
  reconcileFocus();
  const focus = state.focus;
  const left = remainingSeconds(focus);
  const display = `${String(Math.floor(left / 60)).padStart(2, "0")}:${String(left % 60).padStart(2, "0")}`;
  const phase = focus.isBreak ? "BREAK" : "FOCUSED STUDY";
  const action = !focus.active ? "Start focus" : focus.running ? "Pause" : "Resume";
  $("#view-focus").innerHTML = `<div class="card timer"><p class="eyebrow">${phase}</p><h2>${focus.active ? (focus.running ? "Focus session in progress" : "Focus session paused") : "Ready for a focused session?"}</h2><div class="timer-face">${display}</div><div class="timer-actions"><button class="button button-primary" data-action="toggle-focus">${action}</button><button class="button button-quiet" data-action="reset-focus">Reset</button>${focus.active ? `<button class="button button-quiet" data-action="finish-focus">Finish</button>` : ""}${!focus.active && !focus.isBreak ? `<button class="button button-quiet" data-action="start-break">Start break</button>` : ""}</div>${!focus.active ? `<div class="timer-settings"><div class="chip-row">${[25, 45, 60, 90].map(minutes => `<button class="pill-button ${focus.focusMinutes === minutes ? "selected" : ""}" data-focus-minutes="${minutes}">${minutes} min</button>`).join("")}</div><div class="grid two compact-grid"><label>Focus minutes<input id="focus-minutes" type="number" min="5" max="180" value="${focus.focusMinutes}"></label><label>Break minutes<input id="break-minutes" type="number" min="1" max="60" value="${focus.breakMinutes}"></label></div><label>What are you studying?<input id="focus-task" maxlength="160" value="${safe(focus.task)}" placeholder="Optional task"></label><label>Subject<input id="focus-subject" maxlength="80" value="${safe(focus.subject)}" placeholder="General"></label>` : `<p class="focus-intention"><strong>${safe(focus.task || (focus.isBreak ? "Take a short reset" : "Study session"))}</strong>${focus.subject ? ` · ${safe(focus.subject)}` : ""}</p>`}<label class="check-row"><input id="soft-focus" type="checkbox" ${focus.softMode ? "checked" : ""}><span>Enable soft focus mode while this timer runs</span></label><p class="focus-disclaimer">Soft focus mode can show a recovery reminder when you return to this PC app, but it cannot block Windows programs or read your screen. Android app blocking remains Android-only.</p></div>`;
  if (!focus.active) {
    all("[data-focus-minutes]").forEach(button => button.addEventListener("click", () => { state.focus.focusMinutes = Number(button.dataset.focusMinutes); state.focus.remainingSeconds = state.focus.focusMinutes * 60; saveState(); renderFocus(); }));
    $("#focus-minutes").addEventListener("change", event => { state.focus.focusMinutes = Math.max(5, Math.min(180, Number(event.target.value) || 25)); state.focus.remainingSeconds = state.focus.focusMinutes * 60; saveState(); renderFocus(); });
    $("#break-minutes").addEventListener("change", event => { state.focus.breakMinutes = Math.max(1, Math.min(60, Number(event.target.value) || 5)); saveState(); });
    $("#focus-task").addEventListener("input", event => { state.focus.task = event.target.value; saveState(); });
    $("#focus-subject").addEventListener("input", event => { state.focus.subject = event.target.value; saveState(); });
  }
  $("#soft-focus").addEventListener("change", event => { state.focus.softMode = event.target.checked; saveState(); });
  $("[data-action=toggle-focus]").addEventListener("click", () => {
    if (!state.focus.active) beginFocus(false); else if (state.focus.running) pauseFocus(); else resumeFocus();
    renderAll();
  });
  $("[data-action=reset-focus]").addEventListener("click", () => { resetFocus(); renderAll(); });
  $("[data-action=finish-focus]")?.addEventListener("click", () => { finishFocus(false); renderAll(); });
  $("[data-action=start-break]")?.addEventListener("click", () => { beginFocus(true); renderAll(); });
}
function reconcileFocus() {
  if (!state.focus.active || !state.focus.running) return;
  const left = remainingSeconds(state.focus);
  state.focus.remainingSeconds = left;
  if (left === 0) finishFocus(true);
}
function beginFocus(isBreak = false) {
  state.focus = beginFocusState(state.focus, isBreak, Date.now());
  saveState();
}
function pauseFocus() {
  reconcileFocus();
  if (!state.focus.active || !state.focus.running) return;
  state.focus = pauseFocusState(state.focus, Date.now());
  saveState();
}
function resumeFocus() {
  if (!state.focus.active || state.focus.running) return;
  state.focus = resumeFocusState(state.focus, Date.now());
  saveState();
}
function finishFocus(natural = false) {
  if (!state.focus.active) return;
  state = finishFocusState(state, Date.now());
  if (natural) setError("Focus session complete. Nice work — your time was added to Progress.");
}
function resetFocus() {
  state.focus = { ...state.focus, active: false, running: false, isBreak: false, endAt: 0, remainingSeconds: state.focus.focusMinutes * 60 };
  saveState();
}

function renderProgress() {
  const totals = totalsForDays(state, progressDays);
  const subjects = subjectMinutes(state, progressDays);
  const bars = totals.dailyMinutes.map((minutes, index) => `<div class="chart-column"><div class="chart-bar" style="height:${Math.max(4, Math.min(100, (minutes / Math.max(1, ...totals.dailyMinutes)) * 100))}%" title="${minutes} minutes"></div><small>${index === totals.dailyMinutes.length - 1 ? "Today" : `-${totals.dailyMinutes.length - index - 1}d`}</small></div>`).join("");
  $("#view-progress").innerHTML = `<div class="card"><div class="card-heading"><div><p class="eyebrow">LOCAL STUDY HISTORY</p><h2>Progress</h2></div><span class="pill">${streak(state)} day streak</span></div><div class="filters">${[1, 7, 30].map(days => `<button class="pill-button ${progressDays === days ? "selected" : ""}" data-progress-days="${days}">${days === 1 ? "Today" : `${days} days`}</button>`).join("")}</div><div class="grid four"><div class="card stat-card"><span class="stat-number">${totals.focusedMinutes}m</span><span class="stat-label">Focused</span></div><div class="card stat-card"><span class="stat-number">${totals.sessions}</span><span class="stat-label">Sessions</span></div><div class="card stat-card"><span class="stat-number">${totals.tasks}</span><span class="stat-label">Tasks done</span></div><div class="card stat-card"><span class="stat-number">${totals.questions ? `${totals.correct}/${totals.questions}` : "—"}</span><span class="stat-label">Practice correct</span></div></div><h3 class="section-title">Daily focus</h3><div class="chart">${bars || '<div class="empty-state">No focus sessions recorded yet.</div>'}</div><h3 class="section-title">Subject focus</h3><div class="subject-metrics">${subjects.length ? subjects.map(([subject, minutes]) => `<div class="metric-row"><span>${safe(subject)}</span><strong>${minutes} min</strong></div>`).join("") : "<p class=\"muted\">Complete a focus session to see subject history.</p>"}</div><p class="muted">PC Progress measures Study Sprint activity on this computer. It cannot measure other Windows apps or Android device usage.</p></div>`;
  all("[data-progress-days]").forEach(button => button.addEventListener("click", () => { progressDays = Number(button.dataset.progressDays); renderProgress(); }));
}

function renderNotes() {
  const today = dayKey();
  const current = state.notesByDay[today] ?? state.notes;
  const history = Object.entries(state.notesByDay).sort((a, b) => b[0].localeCompare(a[0])).slice(0, 10);
  $("#view-notes").innerHTML = `<div class="grid two"><div class="card"><div class="card-heading"><div><p class="eyebrow">PRIVATE ON THIS COMPUTER</p><h2>Today’s notes</h2></div><span class="pill">Auto-saved</span></div><p>Notes stay local to this PC. They are included in Backup & restore.</p><textarea id="notes-editor" placeholder="Write your revision notes here...">${safe(current)}</textarea></div><div class="card"><p class="eyebrow">RECENT DAYS</p><h2>What I learned</h2>${history.length ? history.map(([day, note]) => `<div class="note-history"><strong>${safe(day)}</strong><p>${safe(note).slice(0, 220)}</p></div>`).join("") : '<div class="empty-state"><p>No dated notes yet.</p></div>'}</div></div>`;
  $("#notes-editor").addEventListener("input", event => { state.notesByDay[today] = event.target.value; state.notes = event.target.value; saveState(); });
}

function renderSettings() {
  $("#view-settings").innerHTML = `<div class="grid two"><div class="card"><p class="eyebrow">PROFILE</p><h2>PC preferences</h2><p>Your selected exam controls the chapter plan and practice labels.</p><button class="button button-primary" data-action="profile">Edit profile</button><p class="muted" style="margin-top:14px">Signed in locally as <strong>${safe(state.profile.name)}</strong>. This is a guest profile stored on this computer.</p></div><div class="card"><p class="eyebrow">LOCAL DATA</p><h2>Backup & restore</h2><p>Export your PC profile, tasks, focus history, notes and progress before changing computers.</p><div class="dialog-actions"><button class="button button-quiet" data-action="export">Export backup</button><label class="button button-quiet file-button">Import backup<input type="file" id="import-file" accept="application/json" class="hidden"></label></div><button class="button button-danger" data-action="reset-data" style="margin-top:12px">Reset PC data</button></div></div><div class="card" style="margin-top:18px"><p class="eyebrow">FOCUS & LIMITS</p><h2>Desktop protection</h2><p>Use Focus timer for a self-managed session and recovery reminder. A browser or desktop app cannot inspect, block or force-close other Windows programs. Android Accessibility limits and protected-app recovery remain Android-only.</p><label>Daily focus goal (minutes)<input id="daily-goal" type="number" min="15" max="480" value="${state.dailyFocusGoal}"></label></div><div class="card" style="margin-top:18px"><p class="eyebrow">ABOUT</p><h2>Study Sprint PC ${PC_VERSION}</h2><p>Updates arrive through the installed PWA or Windows installer. If a web update banner appears, click Reload. The Windows build is offline-first and does not include provider keys.</p></div>`;
  bindCommonActions($("#view-settings"));
  $("[data-action=export]").addEventListener("click", exportBackup);
  $("#import-file").addEventListener("change", importBackup);
  $("#daily-goal").addEventListener("change", event => { state.dailyFocusGoal = Math.max(15, Math.min(480, Number(event.target.value) || 120)); saveState(); renderHome(); });
  $("[data-action=reset-data]").addEventListener("click", () => { if (confirm("Reset all Study Sprint PC data on this computer?")) { state = createDefaultState(); saveState(); renderAll(); } });
}

function bindCommonActions(root) {
  root.querySelectorAll("[data-action]").forEach(button => {
    if (button.dataset.bound) return;
    button.dataset.bound = "true";
    button.addEventListener("click", () => {
      const action = button.dataset.action;
      if (action === "focus") showView("focus");
      else if (action === "study") showView("study");
      else if (action === "planner") showView("planner");
      else if (action === "progress") showView("progress");
      else if (action === "practice") showView("practice");
      else if (action === "profile") openProfile();
      else if (action === "add-task") addTask(root);
    });
  });
}
function bindTasks(root) {
  root.querySelectorAll("input[data-task-id]").forEach(input => input.addEventListener("change", event => {
    const task = state.tasks.find(item => item.id === event.target.dataset.taskId);
    if (task) {
      task.done = event.target.checked;
      task.completedAt = task.done ? Date.now() : 0;
      if (task.done) state = recordEvent(state, { kind: "task", task: task.text });
      saveState(); renderAll();
    }
  }));
  root.querySelectorAll("[data-task-action]").forEach(button => button.addEventListener("click", event => {
    event.preventDefault();
    const task = state.tasks.find(item => item.id === button.dataset.taskId);
    if (!task) return;
    if (button.dataset.taskAction === "focus") { state.focus.task = task.text; state.focus.subject = task.chapter || ""; saveState(); showView("focus"); renderFocus(); }
    if (button.dataset.taskAction === "edit") editTask(task);
    if (button.dataset.taskAction === "delete") confirmDialog("Delete task?", `Remove “${task.text}” from your planner?`, () => { state.tasks = state.tasks.filter(item => item.id !== task.id); saveState(); renderAll(); });
    if (button.dataset.taskAction === "up") {
      const index = state.tasks.findIndex(item => item.id === task.id);
      if (index > 0) [state.tasks[index - 1], state.tasks[index]] = [state.tasks[index], state.tasks[index - 1]];
      saveState(); renderAll();
    }
  }));
}
function addTask(root, mode = "home") {
  const textInput = root.querySelector(mode === "planner" ? "#planner-task" : "#new-task");
  const chapterInput = root.querySelector(mode === "planner" ? "#planner-chapter" : "#new-task-chapter");
  const text = textInput?.value.trim();
  if (!text) return textInput?.focus();
  state.tasks.unshift({ id: id(), text, chapter: chapterInput?.value.trim() || "", done: false, createdAt: Date.now(), completedAt: 0 });
  saveState(); renderAll();
}
function editTask(task) {
  editingTaskId = task.id;
  $("#task-edit-text").value = task.text;
  $("#task-edit-chapter").value = task.chapter || "";
  $("#task-dialog").showModal();
}
function openProfile() { $("#profile-name-input").value = state.profile.name; $("#profile-exam-input").value = state.profile.exam; $("#profile-grade-input").value = state.profile.grade; $("#profile-dialog").showModal(); }
function exportBackup() { const blob = new Blob([JSON.stringify({ version: PC_VERSION, schema: 2, exportedAt: new Date().toISOString(), state }, null, 2)], { type: "application/json" }); const link = document.createElement("a"); link.href = URL.createObjectURL(blob); link.download = `study-sprint-pc-${PC_VERSION}-backup.json`; link.click(); URL.revokeObjectURL(link.href); }
async function importBackup(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  try {
    if (file.size > 4 * 1024 * 1024) throw new Error("Backup is too large");
    const payload = JSON.parse(await file.text());
    state = parseBackup(payload, Date.now());
    state.profile.onboarded = true;
    state.profile.termsAccepted = true;
    saveState(); renderAll(); setError("");
  } catch { setError("That backup could not be imported. Choose a valid Study Sprint PC JSON backup under 4 MB."); }
  event.target.value = "";
}

$("#navigation").addEventListener("click", event => { const button = event.target.closest("[data-view]"); if (button) showView(button.dataset.view); });
$("#top-profile").addEventListener("click", openProfile);
$("#profile-form").addEventListener("submit", event => { event.preventDefault(); state.profile = { ...state.profile, name: $("#profile-name-input").value.trim() || "Student", exam: $("#profile-exam-input").value, grade: $("#profile-grade-input").value, onboarded: true, termsAccepted: true }; saveState(); $("#profile-dialog").close(); practiceAnswers = []; practiceSubmitted = false; renderAll(); });
$("#task-form").addEventListener("submit", event => { event.preventDefault(); const task = state.tasks.find(item => item.id === editingTaskId); if (!task) return; const text = $("#task-edit-text").value.trim(); if (!text) return; task.text = text.slice(0, 160); task.chapter = $("#task-edit-chapter").value.trim().slice(0, 100); saveState(); $("#task-dialog").close(); editingTaskId = null; renderAll(); });
all("[data-close-dialog]").forEach(button => button.addEventListener("click", () => { const dialog = document.getElementById(button.dataset.closeDialog); dialog?.close(); if (dialog?.id === "task-dialog") editingTaskId = null; }));
$("#confirm-cancel").addEventListener("click", () => { $("#confirm-dialog").close(); confirmAction = null; });
$("#confirm-accept").addEventListener("click", () => { const action = confirmAction; $("#confirm-dialog").close(); confirmAction = null; action?.(); });
function confirmDialog(title, message, action) { $("#confirm-title").textContent = title; $("#confirm-message").textContent = message; confirmAction = action; $("#confirm-dialog").showModal(); }
$("#welcome-form").addEventListener("submit", event => { event.preventDefault(); state.profile = { ...state.profile, name: $("#welcome-name").value.trim() || "Student", exam: $("#welcome-exam").value, grade: $("#welcome-grade").value, onboarded: true, termsAccepted: $("#welcome-terms").checked }; saveState(); renderAll(); });
$("#install-button").addEventListener("click", async () => { if (!deferredInstall) return; deferredInstall.prompt(); await deferredInstall.userChoice; deferredInstall = null; $("#install-button").classList.add("hidden"); });
window.addEventListener("beforeinstallprompt", event => { event.preventDefault(); deferredInstall = event; $("#install-button").classList.remove("hidden"); });
window.addEventListener("appinstalled", () => { deferredInstall = null; $("#install-button").classList.add("hidden"); });

function startFocusTicker() {
  clearInterval(focusTimer);
  focusTimer = setInterval(() => {
    if (!state.focus.active) return;
    const before = state.focus.remainingSeconds;
    reconcileFocus();
    if (before !== state.focus.remainingSeconds) { saveState(); if (currentView === "focus") renderFocus(); renderHome(); }
  }, 1000);
}
if (["http:", "https:"].includes(location.protocol) && "serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").then(registration => {
    registration.addEventListener("updatefound", () => { const worker = registration.installing; worker?.addEventListener("statechange", () => { if (worker.state === "installed" && navigator.serviceWorker.controller) $("#update-banner").classList.remove("hidden"); }); });
  }).catch(() => {});
  navigator.serviceWorker.addEventListener("message", event => { if (event.data?.type === "PC_UPDATE_READY") $("#update-banner").classList.remove("hidden"); });
}
$("#reload-update").addEventListener("click", () => location.reload());
startFocusTicker();
function showSoftRecovery() {
  if (!state.focus.active || !state.focus.softMode) return;
  const dialog = $("#recovery-dialog");
  if (!dialog.open) dialog.showModal();
  $("#recovery-countdown").textContent = `${Math.ceil(remainingSeconds(state.focus) / 60)} minutes remain in this session.`;
}
$("#recovery-return").addEventListener("click", () => { $("#recovery-dialog").close(); showView("focus"); renderFocus(); });
$("#recovery-end").addEventListener("click", () => { $("#recovery-dialog").close(); finishFocus(false); renderAll(); });
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && state.focus.active && state.focus.softMode) {
    setError("Welcome back. Your soft focus session is still running; other desktop apps were not blocked.");
    renderHome(); showSoftRecovery();
  }
});
const requestedView = new URLSearchParams(location.search).get("view");
if (["home", "study", "planner", "practice", "focus", "progress", "notes", "settings"].includes(requestedView)) currentView = requestedView;
renderAll();
