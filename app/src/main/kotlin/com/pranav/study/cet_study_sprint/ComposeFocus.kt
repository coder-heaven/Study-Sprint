package com.pranav.study.cet_study_sprint

import android.app.Application
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class FocusState(
    val task: String = "", val subject: String = "General",
    val focusMinutes: Int = 25, val breakMinutes: Int = 5,
    val remainingSeconds: Int = 1500, val active: Boolean = false,
    val running: Boolean = false, val isBreak: Boolean = false,
    val blockApps: Boolean = true, val completedMinutes: Int? = null
)

internal class FocusViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
    private val blocker = app.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE)
    private val events = StudyData.events(app)
    private val _state = MutableStateFlow(
        FocusState(
            task = prefs.getString("focus_intention", "").orEmpty(),
            subject = prefs.getString("focus_subject", "General").orEmpty(),
            focusMinutes = prefs.getInt("pomodoro_focus", 25),
            breakMinutes = prefs.getInt("pomodoro_break", 5),
            remainingSeconds = prefs.getInt("focus_remaining", prefs.getInt("pomodoro_focus", 25) * 60),
            active = prefs.getBoolean("focus_active", false),
            running = prefs.getBoolean("focus_running", false),
            isBreak = prefs.getBoolean("focus_is_break", false),
            blockApps = prefs.getBoolean("focus_block_enabled", true)
        )
    )
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            while (true) { tick(); delay(1000) }
        }
    }
    fun task(value: String) {
        _state.value = state.value.copy(task = value)
        prefs.edit().putString("focus_intention", value).apply()
    }
    fun subject(value: String) {
        _state.value = state.value.copy(subject = value)
        prefs.edit().putString("focus_subject", value).apply()
    }
    fun duration(value: Int) {
        if (state.value.active) return
        _state.value = state.value.copy(focusMinutes = value, remainingSeconds = value * 60)
        prefs.edit().putInt("pomodoro_focus", value).apply()
    }
    fun breakDuration(value: Int) {
        if (state.value.active) return
        _state.value = state.value.copy(breakMinutes = value)
        prefs.edit().putInt("pomodoro_break", value).apply()
    }
    fun blocking(value: Boolean) {
        _state.value = state.value.copy(blockApps = value)
        prefs.edit().putBoolean("focus_block_enabled", value).apply()
    }
    fun start() = begin(state.value.focusMinutes * 60, isBreak = false)
    fun startBreak() = begin(state.value.breakMinutes * 60, isBreak = true)
    private fun begin(seconds: Int, isBreak: Boolean) {
        if (!isBreak && state.value.blockApps && !StrictLimits.blockerEnabled(getApplication())) return
        val end = System.currentTimeMillis() + seconds * 1000L
        _state.value = state.value.copy(active = true, running = true, isBreak = isBreak,
            remainingSeconds = seconds, completedMinutes = null)
        prefs.edit().putBoolean("focus_active", true).putBoolean("focus_running", true)
            .putBoolean("focus_is_break", isBreak).putInt("focus_remaining", seconds)
            .putLong("focus_end_at", end).putBoolean("focus_alarm_delivered", false).apply()
        FocusAlarm.schedule(getApplication(), end)
        setBlocking(!isBreak, end)
    }
    fun pause() {
        val current = state.value
        if (!current.active || !current.running) return
        val seconds = StudyTimeMath.remainingSeconds(prefs.getLong("focus_end_at", 0), System.currentTimeMillis())
        if (seconds == 0) { _state.value = current.copy(remainingSeconds = 0); finish(true); return }
        _state.value = current.copy(running = false, remainingSeconds = seconds)
        prefs.edit().putBoolean("focus_running", false).putInt("focus_remaining", seconds).apply()
        FocusAlarm.cancel(getApplication())
        // A pause does not turn a committed distraction block into a bypass.
        setBlocking(!current.isBreak, Long.MAX_VALUE)
    }
    fun resume() {
        if (!state.value.active || state.value.running) return
        val end = System.currentTimeMillis() + state.value.remainingSeconds * 1000L
        _state.value = state.value.copy(running = true)
        prefs.edit().putBoolean("focus_running", true).putLong("focus_end_at", end).apply()
        FocusAlarm.schedule(getApplication(), end)
        setBlocking(!state.value.isBreak, end)
    }
    fun finish(completedNaturally: Boolean = false) {
        val current = state.value
        if (!current.active) return
        FocusAlarm.cancel(getApplication())
        if (completedNaturally && !prefs.getBoolean("focus_alarm_delivered", false)) {
            prefs.edit().putBoolean("focus_alarm_delivered", true).apply()
            FocusAlarm.notifyFinished(getApplication(), current.isBreak)
        }
        val elapsed = if (current.isBreak) 0
            else (current.focusMinutes * 60 - current.remainingSeconds).coerceAtLeast(0)
        if (!current.isBreak && elapsed >= 60) {
            events.recordFocus(elapsed * 1000L, current.subject)
            prefs.edit().putInt("focus_sessions", prefs.getInt("focus_sessions", 0) + 1).apply()
        }
        _state.value = current.copy(active = false, running = false, isBreak = false,
            completedMinutes = if (current.isBreak) null else if (elapsed >= 60) elapsed / 60 else 0,
            remainingSeconds = current.focusMinutes * 60)
        prefs.edit().putBoolean("focus_active", false).putBoolean("focus_running", false)
            .putBoolean("focus_is_break", false).putInt("focus_remaining", current.focusMinutes * 60).apply()
        setBlocking(false, 0)
    }
    fun clearCompletion() { FocusAlarm.dismiss(getApplication()); _state.value = state.value.copy(completedMinutes = null) }
    private fun tick() {
        val current = state.value
        if (!current.active || !current.running) return
        val seconds = StudyTimeMath.remainingSeconds(prefs.getLong("focus_end_at", 0), System.currentTimeMillis())
        if (seconds != current.remainingSeconds) _state.value = current.copy(remainingSeconds = seconds)
        if (seconds == 0) finish(true)
    }
    private fun setBlocking(active: Boolean, end: Long) {
        val enabled = active && state.value.blockApps
        if (!enabled && blocker.getBoolean("focus_block_active", false)) StrictLimits.endFocus(getApplication())
        else blocker.edit().putBoolean("focus_block_active", enabled).putLong("focus_block_end", end).apply()
    }
}

