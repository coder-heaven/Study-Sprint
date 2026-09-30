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
    val used by produceState<Long?>(null, revision) {
        while (isActive) {
            value = withContext(Dispatchers.IO) { DailyUsage.usedToday(context, YouTubeQuota.PACKAGE) }
            delay(5_000L)
        }
    }
    StudyCard {
        Text("YouTube · intentional breaks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("2 sessions per day · 5 minutes each", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { index ->
                Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                    color = if (quota.sessions > index) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer) {
                    Text("Session ${index + 1} · ${if (quota.sessions > index) "Used" else "Available"}",
                        modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(when {
            focus -> "Blocked until this focus session ends."
            !setup -> "Enable Usage Access and app blocking below to use this allowance."
            (used ?: 0L) >= 600_000L -> "Daily 10-minute allowance reached. Resets at midnight."
            remaining > 0L -> "${remaining / 60000}:${((remaining / 1000) % 60).toString().padStart(2, '0')} left in this window"
            quota.sessions >= 2 -> "Both sessions used. New sessions tomorrow."
            else -> "${2 - quota.sessions} session(s) left. The window keeps running if you leave YouTube."
        }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        Button(enabled = setup && !focus && !busy && used != null && used!! < 600_000L && (remaining > 0 || quota.sessions < 2),
            onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage(YouTubeQuota.PACKAGE)
                if (launch == null) { message = "The YouTube app is not installed."; return@Button }
                scope.launch {
                    busy = true
                    try {
                        val currentUse = withContext(Dispatchers.IO) { DailyUsage.usedToday(context, YouTubeQuota.PACKAGE) }
                        if (StrictLimits.startYouTube(context, currentUse)) {
                            revision++
                            context.startActivity(launch)
                        } else message = "No session available. Check permissions, focus lock and today's allowance."
                    } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(if (busy) "Checking allowance…" else if (remaining > 0) "Continue current session" else "Start 5-minute session")
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
