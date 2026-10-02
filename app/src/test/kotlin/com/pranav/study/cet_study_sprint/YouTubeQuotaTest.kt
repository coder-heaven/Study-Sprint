package com.pranav.study.cet_study_sprint

import org.junit.Assert.*
import org.junit.Test

class YouTubeQuotaTest {
    private val day = 86_400_000L
    @Test fun bypassUnlocksOnlyAfterConfiguredDailyLimit() {
        val quota = YouTubeQuota()
        assertNull(quota.startAfterLimit(30, true, 1_799_999L, day, day + 1000, 1000))
        assertNotNull(quota.startAfterLimit(30, true, 1_800_000L, day, day + 1000, 1000))
        assertNotNull(quota.startAfterLimit(30, true, 2_400_000L, day, day + 1000, 1000))
        assertNull(quota.startAfterLimit(0, true, 2_400_000L, day, day + 1000, 1000))
        assertNull(quota.startAfterLimit(30, false, 2_400_000L, day, day + 1000, 1000))
    }
    @Test fun twoPostLimitBypassesExpireAndResetAtMidnight() {
        val first = YouTubeQuota().startAfterLimit(15, true, 900_000L, day, day + 1000, 1000)!!
        assertEquals(first, first.startAfterLimit(15, true, 960_000L, day, day + 61000, 61000))
        val second = first.startAfterLimit(15, true, 1_200_000L, day, day + 301000, 301000)!!
        assertEquals(2, second.sessions)
        assertNull(second.startAfterLimit(15, true, 1_500_000L, day, day + 601000, 601000))
        assertEquals(0, second.forDay(day * 2).sessions)
        assertNull(second.forDay(day * 2).startAfterLimit(15, true, 0L, day * 2, day * 2, 900000))
    }
    @Test fun exactlyTwoWindowsAndNoThird() {
        val first = YouTubeQuota().start(day, day + 1000, 1000)!!
        assertEquals(1, first.sessions)
        assertEquals(300_000L, first.remaining(day + 1000, 1000))
        val second = first.start(day, day + 301000, 301000)!!
        assertEquals(2, second.sessions)
        assertNull(second.start(day, day + 601000, 601000))
    }
    @Test fun reopeningNeverExtendsCurrentWindow() {
        val first = YouTubeQuota().start(day, day + 1000, 1000)!!
        assertEquals(first, first.start(day, day + 61000, 61000))
        assertEquals(240_000L, first.remaining(day + 61000, 61000))
        assertEquals(0L, first.remaining(day + 301000, 301000))
    }
    @Test fun stateSurvivesRecreationAndMidnightResetsIt() {
        val original = YouTubeQuota().start(day, day + 1000, 1000)!!
        val restored = original.copy()
        assertEquals(1, restored.sessions)
        assertEquals(299_000L, restored.remaining(day + 2000, 2000))
        assertEquals(0, restored.forDay(day * 2).sessions)
        assertEquals(0L, restored.forDay(day * 2).remaining(day * 2, 6000))
    }
    @Test fun clockRollbackOrRebootCannotExtendAnAllowance() {
        val first = YouTubeQuota().start(day, day + 1000, 10000)!!
        assertEquals(0L, first.remaining(day, 20000))
        assertEquals(0L, first.remaining(day + 2000, 500))
        val exhausted = first.copy(sessions = 2)
        assertEquals(2, exhausted.forDay(day - 86400000).sessions)
        assertNull(exhausted.start(day - 86400000, day - 1000, 400000))
    }
    @Test fun monotonicTimeCapsForwardAndBackwardClockChanges() {
        val first = YouTubeQuota().start(day, day + 1000, 1000)!!
        assertEquals(0L, first.remaining(day + 2000, 301000))
        assertEquals(0L, first.remaining(day + 301000, 2000))
    }
}
