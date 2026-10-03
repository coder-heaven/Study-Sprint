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
            focusSelected(prefs, pkg)

    /** Focus protects every limited app, plus apps selected for focus only. */
    fun focusSelected(prefs: SharedPreferences, pkg: String): Boolean =
        pkg == YouTubeQuota.PACKAGE || dailyLimit(prefs, pkg) > 0 || prefs.getBoolean("focus_block_$pkg", false)

    fun dailyLimit(p: SharedPreferences, pkg: String): Int =
        p.getInt("limit_$pkg", if (pkg == YouTubeQuota.PACKAGE) YouTubeQuota.DEFAULT_LIMIT_MINUTES else 0)

    fun appliesToday(p: SharedPreferences, pkg: String, now: Long): Boolean {
        val weekday = StudyTimeMath.weekdayIndex(Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK))
        return p.getInt("limit_days_$pkg", 127) and (1 shl weekday) != 0
    }

    @Synchronized fun quota(context: Context, now: Long = System.currentTimeMillis()): YouTubeQuota {
        val p = prefs(context)
        // Separate from the old pre-limit sessions so upgrading does not consume bypasses.
        val old = YouTubeQuota(p.getLong("youtube_bypass_day", 0), p.getInt("youtube_bypass_sessions", 0),
            p.getLong("youtube_bypass_started", 0), p.getLong("youtube_bypass_elapsed", 0))
        val current = old.forDay(DailyUsage.startOfLocalDay(now))
        if (current != old) writeQuota(p, current)
        return current
    }
    private fun writeQuota(p: SharedPreferences, value: YouTubeQuota): Boolean = p.edit()
        .putLong("youtube_bypass_day", value.day).putInt("youtube_bypass_sessions", value.sessions)
        .putLong("youtube_bypass_started", value.startedAt).putLong("youtube_bypass_elapsed", value.startedElapsed).commit()

    @Synchronized fun startYouTube(context: Context, usage: DailyUsage.Measurement): Boolean {
        val now = System.currentTimeMillis()
        applyPending(context, now)
        val p = prefs(context)
        val pkg = YouTubeQuota.PACKAGE
        if (usage.packageName != pkg || usage.day != LocalDay.start(now) || !usageAllowed(context) ||
            !blockerEnabled(context) || focusBlocked(p, pkg, now)) return false
        val next = quota(context, now).startAfterLimit(dailyLimit(p, pkg), appliesToday(p, pkg, now),
            usage.millis, LocalDay.start(now), now, SystemClock.elapsedRealtime()) ?: return false
        return writeQuota(prefs(context), next)
    }
    fun remainingYouTube(context: Context, now: Long = System.currentTimeMillis()): Long =
        quota(context, now).remaining(now, SystemClock.elapsedRealtime())

    /** Regular app edits apply now; an active focus lock lasts until its session ends. */
    @Synchronized fun save(context: Context, pkg: String, minutes: Int, days: Int, focus: Boolean): Boolean {
        val p = prefs(context)
        applyPending(context)
        val weaker = focusBlocked(p, pkg) && minutes <= 0 && !focus && pkg != YouTubeQuota.PACKAGE
        val edit = p.edit()
        if (weaker) {
            edit.putLong("pending_at_$pkg", p.getLong("focus_block_end", System.currentTimeMillis()))
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

    /** The classic five-minute extension is for regular daily limits only. */
    @Synchronized fun grantExtraTime(context: Context, pkg: String, usage: DailyUsage.Measurement?,
        now: Long = System.currentTimeMillis()): Boolean {
        if (pkg == YouTubeQuota.PACKAGE || blockReason(context, pkg, usage, now) != "daily") return false
        return prefs(context).edit().putLong("bypass_until_$pkg", minOf(now + 300_000L, LocalDay.next(now))).commit()
    }

    /** A pre-midnight query must never decide whether an app is blocked after midnight. */
    fun blockReason(context: Context, pkg: String, usage: DailyUsage.Measurement?, now: Long): String? {
        val p = prefs(context)
        if (focusBlocked(p, pkg, now)) return "focus"
        val youtube = pkg == YouTubeQuota.PACKAGE
        val limit = dailyLimit(p, pkg)
        val daily = limit > 0 && appliesToday(p, pkg, now)
        if (!daily) return null
        if (!youtube && now < p.getLong("bypass_until_$pkg", 0L)) return null
        if (!usageAllowed(context)) return "permission"
        if (usage == null || usage.packageName != pkg || usage.day != LocalDay.start(now)) return "checking"
        if (!YouTubeQuota.limitReached(limit, daily, usage.millis)) return null
        if (youtube) return if (remainingYouTube(context, now) > 0L) null else "youtube_daily"
        return "daily"
    }
}
