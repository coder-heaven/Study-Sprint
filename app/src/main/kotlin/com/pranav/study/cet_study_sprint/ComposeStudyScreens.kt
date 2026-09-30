package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SyllabusScreen(prefs: SharedPreferences, revision: Int, initialSubject: String? = null, initialChapterKey: String? = null) {
    val context = LocalContext.current
    val store = remember { StudyData.events(context) }
    val scope = rememberCoroutineScope()
    val course = prefs.getString("exam", "CET") ?: "CET"
    val grade = prefs.getString("grade", "11") ?: "11"
    val data = SyllabusData.chapters(course, grade)
    var subject by remember(course, grade, initialSubject) { mutableStateOf(initialSubject?.takeIf { it in data } ?: data.keys.firstOrNull().orEmpty()) }
    var filter by remember { mutableStateOf("All") }
    var localRevision by remember { mutableIntStateOf(0) }
    var editingChapter by remember { mutableStateOf<String?>(null) }
    var chapterDraft by remember { mutableStateOf("") }
    var savedChapterNote by remember { mutableStateOf(ChapterNote()) }
    var pdfError by remember { mutableStateOf("") }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = editingChapter
        if (uri != null && key != null) scope.launch {
            try {
                val (name, file) = withContext(Dispatchers.IO) { ChapterFiles.importPdf(context, uri) }
                savedChapterNote = savedChapterNote.copy(pdfName = name, pdfFile = file)
                store.saveChapterNote(key, savedChapterNote.copy(body = chapterDraft.trim()))
                pdfError = "PDF saved for this chapter."
                localRevision++
            } catch (error: Throwable) { pdfError = error.message ?: "Could not save PDF." }
        }
    }
    LaunchedEffect(initialChapterKey) {
        initialChapterKey?.let {
            editingChapter = it; savedChapterNote = store.chapterNote(it); chapterDraft = savedChapterNote.body
        }
    }
    var chapterQuery by remember { mutableStateOf("") }
    val redraw = revision + localRevision
    val total = data.values.sumOf { it.size }
    val done = data.entries.sumOf { (name, chapters) ->
        chapters.indices.count { prefs.getBoolean(chapterKey(course, grade, name, it), false) }
    }
    val selected = data[subject].orEmpty()
    val selectedDone = selected.indices.count { prefs.getBoolean(chapterKey(course, grade, subject, it), false) }
    Column(
        Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        AppHeading("$course syllabus", "Class $grade · Keep every chapter visible.")
        Spacer(Modifier.height(20.dp))
        StudyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Overall progress", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    Text("$done of $total chapters done", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${if (total == 0) 0 else done * 100 / total}%", fontSize = 25.sp,
                    fontWeight = FontWeight.Bold, color = Pine)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(9.dp)),
                color = Pine, trackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            )
        }
        if (course != "CET") {
            Spacer(Modifier.height(10.dp))
            Text("NCERT textbook chapters for planning. Check the official $course syllabus for exam coverage.",
                fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        OutlinedTextField(chapterQuery, { chapterQuery = it }, label = { Text("Find a chapter") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        SectionLabel("Subjects")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            data.keys.forEach { item ->
                FilterChip(selected = subject == item, onClick = { subject = item; filter = "All" },
                    label = { Text(item) })
            }
        }
        Spacer(Modifier.height(14.dp))
        StudyCard {
            Text(subject, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Text("$selectedDone of ${selected.size} complete", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(13.dp))
            LinearProgressIndicator(
                progress = { if (selected.isEmpty()) 0f else selectedDone.toFloat() / selected.size },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(9.dp)),
                color = Pine, trackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "To do", "Done").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option },
                        label = { Text(option, fontSize = 12.sp) })
                }
            }
            selected.forEachIndexed { index, chapter ->
                if (!chapter.contains(chapterQuery.trim(), ignoreCase = true)) return@forEachIndexed
                val key = chapterKey(course, grade, subject, index)
                val checked = prefs.getBoolean(key, false)
                if ((filter == "To do" && checked) || (filter == "Done" && !checked)) return@forEachIndexed
                if (index > 0) HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        prefs.edit().putBoolean(key, !checked).apply()
                        localRevision++
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = {
                        prefs.edit().putBoolean(key, it).apply()
                        localRevision++
                    })
                    Text(chapter, fontSize = 15.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 5.dp))
                }
                TextButton(onClick = {
                    editingChapter = key
                    savedChapterNote = store.chapterNote(key)
                    chapterDraft = savedChapterNote.body
                    pdfError = ""
                }) { Text(if (store.chapterNote(key).body.isNotBlank() || store.chapterNote(key).pdfFile.isNotBlank())
                    "Open notes / PDF" else "Add notes / PDF") }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (editingChapter != null) AlertDialog(
        onDismissRequest = { editingChapter = null },
        title = { Text("Chapter notes") },
        text = { Column {
            OutlinedTextField(chapterDraft, { chapterDraft = it }, label = { Text("Notes / to do") },
                modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                Text(if (savedChapterNote.pdfFile.isBlank()) "Upload PDF" else "Replace PDF")
            }
            if (savedChapterNote.pdfFile.isNotBlank()) TextButton(onClick = {
                try { ChapterFiles.openPdf(context, savedChapterNote.pdfFile) }
                catch (error: Throwable) { Toast.makeText(context, error.message ?: "Cannot open PDF", Toast.LENGTH_LONG).show() }
            }) { Text("Open ${savedChapterNote.pdfName}") }
            if (pdfError.isNotBlank()) Text(pdfError)
        } },
        confirmButton = { TextButton(onClick = {
            editingChapter?.let { store.saveChapterNote(it, savedChapterNote.copy(body = chapterDraft.trim())) }
            localRevision++; editingChapter = null
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editingChapter = null }) { Text("Close") } }
    )
}

