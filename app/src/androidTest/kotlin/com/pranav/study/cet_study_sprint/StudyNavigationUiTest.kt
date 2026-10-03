package com.pranav.study.cet_study_sprint

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.*
import com.google.android.gms.tasks.TaskCompletionSource
import org.junit.Rule
import org.junit.Test

class StudyNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun showApp(theme: String = "light") {
        val prefs = compose.activity.getSharedPreferences("navigation_ui_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("profile_name", "Student").putString("exam", "CET").putString("grade", "11").putString("theme_mode", theme).commit()
        compose.setContent {
            StudyTheme(prefs, 0) {
                StudyRoot(prefs, 0, null, {}, {}, {})
            }
        }
    }
    private fun capture(name: String, tag: String? = null) {
        val node = if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)
        val bitmap = node.captureToImage().asAndroidBitmap()
        val resolver = compose.activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StudySprintUi")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        checkNotNull(resolver.openOutputStream(uri)).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
    @Test fun figmaCoreScreensAndProtectedFocusSetupAreReachable() {
        val studyPrefs = compose.activity.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val previousBlocking = studyPrefs.getBoolean("focus_block_enabled", true)
        studyPrefs.edit().putBoolean("focus_block_enabled", true).putBoolean("focus_active", false)
            .putBoolean("focus_running", false).commit()
        try {
            showApp("dark")
            val displayPrefs = compose.activity.getSharedPreferences("navigation_ui_test", Context.MODE_PRIVATE)
            saveTasks(displayPrefs, listOf("Revise electrostatics", "Read 30 pages", "Practice calculus"))
            compose.onNodeWithTag("tab_plan").performClick()
            capture("focusiq-tasks-dark")
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithText("Today's Focus Time").assertIsDisplayed()
            capture("focusiq-home-dark")
            compose.onNodeWithTag("tab_focus").performClick()
            capture("focusiq-timer-dark")
            compose.onNodeWithTag("focus_protection_card").performScrollTo()
            compose.onNodeWithTag("focus_block_toggle").assertIsOn()
            compose.onNodeWithTag("focus_protection_status").assertIsDisplayed()
            capture("focusiq-protection-dark")
            if (!StrictLimits.blockerEnabled(compose.activity)) {
                compose.onNodeWithTag("focus_start").performScrollTo().performClick()
                compose.onNodeWithText("Enable app blocking").assertIsDisplayed()
                compose.onNodeWithText("Set up blocking").performClick()
                compose.onNodeWithTag("screen_limits").assertIsDisplayed()
            }
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("screen_home").assertIsDisplayed()
        } finally {
            studyPrefs.edit().putBoolean("focus_block_enabled", previousBlocking).commit()
        }
    }

    @Test fun studyThenTodayWorksRepeatedly() {
        showApp()
        capture("today")
        repeat(5) {
            compose.onNodeWithTag("tab_study").performClick()
            compose.onNodeWithTag("screen_study").assertIsDisplayed()
            if (it == 0) capture("study")
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("screen_home").assertIsDisplayed()
            compose.onNodeWithText("Welcome back").assertIsDisplayed()
        }
    }
    @Test fun todayWorksAfterStudySubpageAndOtherTabs() {
        showApp()
        compose.onNodeWithTag("tab_study").performClick()
        compose.onNodeWithText("MCQ practice").performScrollTo().performClick()
        compose.onNodeWithTag("screen_practice").assertIsDisplayed()
        capture("practice")
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
        listOf("focus", "plan", "statistics", "study").forEach { route ->
            compose.onNodeWithTag("tab_$route").performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
            if (route == "focus") compose.onNodeWithText("Start Focus").assertIsDisplayed()
            capture(route)
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("screen_home").assertIsDisplayed()
        }
    }

