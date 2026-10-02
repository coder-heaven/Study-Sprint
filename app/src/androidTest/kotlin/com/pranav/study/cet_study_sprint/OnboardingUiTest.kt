package com.pranav.study.cet_study_sprint

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val prefs get() = compose.activity.getSharedPreferences("onboarding_ui_test", Context.MODE_PRIVATE)

    private fun showApp() {
        compose.setContent {
            var revision by remember { mutableIntStateOf(0) }
            StudyTheme(prefs, revision) {
                OnboardingGate(prefs, { revision++ }) {
                    StudyRoot(prefs, revision, null, {}, {}, { revision++ })
                }
            }
        }
    }

    @Test fun freshUserAcceptsTermsThenSetsUpWithoutAnyRequiredPermissions() {
        prefs.edit().clear().commit()
        showApp()
        compose.onNodeWithTag("terms_accept_checkbox").assertIsOff()
        compose.onNodeWithTag("terms_continue").assertIsNotEnabled()
        compose.onNodeWithText("Read Terms of Use").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("terms_accept_checkbox").assertIsOff().performClick()
        compose.onNodeWithTag("terms_continue").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Continue as guest", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Continue as guest", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("setup_screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Optional Android permissions").assertExists()
        compose.onNodeWithTag("setup_continue").performClick()
        compose.onNodeWithTag("screen_tutorial").assertIsDisplayed()
        compose.onNodeWithTag("tutorial_done").performClick()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
        assertEquals(StartupStage.READY, OnboardingStore.stage(prefs))
        assertTrue(prefs.getLong("terms_accepted_at", 0) > 0)
        assertTrue(prefs.getBoolean("tutorial_seen", false))
        assertFalse(PrivacyConsent.has(prefs))
        assertFalse(prefs.getBoolean("leaderboard_enabled", false))
    }

    @Test fun homeShortcutsOpenMainFeaturesAndTutorialCanBeReplayed() {
        prefs.edit().clear().putBoolean("onboarding_v3", true).putString("profile_name", "Returning student").commit()
        showApp()
        listOf("limits", "plan", "study", "practice", "settings", "tutorial", "setup").forEach { route ->
            compose.onNodeWithTag("home_action_$route").performScrollTo().performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("screen_home").assertIsDisplayed()
        }
    }

    @Test fun returningUserSkipsNewUserGateAndAllStoredKeysSurvive() {
        prefs.edit().clear().putBoolean("onboarding_v3", true)
            .putString("profile_name", "Existing student").putString("exam", "NEET")
            .putString("grade", "12").putString("theme_mode", "dark")
            .putInt("daily_focus_goal", 180).putString("owned_mcqs", "saved questions")
            .putBoolean("chapter_progress_test", true).putString("tasks", "saved plan").commit()
        val before = prefs.all.toMap()
        compose.setContent {
            StudyTheme(prefs, 0) { OnboardingGate(prefs, {}) { Text("Existing data ready") } }
        }
        compose.onNodeWithText("Existing data ready").assertIsDisplayed()
        compose.onNodeWithTag("first_run_terms").assertDoesNotExist()
        assertEquals(before, prefs.all)
        assertEquals(StartupStage.READY, OnboardingStore.stage(prefs))
    }

    @Test fun interruptedOnboardingResumesWithoutOverwritingStudyData() {
        prefs.edit().clear().putString("saved_note", "Keep this note").putInt("daily_focus_goal", 180).commit()
        assertTrue(OnboardingStore.acceptTerms(prefs))
        assertEquals(StartupStage.PROFILE, OnboardingStore.stage(prefs))
        assertTrue(OnboardingStore.saveProfile(prefs, "Student", "JEE", "12"))
        assertEquals(StartupStage.SETUP, OnboardingStore.stage(prefs))
        assertTrue(OnboardingStore.finishSetup(prefs))
        assertEquals(StartupStage.TUTORIAL, OnboardingStore.stage(prefs))
        assertTrue(OnboardingStore.finishTutorial(prefs))
        assertEquals(StartupStage.READY, OnboardingStore.stage(prefs))
        assertEquals("Keep this note", prefs.getString("saved_note", null))
        assertEquals(180, prefs.getInt("daily_focus_goal", 0))
    }
}
