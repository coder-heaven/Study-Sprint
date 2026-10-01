package com.pranav.study.cet_study_sprint

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class BlockedActivity : ComponentActivity() {
    private var blockedIntent by mutableStateOf<Intent?>(null)
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); blockedIntent = intent }
    private fun home() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedIntent = intent
        val prefs = getSharedPreferences("study_sprint", MODE_PRIVATE)
        setContent {
            StudyTheme(prefs, 0) {
                val current = blockedIntent
                val pkg = current?.getStringExtra("package").orEmpty()
                val label = remember(pkg) { runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault("This app") }
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000L) } }
                val day = LocalDay.start(now)
                val usage by produceState<DailyUsage.Measurement?>(null, pkg, day) {
                    while (true) {
                        StrictLimits.applyPending(this@BlockedActivity)
                        value = if (StrictLimits.usageAllowed(this@BlockedActivity)) withContext(Dispatchers.IO) {
                            DailyUsage.measurement(this@BlockedActivity, pkg)
                        } else null
                        delay(5_000L)
                    }
                }
                val reason = StrictLimits.blockReason(this@BlockedActivity, pkg, usage, now)
                val launchApp = remember(pkg) { packageManager.getLaunchIntentForPackage(pkg) }
                val detail = when (reason) {
                    null -> "Your allowance is available. Daily limits reset at 12:00 AM in your phone's time zone."
                    "checking" -> "Checking today's allowance…"
                    "focus" -> "Your focus session is still running. Come back when it finishes."
                    "youtube_daily" -> "YouTube's 10-minute daily allowance is finished. New sessions are available tomorrow."
                    "youtube_session" -> "Start a five-minute window below. You get two windows per day, with no extensions."
                    "permission" -> "Finish Usage Access setup in App Limits before opening $label."
                    else -> "Your daily limit for $label is reached. There is no extra-time bypass."
                }
                BackHandler { home() }
                Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("PAUSE & RESET", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(detail, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                    if (pkg == YouTubeQuota.PACKAGE) YouTubeAllowanceCard()
                    else if (reason == null && launchApp != null) Button(onClick = {
                        startActivity(launchApp); finish()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open $label") }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { home() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Return to home screen") }
                    OutlinedButton(onClick = {
                        startActivity(BlockNavigation.studyIntent(this@BlockedActivity,
                            if (pkg == YouTubeQuota.PACKAGE || current?.getStringExtra("reason") == "permission") "limits" else "focus"))
                        finish()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open Study Sprint") }
                }
            }
        }
    }
}
