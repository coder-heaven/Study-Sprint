package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun StudyHubScreen(prefs: SharedPreferences, revision: Int, go: (String) -> Unit, openSubject: (String) -> Unit) {
    val course = prefs.getString("exam", "CET").orEmpty()
    val grade = prefs.getString("grade", "11").orEmpty()
    val chapters = SyllabusData.chapters(course, grade)
    val saved = remember(revision) { savedQuestions(prefs) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        AppHeading("Your study space", "$course · Class $grade")
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(20.dp)) {
                Text("A little practice, every day", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(if (saved.isEmpty()) "Start with your course's concept questions." else "${saved.size} saved questions ready to practise.",
                    modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { go(if (saved.isEmpty()) "practice" else "my_quiz") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saved.isEmpty()) "Open practice" else "Practise my latest set")
                }
            }
        }
        SectionLabel("Subjects & chapters")
        chapters.forEach { (subject, list) ->
            val done = list.indices.count { prefs.getBoolean(chapterKey(course, grade, subject, it), false) }
            StudyLink(subject, "$done / ${list.size} complete · Notes & PDFs") { openSubject(subject) }
            Spacer(Modifier.height(8.dp))
        }
        SectionLabel("Study tools")
        StudyLink("MCQ practice", "Concept questions and your saved sets") { go("practice") }
        Spacer(Modifier.height(8.dp))
        StudyLink("Import questions", "Create, review or import a PDF question set") { go("mcq_editor") }
        Spacer(Modifier.height(8.dp))
        StudyLink("Class notes", "Revisit what you learned in class") { go("notes") }
        Spacer(Modifier.height(8.dp))
        StudyLink("Arihant log", "Record practice from your own books") { go("arihant") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun StudyLink(title: String, detail: String, onClick: () -> Unit) {
    StudyCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun ActiveFocusBar(state: FocusState, open: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.isBreak) "Break in progress" else if (state.running) "Focus in progress" else "Focus paused",
                    style = MaterialTheme.typography.labelLarge)
                if (state.task.isNotBlank()) Text(state.task, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            Text("%02d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Return to timer")
        }
    }
}

@Composable
internal fun QuickStudyTaskDialog(prefs: SharedPreferences, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a study task") },
        text = { OutlinedTextField(draft, { draft = it }, label = { Text("What will you study?") },
            placeholder = { Text("e.g. Revise atomic structure") }, modifier = Modifier.fillMaxWidth(), maxLines = 3) },
        confirmButton = { TextButton(enabled = draft.isNotBlank(), onClick = {
            saveTasks(prefs, (orderedTasks(prefs) + draft.trim()).distinct()); onSaved()
        }) { Text("Add task") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private data class StudySearchItem(val title: String, val detail: String, val route: String,
    val subject: String? = null, val chapterKey: String? = null)

@Composable
internal fun StudySearchScreen(prefs: SharedPreferences, revision: Int, go: (String) -> Unit,
    openChapter: (String, String) -> Unit) {
    val context = LocalContext.current
    val store = remember { StudyData.events(context) }
    val dataRevision by store.revision.collectAsState()
    var query by remember { mutableStateOf("") }
    val items by produceState<List<StudySearchItem>>(emptyList(), revision, dataRevision) {
        value = withContext(Dispatchers.IO) {
            val course = prefs.getString("exam", "CET").orEmpty()
            val grade = prefs.getString("grade", "11").orEmpty()
            buildList {
                add(StudySearchItem("Focus timer", "Pomodoro, breaks and session history", "focus"))
                add(StudySearchItem("App limits", "Block distractions and set daily limits", "limits"))
                add(StudySearchItem("Plan & reminders", "Your tasks and daily plan", "plan"))
                add(StudySearchItem("Notification settings", "Timer sound and study reminders", "settings"))
                add(StudySearchItem("Setup checklist", "All key settings and optional Android permissions", "setup"))
                add(StudySearchItem("How to use the app", "Step-by-step Study Sprint tutorial", "tutorial"))
                add(StudySearchItem("Leaderboard", "Study and quiz rankings", "leaderboard"))
                add(StudySearchItem("Study buddy", "Nemotron AI chatbot · concept help and original practice", "chat"))
                add(StudySearchItem("MCQ practice", "Concept questions and saved quizzes", "practice"))
                add(StudySearchItem("Import PDF questions", "Add or edit your questions", "mcq_editor"))
                add(StudySearchItem("Progress", "Statistics, study history and app usage", "statistics"))
                SyllabusData.chapters(course, grade).forEach { (subject, chapters) ->
                    chapters.forEachIndexed { index, chapter ->
                        val key = chapterKey(course, grade, subject, index)
                        val note = store.chapterNote(key)
                        add(StudySearchItem(chapter, "$subject · Class $grade", "syllabus", subject, key))
                        if (note.body.isNotBlank()) add(StudySearchItem("Notes: $chapter", note.body, "syllabus", subject, key))
                        if (note.pdfFile.isNotBlank()) add(StudySearchItem(note.pdfName, "$subject · $chapter · PDF", "syllabus", subject, key))
                    }
                }
                orderedTasks(prefs).forEach { add(StudySearchItem(it, "Study task", "plan")) }
                store.notes().forEach { (day, body) -> if (body.isNotBlank()) add(StudySearchItem("Class notes · $day", body, "notes")) }
                savedQuizChapters(prefs).forEach { add(StudySearchItem(it, "Saved question set", "practice")) }
            }
        }
    }
    val matches = items.filter { query.isBlank() || (it.title + " " + it.detail).contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        AppHeading("Find your next step", "Search your chapters, notes and tools.")
        OutlinedTextField(query, { query = it }, label = { Text("Search chapters, notes, PDFs or features") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(if (query.isBlank()) "Find a feature or your study materials" else "${matches.size} results",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp))
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            items(count = matches.size) { index ->
                val item = matches[index]
                StudyLink(item.title, item.detail.take(100)) {
                    if (item.subject != null && item.chapterKey != null) openChapter(item.subject, item.chapterKey)
                    else go(item.route)
                }
            }
            if (matches.isEmpty()) item { Text("No results. Try a subject, chapter name or ‘timer’.") }
        }
    }
}
