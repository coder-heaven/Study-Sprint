package com.pranav.study.cet_study_sprint

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class YouTubeReturnUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun waitForBlock() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Open Study Sprint").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun withLimits(test: (Context, ActivityScenario<ComposeStudyActivity>, ComposeStudyActivity) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarding_v3", true).putString("profile_name", "Student")
            .putString("exam", "CET").putString("grade", "11").commit()
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        ActivityScenario.launch<ComposeStudyActivity>(launch).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("tab_focus").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("tab_focus").performClick()
            compose.onNodeWithText("App limits", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithTag("screen_limits").assertIsDisplayed()
            lateinit var original: ComposeStudyActivity
            scenario.onActivity { original = it }
            test(context, scenario, original)
        }
    }

    @Test fun youtubeReturnReusesTheExistingLimitsActivity() = withLimits { context, scenario, original ->
        repeat(3) {
            // A separate blocking task stands in for the external app while Study Sprint is paused.
            context.startActivity(BlockNavigation.blockedIntent(context, YouTubeQuota.PACKAGE, "youtube_session", 0))
            waitForBlock()
            context.startActivity(BlockNavigation.studyIntent(context, "limits"))
            compose.onNodeWithTag("screen_limits").assertIsDisplayed()
            scenario.onActivity { assertSame(original, it); assertFalse(it.isFinishing) }
        }
        // Remove the background test block screen before closing the main activity.
        context.startActivity(BlockNavigation.blockedIntent(context, YouTubeQuota.PACKAGE, "youtube_session", 0))
        waitForBlock()
        compose.onNodeWithText("Open Study Sprint").performScrollTo().performClick()
        compose.onNodeWithTag("screen_limits").assertIsDisplayed()
    }

    @Test fun openingStudyFromBlockScreenNeverPlacesItInsideTheBlockingTask() = withLimits { context, scenario, original ->
        repeat(2) {
            context.startActivity(BlockNavigation.blockedIntent(context, YouTubeQuota.PACKAGE, "youtube_session", 0))
            waitForBlock()
            compose.onNodeWithText("Open Study Sprint").performScrollTo().performClick()
            compose.onNodeWithTag("screen_limits").assertIsDisplayed()
            scenario.onActivity { assertSame(original, it); assertEquals(original.taskId, it.taskId) }
        }
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
    }
}
