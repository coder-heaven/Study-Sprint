package com.pranav.study.cet_study_sprint

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatInteractiveQuizUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val text = "1. Which formula gives photon energy?\nA. h/f\nB. h*f\nC. f/h\nD. h+f\nAnswer: B\n\n" +
        "2. How many moles are in 9 g of water?\nA. 1\nB. 2\nC. 0.5\nD. 9\nAnswer: C"
    private fun show(exam: String = "CET") {
        val prefs = compose.activity.getSharedPreferences("interactive_quiz_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("theme_mode", "dark").commit()
        compose.setContent {
            StudyTheme(prefs, 0) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ChatInteractiveQuiz(text, chatQuizQuestions(text), exam)
                }
            }
        }
    }
    @Test fun quizChecksAnswersScoresOnceAndKeepsProgressWhenSwitchingViews() {
        show("JEE")
        compose.onNodeWithText("1 / 2").assertExists()
        compose.onNodeWithTag("chat_quiz_feedback").assertDoesNotExist()
        compose.onNodeWithTag("chat_quiz_next").assertIsNotEnabled()
        compose.onNodeWithTag("chat_quiz_option_B").performClick().assertIsSelected()
        compose.onNodeWithTag("chat_quiz_view").performClick()
        compose.onNodeWithTag("chat_quiz_text").assertExists()
        compose.onNodeWithTag("chat_quiz_option_B").assertDoesNotExist()
        compose.onNodeWithTag("chat_quiz_view").performClick()
        compose.onNodeWithTag("chat_quiz_option_B").assertIsSelected()
        compose.onNodeWithTag("chat_quiz_next").performScrollTo().performClick()
        compose.onNodeWithText("Correct!").assertExists()
        compose.onNodeWithTag("chat_quiz_option_A").assertIsNotEnabled()
        compose.onNodeWithTag("chat_quiz_next").performClick()
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithTag("chat_quiz_next").assertIsNotEnabled()
        compose.onNodeWithTag("chat_quiz_option_A").performScrollTo().performClick()
        compose.onNodeWithTag("chat_quiz_next").performScrollTo().performClick()
        compose.onNodeWithText("Correct answer: C").assertExists()
        compose.onNodeWithTag("chat_quiz_next").performClick()
        compose.onNodeWithText("1 / 2 correct").assertExists()
        compose.onNodeWithText("3 / 8 marks · JEE").assertExists()
        compose.onNodeWithTag("chat_quiz_restart").performClick()
        compose.onNodeWithText("1 / 2").assertExists()
        compose.onNodeWithTag("chat_quiz_next").assertIsNotEnabled()
    }
    @Test fun quizUsesEmeraldThemeAndRejectsIncompleteOrFiveOptionQuestions() {
        assertTrue(chatQuizQuestions("1. Explain energy\n2. Give examples").isEmpty())
        assertTrue(chatQuizQuestions(text.replace("D. h+f", "D. h+f\nE. none")).isEmpty())
        assertTrue(chatQuizQuestions(text.replace("Answer: C", "")).isEmpty())
        assertEquals(2, chatQuizQuestions(text).size)
        show()
        saveUiProof(compose.activity, compose.onRoot().captureToImage().asAndroidBitmap(), "chat-interactive-quiz")
    }
}
