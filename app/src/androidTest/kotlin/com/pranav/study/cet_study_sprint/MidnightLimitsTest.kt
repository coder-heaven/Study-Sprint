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

    private fun withClassicLimit(pkg: String = "test.classic.limit",
                                 test: (android.content.SharedPreferences, String, Long) -> Unit) {
        val p = StrictLimits.prefs(context)
        val keys = listOf("limit_$pkg", "limit_days_$pkg", "focus_block_$pkg", "bypass_until_$pkg",
            "pending_at_$pkg", "pending_minutes_$pkg", "pending_days_$pkg", "pending_focus_$pkg",
            "focus_block_active", "focus_block_end", "youtube_bypass_day", "youtube_bypass_sessions",
            "youtube_bypass_started", "youtube_bypass_elapsed")
        val previous = p.all.filterKeys { it in keys }
        val mode = context.getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        try {
            usageMode("allow")
            p.edit().putBoolean("focus_block_active", false).putInt("limit_$pkg", 10)
                .putInt("limit_days_$pkg", 127).commit()
            test(p, pkg, System.currentTimeMillis())
        } finally {
            val edit = p.edit(); keys.forEach(edit::remove)
            previous.forEach { (key, value) -> when (value) {
                is Long -> edit.putLong(key, value)
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
            } }
            edit.commit()
            usageMode(when (mode) {
                AppOpsManager.MODE_ALLOWED -> "allow"
                AppOpsManager.MODE_IGNORED -> "ignore"
                AppOpsManager.MODE_ERRORED -> "deny"
                else -> "default"
            })
        }
    }

    @Test fun classicEditsApplyImmediatelyAndClearQueuedChanges() = withClassicLimit { p, pkg, now ->
        p.edit().putLong("pending_at_$pkg", LocalDay.next(now)).putInt("pending_minutes_$pkg", 15).commit()
        assertFalse(StrictLimits.save(context, pkg, 30, 127, false))
        assertEquals(30, p.getInt("limit_$pkg", 0))
        assertFalse(p.contains("pending_at_$pkg"))
        assertFalse(StrictLimits.save(context, pkg, 30, 1, false))
        assertEquals(1, p.getInt("limit_days_$pkg", 0))
        assertFalse(StrictLimits.save(context, pkg, 0, 127, false))
        assertEquals(0, p.getInt("limit_$pkg", -1))
        assertNull(StrictLimits.blockReason(context, pkg, null, now))
    }

    @Test fun classicExtensionExpiresAfterFiveMinutesOrMidnight() = withClassicLimit { p, pkg, now ->
        val start = LocalDay.start(now) + 60_000L
        val use = DailyUsage.Measurement(LocalDay.start(start), 600_000L, pkg)
        assertTrue(StrictLimits.grantExtraTime(context, pkg, use, start))
        assertNull(StrictLimits.blockReason(context, pkg, use, start + 299_999L))
        assertEquals("daily", StrictLimits.blockReason(context, pkg, use, start + 300_000L))
        val late = LocalDay.next(start) - 60_000L
        assertTrue(StrictLimits.grantExtraTime(context, pkg, use, late))
        assertEquals(LocalDay.next(start), p.getLong("bypass_until_$pkg", 0))
        val nextDay = DailyUsage.Measurement(LocalDay.next(start), 600_000L, pkg)
        assertEquals("daily", StrictLimits.blockReason(context, pkg, nextDay, LocalDay.next(start)))
    }

    @Test fun classicExtensionCannotUnlockFocusOrYoutube() = withClassicLimit { p, pkg, now ->
        p.edit().putBoolean("focus_block_active", true).putLong("focus_block_end", now + 60_000L)
            .putBoolean("focus_block_$pkg", true).commit()
        val use = DailyUsage.Measurement(LocalDay.start(now), 600_000L, pkg)
        assertFalse(StrictLimits.grantExtraTime(context, pkg, use, now))
        assertEquals("focus", StrictLimits.blockReason(context, pkg, use, now))
        assertTrue(StrictLimits.save(context, pkg, 0, 127, false))
        assertEquals(10, p.getInt("limit_$pkg", 0))
        StrictLimits.applyPending(context, now + 60_001L)
        assertEquals(0, p.getInt("limit_$pkg", -1))
        assertFalse(StrictLimits.grantExtraTime(context, YouTubeQuota.PACKAGE,
            DailyUsage.Measurement(LocalDay.start(now), 600_000L, YouTubeQuota.PACKAGE), now))
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

    @Test fun youtubeUsesEditableLimitThenTimedBypassesAndRespectsFocus() = withClassicLimit(YouTubeQuota.PACKAGE) { p, pkg, now ->
        assertFalse(StrictLimits.save(context, pkg, 30, 127, true))
        val day = LocalDay.start(now)
        val below = DailyUsage.Measurement(day, 1_799_999L, pkg)
        val reached = DailyUsage.Measurement(day, 1_800_000L, pkg)
        p.edit().putLong("youtube_bypass_day", day).putInt("youtube_bypass_sessions", 0)
            .putLong("youtube_bypass_started", 0).putLong("youtube_bypass_elapsed", 0).commit()
        assertNull(StrictLimits.blockReason(context, pkg, below, now))
        assertEquals("youtube_daily", StrictLimits.blockReason(context, pkg, reached, now))
        p.edit().putLong("youtube_bypass_day", day).putInt("youtube_bypass_sessions", 1)
            .putLong("youtube_bypass_started", now)
            .putLong("youtube_bypass_elapsed", android.os.SystemClock.elapsedRealtime()).commit()
        assertNull(StrictLimits.blockReason(context, pkg, reached, now))
        if (LocalDay.start(now + 300_000L) == day) {
            assertEquals("youtube_daily", StrictLimits.blockReason(context, pkg, reached, now + 300_000L))
        }
        p.edit().putBoolean("focus_block_active", true).putLong("focus_block_end", now + 60_000L).commit()
        assertEquals("focus", StrictLimits.blockReason(context, pkg, reached, now))
        assertFalse(StrictLimits.startYouTube(context, reached))
        p.edit().putBoolean("focus_block_active", false).commit()
        assertFalse(StrictLimits.save(context, pkg, 30, 0, true))
        assertNull(StrictLimits.blockReason(context, pkg, reached, now))
        assertFalse(StrictLimits.save(context, pkg, 0, 127, true))
        assertNull(StrictLimits.blockReason(context, pkg, reached, now))
    }

    @Test fun storedYoutubeQuotaResetsAtMidnightAndSurvivesReadingAgain() {
        val p = StrictLimits.prefs(context)
        val keys = listOf("youtube_bypass_day", "youtube_bypass_sessions", "youtube_bypass_started", "youtube_bypass_elapsed")
        val previous = p.all.filterKeys { it in keys }
        val midnight = LocalDay.start(System.currentTimeMillis())
        try {
            p.edit().putLong("youtube_bypass_day", LocalDay.start(midnight - 1)).putInt("youtube_bypass_sessions", 2)
                .putLong("youtube_bypass_started", midnight - 400_000).putLong("youtube_bypass_elapsed", 1).commit()
            assertEquals(2, StrictLimits.quota(context, midnight - 1).sessions)
            assertEquals(0, StrictLimits.quota(context, midnight).sessions)
            assertEquals(midnight, p.getLong("youtube_bypass_day", 0))
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
