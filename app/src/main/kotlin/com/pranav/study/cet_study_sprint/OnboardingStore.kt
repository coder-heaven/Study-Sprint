package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences

internal object OnboardingStore {
    fun stage(prefs: SharedPreferences): StartupStage = OnboardingPolicy.stage(
        prefs.getBoolean("onboarding_v3", false), prefs.getBoolean("onboarding_v2", false),
        prefs.getInt("terms_accepted_version", 0) >= OnboardingPolicy.TERMS_VERSION,
        prefs.getBoolean("initial_setup_pending", false), prefs.getBoolean("initial_tutorial_pending", false)
    )

    fun acceptTerms(prefs: SharedPreferences): Boolean = prefs.edit()
        .putInt("terms_accepted_version", OnboardingPolicy.TERMS_VERSION)
        .putLong("terms_accepted_at", System.currentTimeMillis()).commit()

    fun saveProfile(prefs: SharedPreferences, name: String, course: String, grade: String): Boolean =
        prefs.edit().putString("profile_name", name.trim().ifBlank { "Student" })
            .putString("exam", course).putString("grade", grade)
            .putBoolean("onboarding_v3", true).putBoolean("initial_setup_pending", true).commit()

    fun finishSetup(prefs: SharedPreferences): Boolean = prefs.edit()
        .putBoolean("initial_setup_pending", false).putBoolean("initial_tutorial_pending", true).commit()

    fun finishTutorial(prefs: SharedPreferences): Boolean = prefs.edit()
        .putBoolean("initial_tutorial_pending", false).putBoolean("tutorial_seen", true).commit()
}
