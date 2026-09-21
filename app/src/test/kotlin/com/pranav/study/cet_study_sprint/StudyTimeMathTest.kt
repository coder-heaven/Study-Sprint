package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class StudyTimeMathTest {
    @Test fun countdownUsesEndTimestampAndRoundsUpPartialSecond() {
        assertEquals(90, StudyTimeMath.remainingSeconds(90_000, 0))
        assertEquals(89, StudyTimeMath.remainingSeconds(90_000, 1_001))
        assertEquals(0, StudyTimeMath.remainingSeconds(90_000, 90_001))
    }
    @Test fun weekdayMaskUsesMondayFirst() {
        assertEquals(0, StudyTimeMath.weekdayIndex(Calendar.MONDAY))
        assertEquals(5, StudyTimeMath.weekdayIndex(Calendar.SATURDAY))
        assertEquals(6, StudyTimeMath.weekdayIndex(Calendar.SUNDAY))
    }
}
