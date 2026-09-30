package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardParticipationTest {
    @Test fun completedAppProfileJoinsWithoutAnOptInOrGoogleAccount() {
        assertTrue(LeaderboardParticipation.shouldConnect(profileCompleted = true, profileHidden = false))
    }
    @Test fun unfinishedProfileIsNotPublished() {
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = false, profileHidden = false))
    }
    @Test fun explicitlyHiddenProfileStaysHidden() {
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = true, profileHidden = true))
        assertFalse(LeaderboardParticipation.shouldConnect(profileCompleted = false, profileHidden = true))
    }
}