@Composable
internal fun PracticeScreen(prefs: SharedPreferences, revision: Int, onLegacy: (String) -> Unit) {
    val course = prefs.getString("exam", "CET") ?: "CET"
    val originals = remember(course) { originalQuestions(course) }
    val yours = remember(revision) { savedQuestions(prefs) }
    val chapters = remember(revision) { savedQuizChapters(prefs) }
    var active by remember { mutableStateOf<List<PracticeQuestion>?>(null) }
    var activeTitle by remember { mutableStateOf("Practice") }
    if (active != null) {
        QuizScreen(activeTitle, active.orEmpty(), onBack = { active = null })
        return
    }
    Column(
        Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        AppHeading("Practice", "Build confidence, one question at a time.")
        SectionLabel("MCQ practice")
        StudyCard {
            Text("Original concept questions", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Text("${originals.size} questions matched to your $course subjects.", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(15.dp))
            Button(onClick = { activeTitle = "Concept practice"; active = originals },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Start practice") }
        }
        Spacer(Modifier.height(12.dp))
        StudyCard {
            Text("Your own 10 MCQs", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Text(if (yours.isEmpty()) "Add your questions and answers to build a personal set."
                 else "${yours.size} saved questions are ready.", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(15.dp))
            if (yours.isNotEmpty()) {
                Button(onClick = { activeTitle = prefs.getString("owned_mcqs_chapter", "My questions").orEmpty(); active = yours },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)) { Text("Practice latest: ${prefs.getString("owned_mcqs_chapter", "My questions")}") }
                Spacer(Modifier.height(9.dp))
            }
            chapters.filter { it != prefs.getString("owned_mcqs_chapter", "") }.forEach { chapter ->
                OutlinedButton(onClick = { activeTitle = chapter; active = savedQuestions(prefs, chapter) },
                    modifier = Modifier.fillMaxWidth()) { Text("Practice: $chapter") }
            }
            OutlinedButton(onClick = { onLegacy("import") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Add or edit my questions") }
        }
        SectionLabel("From your books")
        StudyCard {
            Text("Arihant practice log", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Text("Log attempted and correct answers from your own book.", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(15.dp))
            OutlinedButton(onClick = { onLegacy("arihant") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Open practice log") }
        }
        Spacer(Modifier.height(20.dp))
    }
}
