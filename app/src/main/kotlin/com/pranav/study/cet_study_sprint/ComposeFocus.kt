package com.pranav.study.cet_study_sprint

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val blockApps: Boolean = false, val completedMinutes: Int? = null
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
            blockApps = prefs.getBoolean("focus_block_enabled", false)
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
        val end = System.currentTimeMillis() + seconds * 1000L
        _state.value = state.value.copy(active = true, running = true, isBreak = isBreak,
            remainingSeconds = seconds, completedMinutes = null)
        prefs.edit().putBoolean("focus_active", true).putBoolean("focus_running", true)
            .putBoolean("focus_is_break", isBreak).putInt("focus_remaining", seconds)
            .putLong("focus_end_at", end).apply()
        setBlocking(!isBreak, end)
    }
    fun pause() {
        val current = state.value
        if (!current.active || !current.running) return
        val seconds = StudyTimeMath.remainingSeconds(prefs.getLong("focus_end_at", 0), System.currentTimeMillis())
        if (seconds == 0) { _state.value = current.copy(remainingSeconds = 0); finish(); return }
        _state.value = current.copy(running = false, remainingSeconds = seconds)
        prefs.edit().putBoolean("focus_running", false).putInt("focus_remaining", seconds).apply()
        setBlocking(false, 0)
    }
    fun resume() {
        if (!state.value.active || state.value.running) return
        val end = System.currentTimeMillis() + state.value.remainingSeconds * 1000L
        _state.value = state.value.copy(running = true)
        prefs.edit().putBoolean("focus_running", true).putLong("focus_end_at", end).apply()
        setBlocking(!state.value.isBreak, end)
    }
    fun finish() {
        val current = state.value
        if (!current.active) return
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
    fun clearCompletion() { _state.value = state.value.copy(completedMinutes = null) }
    private fun tick() {
        val current = state.value
        if (!current.active || !current.running) return
        val seconds = StudyTimeMath.remainingSeconds(prefs.getLong("focus_end_at", 0), System.currentTimeMillis())
        if (seconds != current.remainingSeconds) _state.value = current.copy(remainingSeconds = seconds)
        if (seconds == 0) finish()
    }
    private fun setBlocking(active: Boolean, end: Long) {
        blocker.edit().putBoolean("focus_block_active", active && state.value.blockApps)
            .putLong("focus_block_end", end).apply()
    }
}

@Composable
internal fun FocusScreen(
    prefs: android.content.SharedPreferences,
    onBack: () -> Unit,
    onLegacy: (String) -> Unit
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
    val model: FocusViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf(false) }
    var customBreak by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    val phaseSeconds = if (state.isBreak) state.breakMinutes * 60 else state.focusMinutes * 60
    val progress = 1f - state.remainingSeconds.toFloat() / phaseSeconds.coerceAtLeast(1)
    val ringTrack = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    val ringColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppHeading(if (state.isBreak) "Break time" else if (state.active) "Focus in progress" else "Focus",
            if (state.isBreak) "Rest, then return refreshed." else if (state.active) "Stay with this one task." else "Make time for what matters.")
        Spacer(Modifier.height(24.dp))
        if (state.completedMinutes != null) {
            StudyCard {
                Text("Session complete", style = MaterialTheme.typography.titleLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(if (state.completedMinutes == 0) "No study time recorded. Try again when you're ready."
                     else "${state.completedMinutes} min focused", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Today: ${today.focusedMinutes} min • ${today.sessions} sessions",
                    style = MaterialTheme.typography.bodySmall)
                Text("Current streak: $streak days", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::clearCompletion) { Text("Done") }
                    OutlinedButton(onClick = model::startBreak) { Text("Start ${state.breakMinutes} min break") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (!state.active) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Block distracting apps", modifier = Modifier.weight(1f), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                Switch(state.blockApps, onCheckedChange = model::blocking)
            }
            TextButton(onClick = { onLegacy("limits") }) { Text("Manage blocked apps and daily limits") }
        } else {
            Text(if (state.isBreak) "Take a short rest" else state.task.ifBlank { "Study session" }, style = MaterialTheme.typography.titleMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            if (!state.isBreak) Text(state.subject, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(25.dp))
        Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 11.dp.toPx()
                drawCircle(ringTrack, style = Stroke(stroke))
                drawArc(ringColor, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60),
                    fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                Text(if (state.active) if (state.running) if (state.isBreak) "On break" else "Focusing" else "Paused" else "Ready to begin", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(30.dp))
        Button(onClick = { when { !state.active -> model.start(); state.running -> model.pause(); else -> model.resume() } },
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text(when { !state.active -> "Start Focus"; state.running -> "Pause"; else -> "Resume" })
        }
        if (state.active) TextButton(onClick = model::finish) { Text(if (state.isBreak) "End break" else "Finish session") }
        Spacer(Modifier.height(20.dp))
    }
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
