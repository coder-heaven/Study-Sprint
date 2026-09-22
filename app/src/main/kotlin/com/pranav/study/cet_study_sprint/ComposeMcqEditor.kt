package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class McqDraft(
    val question: String = "",
    val options: List<String> = List(4) { "" },
    val answer: Int = 0
)

@Composable
internal fun McqEditorScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts = remember {
        val saved = savedQuestions(prefs)
        mutableStateListOf<McqDraft>().apply {
            repeat(10) { index ->
                val item = saved.getOrNull(index)
                add(
                    if (item == null) McqDraft()
                    else McqDraft(
                        item.prompt,
                        item.options.take(4) + List((4 - item.options.size).coerceAtLeast(0)) { "" },
                        item.answer
                    )
                )
            }
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var pdfBusy by remember { mutableStateOf(false) }

    val aiFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val opened = AiMcqAssistant.shareFiles(context, uris)
            message = if (opened) {
                "Prompt copied. Choose an AI app, review the attached files, then export its 10 MCQs as a text-based PDF."
            } else {
                "No compatible app was found. The MCQ prompt is still copied to your clipboard."
            }
            messageIsError = !opened
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            pdfBusy = true
            message = "Reading PDF…"
            messageIsError = false
            try {
                val result = PdfQuestionImporter.read(context, uri)
                repeat(10) { drafts[it] = McqDraft() }
                result.questions.forEachIndexed { questionIndex, question ->
                    drafts[questionIndex] = McqDraft(
                        question = question.question,
                        options = question.options,
                        answer = question.answer ?: 0
                    )
                }
                index = 0
                val remaining = 10 - result.questions.size
                message = buildString {
                    append("Imported ${result.questions.size} MCQ")
                    if (result.questions.size != 1) append("s")
                    append(" from PDF.")
                    if (remaining > 0) append(" Add $remaining more to complete the set.")
                    if (result.missingAnswers > 0) append(" Check ${result.missingAnswers} answer${if (result.missingAnswers == 1) "" else "s"}; A was selected by default.")
                }
            } catch (error: Throwable) {
                message = error.message ?: "The PDF could not be imported."
                messageIsError = true
            } finally {
                pdfBusy = false
            }
        }
    }

    fun clearMessage() {
        message = null
        messageIsError = false
    }

    fun save() {
        val incomplete = drafts.indexOfFirst { draft ->
            draft.question.isBlank() || draft.options.size != 4 || draft.options.any { it.isBlank() }
        }
        if (incomplete >= 0) {
            index = incomplete
            message = "Complete question ${incomplete + 1} and all four options."
            messageIsError = true
            return
        }
        val json = JSONArray()
        drafts.forEach { draft ->
            json.put(JSONObject().apply {
                put("question", draft.question.trim())
                put("options", JSONArray(draft.options.map { it.trim() }))
                put("answer", draft.answer)
            })
        }
        prefs.edit().putString("owned_mcqs_json", json.toString()).apply()
        onSaved()
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹  Back to practice") }
        AppHeading("Your 10 MCQs", "Type questions, create them with an AI app, or import a PDF.")
        Spacer(Modifier.height(12.dp))
        StudyCard {
            Text("Create a compatible PDF with AI", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(
                "Study Sprint prepares the exact 10-MCQ format. Choose any compatible AI app, attach your notes or textbook files, and ask it to export a text-based PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
            )
            Button(
                onClick = {
                    val opened = AiMcqAssistant.openPrompt(context)
                    message = if (opened) {
                        "Prompt copied. Choose your AI app, attach files there, and paste the prompt if it is not filled automatically."
                    } else {
                        "No compatible app was found. The MCQ prompt is copied to your clipboard."
                    }
                    messageIsError = !opened
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Text("Open an AI app") }
            OutlinedButton(
                onClick = { aiFilePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Text("Choose files and open AI") }
            Text(
                "You can share up to five files. App support for shared attachments varies; you can always attach more files from inside the AI app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
            enabled = !pdfBusy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(15.dp)
        ) {
            if (pdfBusy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("Reading PDF…")
            } else {
                Text("Import questions from PDF")
            }
        }
        Text(
            "Supported layout: numbered questions, A–D options, and inline answers or an answer key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        LinearProgressIndicator(
            progress = { (index + 1) / 10f },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Question ${index + 1} of 10",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(140)) + slideInHorizontally(tween(160)) { it / 14 }) togetherWith
                    (fadeOut(tween(90)) + slideOutHorizontally(tween(130)) { -it / 16 })
            },
            label = "mcq-editor-page"
        ) { page ->
            val draft = drafts[page]
            StudyCard {
                Text("Question", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.question,
                    onValueChange = { drafts[page] = draft.copy(question = it); clearMessage() },
                    label = { Text("Type your question") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Answer choices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                draft.options.forEachIndexed { optionIndex, option ->
                    OutlinedTextField(
                        value = option,
                        onValueChange = { value ->
                            val updated = draft.options.toMutableList().also { it[optionIndex] = value }
                            drafts[page] = drafts[page].copy(options = updated)
                            clearMessage()
                        },
                        label = { Text("${'A' + optionIndex}. Option") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Correct answer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { answerIndex ->
                        FilterChip(
                            selected = draft.answer == answerIndex,
                            onClick = { drafts[page] = drafts[page].copy(answer = answerIndex) },
                            label = { Text(('A' + answerIndex).toString()) }
                        )
                    }
                }
            }
        }

        if (message != null) {
            Text(
                message.orEmpty(),
                color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (index > 0) { index--; clearMessage() } },
                enabled = index > 0,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Text("Previous") }
            Button(
                onClick = {
                    if (index < 9) { index++; clearMessage() } else save()
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Text(if (index == 9) "Save all" else "Next") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
