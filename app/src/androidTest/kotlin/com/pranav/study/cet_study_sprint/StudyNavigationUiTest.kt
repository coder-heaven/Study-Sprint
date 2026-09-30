package com.pranav.study.cet_study_sprint

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
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
        compose.onNodeWithText("2 sessions per day · 5 minutes each").assertIsDisplayed()
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
}
