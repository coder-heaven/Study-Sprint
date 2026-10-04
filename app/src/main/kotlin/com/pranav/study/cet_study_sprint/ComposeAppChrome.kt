package com.pranav.study.cet_study_sprint

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StudyPillNavigation(selectedTab: String?, go: (String) -> Unit) {
    Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(84.dp).testTag("pill_navigation")) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp), shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            StudyNavigation.tabs.forEach { item ->
                val current = selectedTab == item.route
                val res = when (item.route) { "home" -> R.drawable.nav_home; "study" -> R.drawable.nav_syllabus
                    "focus" -> R.drawable.nav_focus; "plan" -> R.drawable.nav_plan; else -> R.drawable.nav_progress }
                val isFocus = item.route == "focus"
                Column(Modifier.weight(1f).height(if (isFocus) 84.dp else 70.dp).clip(RoundedCornerShape(28.dp))
                    .clickable(role = Role.Tab) { go(item.route) }.semantics { selected = current }
                    .testTag("tab_${item.route}"), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Surface(shape = CircleShape, color = if (isFocus) MaterialTheme.colorScheme.primary else if (current)
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (isFocus) BorderStroke(4.dp, MaterialTheme.colorScheme.background) else null,
                        shadowElevation = if (isFocus) 4.dp else 0.dp) {
                        Box(Modifier.size(if (isFocus) 54.dp else 34.dp), contentAlignment = Alignment.Center) {
                            Icon(painterResource(res), null, Modifier.size(22.dp), tint = if (isFocus) MaterialTheme.colorScheme.onPrimary
                                else if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(item.label, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable
internal fun StudyBuddyShortcut(open: () -> Unit) {
    FloatingActionButton(onClick = open, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp).testTag("chat_fab").semantics { contentDescription = "Open Study buddy" }) {
        Image(painterResource(R.drawable.study_buddy_3d), null, Modifier.size(48.dp))
    }
}
internal object BugReports {
    fun draft(version: String, sdk: Int, description: String, steps: String): String =
        "Study Sprint $version · Android API $sdk\n\nWhat happened:\n${description.trim().take(2000)}\n\nSteps to reproduce:\n${steps.trim().take(1000)}\n\nExpected behavior:\n[Describe what should happen]"
    fun uri(version: String, sdk: Int, description: String, steps: String): Uri = Uri.parse("https://github.com/coder-heaven/Study-Sprint/issues/new")
        .buildUpon().appendQueryParameter("title", "Bug: " + description.trim().lineSequence().first().take(80))
        .appendQueryParameter("body", draft(version, sdk, description, steps)).build()
}
@Composable
internal fun BugReportScreen() {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }; var steps by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "4.9.0" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 76.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Help make Study Sprint better", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Describe the problem and how to reproduce it. The next step opens an editable GitHub report; you choose whether to submit it.")
        Text("GitHub reports are public. Leave out API keys, private photos and personal information.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(description, { description = it.take(2000) }, label = { Text("What went wrong?") }, minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("bug_description"))
        OutlinedTextField(steps, { steps = it.take(1000) }, label = { Text("Steps to reproduce") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("Study Sprint $version · Android API ${Build.VERSION.SDK_INT}", style = MaterialTheme.typography.bodySmall)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Button(enabled = description.isNotBlank(), onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, BugReports.uri(version, Build.VERSION.SDK_INT, description, steps))) }
                .onFailure { error = "No browser could open the report. Use GitHub → Study-Sprint → Issues." }
        }, modifier = Modifier.fillMaxWidth().testTag("bug_open_report")) { Text("Review report on GitHub") }
    }
}
