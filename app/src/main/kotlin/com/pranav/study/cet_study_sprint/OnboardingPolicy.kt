package com.pranav.study.cet_study_sprint

internal enum class StartupStage { TERMS, PROFILE, SETUP, TUTORIAL, READY }

/** Existing installs are never sent through a destructive profile/default reset on update. */
internal object OnboardingPolicy {
    const val TERMS_VERSION = 1

    fun stage(profileComplete: Boolean, legacyProfileComplete: Boolean, termsAccepted: Boolean,
              setupPending: Boolean, tutorialPending: Boolean): StartupStage = when {
        profileComplete || legacyProfileComplete -> when {
            setupPending -> StartupStage.SETUP
            tutorialPending -> StartupStage.TUTORIAL
            else -> StartupStage.READY
        }
        !termsAccepted -> StartupStage.TERMS
        else -> StartupStage.PROFILE
    }
}
