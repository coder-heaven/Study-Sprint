package com.pranav.study.cet_study_sprint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Only complete, four-option questions can become an interactive test.
internal fun chatQuizQuestions(text: String): List<PdfImportedMcq> = runCatching {
    val count = Regex("(?m)^\\s*\\d{1,2}[.)]\\s+").findAll(ChatMcqPdf.normalize(text)).count()
    ChatMcqPdf.questions(text, count)
}.getOrDefault(emptyList())

@Composable
internal fun ChatInteractiveQuiz(text: String, questions: List<PdfImportedMcq>, exam: String) {
    var textView by rememberSaveable(text) { mutableStateOf(false) }
    var page by rememberSaveable(text) { mutableIntStateOf(0) }
    var selected by rememberSaveable(text) { mutableIntStateOf(-1) }
    var answers by rememberSaveable(text) { mutableStateOf(IntArray(questions.size) { -1 }) }
    val scheme = markingSchemeFor(exam)
    val completed = page >= questions.size
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("chat_interactive_quiz")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Practice quiz", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { textView = !textView }, modifier = Modifier.testTag("chat_quiz_view")) {
                Text(if (textView) "Interactive quiz" else "Text view")
            }
        }
        if (textView) {
            ChatMarkdown(text.replace("\n", "  \n"), Modifier.fillMaxWidth().testTag("chat_quiz_text"))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    questions.indices.forEach { n ->
                        Surface(Modifier.weight(1f).height(5.dp), shape = RoundedCornerShape(3.dp),
                            color = if (n <= page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {}
                    }
                }
                Text(if (completed) "${questions.size} / ${questions.size}" else "${page + 1} / ${questions.size}",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("chat_quiz_progress"))
            }
            if (completed) {
                val correct = questions.indices.count { answers[it] == questions[it].answer }
                val score = questions.indices.sumOf { scheme.score(answers[it] == questions[it].answer) }
                Text("Quiz complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$correct / ${questions.size} correct", modifier = Modifier.testTag("chat_quiz_result"))
                Text("$score / ${questions.size * scheme.correct} marks · $exam", color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { page = 0; selected = -1; answers = IntArray(questions.size) { -1 } },
                    modifier = Modifier.testTag("chat_quiz_restart")) { Text("Try again") }
            } else {
                val question = questions[page]
                val answered = answers[page] >= 0
                ChatMarkdown("${page + 1}. ${question.question}", Modifier.fillMaxWidth(), selectable = false)
                question.options.forEachIndexed { n, option ->
                    val correct = answered && n == question.answer
                    val wrong = answered && n == answers[page] && !correct
                    val color = when {
                        correct -> MaterialTheme.colorScheme.primaryContainer
                        wrong -> MaterialTheme.colorScheme.errorContainer
                        selected == n -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Surface(Modifier.fillMaxWidth().testTag("chat_quiz_option_${'A' + n}")
                        .selectable(selected = selected == n, enabled = !answered, role = Role.RadioButton, onClick = { selected = n })
                        .semantics { contentDescription = "Option ${'A' + n}" },
                        shape = RoundedCornerShape(18.dp), color = color) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${'A' + n}.", fontWeight = FontWeight.Bold)
                            ChatMarkdown(option, Modifier.weight(1f), selectable = false)
                        }
                    }
                }
                if (answered) {
                    Text(if (answers[page] == question.answer) "Correct!" else "Correct answer: ${'A' + requireNotNull(question.answer)}",
                        color = if (answers[page] == question.answer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold, modifier = Modifier.testTag("chat_quiz_feedback"))
                }
                Button(onClick = {
                    if (!answered && selected >= 0) answers = answers.copyOf().also { it[page] = selected }
                    else if (answered) { page++; selected = -1 }
                }, enabled = answered || selected >= 0, modifier = Modifier.fillMaxWidth().testTag("chat_quiz_next")) {
                    Text(if (!answered) "Check answer" else if (page == questions.lastIndex) "Finish quiz" else "Next question")
                }
            }
        }
    }
}
