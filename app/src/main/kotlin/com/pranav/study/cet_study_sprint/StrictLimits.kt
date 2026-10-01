package com.pranav.study.cet_study_sprint

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock
import java.util.Calendar

internal object StrictLimits {
    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE)
    fun usageAllowed(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun blockerEnabled(context: Context): Boolean = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == StudyBlockerService::class.java.name }

    fun focusBlocked(prefs: SharedPreferences, pkg: String, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getBoolean("focus_block_active", false) && now < prefs.getLong("focus_block_end", 0L) &&
            (pkg == YouTubeQuota.PACKAGE || prefs.getBoolean("focus_block_$pkg", false))

    @Synchronized fun quota(context: Context, now: Long = System.currentTimeMillis()): YouTubeQuota {
        val p = prefs(context)
        val old = YouTubeQuota(p.getLong("youtube_day", 0), p.getInt("youtube_sessions", 0),
            p.getLong("youtube_started", 0), p.getLong("youtube_elapsed", 0))
        val current = old.forDay(DailyUsage.startOfLocalDay(now))
        if (current != old) writeQuota(p, current)
        return current
    }
    private fun writeQuota(p: SharedPreferences, value: YouTubeQuota): Boolean = p.edit()
        .putLong("youtube_day", value.day).putInt("youtube_sessions", value.sessions)
        .putLong("youtube_started", value.startedAt).putLong("youtube_elapsed", value.startedElapsed).commit()

    @Synchronized fun startYouTube(context: Context, usage: DailyUsage.Measurement): Boolean {
        val now = System.currentTimeMillis()
        if (usage.day != LocalDay.start(now) || !usageAllowed(context) || !blockerEnabled(context) || focusBlocked(prefs(context), YouTubeQuota.PACKAGE, now) || usage.millis >= 600_000L) return false
        val next = quota(context, now).start(DailyUsage.startOfLocalDay(now), now, SystemClock.elapsedRealtime()) ?: return false
        return writeQuota(prefs(context), next)
    }
    fun remainingYouTube(context: Context, now: Long = System.currentTimeMillis()): Long =
        quota(context, now).remaining(now, SystemClock.elapsedRealtime())

    /** Weaker policies are queued, so increasing a reached limit cannot unlock an app today. */
    @Synchronized fun save(context: Context, pkg: String, minutes: Int, days: Int, focus: Boolean): Boolean {
        val p = prefs(context)
        applyPending(context)
        val oldMinutes = p.getInt("limit_$pkg", 0)
        val oldDays = p.getInt("limit_days_$pkg", 127)
        val weaker = StrictLimitPolicy.mustDefer(oldMinutes, oldDays, minutes, days, focusBlocked(p, pkg), focus)
        val edit = p.edit()
        if (weaker) {
            edit.putLong("pending_at_$pkg", LocalDay.next(System.currentTimeMillis()))
                .putInt("pending_minutes_$pkg", minutes).putInt("pending_days_$pkg", days)
                .putBoolean("pending_focus_$pkg", focus)
        } else {
            edit.putInt("limit_$pkg", minutes).putInt("limit_days_$pkg", days).putBoolean("focus_block_$pkg", focus)
                .remove("pending_at_$pkg").remove("pending_minutes_$pkg").remove("pending_days_$pkg").remove("pending_focus_$pkg")
        }
        edit.remove("bypass_until_$pkg").commit()
        return weaker
    }
    @Synchronized fun applyPending(context: Context, now: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val due = p.all.keys.filter { it.startsWith("pending_at_") && now >= p.getLong(it, Long.MAX_VALUE) }
        if (due.isEmpty()) return
        val edit = p.edit()
        due.forEach { key ->
            val pkg = key.removePrefix("pending_at_")
            // A focus lock continues until its session ends, even across midnight.
            if (focusBlocked(p, pkg, now) && !p.getBoolean("pending_focus_$pkg", false)) return@forEach
            edit.putInt("limit_$pkg", p.getInt("pending_minutes_$pkg", 0))
                .putInt("limit_days_$pkg", p.getInt("pending_days_$pkg", 127))
                .putBoolean("focus_block_$pkg", p.getBoolean("pending_focus_$pkg", false))
                .remove(key).remove("pending_minutes_$pkg").remove("pending_days_$pkg").remove("pending_focus_$pkg")
        }
        edit.commit()
    }

    /** A pre-midnight query must never decide whether an app is blocked after midnight. */
    fun blockReason(context: Context, pkg: String, usage: DailyUsage.Measurement?, now: Long): String? {
        val p = prefs(context)
        if (focusBlocked(p, pkg, now)) return "focus"
        val youtube = pkg == YouTubeQuota.PACKAGE
        val weekday = StudyTimeMath.weekdayIndex(Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK))
        val limit = p.getInt("limit_$pkg", 0)
        val daily = !youtube && limit > 0 && p.getInt("limit_days_$pkg", 127) and (1 shl weekday) != 0
        if (!daily && !youtube) return null
        if (!usageAllowed(context)) return "permission"
        if (usage == null || usage.day != LocalDay.start(now)) return "checking"
        return when {
            youtube && usage.millis >= 600_000L -> "youtube_daily"
            daily && usage.millis >= limit * 60_000L -> "daily"
            youtube && remainingYouTube(context, now) == 0L -> "youtube_session"
            else -> null
        }
    }
}
