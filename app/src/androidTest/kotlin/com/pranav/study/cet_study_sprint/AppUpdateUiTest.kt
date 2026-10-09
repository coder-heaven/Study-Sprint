package com.pranav.study.cet_study_sprint

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppUpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val notes = (1..60).joinToString("\n") { "Feature $it: detailed update improvements and instructions." }
    private val update = AppUpdateInfo("v5.0.2", "5.0.2", notes, "", "")
    private fun theme(content: @androidx.compose.runtime.Composable () -> Unit) {
        val prefs = compose.activity.getSharedPreferences("update_ui_test", 0)
        prefs.edit().putString("theme_mode", "dark").commit()
        compose.setContent { StudyTheme(prefs, 0, content) }
    }

    @Test fun allRequiredUpdateFeaturesScrollOnShortScreenWhileInstallRemainsReachable() {
        var clicked = false
        theme {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                RequiredUpdateScreen(update, onRetry = {}) {
                    Button(onClick = { clicked = true }, modifier = Modifier.fillMaxWidth().testTag("test_install")) { Text("Update now") }
                }
            }
        }
        compose.onNodeWithTag("test_install").assertIsDisplayed()
        compose.onNodeWithTag("update_notes_end").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(notes).assertExists()
        compose.onNodeWithTag("test_install").assertIsDisplayed().performClick()
        assertTrue(clicked)
        compose.onNodeWithTag("update_check_again").assertIsDisplayed()
    }

    @Test fun updateDialogNotesScrollWithoutHidingButtons() {
        theme {
            UpdatePromptDialog(update, onDismiss = {}) {
                Button(onClick = {}, modifier = Modifier.testTag("test_install")) { Text("Update now") }
            }
        }
        compose.onNodeWithTag("update_notes_end").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(notes).assertExists()
        compose.onNodeWithTag("test_install").assertIsDisplayed()
        compose.onNodeWithText("Later").assertIsDisplayed()
    }

    @Test fun installerReceivesReadOnlyContentUriNotAnExternalFilePath() {
        val directory = File(compose.activity.cacheDir, "updates").apply { mkdirs() }
        val file = File(directory, "provider-test.apk").apply { writeText("test payload") }
        try {
            val intent = updateInstallIntent(compose.activity, file)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${compose.activity.packageName}.files", intent.data?.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val payload = compose.activity.contentResolver.openInputStream(requireNotNull(intent.data))!!.bufferedReader().use { it.readText() }
            assertEquals("test payload", payload)
        } finally { file.delete() }
    }
}
