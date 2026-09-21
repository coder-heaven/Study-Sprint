package com.pranav.study.cet_study_sprint

internal object StudyTimeMath {
    fun remainingSeconds(endAtMs: Long, nowMs: Long): Int =
        ((endAtMs - nowMs + 999L) / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /** Android Calendar.DAY_OF_WEEK is Sunday=1; limit mask is Monday=bit 0. */
    fun weekdayIndex(calendarDay: Int): Int = (calendarDay + 5) % 7
}
