package com.pranav.study.cet_study_sprint

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class StudyNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun showApp() {
        val prefs = compose.activity.getSharedPreferences("navigation_ui_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("profile_name", "Student").putString("exam", "CET").putString("grade", "11").commit()
        compose.setContent {
            StudyTheme(prefs, 0) {
                StudyRoot(prefs, 0, null, {}, {}, {})
            }
        }
    }
    private fun capture(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "ui-proof").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
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
            capture(route)
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("screen_home").assertIsDisplayed()
        }
    }
}
