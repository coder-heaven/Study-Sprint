package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

internal data class PracticeQuestion(
    val subject: String,
    val prompt: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

internal fun originalQuestions(course: String): List<PracticeQuestion> {
    val all = listOf(
        PracticeQuestion("Physics", "A car moves east and then north with equal displacement. Resultant angle to east?",
            listOf("30°", "45°", "60°", "90°"), 1, "Equal perpendicular components give tan θ = 1."),
        PracticeQuestion("Physics", "If a planet's mass doubles at fixed radius, surface gravity becomes:",
            listOf("Half", "Unchanged", "Double", "Four times"), 2, "g = GM/R²."),
        PracticeQuestion("Chemistry", "How many moles are in 9 g water? M = 18 g mol⁻¹",
            listOf("0.25", "0.5", "1", "2"), 1, "Moles = mass ÷ molar mass."),
        PracticeQuestion("Chemistry", "Which change is oxidation?",
            listOf("Gain electrons", "Lower oxidation number", "Loss of electrons", "Gain hydrogen"),
            2, "Oxidation is loss of electrons."),
        PracticeQuestion("Mathematics", "If sin θ = 3/5 for an acute angle, cos θ is:",
            listOf("2/5", "3/4", "4/5", "5/4"), 2, "Use sin²θ + cos²θ = 1."),
        PracticeQuestion("Mathematics", "A fair coin is tossed twice. Probability of exactly one head:",
            listOf("1/4", "1/3", "1/2", "3/4"), 2, "HT and TH are two of four outcomes."),
        PracticeQuestion("Biology", "Which organelle is the main site of aerobic respiration?",
            listOf("Ribosome", "Mitochondrion", "Golgi body", "Lysosome"), 1,
            "Mitochondria produce most cellular ATP during aerobic respiration."),
        PracticeQuestion("Biology", "In an Aa × Aa cross, what fraction of offspring are aa?",
            listOf("One quarter", "One half", "Three quarters", "All"), 0,
            "A monohybrid cross gives aa in one of four outcomes.")
    )
    val subjects = if (course == "NEET") setOf("Physics", "Chemistry", "Biology")
        else setOf("Physics", "Chemistry", "Mathematics")
    return all.filter { it.subject in subjects }
}

internal fun savedQuestions(prefs: SharedPreferences): List<PracticeQuestion> = runCatching {
    val items = JSONArray(prefs.getString("owned_mcqs_json", "[]"))
    (0 until items.length()).map { index ->
        val item = items.getJSONObject(index)
        val options = item.getJSONArray("options")
        PracticeQuestion(
            "My saved MCQs", item.getString("question"),
            (0 until options.length()).map { options.getString(it) },
            item.getInt("answer").coerceIn(0, 3),
            "Saved from your own set."
        )
    }
}.getOrDefault(emptyList())

@Composable
internal fun QuizScreen(title: String, questions: List<PracticeQuestion>, onBack: () -> Unit) {
    val context = LocalContext.current
    val course = remember { context.getSharedPreferences("study_sprint", android.content.Context.MODE_PRIVATE)
        .getString("exam", "CET") ?: "CET" }
    val scheme = remember(course) { markingSchemeFor(course) }
    val scrollState = rememberScrollState()
    var index by remember(questions) { mutableIntStateOf(0) }
    var marks by remember(questions, course) { mutableIntStateOf(0) }
    var correctCount by remember(questions) { mutableIntStateOf(0) }
    var selected by remember(index) { mutableIntStateOf(-1) }
    var revealed by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        if (index > 0) scrollState.scrollTo(0)
        if (index == questions.size && questions.isNotEmpty()) {
            StudyData.events(context).recordPractice(questions.size, correctCount)
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹  Back to practice") }
        if (questions.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { (index.coerceAtMost(questions.size)).toFloat() / questions.size },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(140)) + slideInHorizontally(tween(160)) { it / 14 }) togetherWith
                    (fadeOut(tween(90)) + slideOutHorizontally(tween(130)) { -it / 16 })
            },
            label = "quiz-question"
        ) { page ->
            if (page >= questions.size) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    AppHeading("Practice complete", "Nice work showing up today.")
                    Spacer(Modifier.height(20.dp))
                    StudyCard {
                        Text("$marks / ${questions.size * scheme.correct}", fontSize = 43.sp,
                            fontWeight = FontWeight.Bold, color = Pine)
                        Text("marks · $correctCount correct · ${questions.size - correctCount} wrong",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$course marking: ${scheme.label()}", fontSize = 13.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 5.dp))
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { index = 0; marks = 0; correctCount = 0 }, modifier = Modifier.fillMaxWidth()) {
                            Text("Try this set again")
                        }
                    }
                }
            } else {
                val question = questions[page]
                Column {
                    AppHeading(title, "Question ${page + 1} of ${questions.size} · ${question.subject} · ${scheme.label()}")
                    Spacer(Modifier.height(16.dp))
                    StudyCard {
                        Text(question.prompt, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(18.dp))
                        question.options.forEachIndexed { answerIndex, option ->
                            val selectedOption = selected == answerIndex
                            val rightOption = revealed && answerIndex == question.answer
                            val tint = when {
                                rightOption -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                                selectedOption && revealed -> androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                                selectedOption -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                else -> androidx.compose.material3.MaterialTheme.colorScheme.surface
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
                                    .clickable(enabled = !revealed) { selected = answerIndex },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = tint),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Text("${'A' + answerIndex}. $option", fontSize = 15.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().padding(15.dp))
                            }
                        }
                        if (revealed) {
                            Spacer(Modifier.height(8.dp))
                            val isCorrect = selected == question.answer
                            val delta = scheme.score(isCorrect)
                            Text(if (isCorrect) "Correct! +${scheme.correct}" else "Correct answer: ${'A' + question.answer} · $delta",
                                fontWeight = FontWeight.Bold,
                                color = if (isCorrect) androidx.compose.material3.MaterialTheme.colorScheme.primary
                                else androidx.compose.material3.MaterialTheme.colorScheme.error)
                            Text(question.explanation, fontSize = 14.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (!revealed) {
                                    if (selected >= 0) {
                                        revealed = true
                                        val isCorrect = selected == question.answer
                                        marks += scheme.score(isCorrect)
                                        if (isCorrect) correctCount++
                                    }
                                } else index++
                            },
                            enabled = revealed || selected >= 0,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(if (revealed) "Next question" else "Check answer") }
                        if (!revealed && selected < 0) {
                            Text("Choose an option to continue.",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}
