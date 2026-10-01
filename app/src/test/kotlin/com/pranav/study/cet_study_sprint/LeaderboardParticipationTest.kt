package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardParticipationTest {
    @Test fun completedAppProfileRequiresCurrentPrivacyConsent() {
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = true, profileHidden = false))
        assertTrue(LeaderboardParticipation.shouldConnect(profileCompleted = true, profileHidden = false, consentVersion = PrivacyConsent.VERSION))
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = true, profileHidden = false, consentVersion = PrivacyConsent.VERSION + 1))
    }
    @Test fun unfinishedProfileIsNotPublished() {
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = false, profileHidden = false, consentVersion = PrivacyConsent.VERSION))
    }
    @Test fun explicitlyHiddenProfileStaysHidden() {
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = true, profileHidden = true, consentVersion = PrivacyConsent.VERSION))
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = false, profileHidden = true))
    }
}
