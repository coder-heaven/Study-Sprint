package com.pranav.study.cet_study_sprint

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class YouTubeReturnUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun waitForBlock() {
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithText("Open Study Sprint").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
    private fun waitForScreen(tag: String) {
        compose.waitUntil(30_000) {
            runCatching { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    private fun assertOriginalResumed(original: ComposeStudyActivity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComposeStudyActivity>().single()
            assertSame(original, resumed)
            assertFalse(resumed.isFinishing)
        }
    }
    private fun withLimits(test: (Context, ComposeStudyActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarding_v3", true).putString("profile_name", "Student")
            .putString("exam", "CET").putString("grade", "11").commit()
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        // Track the actual activity: ActivityScenario's intent matching loses the
        // activity after onNewIntent replaces a launcher intent with an alert.
        val original = instrumentation.startActivitySync(launch) as ComposeStudyActivity
        try {
            waitForScreen("tab_focus")
            compose.onNodeWithTag("tab_focus").performClick()
            compose.onNodeWithText("App limits", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithTag("screen_limits").assertIsDisplayed()
            test(context, original)
        } finally {
            instrumentation.runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                Stage.values().filter { it != Stage.DESTROYED }.flatMap { monitor.getActivitiesInStage(it) }
                    .filter { it is ComposeStudyActivity || it is BlockedActivity }.distinct().forEach { it.finish() }
            }
        }
    }

    @Test fun youtubeReturnReusesTheExistingLimitsActivity() = withLimits { context, original ->
        repeat(3) {
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("screen_home").assertIsDisplayed()
            // A separate blocking task stands in for the external app while Study Sprint is paused.
            context.startActivity(BlockNavigation.blockedIntent(context, YouTubeQuota.PACKAGE, "youtube_session", 0))
            waitForBlock()
            context.startActivity(BlockNavigation.studyIntent(context, "limits"))
            waitForScreen("screen_limits")
            compose.onNodeWithTag("screen_limits").assertIsDisplayed()
            assertOriginalResumed(original)
        }
        // Remove the background test block screen before closing the main activity.
        context.startActivity(BlockNavigation.blockedIntent(context, YouTubeQuota.PACKAGE, "youtube_session", 0))
        waitForBlock()
        compose.onNodeWithText("Open Study Sprint").performScrollTo().performClick()
        waitForScreen("screen_limits")
        compose.onNodeWithTag("screen_limits").assertIsDisplayed()
    }

    @Test fun openingStudyFromBlockScreenNeverPlacesItInsideTheBlockingTask() = withLimits { context, original ->
        repeat(2) {
            context.startActivity(BlockNavigation.blockedIntent(context, YouTubeQuota.PACKAGE, "youtube_session", 0))
            waitForBlock()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val block = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<BlockedActivity>().single()
                assertNotEquals(original.taskId, block.taskId)
            }
            compose.onNodeWithText("Open Study Sprint").performScrollTo().performClick()
            waitForScreen("screen_limits")
            compose.onNodeWithTag("screen_limits").assertIsDisplayed()
            assertOriginalResumed(original)
        }
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
    }
}
