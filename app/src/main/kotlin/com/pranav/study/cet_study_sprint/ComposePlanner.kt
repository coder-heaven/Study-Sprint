package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun orderedTasks(prefs: SharedPreferences): List<String> {
    val tasks = prefs.getStringSet("tasks", emptySet()).orEmpty()
    val saved = runCatching {
        val array = JSONArray(prefs.getString("task_order", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    return saved.filter { it in tasks } + tasks.filter { it !in saved }.sorted()
}
internal fun saveTasks(prefs: SharedPreferences, tasks: List<String>) {
    prefs.edit().putStringSet("tasks", tasks.toSet())
        .putString("task_order", JSONArray(tasks).toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlannerScreen(prefs: SharedPreferences, revision: Int, go: (String) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { StudyData.events(context) }
    val day = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var localRevision by remember { mutableIntStateOf(0) }
    val tasks = remember(revision, localRevision) { orderedTasks(prefs) }
    val completed by produceState(0, localRevision) { value = store.totals(1).tasks }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var taskChapter by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    var noteExpanded by remember { mutableStateOf(false) }
    var note by remember(day) { mutableStateOf(store.note(day).ifBlank { prefs.getString("journal", "").orEmpty() }) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        AppHeading("Today's plan", SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date()))
        Spacer(Modifier.height(15.dp))
        StudyCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$completed done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${tasks.size} next steps", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { completed.toFloat() / (completed + tasks.size).coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = null; draft = ""; taskChapter = ""; sheet = true }) { Text("Add task") }
            OutlinedButton(onClick = { go("settings") }) { Text("Reminders") }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Your next steps")
        if (tasks.isEmpty()) StudyCard {
            Text("Nothing planned yet", fontWeight = FontWeight.SemiBold)
            Text("Add one clear next step for today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        tasks.forEachIndexed { index, task ->
            Spacer(Modifier.height(8.dp))
            StudyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { checked ->
                        if (checked) {
                            saveTasks(prefs, tasks - task)
                            store.recordTask()
                            localRevision++
                        }
                    })
                    Text(task, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        editing = task
                        taskChapter = task.substringBefore(" • ", "")
                        draft = if (" • " in task) task.substringAfter(" • ") else task
                        sheet = true
                    }) { Text("Edit") }
                }
                if (tasks.size > 1) Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = index > 0, onClick = {
                        val reordered = tasks.toMutableList()
                        val item = reordered.removeAt(index)
                        reordered.add(index - 1, item)
                        saveTasks(prefs, reordered); localRevision++
                    }) { Text("↑") }
                    TextButton(enabled = index < tasks.lastIndex, onClick = {
                        val reordered = tasks.toMutableList()
                        val item = reordered.removeAt(index)
                        reordered.add(index + 1, item)
                        saveTasks(prefs, reordered); localRevision++
                    }) { Text("↓") }
                }
            }
        }
        SectionLabel("What I learned today")
        StudyCard {
            Row(Modifier.fillMaxWidth().clickable { noteExpanded = !noteExpanded },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Class notes", fontWeight = FontWeight.SemiBold)
                    Text(if (note.isBlank()) "Add a concept, doubt, or useful example"
                        else note.take(80), maxLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (noteExpanded) "⌃" else "⌄", fontSize = 22.sp)
            }
            if (noteExpanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your note") }, minLines = 3)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    store.saveNote(day, note.trim())
                    prefs.edit().putString("journal", note.trim()).apply()
                    noteExpanded = false
                }, modifier = Modifier.fillMaxWidth()) { Text("Save note") }
            }
        }
        Spacer(Modifier.height(88.dp))
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text(if (editing == null) "Add task" else "Edit task",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(draft, { draft = it }, label = { Text("What will you study?") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(taskChapter, { taskChapter = it }, label = { Text("Chapter (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val value = if (taskChapter.isBlank()) draft.trim()
                    else "${taskChapter.trim()} • ${draft.trim()}"
                if (value.isNotEmpty()) {
                    val updated = if (editing == null) tasks + value
                        else tasks.map { if (it == editing) value else it }
                    saveTasks(prefs, updated.distinct())
                    localRevision++; sheet = false
                }
            }, enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save task") }
            if (editing != null) TextButton(onClick = {
                saveTasks(prefs, tasks - editing!!)
                localRevision++; sheet = false
            }, modifier = Modifier.fillMaxWidth()) { Text("Delete task") }
        }
    }
}

@Composable
internal fun NotesHistoryScreen() {
    val context = LocalContext.current
    val store = remember { StudyData.events(context) }
    val notes by produceState<List<Pair<String, String>>>(emptyList()) { value = store.notes() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        AppHeading("What I learned", "Your notes, by day.")
        if (notes.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            StudyCard { Text("No saved notes yet.") }
        }
        notes.forEach { (day, body) ->
            Spacer(Modifier.height(10.dp))
            StudyCard {
                Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
