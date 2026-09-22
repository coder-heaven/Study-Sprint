package com.pranav.study.cet_study_sprint

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class StudyBlockerService : AccessibilityService() {
    companion object { const val PREFS = "study_blocker" }
    private var lastBlockAt = 0L
    private var lastPackage = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in Protection.packages(this)) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("bypass_until_$pkg", 0)) return
        val focus = prefs.getBoolean("focus_block_active", false) &&
            now < prefs.getLong("focus_block_end", 0) &&
            prefs.getBoolean("focus_block_$pkg", false)
        val limit = prefs.getInt("limit_$pkg", 0)
        val weekday = StudyTimeMath.weekdayIndex(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        val appliesToday = prefs.getInt("limit_days_$pkg", 127) and (1 shl weekday) != 0
        val daily = appliesToday && limit > 0 && usedToday(pkg) >= limit * 60_000L
        if (!focus && !daily) return
        if (pkg == lastPackage && now - lastBlockAt < 1500) return
        lastPackage = pkg
        lastBlockAt = now
        val history = StudyData.events(this)
        if (daily) {
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
            if (prefs.getString("last_reached_$pkg", null) != day) {
                prefs.edit().putString("last_reached_$pkg", day).apply()
                history.recordLimitEvent("limit_reached", pkg)
            }
        }
        history.recordLimitEvent("blocked", pkg)
        startActivity(Intent(this, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("package", pkg).putExtra("focus_block", focus)
            .putExtra("limit_minutes", limit))
    }

    private fun usedToday(target: String): Long = DailyUsage.usedToday(this, target)
    override fun onInterrupt() = Unit
}
