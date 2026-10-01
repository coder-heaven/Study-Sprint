package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences

internal object PrivacyConsent {
    const val VERSION = 1
    const val KEY = "leaderboard_privacy_version"
    const val PENDING = "leaderboard_privacy_hide_pending"
    fun has(prefs: SharedPreferences): Boolean = prefs.getInt(KEY, 0) == VERSION
    fun allow(prefs: SharedPreferences) {
        check(prefs.edit().putInt(KEY, VERSION).putLong("leaderboard_privacy_accepted_at", System.currentTimeMillis())
            .putBoolean("leaderboard_opted_out", false).commit()) { "Could not save your privacy choice." }
    }
    fun revoke(prefs: SharedPreferences) {
        check(prefs.edit().remove(KEY).remove("leaderboard_privacy_accepted_at")
            .putBoolean("leaderboard_enabled", false).putBoolean("leaderboard_opted_out", true)
            .putBoolean(PENDING, prefs.getString("leaderboard_uid", null) != null).commit()) { "Could not save your privacy choice." }
    }
}