@Composable
internal fun FocusScreen(
    prefs: android.content.SharedPreferences,
    onBack: () -> Unit,
    onLegacy: (String) -> Unit,
    model: FocusViewModel = viewModel(),
    onHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val history = remember { StudyData.events(context) }
    val historyRevision by history.revision.collectAsStateWithLifecycle()
    val today by produceState(StudyTotals(), historyRevision) {
        value = withContext(Dispatchers.IO) { history.totals(1) }
    }
    val streak by produceState(0, historyRevision) {
        value = withContext(Dispatchers.IO) { history.streak() }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.start() }
    val exactAllowed = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val state by model.state.collectAsStateWithLifecycle()
    val fullScreenAllowed = FocusAlarm.canOpenFullScreen(context)
    var protectionRevision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { protectionRevision++ }
    LaunchedEffect(Unit) { while (true) { delay(1_000L); protectionRevision++ } }
    val blockingReady = remember(protectionRevision) { StrictLimits.blockerEnabled(context) }
    val usageReady = remember(protectionRevision) { StrictLimits.usageAllowed(context) }
    var needsBlocking by remember { mutableStateOf(false) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var customBreak by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    val phaseSeconds = if (state.isBreak) state.breakMinutes * 60 else state.focusMinutes * 60
    val progress = 1f - state.remainingSeconds.toFloat() / phaseSeconds.coerceAtLeast(1)
    val ringColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Keep the primary action visible on short screens while retaining the
    // full-size centered ring on taller devices.
    val compact = maxHeight < 520.dp
    val ringSize = (maxHeight - if (compact) 280.dp else 330.dp).coerceIn(100.dp, 300.dp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FocusIqHeader(prefs, "Your focus space", onLegacy)
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureArtwork(R.drawable.art_focus_3d, if (compact) 36.dp else 40.dp)
            Text(if (state.isBreak) "BREAK" else "TIMER", fontSize = if (compact) 28.sp else 32.sp,
                lineHeight = if (compact) 36.sp else 40.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        if (state.completedMinutes != null) {
            StudyCard {
                Text("Session complete", style = MaterialTheme.typography.titleLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(if (state.completedMinutes == 0) "No study time recorded. Try again when you're ready."
                     else "${state.completedMinutes} min focused", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Today: ${today.focusedMinutes} min • ${today.sessions} sessions",
                    style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.art_streak_flame_3d), null, Modifier.size(28.dp))
                    Text("Current streak: $streak days", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::clearCompletion) { Text("Done") }
                    OutlinedButton(onClick = model::startBreak) { Text("Start ${state.breakMinutes} min break") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (!state.active) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 45, 60, 90).forEach { minutes ->
                FilterChip(selected = state.focusMinutes == minutes, onClick = { model.duration(minutes) },
                    modifier = Modifier.weight(1f),
                    label = { Text("$minutes", fontSize = 12.sp, maxLines = 1) })
            }
        }
        BoxWithConstraints(Modifier.widthIn(max = ringSize).fillMaxWidth().aspectRatio(1f)
            .testTag("focus_clock"), contentAlignment = Alignment.Center) {
            val timerFontSize = (maxWidth.value * 0.23f).coerceIn(22f, 56f).sp
            val statusFontSize = (maxWidth.value * 0.065f).coerceIn(11f, 14f).sp
            val faceColor = MaterialTheme.colorScheme.surface
            val trackColor = MaterialTheme.colorScheme.primaryContainer
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = 8.dp.toPx() + stroke / 2f
                val diameter = size.minDimension - 2f * inset
                val origin = androidx.compose.ui.geometry.Offset(
                    (size.width - diameter) / 2f, (size.height - diameter) / 2f)
                drawCircle(color = faceColor, radius = diameter / 2f)
                drawCircle(color = trackColor, radius = diameter / 2f, style = Stroke(stroke))
                if (progress > 0f) drawArc(ringColor, startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                    topLeft = origin, size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Column(Modifier.widthIn(max = maxWidth * 0.76f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("%02d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60),
                    modifier = Modifier.testTag("focus_countdown"),
                    fontSize = timerFontSize, lineHeight = timerFontSize * 1.1f,
                    maxLines = 1, fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (state.active) if (state.running) if (state.isBreak) "On break" else "Focusing" else "Paused" else "Ready",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = statusFontSize,
                    maxLines = 1, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        FocusIqButton(when { !state.active -> "Start Focus"; state.running -> "Pause"; else -> "Resume" },
            Modifier.testTag("focus_start")) {
            when {
                !state.active && state.blockApps && !StrictLimits.blockerEnabled(context) -> needsBlocking = true
                !state.active && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                !state.active -> model.start()
                state.running -> model.pause()
                else -> model.resume()
            }
        }
        Spacer(Modifier.height(16.dp))
        StudyCard(Modifier.testTag("focus_protection_card")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureArtwork(R.drawable.art_protection_3d, 40.dp)
                Column(Modifier.weight(1f)) {
                    Text("Block distracting apps", style = MaterialTheme.typography.titleSmall)
                    Text("Limited apps + focus-only selections", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(state.blockApps, onCheckedChange = model::blocking, enabled = !state.active,
                    modifier = Modifier.testTag("focus_block_toggle"))
            }
            Text(if (blockingReady) "App blocking is ready" else "App blocking needs setup",
                color = if (blockingReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("focus_protection_status"))
            if (!usageReady) Text("Usage Access is needed for daily limits", style = MaterialTheme.typography.bodySmall)
            if (!blockingReady || !usageReady) TextButton(onClick = { onLegacy("limits") }) { Text("Set up protection") }
        }
        Spacer(Modifier.height(20.dp))
        if (state.active && state.blockApps && !state.isBreak) Text("App blocking stays active while paused.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.active) TextButton(onClick = { model.finish() }) { Text(if (state.isBreak) "End break" else "Finish session") }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { onLegacy("limits") }) { Text("App limits") }
            TextButton(onClick = onHistory) { Text("History") }
        }
        if (!state.active) TextButton(onClick = { optionsExpanded = !optionsExpanded }) {
            Text(if (optionsExpanded) "Hide session settings" else "Session settings · task, breaks & blocking")
        }
        if (!state.active && optionsExpanded) {
            OutlinedTextField(state.task, model::task, label = { Text("What are you studying?") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.subject, model::subject, label = { Text("Subject (optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionLabel("Focus duration")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(25, 45, 60, 90).forEach { minutes ->
                    FilterChip(selected = state.focusMinutes == minutes, onClick = { model.duration(minutes) },
                        label = { Text("$minutes") })
                }
            }
            TextButton(onClick = { customText = state.focusMinutes.toString(); custom = true }) { Text("Custom timing") }
            SectionLabel("Break duration")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15).forEach { minutes ->
                    FilterChip(selected = state.breakMinutes == minutes, onClick = { model.breakDuration(minutes) },
                        label = { Text("$minutes min") })
                }
            }
            TextButton(onClick = { customText = state.breakMinutes.toString(); customBreak = true }) {
                Text("Custom break")
            }
            TextButton(onClick = { onLegacy("limits") }) { Text("Manage blocked apps and daily limits") }
            if (!exactAllowed) TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }) { Text("Allow precise timer alerts in Settings") }
        } else if (state.active) {
            Text(if (state.isBreak) "Take a short rest" else state.task.ifBlank { "Study session" }, style = MaterialTheme.typography.titleMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            if (!state.isBreak) Text(state.subject, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!state.active && !fullScreenAllowed) TextButton(onClick = {
            context.startActivity(FocusAlarm.fullScreenSettings(context))
        }) { Text("Allow full-screen timer alarms") }
        Spacer(Modifier.height(20.dp))
    }
    }
    if (needsBlocking) AlertDialog(onDismissRequest = { needsBlocking = false },
        title = { Text("Enable app blocking") },
        text = { Text("Turn on Study Sprint app limits in Android Accessibility before starting a protected focus session.") },
        confirmButton = { TextButton(onClick = { needsBlocking = false; onLegacy("limits") }) { Text("Set up blocking") } },
        dismissButton = { TextButton(onClick = { needsBlocking = false; model.blocking(false); model.start() }) { Text("Focus without blocking") } })
    if (custom || customBreak) AlertDialog(onDismissRequest = { custom = false; customBreak = false },
        title = { Text(if (customBreak) "Custom break duration" else "Custom focus duration") },
        text = { OutlinedTextField(customText, { customText = it.filter(Char::isDigit) },
            label = { Text(if (customBreak) "Minutes (1–60)" else "Minutes (5–180)") }) },
        confirmButton = { TextButton(onClick = {
            customText.toIntOrNull()?.let { minutes ->
                if (customBreak) model.breakDuration(minutes.coerceIn(1, 60))
                else model.duration(minutes.coerceIn(5, 180))
            }
            custom = false; customBreak = false
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { custom = false; customBreak = false }) { Text("Cancel") } })
}
