package com.pranav.study.cet_study_sprint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun PrivacyDocumentScreen(terms: Boolean) {
    val context = LocalContext.current
    val blocks = remember(terms) { context.assets.open(if (terms) "terms.md" else "privacy.md")
        .bufferedReader().use { it.readText() }.trim().split("\n\n") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { AppHeading(if (terms) "Terms of Use" else "Privacy Policy", "Your data. Your choices.") }
        items(blocks.drop(1)) { block ->
            Text(block.removePrefix("## "), style = if (block.startsWith("## ")) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun PrivacyDocumentDialog(terms: Boolean, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (terms) "Terms of Use" else "Privacy Policy") },
        text = { Box(Modifier.heightIn(max = 420.dp)) { PrivacyDocumentScreen(terms) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
internal fun LeaderboardConsentDialog(onDismiss: () -> Unit, onAllow: () -> Unit, go: (String) -> Unit) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose public sharing") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Your app profile name, photo and study/quiz scores will be uploaded to Firebase and shown to other leaderboard users. Use a nickname if you prefer. Local study features work without sharing.")
                TextButton(onClick = { onDismiss(); go("privacy") }) { Text("Read Privacy Policy") }
                TextButton(onClick = { onDismiss(); go("terms") }) { Text("Read Terms of Use") }
                Row(Modifier.fillMaxWidth().clickable { checked = !checked }) {
                    Checkbox(checked, { checked = it }, modifier = Modifier.testTag("privacy_permission"))
                    Text("I have read the Privacy Policy and Terms, and permit this public sharing. I have any parent/guardian permission needed.",
                        modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                }
                Text("Withdraw in Student leaderboards → Hide my profile. Server removal needs internet access.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = onAllow, enabled = checked) { Text("Allow public sharing") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep private") } })
}
