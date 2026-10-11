package com.pranav.study.cet_study_sprint

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen widgets backed by the same local preferences and history as the app. */
internal object StudyWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { refreshNow(app) }
    }

    private fun refreshNow(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val focus = ComponentName(context, FocusWidgetProvider::class.java)
        manager.getAppWidgetIds(focus).forEach { updateFocus(context, manager, it) }
        val progress = ComponentName(context, StudyProgressWidgetProvider::class.java)
        manager.getAppWidgetIds(progress).forEach { updateProgress(context, manager, it) }
    }

    internal fun updateFocus(context: Context, manager: AppWidgetManager, id: Int) {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val active = prefs.getBoolean("focus_active", false)
        val running = prefs.getBoolean("focus_running", false)
        val breakSession = prefs.getBoolean("focus_is_break", false)
        val configuredMinutes = if (breakSession) prefs.getInt("pomodoro_break", 5)
        else prefs.getInt("pomodoro_focus", 25)
        val storedSeconds = prefs.getInt("focus_remaining", configuredMinutes * 60)
        val remaining = if (active && running) {
            StudyTimeMath.remainingSeconds(prefs.getLong("focus_end_at", 0L), System.currentTimeMillis())
        } else storedSeconds.coerceAtLeast(0)
        val task = prefs.getString("focus_intention", "").orEmpty().trim()
        val status = when {
            active && breakSession -> if (running) "On break" else "Break paused"
            active && running -> "Focusing"
            active -> "Focus paused"
            else -> "Ready to focus"
        }
        val timer = if (active) formatDuration(remaining) else "$configuredMinutes min"
        val detail = when {
            task.isNotBlank() -> task
            active && breakSession -> "Take a short reset"
            active -> "Stay with this session"
            else -> "Choose a task when you start"
        }
        val views = RemoteViews(context.packageName, R.layout.widget_focus).apply {
            setTextViewText(R.id.widget_focus_status, status)
            setTextViewText(R.id.widget_focus_timer, timer)
            setTextViewText(R.id.widget_focus_task, detail)
            setTextViewText(R.id.widget_focus_action, if (active) "Open focus timer" else "Start focus")
            setOnClickPendingIntent(R.id.widget_focus_root, activityIntent(context, "focus", id * 2 + 1))
        }
        manager.updateAppWidget(id, views)
    }

    internal fun updateProgress(context: Context, manager: AppWidgetManager, id: Int) {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val totals = StudyData.events(context).totals(1)
        val tasks = orderedTasks(prefs)
        val goal = prefs.getInt("daily_focus_goal", 120).coerceAtLeast(1)
        val progress = (totals.focusedMinutes.toFloat() / goal).coerceIn(0f, 1f)
        val limitPrefs = StrictLimits.prefs(context)
        val savedLimited = limitPrefs.all.keys.count {
            it.startsWith("limit_") && !it.startsWith("limit_days_") &&
                limitPrefs.getInt(it, 0) > 0
        }
        val limited = savedLimited + if (
            YouTubeQuota.PACKAGE !in limitPrefs.all.keys.map { it.removePrefix("limit_") } &&
            StrictLimits.dailyLimit(limitPrefs, YouTubeQuota.PACKAGE) > 0
        ) 1 else 0
        val name = prefs.getString("profile_name", "").orEmpty().trim().ifBlank { "Student" }
        val next = tasks.firstOrNull() ?: "Add a next step in Plan"
        val views = RemoteViews(context.packageName, R.layout.widget_progress).apply {
            setTextViewText(R.id.widget_progress_title, "Hi, $name")
            setTextViewText(R.id.widget_progress_focus, "${totals.focusedMinutes} min focused today")
            setTextViewText(R.id.widget_progress_goal, "${(progress * 100).toInt()}% of $goal min goal")
            setProgressBar(R.id.widget_progress_bar, 100, (progress * 100).toInt(), false)
            setTextViewText(R.id.widget_progress_task, next)
            setTextViewText(R.id.widget_progress_limits, if (limited == 0) "No app limits set" else "$limited app limit${if (limited == 1) "" else "s"} active")
            setOnClickPendingIntent(R.id.widget_progress_root, activityIntent(context, "home", id * 2 + 2))
        }
        manager.updateAppWidget(id, views)
    }

    private fun activityIntent(context: Context, route: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            BlockNavigation.studyIntent(context, route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun formatDuration(seconds: Int): String =
        "%02d:%02d".format(seconds / 60, seconds % 60)
}

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { StudyWidgetUpdater.updateFocus(context.applicationContext, manager, it) }
            } finally {
                result.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        StudyWidgetUpdater.refresh(context)
    }
}

class StudyProgressWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { StudyWidgetUpdater.updateProgress(context.applicationContext, manager, it) }
            } finally {
                result.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        StudyWidgetUpdater.refresh(context)
    }
}
