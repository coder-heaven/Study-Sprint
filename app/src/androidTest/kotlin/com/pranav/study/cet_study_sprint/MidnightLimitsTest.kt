package com.pranav.study.cet_study_sprint

import android.content.Context
import android.app.AppOpsManager
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class MidnightLimitsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun usageMode(mode: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS $mode")
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    @Test fun oldDayOrAnotherAppsUsageCannotBlockTodaysFreshAllowance() {
        val p = StrictLimits.prefs(context)
        val pkg = "test.midnight.measurement"
        val mode = context.getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        val midnight = LocalDay.start(System.currentTimeMillis())
        try {
            usageMode("allow")
            assertTrue(StrictLimits.usageAllowed(context))
            p.edit().putInt("limit_$pkg", 10).putInt("limit_days_$pkg", 127).commit()
            assertEquals("checking", StrictLimits.blockReason(context, pkg,
                DailyUsage.Measurement(LocalDay.start(midnight - 1), 900_000, pkg), midnight))
            assertEquals("checking", StrictLimits.blockReason(context, pkg,
                DailyUsage.Measurement(midnight, 900_000, "other.app"), midnight))
            assertEquals("daily", StrictLimits.blockReason(context, pkg,
                DailyUsage.Measurement(midnight, 600_000, pkg), midnight))
            assertNull(StrictLimits.blockReason(context, pkg,
                DailyUsage.Measurement(midnight, 0, pkg), midnight))
        } finally {
            p.edit().remove("limit_$pkg").remove("limit_days_$pkg").commit()
            usageMode(when (mode) {
                AppOpsManager.MODE_ALLOWED -> "allow"
                AppOpsManager.MODE_IGNORED -> "ignore"
                AppOpsManager.MODE_ERRORED -> "deny"
                else -> "default"
            })
        }
    }

    @Test fun storedYoutubeQuotaResetsAtMidnightAndSurvivesReadingAgain() {
        val p = StrictLimits.prefs(context)
        val keys = listOf("youtube_day", "youtube_sessions", "youtube_started", "youtube_elapsed")
        val previous = p.all.filterKeys { it in keys }
        val midnight = LocalDay.start(System.currentTimeMillis())
        try {
            p.edit().putLong("youtube_day", LocalDay.start(midnight - 1)).putInt("youtube_sessions", 2)
                .putLong("youtube_started", midnight - 400_000).putLong("youtube_elapsed", 1).commit()
            assertEquals(2, StrictLimits.quota(context, midnight - 1).sessions)
            assertEquals(0, StrictLimits.quota(context, midnight).sessions)
            assertEquals(midnight, p.getLong("youtube_day", 0))
            assertEquals(0, StrictLimits.quota(context, midnight + 1).sessions)
        } finally {
            val edit = p.edit(); keys.forEach(edit::remove)
            previous.forEach { (key, value) -> if (value is Long) edit.putLong(key, value) else if (value is Int) edit.putInt(key, value) }
            edit.commit()
        }
    }

    @Test fun pendingLimitAppliesAtMidnightWithoutReopeningSettings() {
        val p = StrictLimits.prefs(context)
        val pkg = "test.midnight.limit"
        val keys = listOf("limit_", "limit_days_", "focus_block_", "pending_at_", "pending_minutes_", "pending_days_", "pending_focus_").map { it + pkg }
        val midnight = LocalDay.start(System.currentTimeMillis())
        try {
            p.edit().putInt("limit_$pkg", 15).putLong("pending_at_$pkg", midnight)
                .putInt("pending_minutes_$pkg", 30).putInt("pending_days_$pkg", 127).putBoolean("pending_focus_$pkg", false).commit()
            StrictLimits.applyPending(context, midnight - 1)
            assertEquals(15, p.getInt("limit_$pkg", 0))
            StrictLimits.applyPending(context, midnight)
            assertEquals(30, p.getInt("limit_$pkg", 0))
            assertFalse(p.contains("pending_at_$pkg"))
        } finally { val edit = p.edit(); keys.forEach(edit::remove); edit.commit() }
    }
}
