package com.pranav.study.cet_study_sprint

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.*

@Composable
internal fun YouTubeAllowanceCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    LaunchedEffect(Unit) { while (isActive) { now = System.currentTimeMillis(); delay(1_000L) } }
    val quota = StrictLimits.quota(context, now)
    val remaining = quota.remaining(now, SystemClock.elapsedRealtime())
    val setup = remember(revision) { StrictLimits.usageAllowed(context) && StrictLimits.blockerEnabled(context) }
    val focus = StrictLimits.focusBlocked(StrictLimits.prefs(context), YouTubeQuota.PACKAGE, now)
    val day = LocalDay.start(now)
    LaunchedEffect(day) { message = "" }
    val measurement by produceState<DailyUsage.Measurement?>(null, revision, day) {
        while (isActive) {
            value = withContext(Dispatchers.IO) { DailyUsage.measurement(context, YouTubeQuota.PACKAGE) }
            delay(5_000L)
        }
    }
    val used = measurement?.takeIf { it.day == day }?.millis
    val prefs = StrictLimits.prefs(context)
    val limit = StrictLimits.dailyLimit(prefs, YouTubeQuota.PACKAGE)
    val applies = StrictLimits.appliesToday(prefs, YouTubeQuota.PACKAGE, now)
    val reached = used?.let { YouTubeQuota.limitReached(limit, applies, it) } ?: false
    StudyCard {
        Text("YouTube · extra time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("2 bypasses per day · 5 minutes each · after your daily limit", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { index ->
                Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                    color = if (quota.sessions > index) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer) {
                    Text("Bypass ${index + 1} · ${if (quota.sessions > index) "Used" else "Available"}",
                        modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(when {
            focus -> "Blocked until this focus session ends."
            !setup -> "Enable Usage Access and app blocking below to use this allowance."
            limit <= 0 -> "No daily limit. Tap YouTube in the app list to set one."
            !applies -> "No daily limit scheduled for today."
            used == null -> "Checking today's usage…"
            !reached -> "${used / 60000}m of ${limit}m used. Bypasses unlock after your daily limit."
            remaining > 0L -> "${remaining / 60000}:${((remaining / 1000) % 60).toString().padStart(2, '0')} left in this bypass"
            quota.sessions >= 2 -> "Both bypasses used. Your daily limit and bypasses reset at midnight."
            else -> "Daily limit reached. ${2 - quota.sessions} bypass(es) left. Each window keeps running if you leave YouTube."
        }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        Button(enabled = setup && !focus && !busy && reached && (remaining > 0 || quota.sessions < 2),
            onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage(YouTubeQuota.PACKAGE)
                if (launch == null) { message = "The YouTube app is not installed."; return@Button }
                scope.launch {
                    busy = true
                    try {
                        val currentUse = withContext(Dispatchers.IO) { DailyUsage.measurement(context, YouTubeQuota.PACKAGE) }
                        if (StrictLimits.startYouTube(context, currentUse)) {
                            revision++
                            context.startActivity(launch)
                        } else message = "No bypass available. Check your daily limit, permissions and focus lock."
                    } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(if (busy) "Checking usage…" else if (reached && remaining > 0) "Continue current bypass" else "Use 5-minute bypass")
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
