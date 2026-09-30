package com.pranav.study.cet_study_sprint

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

internal data class LeaderboardDelta(
    val focusMinutes: Long = 0,
    val quizAttempts: Long = 0,
    val quizWins: Long = 0,
    val quizCorrect: Long = 0,
    val quizQuestions: Long = 0
)

internal object LeaderboardScoring {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    fun focus(durationMs: Long): LeaderboardDelta? {
        val minutes = durationMs / 60_000
        return if (minutes in 1..480) LeaderboardDelta(focusMinutes = minutes) else null
    }

    // Only completed in-app sets qualify. Manual book logs never count as quiz wins.
    fun quiz(attempted: Int, correct: Int): LeaderboardDelta? {
        if (attempted !in 1..500 || correct !in 0..attempted) return null
        return LeaderboardDelta(quizAttempts = 1, quizWins = if (correct * 100 >= attempted * 80) 1 else 0,
            quizCorrect = correct.toLong(), quizQuestions = attempted.toLong())
    }

    // The same set can count only once per India calendar day, including on another device.
    fun quizEventId(canonicalSet: String, atMs: Long): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalSet.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val day = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
        return "quiz_${day}_$digest"
    }
}
