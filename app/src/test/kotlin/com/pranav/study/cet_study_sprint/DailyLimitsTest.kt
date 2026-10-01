package com.pranav.study.cet_study_sprint

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DailyLimitsTest {
    private val india = ZoneId.of("Asia/Kolkata")
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private val midnight = time("2026-10-01T18:30:00Z")
    private val app = "test.app"

    @Test fun resetIsExactlyLocalMidnightInsteadOfUtcOrTwentyFourHoursLater() {
        assertEquals(midnight, LocalDay.next(midnight - 1, india))
        assertEquals(midnight, LocalDay.start(midnight, india))
        assertEquals(midnight - 86_400_000L, LocalDay.start(midnight - 1, india))
        val exhausted = YouTubeQuota(day = LocalDay.start(midnight - 1, india), sessions = 2)
        assertEquals(2, exhausted.forDay(LocalDay.start(midnight - 1, india)).sessions)
        assertEquals(0, exhausted.forDay(LocalDay.start(midnight, india)).sessions)
    }
    @Test fun nextDayUsesCalendarDaysAcrossDaylightSavingChanges() {
        val zone = ZoneId.of("America/New_York")
        val spring = time("2026-03-08T05:00:00Z")
        val autumn = time("2026-11-01T04:00:00Z")
        assertEquals(23 * 3_600_000L, LocalDay.next(spring, zone) - spring)
        assertEquals(25 * 3_600_000L, LocalDay.next(autumn, zone) - autumn)
    }
    @Test fun sessionCrossingMidnightCountsOnlyTheNewDaysPart() {
        val usage = UsageTimeline(midnight, midnight + 120_000)
        usage.accept(UsageTimeline.Kind.RESUME, midnight - 600_000, app, "Player")
        usage.accept(UsageTimeline.Kind.PAUSE, midnight + 60_000, app, "Player")
        assertEquals(60_000L, usage.result()[app])
    }
    @Test fun finishedYesterdayIsZeroToday() {
        val usage = UsageTimeline(midnight, midnight + 60_000)
        usage.accept(UsageTimeline.Kind.RESUME, midnight - 600_000, app, "Player")
        usage.accept(UsageTimeline.Kind.PAUSE, midnight - 1, app, "Player")
        assertEquals(0L, usage.result()[app] ?: 0L)
    }
    @Test fun screenOffAndLockOvernightDoNotConsumeTheNewDaysAllowance() {
        val usage = UsageTimeline(midnight, midnight + 3_600_000)
        usage.accept(UsageTimeline.Kind.RESUME, midnight - 600_000, app, "Player")
        usage.accept(UsageTimeline.Kind.LOCK, midnight - 2)
        usage.accept(UsageTimeline.Kind.SCREEN_OFF, midnight - 1)
        usage.accept(UsageTimeline.Kind.SCREEN_ON, midnight + 1_000)
        usage.accept(UsageTimeline.Kind.UNLOCK, midnight + 3_540_000)
        assertEquals(60_000L, usage.result()[app])
    }
    @Test fun shutdownWithoutPauseDoesNotCreateGhostUsageAfterReboot() {
        val usage = UsageTimeline(midnight, midnight + 3_600_000)
        usage.accept(UsageTimeline.Kind.RESUME, midnight - 600_000, app, "Player")
        usage.accept(UsageTimeline.Kind.SHUTDOWN, midnight - 1)
        usage.accept(UsageTimeline.Kind.STARTUP, midnight + 1_000)
        assertEquals(0L, usage.result()[app] ?: 0L)
    }
    @Test fun latePauseOfOldActivityDoesNotStopCountingTheNewActivity() {
        val usage = UsageTimeline(midnight, midnight + 120_000)
        usage.accept(UsageTimeline.Kind.RESUME, midnight, app, "List")
        usage.accept(UsageTimeline.Kind.RESUME, midnight + 30_000, app, "Player")
        usage.accept(UsageTimeline.Kind.PAUSE, midnight + 30_001, app, "List")
        usage.accept(UsageTimeline.Kind.PAUSE, midnight + 90_000, app, "Player")
        assertEquals(90_000L, usage.result()[app])
    }
    @Test fun overlappingActivitiesAndDuplicateStopsAreNotDoubleCounted() {
        val usage = UsageTimeline(midnight, midnight + 120_000)
        usage.accept(UsageTimeline.Kind.RESUME, midnight, app, "List")
        usage.accept(UsageTimeline.Kind.RESUME, midnight + 30_000, app, "Player")
        usage.accept(UsageTimeline.Kind.PAUSE, midnight + 60_000, app, "Player")
        usage.accept(UsageTimeline.Kind.PAUSE, midnight + 90_000, app, "List")
        usage.accept(UsageTimeline.Kind.PAUSE, midnight + 100_000, app, "List")
        assertEquals(90_000L, usage.result()[app])
    }
}
