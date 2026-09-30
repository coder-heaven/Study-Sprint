package com.pranav.study.cet_study_sprint

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LeaderboardScoringTest {
    @Test fun quizWinBoundaryAndInvalidResults() {
        assertEquals(1L, LeaderboardScoring.quiz(10, 8)?.quizWins)
        assertEquals(0L, LeaderboardScoring.quiz(10, 7)?.quizWins)
        assertEquals(1L, LeaderboardScoring.quiz(3, 3)?.quizWins)
        assertEquals(0L, LeaderboardScoring.quiz(3, 2)?.quizWins)
        assertNull(LeaderboardScoring.quiz(0, 0))
        assertNull(LeaderboardScoring.quiz(10, 11))
        assertNull(LeaderboardScoring.quiz(10, -1))
    }
    @Test fun focusExcludesSubMinuteAndInvalidDurations() {
        assertNull(LeaderboardScoring.focus(59_999))
        assertEquals(25L, LeaderboardScoring.focus(25 * 60_000L)?.focusMinutes)
        assertNull(LeaderboardScoring.focus(-1))
        assertNull(LeaderboardScoring.focus(481 * 60_000L))
    }
    @Test fun sameQuizUsesTheSameDailyIdAcrossRetriesAndRestarts() {
        val first = Instant.parse("2026-09-30T00:00:00Z").toEpochMilli()
        assertEquals(LeaderboardScoring.quizEventId("set", first), LeaderboardScoring.quizEventId("set", first + 60_000))
        assertNotEquals(LeaderboardScoring.quizEventId("set", first), LeaderboardScoring.quizEventId("other", first))
    }
    @Test fun dailyQuizBoundaryUsesIndiaTime() {
        val before = Instant.parse("2026-09-30T18:29:59Z").toEpochMilli()
        val after = Instant.parse("2026-09-30T18:30:00Z").toEpochMilli()
        assertTrue(LeaderboardScoring.quizEventId("set", before).startsWith("quiz_2026-09-30_"))
        assertTrue(LeaderboardScoring.quizEventId("set", after).startsWith("quiz_2026-10-01_"))
    }
}
