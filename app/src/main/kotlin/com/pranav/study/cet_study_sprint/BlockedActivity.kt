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
import androidx.compose.ui.platform.testTag
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
                val focusEnd = StrictLimits.prefs(this@BlockedActivity).getLong("focus_block_end", 0L)
                val focusRemaining = if (focusEnd in 1L until Long.MAX_VALUE) {
                    StudyTimeMath.remainingSeconds(focusEnd, now)
                } else 0
                val detail = when (reason) {
                    null -> "Your allowance is available again. You can return to $label."
                    "checking" -> "Checking today's allowance…"
                    "focus" -> if (focusRemaining > 0) {
                        "Stay in your focus session for ${focusRemaining / 60}:${(focusRemaining % 60).toString().padStart(2, '0')} more."
                    } else {
                        "Your focus protection is still active. Open Study Sprint to finish or resume the session."
                    }
                    "youtube_daily" -> "Your YouTube daily limit is reached. You can choose up to two five-minute bypasses each day."
                    "permission" -> "Finish Usage Access setup in App Limits before opening $label."
                    else -> "Your daily limit for $label is reached. Take a break or regain five more minutes."
                }
                BackHandler { home() }
                Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)
                    .testTag(if (reason == "focus") "blocked_focus_page" else "blocked_limit_page"),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(when (reason) {
                            "focus" -> "FOCUS PROTECTED"
                            "permission" -> "SET UP PROTECTION"
                            null -> "ACCESS RESTORED"
                            else -> "PAUSE & RESET"
                        }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(when (reason) {
                        "focus" -> "Stay focused"
                        "daily", "youtube_daily" -> "Daily limit reached"
                        "permission" -> "Protection setup needed"
                        else -> label
                    },
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(detail, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                    if (pkg == YouTubeQuota.PACKAGE) YouTubeAllowanceCard()
                    if (reason == null && launchApp != null) Button(onClick = {
                        startActivity(launchApp); finish()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open $label") }
                    if (reason == "daily" && pkg != YouTubeQuota.PACKAGE && launchApp != null) TextButton(onClick = {
                        val granted = StrictLimits.grantExtraTime(this@BlockedActivity, pkg, usage)
                        if (granted) {
                            StudyData.events(this@BlockedActivity).recordLimitEvent("bypass", pkg)
                            startActivity(launchApp); finish()
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("regain_app_time")) {
                        Text("Regain 5 minutes in $label")
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { home() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Return to home screen") }
                    OutlinedButton(onClick = {
                        val route = when {
                            reason == "focus" -> "focus"
                            reason == "daily" || reason == "youtube_daily" || reason == "permission" || reason == "checking" ||
                                pkg == YouTubeQuota.PACKAGE -> "limits"
                            else -> "focus"
                        }
                        startActivity(BlockNavigation.studyIntent(this@BlockedActivity, route))
                        finish()
                    }, modifier = Modifier.fillMaxWidth().testTag("open_study_sprint_from_blocked")) {
                        Text(if (reason == "focus") "Open focus timer to regain access" else "Open Study Sprint")
                    }
                }
            }
        }
    }
}
