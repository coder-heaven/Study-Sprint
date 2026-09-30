package com.pranav.study.cet_study_sprint

internal object LeaderboardParticipation {
    fun shouldConnect(profileCompleted: Boolean, profileHidden: Boolean): Boolean =
        profileCompleted && !profileHidden
}
