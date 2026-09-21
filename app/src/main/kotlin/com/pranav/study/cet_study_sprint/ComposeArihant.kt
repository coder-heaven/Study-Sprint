package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun ArihantPracticeScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var chapter by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf("") }
    var correct by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var records by remember {
        mutableStateOf(prefs.getStringSet("arihant", emptySet()).orEmpty().toList().sorted())
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹  Back to practice") }
        AppHeading("Arihant practice log", "Record results from your own book.")
        Spacer(Modifier.height(16.dp))
        StudyCard {
            OutlinedTextField(
                value = chapter,
                onValueChange = { chapter = it; error = null },
                label = { Text("Chapter") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = attempted,
                    onValueChange = { attempted = it.filter(Char::isDigit); error = null },
                    label = { Text("Attempted") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = correct,
                    onValueChange = { correct = it.filter(Char::isDigit); error = null },
                    label = { Text("Correct") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = {
                val total = attempted.toIntOrNull()
                val right = correct.toIntOrNull()
                if (chapter.isBlank() || total == null || right == null || total <= 0 || right !in 0..total) {
                    error = "Enter a chapter and a correct score between 0 and attempted."
                } else {
                    val item = "${chapter.trim()}: $right/$total"
                    records = (records + item).distinct().sorted()
                    prefs.edit().putStringSet("arihant", records.toSet()).apply()
                    scope.launch(Dispatchers.IO) { StudyData.events(context).recordPractice(total, right) }
                    chapter = ""; attempted = ""; correct = ""; error = null
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                Text("Save result")
            }
        }
        SectionLabel("Saved results")
        if (records.isEmpty()) {
            StudyCard { Text("No book-practice results yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            records.forEach { item ->
                StudyCard(Modifier.padding(bottom = 8.dp)) {
                    Row {
                        Text(item, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            records = records - item
                            prefs.edit().putStringSet("arihant", records.toSet()).apply()
                        }) { Text("Remove") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}