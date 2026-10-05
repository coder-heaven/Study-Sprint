package com.pranav.study.cet_study_sprint

import android.graphics.BitmapFactory
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun StudyChatScreen(model: StudyChatViewModel, go: (String) -> Unit, refresh: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val photoSlots = (StudyChatClient.MAX_PHOTOS - state.photos.size).coerceAtLeast(0)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(photoSlots.coerceAtLeast(2)), model::importPhotos)
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.importPhotos(listOf(uri))
    }
    LaunchedEffect(model) { model.selectModel("auto") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val name = state.pdfFile
        if (uri != null && name != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> File(context.filesDir, "chapter_pdfs/$name").inputStream().use { it.copyTo(output) } }
                    ?: error("Cannot open destination")
            }.isSuccess }
            Toast.makeText(context, if (saved) "PDF saved" else "Could not save PDF. Please retry.", Toast.LENGTH_SHORT).show()
        }
    }
    StudyChatContent(state, { model.send(it) }, model::retry, model::stop, model::clear, model::removePhoto,
        { if (photoSlots == 1) singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          else if (photoSlots > 1) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, { go("privacy") }, model::generatePdf,
        { export.launch("Study-Sprint-Photo-MCQs.pdf") },
        { state.pdfFile?.let { name -> runCatching { ChapterFiles.openPdf(context, name) }.onFailure { Toast.makeText(context, "No PDF viewer is available. Use Save PDF.", Toast.LENGTH_SHORT).show() } } },
        { refresh(); go("my_quiz") }, { model.send(it, true) }, model::resend, { message -> if (model.startQuiz(message)) { refresh(); go("my_quiz") } }, model::selectDifficulty)
}
@Composable
internal fun StudyChatContent(state: ChatUiState, send: (String) -> Boolean, retry: () -> Unit, stop: () -> Unit,
    clear: () -> Unit, removePhoto: (String) -> Unit, choosePhotos: () -> Unit, privacy: () -> Unit,
    generatePdf: (Boolean) -> Boolean = { false }, savePdf: () -> Unit = {}, openPdf: () -> Unit = {}, startPractice: () -> Unit = {}, sendMcq: (String) -> Boolean = send, resend: () -> Unit = {}, startQuiz: (ChatMessage) -> Unit = {}, selectDifficulty: (String) -> Unit = {}) {
    var mcqMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var autoPractice by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var draft by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { if ((if (mcqMode && state.photos.isEmpty()) sendMcq else send)(draft)) { draft = ""; keyboard?.hide() } }
    val preferences = LocalContext.current.getSharedPreferences("study_sprint", android.content.Context.MODE_PRIVATE)
    val exam = preferences.getString("exam", "CET") ?: "CET"
    val list = rememberLazyListState()
    val quizScope = rememberCoroutineScope()
    LaunchedEffect(state.messages.size, state.error) {
        val count = state.messages.size + (if (state.messages.isEmpty()) 1 else 0) + (if (state.error != null) 1 else 0) + (if (state.pdfFile != null) 1 else 0)
        if (count > 0) list.animateScrollToItem(count - 1)
    }
    Column(Modifier.fillMaxSize().imePadding().testTag("study_chat")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.study_buddy_3d), null, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text("Your study buddy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$exam study help · occasional exam tips", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.messages.isEmpty()) item {
                StudyCard {
                    Text("Let's learn together", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Ask a concept question, work through a problem, or practise for CET, JEE and NEET.")
                    Text("Attach up to 4 photos of your question for explanations or a practice PDF.", style = MaterialTheme.typography.bodySmall)
                    Text("Only questions and photos you send go to Study Sprint's Render chat service and Google, OpenRouter or NVIDIA. Your notes, profile and app usage are not attached.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = privacy) { Text("Privacy details") }
                    listOf("Explain photon energy simply", "Give me one mole-concept MCQ", "Help me plan a 25-minute study session").forEach { prompt ->
                        OutlinedButton(onClick = { draft = prompt }, modifier = Modifier.fillMaxWidth()) { Text(prompt) }
                    }
                }
            }
            items(state.messages.size) { index ->
                val message = state.messages[index]; val user = message.role == "user"
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (user) "You" else "Study buddy", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (message.photoCount > 0) Text("${message.photoCount} photo${if (message.photoCount == 1) "" else "s"} attached", style = MaterialTheme.typography.labelSmall)
                        if (user) SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyMedium) }
                        else {
                            val quizQuestions = remember(message.content) { chatQuizQuestions(message.content) }
                            if (quizQuestions.isNotEmpty()) ChatInteractiveQuiz(message.content, quizQuestions, exam) {
                                quizScope.launch { list.animateScrollToItem(index) }
                            }
                            else ChatMarkdown(if (message.quiz) message.content.replace("\n", "  \n") else message.content, Modifier.fillMaxWidth())
                            if (message.sources.isNotBlank()) { Text(if (message.model == StudyChatClient.SEARCH_MODEL) "Question sources" else "Study context (original practice)", fontWeight = FontWeight.Bold); ChatMarkdown(message.sources, Modifier.fillMaxWidth()) }
                            if (message.suggestions.isNotBlank()) GoogleSearchSuggestions(message.suggestions)
                            if (message.quiz) {
                                OutlinedButton(onClick = { startQuiz(message) }, enabled = !state.busy, modifier = Modifier.testTag("chat_start_web_quiz")) { Text("Start MCQ test") }
                                Text("Saves these questions to Practice. Saved tests work offline.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (state.pdfFile != null) item {
                StudyCard {
                    Text("Saved photo MCQ PDF", fontWeight = FontWeight.Bold)
                    Text("10 questions · ${state.pdfRemaining}/2 PDFs left today. Verify AI answers.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = savePdf, modifier = Modifier.testTag("chat_save_pdf")) { Text("Save PDF") }
                        TextButton(onClick = openPdf) { Text("Open") }
                    }
                    if (state.practiceReady) Button(onClick = startPractice, modifier = Modifier.fillMaxWidth().testTag("chat_start_mcqs")) { Text("Start imported quiz") }
                }
            }
            if (state.error != null) item {
                Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("chat_error"))
                if (state.canRetry) OutlinedButton(onClick = retry, enabled = !state.busy && !state.photoBusy,
                    modifier = Modifier.testTag("chat_retry")) { Text("Retry") }
            }
        }
        if (state.busy) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Preparing your answer…", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = stop) { Text("Stop") }
        }
        if (state.messages.isNotEmpty()) Row(Modifier.align(Alignment.End)) {
            if (state.canResend && !state.canRetry) TextButton(onClick = resend, enabled = !state.busy && !state.photoBusy, modifier = Modifier.testTag("chat_resend")) { Text("Resend") }
            TextButton(onClick = { keyboard?.hide(); draft = ""; clear() }) { Text("New chat") }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mcqMode, onClick = { mcqMode = !mcqMode }, enabled = !state.busy && state.photos.isEmpty(), label = { Text("Online MCQs") }, modifier = Modifier.testTag("chat_mcq_mode"))
                if (mcqMode || state.photos.isNotEmpty() || Regex("\\b(mcq|quiz|practice questions|previous.year questions)\\b", RegexOption.IGNORE_CASE).containsMatchIn(draft)) {
                    Text("MCQ difficulty", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Easy", "Medium", "Hard").forEach { level ->
                            FilterChip(selected = state.mcqDifficulty == level, onClick = { selectDifficulty(level) },
                                enabled = !state.busy && !state.photoBusy, label = { Text(level) }, modifier = Modifier.testTag("chat_difficulty_${level.lowercase()}"))
                        }
                    }
                }
                if (state.photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("chat_photos")) {
                    items(state.photos, key = { it.id }) { photo ->
                        val bitmap = remember(photo.id) { BitmapFactory.decodeByteArray(photo.thumbnail, 0, photo.thumbnail.size)?.asImageBitmap() }
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (bitmap != null) Image(bitmap, "Selected photo", Modifier.size(48.dp), contentScale = ContentScale.Crop)
                                TextButton(onClick = { removePhoto(photo.id) }, enabled = !state.busy, modifier = Modifier.testTag("remove_photo_${photo.id}").semantics { contentDescription = "Remove selected photo" }) { Text("×") }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = choosePhotos, enabled = !state.busy && !state.photoBusy && state.photos.size < 4, modifier = Modifier.testTag("chat_attach")) {
                        Text(if (state.photoBusy) "Preparing photos…" else "＋ Photos · ${(StudyChatClient.MAX_PHOTOS - state.photos.size).coerceAtLeast(0)} remaining")
                    }
                    Text("Up to 4 per message · sent with Send", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.photos.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(autoPractice, { autoPractice = it }, enabled = !state.busy && !state.photoBusy, modifier = Modifier.testTag("chat_auto_mcqs"))
                        Text("Automatically add MCQs to practice", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = { if (generatePdf(autoPractice)) { draft = ""; keyboard?.hide() } },
                        enabled = !state.busy && !state.photoBusy, modifier = Modifier.fillMaxWidth().testTag("chat_generate_pdf")) {
                        Text("Generate 10-MCQ PDF · ${state.pdfRemaining}/2 left today")
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft, { draft = it.take(StudyChatClient.MAX_PROMPT + 1) }, label = { Text("Ask a study question") }, maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }), modifier = Modifier.weight(1f).testTag("chat_input"))
                    Button(onClick = { submit() }, enabled = !state.busy && !state.photoBusy && (draft.isNotBlank() || state.photos.isNotEmpty()),
                        modifier = Modifier.heightIn(min = 56.dp).testTag("chat_send"), contentPadding = PaddingValues(12.dp)) { Text("Send") }
                }
                Text("AI can be wrong. Check answers. Chat is saved on this device until you tap New chat.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GoogleSearchSuggestions(html: String) {
    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(factory = {
        android.webkit.WebView(it).apply {
            settings.javaScriptEnabled = false; settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    if (uri.scheme == "https") runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                    return true
                }
            }
            loadDataWithBaseURL("https://www.google.com/", html, "text/html", "UTF-8", null)
        }
    }, modifier = Modifier.fillMaxWidth().height(140.dp))
}
