package com.pranav.study.cet_study_sprint

internal object LeaderboardParticipation {
    fun shouldConnect(profileCompleted: Boolean, profileHidden: Boolean, consentVersion: Int = 0): Boolean =
        profileCompleted && !profileHidden && consentVersion == PrivacyConsent.VERSION
}
