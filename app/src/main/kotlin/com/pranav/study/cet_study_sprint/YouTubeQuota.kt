package com.pranav.study.cet_study_sprint

/** Two deliberate five-minute windows; an app switch never renews a window. */
internal data class YouTubeQuota(
    val day: Long = 0L,
    val sessions: Int = 0,
    val startedAt: Long = 0L,
    val startedElapsed: Long = 0L
) {
    fun forDay(today: Long): YouTubeQuota = if (today > day) YouTubeQuota(day = today) else this
    fun remaining(now: Long, elapsed: Long): Long {
        if (sessions == 0 || now < startedAt || elapsed < startedElapsed) return 0L
        return minOf(WINDOW - (now - startedAt), WINDOW - (elapsed - startedElapsed)).coerceIn(0L, WINDOW)
    }
    fun start(today: Long, now: Long, elapsed: Long): YouTubeQuota? {
        val current = forDay(today)
        if (current.remaining(now, elapsed) > 0L) return current
        if (current.sessions >= MAX_SESSIONS || today < current.day) return null
        return current.copy(sessions = current.sessions + 1, startedAt = now, startedElapsed = elapsed)
    }
    companion object {
        const val PACKAGE = "com.google.android.youtube"
        const val WINDOW = 300_000L
        const val MAX_SESSIONS = 2
    }
}
