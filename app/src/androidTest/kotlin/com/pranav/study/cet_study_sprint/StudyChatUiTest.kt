package com.pranav.study.cet_study_sprint

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    private fun model(transport: StudyChatTransport) = StudyChatViewModel(compose.activity.application as Application, transport)
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
        val vm = model(StudyChatTransport { messages, _ ->
            assertEquals("What is 2 + 2?", messages.last().content)
            if (calls.incrementAndGet() == 1) throw ChatProblem("Temporary test failure")
            ChatReply("2 + 2 = 4.", StudyChatClient.FALLBACK_MODEL)
        })
        show(vm)
        compose.onNodeWithTag("chat_input").performTextInput("What is 2 + 2?")
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
        val vm = model(StudyChatTransport { _, _ -> if (calls.incrementAndGet() == 1) awaitCancellation() else ChatReply("Ready again.", StudyChatClient.MODEL) })
        show(vm)
        compose.onNodeWithTag("chat_input").performTextInput("Help me study")
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5000) { vm.state.value.busy && calls.get() == 1 }
        compose.onNodeWithText("Stop", useUnmergedTree = true).assertIsDisplayed().performClick()
        assertFalse(vm.state.value.busy)
        compose.onNodeWithTag("chat_retry").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { vm.state.value.messages.any { it.content == "Ready again." } }
        assertEquals(1, vm.state.value.messages.count { it.role == "user" })
        vm.clear()
    }
    @Test fun floatingAvatarOpensChatAndReturnsToToday() {
        prefs.edit().clear().putBoolean("onboarding_v3", true).putString("theme_mode", "dark").commit()
        compose.setContent { StudyTheme(prefs, 0) { StudyRoot(prefs, 0, null, {}, {}, {}) } }
        compose.onNodeWithTag("chat_fab").performClick()
        compose.onNodeWithTag("screen_chat").assertIsDisplayed()
        compose.onNodeWithTag("chat_send").assertIsNotEnabled()
        compose.onNodeWithTag("chat_key_settings").assertDoesNotExist()
        capture("chat-empty-dark")
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
        compose.onNodeWithTag("pill_navigation").assertExists()
        compose.onNodeWithTag("report_bug").performClick()
        compose.onNodeWithTag("bug_description").assertExists()
    }
    @Test fun photosAreCappedAtFourAndRetryKeepsAttachments() {
        val calls = AtomicInteger()
        val vm = model(StudyChatTransport { _, photos ->
            assertEquals(4, photos.size)
            if (calls.incrementAndGet() == 1) throw ChatProblem("Temporary test failure")
            ChatReply("These are four photos.", StudyChatClient.FALLBACK_MODEL)
        })
        show(vm)
        val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        bitmap.recycle()
        compose.runOnIdle { vm.attach((1..5).map { ChatPhoto(it.toString(), "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP), bytes) }) }
        assertEquals(4, vm.state.value.photos.size)
        compose.onNodeWithTag("chat_attach").assertIsNotEnabled()
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5000) { vm.state.value.canRetry }
        compose.onNodeWithTag("chat_retry").performClick()
        compose.waitUntil(5000) { vm.state.value.messages.any { it.content == "These are four photos." } }
        assertEquals(1, vm.state.value.messages.count { it.role == "user" })
        assertEquals(2, calls.get())
        compose.onNodeWithTag("chat_resend").performClick()
        compose.waitUntil(5000) { calls.get() == 3 && !vm.state.value.busy }
        assertEquals(1, vm.state.value.messages.count { it.role == "user" })
        vm.clear()
    }
    @Test fun onlineMcqModeOffersTestWithoutAutomaticImport() {
        val calls = AtomicInteger()
        val text = "1. Energy is?\nA. h*f\nB. h/f\nC. f/h\nD. h+f\nAnswer: A"
        val transport = object : StudyChatTransport {
            override suspend fun reply(messages: List<ChatMessage>, photos: List<ChatPhoto>) = ChatReply(text, StudyChatClient.GPT_MODEL, quiz = true)
            override suspend fun replyWithOptions(messages: List<ChatMessage>, photos: List<ChatPhoto>, model: String, mode: String): ChatReply {
                assertEquals("web_mcq", mode)
                assertTrue(messages.last().content.contains("Difficulty: Hard"))
                calls.incrementAndGet(); return reply(messages, photos)
            }
        }
        val vm = model(transport); show(vm)
        val prefs = compose.activity.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val before = prefs.getString("owned_mcq_sets", "{}")
        compose.onNodeWithTag("chat_mcq_mode").performClick()
        compose.onNodeWithTag("chat_difficulty_easy").assertIsDisplayed()
        compose.onNodeWithTag("chat_difficulty_medium").assertIsSelected()
        compose.onNodeWithTag("chat_difficulty_hard").performClick().assertIsSelected()
        compose.onNodeWithTag("chat_input").performTextInput("Photon energy")
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5000) { vm.state.value.messages.any { it.quiz } }
        assertEquals(before, prefs.getString("owned_mcq_sets", "{}"))
        compose.onNodeWithTag("chat_start_web_quiz").performScrollTo().assertIsDisplayed()
        capture("chat-web-mcqs")
        compose.onNodeWithTag("chat_resend").performClick()
        compose.waitUntil(5000) { calls.get() == 2 && !vm.state.value.busy }
        vm.clear()
    }
    @Test fun applicationOwnsTheSessionAcrossActivityRecreation() {
        val application = compose.activity.application as StudyApplication
        val session = application.studyChat
        compose.runOnIdle { session.selectModel(StudyChatClient.GLM_MODEL) }
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { recreated ->
            assertSame(session, (recreated.application as StudyApplication).studyChat)
            assertEquals(StudyChatClient.GLM_MODEL, session.state.value.selectedModel)
        }
        compose.runOnIdle { session.clear() }
    }
    @Test fun studyBuddyHidesModelSelectionAndProviderLabels() {
        val vm = model(StudyChatTransport { _, _ -> ChatReply("Your study answer.", StudyChatClient.GPT_MODEL) })
        show(vm)
        compose.onNodeWithText("Model: Auto").assertDoesNotExist()
        compose.onNodeWithText("GPT-OSS 20B", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("chat_input").performTextInput("Explain energy")
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5000) { vm.state.value.messages.any { it.content == "Your study answer." } }
        compose.onNodeWithText("Study buddy", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("GPT-OSS 20B", substring = true).assertDoesNotExist()
        capture("chat-simple-study-buddy")
        vm.clear()
    }
    @Test fun remainingPhotoSlotsTrackAttachmentsRemovalAndSend() {
        val vm = model(StudyChatTransport { _, _ -> ChatReply("Photo received.", StudyChatClient.FALLBACK_MODEL) })
        show(vm)
        compose.onNodeWithText("＋ Photos · 4 remaining").assertExists()
        fun photo(id: String) = ChatPhoto(id, "data:image/jpeg;base64,AQ==", byteArrayOf())
        compose.runOnIdle { vm.attach(listOf(photo("slot1"))) }
        compose.onNodeWithText("＋ Photos · 3 remaining").assertExists()
        compose.runOnIdle { vm.attach(listOf(photo("slot2"))) }
        compose.onNodeWithText("＋ Photos · 2 remaining").assertExists()
        capture("chat-photo-slots")
        compose.runOnIdle { vm.removePhoto("slot1") }
        compose.onNodeWithText("＋ Photos · 3 remaining").assertExists()
        compose.runOnIdle { vm.attach(listOf(photo("slot3"), photo("slot4"), photo("slot5"))) }
        compose.onNodeWithText("＋ Photos · 0 remaining").assertExists()
        compose.onNodeWithTag("chat_attach").assertIsNotEnabled()
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5000) { !vm.state.value.busy }
        compose.onNodeWithText("＋ Photos · 4 remaining").assertExists()
        vm.clear()
    }
    @Test fun sevenDayStreakShowsRealMilestoneAndKeepsLongerCount() {
        val count = androidx.compose.runtime.mutableIntStateOf(2)
        compose.setContent { StudyTheme(prefs, 0) { StudyStreakCard(count.intValue) } }
        compose.onNodeWithText("Build your 7-day streak · 2/7 days").assertExists()
        compose.onNodeWithContentDescription("Streak day 2: complete").assertExists()
        compose.onNodeWithContentDescription("Streak day 3: not reached").assertExists()
        compose.runOnIdle { count.intValue = 9 }
        compose.onNodeWithText("9-day study streak").assertExists()
        compose.onNodeWithText("7-day milestone reached! Keep your flame alive.").assertExists()
        compose.onNodeWithContentDescription("Streak day 7: complete").assertExists()
        capture("streak-seven-day-flames")
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