    @Test fun remainingScreensRenderAndReturnHomeInDarkMode() {
        showApp("dark")
        compose.onNodeWithTag("tab_focus").performClick()
        compose.onNodeWithText("App limits", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("screen_limits").assertIsDisplayed()
        compose.onNodeWithText("2 bypasses per day · 5 minutes each · after your daily limit").assertIsDisplayed()
        capture("limits-dark")
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        capture("settings-dark")
        compose.onNodeWithText("Profile", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("screen_profile").assertIsDisplayed()
        capture("profile-dark")
        compose.onNodeWithTag("tab_home").performClick()
        listOf("Class notes" to "notes", "Arihant log" to "arihant", "Import questions" to "mcq_editor", "Physics" to "syllabus").forEach { (label, route) ->
            compose.onNodeWithTag("tab_study").performClick()
            compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
            capture("$route-dark")
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("screen_home").assertIsDisplayed()
        }
    }

    @Test fun youtubeAppearsInAllLimitedAndSearchWithEditableLimit() {
        showApp("dark")
        compose.onNodeWithTag("tab_focus").performClick()
        compose.onNodeWithText("App limits", useUnmergedTree = true).performScrollTo().performClick()
        val row = "limit_app_${YouTubeQuota.PACKAGE}"
        val list = compose.onNodeWithTag("app_limits_list")
        // Records load from Android asynchronously, and the row is below the card.
        compose.waitUntil(15_000) {
            runCatching { list.performScrollToNode(hasTestTag(row)); true }.getOrDefault(false)
        }
        compose.onNodeWithTag(row).assertIsDisplayed()
        compose.onNodeWithTag(row).assertTextContains("Daily limit", substring = true)
        list.performScrollToNode(hasTestTag("app_limits_filters"))
        compose.onNodeWithText("Limited", useUnmergedTree = true).performClick()
        list.performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag(row).assertIsDisplayed()
        capture("youtube-limited-dark")
        list.performScrollToNode(hasTestTag("app_limits_search"))
        compose.onNodeWithTag("app_limits_search").performTextInput("youtube")
        list.performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag(row).assertIsDisplayed()
        compose.onNodeWithTag(row).performClick()
        compose.onNodeWithText("30 min").performScrollTo().performClick()
        compose.onNodeWithText("Save limit").performScrollTo().performClick()
        compose.onNodeWithTag("screen_limits").assertIsDisplayed()
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag(row).assertTextContains("of 30m", substring = true); true }.getOrDefault(false)
        }
        compose.onNodeWithTag(row).performClick()
        compose.onNodeWithText("Custom: 30 min").assertExists()
    }

    @Test fun privacyTermsAndOptionalConsentAreAccessible() {
        showApp("dark")
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Privacy Policy", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("screen_privacy").assertIsDisplayed()
        capture("privacy-dark")
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Terms of Use", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("screen_terms").assertIsDisplayed()
        capture("terms-dark")
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Leaderboard privacy", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Your profile stays private").assertExists()
        compose.onNodeWithText("Enable leaderboard").performScrollTo().performClick()
        compose.onNodeWithText("Allow public sharing").assertIsNotEnabled()
        compose.onNodeWithTag("privacy_permission").assertIsOff().performClick()
        compose.onNodeWithText("Allow public sharing").assertIsEnabled()
        compose.onNodeWithText("Keep private").performClick()
        compose.onNodeWithTag("screen_leaderboard").assertIsDisplayed()
    }

    @Test fun cancellationWaitsForOutstandingServerWrite() = runBlocking {
        val pending = TaskCompletionSource<Void>()
        val entered = CompletableDeferred<Unit>()
        val write = launch {
            entered.complete(Unit)
            pending.task.awaitLeaderboardWrite()
        }
        entered.await()
        yield()
        write.cancel()
        yield()
        org.junit.Assert.assertFalse("A cancelled screen must keep waiting for its server write", write.isCompleted)
        pending.setResult(null)
        withTimeout(5_000) { write.join() }
        org.junit.Assert.assertTrue(write.isCompleted)
    }

    @Test fun revokingConsentKeepsTheIdentityForPendingServerRemoval() {
        val prefs = compose.activity.getSharedPreferences("privacy_revoke_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt(PrivacyConsent.KEY, PrivacyConsent.VERSION).putString("leaderboard_uid", "test-owner")
            .putBoolean("leaderboard_enabled", true).putLong("leaderboard_privacy_accepted_at", 1L).commit()
        PrivacyConsent.revoke(prefs)
        org.junit.Assert.assertFalse(PrivacyConsent.has(prefs))
        org.junit.Assert.assertFalse(prefs.getBoolean("leaderboard_enabled", true))
        org.junit.Assert.assertTrue(prefs.getBoolean(PrivacyConsent.PENDING, false))
        org.junit.Assert.assertEquals("test-owner", prefs.getString("leaderboard_uid", null))
    }
}
