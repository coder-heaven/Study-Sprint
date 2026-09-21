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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val drafts = remember {
        val saved = savedQuestions(prefs)
        mutableStateListOf<McqDraft>().apply {
            repeat(10) { index ->
                val item = saved.getOrNull(index)
                add(
                    if (item == null) McqDraft()
                    else McqDraft(item.prompt, item.options.take(4) + List((4 - item.options.size).coerceAtLeast(0)) { "" }, item.answer)
                )
            }
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    fun save() {
        val incomplete = drafts.indexOfFirst { draft ->
            draft.question.isBlank() || draft.options.size != 4 || draft.options.any { it.isBlank() }
        }
        if (incomplete >= 0) {
            index = incomplete
            message = "Complete question ${incomplete + 1} and all four options."
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
        AppHeading("Your 10 MCQs", "Add one clear question at a time.")
        Spacer(Modifier.height(14.dp))
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
                    onValueChange = { drafts[page] = draft.copy(question = it); message = null },
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
                            message = null
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
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (index > 0) { index--; message = null } },
                enabled = index > 0,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Text("Previous") }
            Button(
                onClick = {
                    if (index < 9) { index++; message = null } else save()
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Text(if (index == 9) "Save all" else "Next") }
        }
        Spacer(Modifier.height(24.dp))
    }
}