package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPolicyTest {
    @Test fun newUserMustAcceptTermsBeforeProfile() {
        assertEquals(StartupStage.TERMS, OnboardingPolicy.stage(false, false, false, false, false))
        assertEquals(StartupStage.PROFILE, OnboardingPolicy.stage(false, false, true, false, false))
    }

    @Test fun completedProfileResumesUnfinishedSetupAndTutorial() {
        assertEquals(StartupStage.SETUP, OnboardingPolicy.stage(true, false, true, true, false))
        assertEquals(StartupStage.TUTORIAL, OnboardingPolicy.stage(true, false, true, false, true))
        assertEquals(StartupStage.READY, OnboardingPolicy.stage(true, false, true, false, false))
    }

    @Test fun existingV3InstallDoesNotRepeatOnboardingAfterUpdate() {
        assertEquals(StartupStage.READY, OnboardingPolicy.stage(true, false, false, false, false))
    }

    @Test fun legacyV2InstallDoesNotResetItsExistingProfile() {
        assertEquals(StartupStage.READY, OnboardingPolicy.stage(false, true, false, false, false))
    }
}
