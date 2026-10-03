package com.pranav.study.cet_study_sprint

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class StudyChatUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val key = "sk-or-v1-" + "a".repeat(64)
    private val prefs get() = compose.activity.getSharedPreferences("study_chat_ui_test", Context.MODE_PRIVATE)
    private fun capture(name: String) {
        saveUiProof(compose.activity, compose.onRoot().captureToImage().asAndroidBitmap(), name)
    }
    private fun fakeStorage() = object : ChatKeyStorage {
        private var saved: String? = key
        override fun read() = saved
        override fun save(key: String) { saved = key }
        override fun remove() { saved = null }
    }
    private fun model(transport: StudyChatTransport) = StudyChatViewModel(compose.activity.application as Application, fakeStorage(), transport)
    private fun show(model: StudyChatViewModel) {
        prefs.edit().clear().putString("theme_mode", "dark").commit()
        compose.setContent { StudyTheme(prefs, 0) { StudyChatScreen(model, {}) } }
        compose.waitUntil(5000) { model.state.value.ready }
    }
    @Test fun encryptedKeyCanBeReopenedReplacedAndRemovedWithoutPlaintextStorage() {
        val vault = ChatKeyVault(compose.activity)
        try {
            vault.save(key)
            assertEquals(key, ChatKeyVault(compose.activity).read())
            val file = File(compose.activity.noBackupFilesDir, "personal_chat_key.v1")
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(key))
            val replacement = "sk-or-v1-" + "b".repeat(64)
            vault.save(replacement)
            assertEquals(replacement, ChatKeyVault(compose.activity).read())
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            try { vault.read(); fail("Expected authenticated encryption to reject changed ciphertext") } catch (_: javax.crypto.AEADBadTagException) { }
        } finally { vault.remove() }
        assertNull(vault.read())
    }
    @Test fun failedReplyCanBeRetriedWithoutDuplicatingTheQuestion() {
        val calls = AtomicInteger()
        val vm = model(StudyChatTransport { _, messages ->
            assertEquals("What is 2 + 2?", messages.last().content)
            if (calls.incrementAndGet() == 1) throw ChatProblem("Temporary test failure")
            "2 + 2 = 4."
        })
        show(vm)
        compose.onNodeWithTag("chat_input").performTextInput("What is 2 + 2?")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5000) { vm.state.value.canRetry }
        compose.onNodeWithTag("chat_retry").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { vm.state.value.messages.any { it.content == "2 + 2 = 4." } }
        assertEquals(1, vm.state.value.messages.count { it.role == "user" })
        assertEquals(2, calls.get())
        compose.onNodeWithText("2 + 2 = 4.").assertExists()
        capture("chat-answer-dark")
        vm.clear()
    }
    @Test fun stoppingARequestRestoresSendingAndRetryWorks() {
        val calls = AtomicInteger()
        val vm = model(StudyChatTransport { _, _ -> if (calls.incrementAndGet() == 1) awaitCancellation() else "Ready again." })
        show(vm)
        compose.onNodeWithTag("chat_input").performTextInput("Help me study")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5000) { vm.state.value.busy && calls.get() == 1 }
        compose.onNodeWithText("Stop", useUnmergedTree = true).assertIsDisplayed().performClick()
        assertFalse(vm.state.value.busy)
        compose.onNodeWithTag("chat_retry").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { vm.state.value.messages.any { it.content == "Ready again." } }
        assertEquals(1, vm.state.value.messages.count { it.role == "user" })
        vm.clear()
    }
    @Test fun homeOpensChatKeyValidationAndReturnsToToday() {
        ChatKeyVault(compose.activity).remove()
        prefs.edit().clear().putBoolean("onboarding_v3", true).putString("theme_mode", "dark").commit()
        compose.setContent { StudyTheme(prefs, 0) { StudyRoot(prefs, 0, null, {}, {}, {}) } }
        compose.onNodeWithTag("home_action_chat").performScrollTo().performClick()
        compose.onNodeWithTag("screen_chat").assertIsDisplayed()
        compose.onNodeWithTag("chat_send").assertIsNotEnabled()
        capture("chat-empty-dark")
        compose.onNodeWithTag("chat_key_settings").performClick()
        compose.onNodeWithTag("chat_key_input").performTextInput("invalid-key")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("chat_save_key").performClick()
        compose.onNodeWithText("Enter a valid OpenRouter API key starting with sk-or-v1-.").assertExists()
        assertNull(ChatKeyVault(compose.activity).read())
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
    }
    @Test fun darkLoginUses3dArtworkAndGuestFlowStillWorks() {
        prefs.edit().clear().putString("theme_mode", "dark").commit()
        var guest = false
        compose.setContent { StudyTheme(prefs, 0) { WelcomeScreen(prefs) { _, _, _ -> guest = true } } }
        compose.onNodeWithTag("login_3d_art").assertIsDisplayed()
        capture("login-dark")
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        assertTrue(guest)
        assertFalse(prefs.getBoolean("google_signed_in", false))
    }
}
