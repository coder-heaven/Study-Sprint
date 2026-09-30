package com.pranav.study.cet_study_sprint

import org.junit.Assert.*
import org.junit.Test

class YouTubeQuotaTest {
    private val day = 86_400_000L
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
