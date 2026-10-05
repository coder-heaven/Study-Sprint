package com.pranav.study.cet_study_sprint

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ChatRecoveryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("Saved request did not finish", condition())
    }
    @Test fun pendingRequestRestoresPhotosAndMigratesModelThenRetryDoesNotDuplicateQuestion() {
        val file = File(app.noBackupFilesDir, "chat_test_${UUID.randomUUID()}.json")
        val store = ChatSessionStore(app, file)
        val calls = AtomicInteger()
        val transport = object : StudyChatTransport {
            override suspend fun reply(messages: List<ChatMessage>, photos: List<ChatPhoto>): ChatReply = error("Model selection lost")
            override suspend fun replyWithModel(messages: List<ChatMessage>, photos: List<ChatPhoto>, model: String): ChatReply {
                assertEquals(if (calls.get() == 0) StudyChatClient.GPT_MODEL else "auto", model)
                assertEquals("Explain this", messages.last().content)
                assertEquals("data:image/jpeg;base64,abcd", photos.single().dataUrl)
                assertArrayEquals(byteArrayOf(1, 2, 3), photos.single().thumbnail)
                if (calls.incrementAndGet() == 1) awaitCancellation()
                return ChatReply("Saved answer", StudyChatClient.FALLBACK_MODEL)
            }
        }
        lateinit var first: StudyChatViewModel
        lateinit var restored: StudyChatViewModel
        try {
            main {
                first = StudyChatViewModel(app, transport, true, store)
                first.selectModel(StudyChatClient.GPT_MODEL)
                first.attach(listOf(ChatPhoto("photo", "data:image/jpeg;base64,abcd", byteArrayOf(1, 2, 3))))
                first.send("Explain this")
            }
            waitUntil { calls.get() == 1 }
            main { first.stop(); restored = StudyChatViewModel(app, transport, true, store) }
            assertEquals(1, restored.state.value.messages.size)
            assertTrue(restored.state.value.canRetry)
            assertEquals("auto", restored.state.value.selectedModel)
            assertEquals(1, calls.get()) // Restoring never silently resends photos or spends an allowance.
            main { restored.retry() }
            waitUntil { restored.state.value.messages.size == 2 && !restored.state.value.busy }
            assertEquals(1, restored.state.value.messages.count { it.role == "user" })
            assertNull(store.load()!!.pending)
            main {
                val reopened = StudyChatViewModel(app, transport, true, store)
                assertEquals(restored.state.value.messages, reopened.state.value.messages)
                reopened.clear()
            }
            assertTrue(store.load()!!.messages.isEmpty())
            assertNull(store.load()!!.reply)
            assertNull(store.load()!!.pending)
        } finally { main { first.stop() }; file.delete() }
    }
    @Test fun cachedPdfReplySurvivesRestartWithoutAnotherProviderCall() {
        val file = File(app.noBackupFilesDir, "chat_test_${UUID.randomUUID()}.json")
        val store = ChatSessionStore(app, file)
        try {
            val question = ChatMessage("user", ChatMcqPdf.PROMPT, 1)
            val photo = ChatPhoto("photo", "data:image/jpeg;base64,abcd", byteArrayOf(9))
            val reply = ChatReply("Completed AI output", StudyChatClient.FALLBACK_MODEL)
            store.save(ChatUiState(messages = listOf(question)), listOf(question) to listOf(photo), "auto", true, true, reply)
            val restored = store.load()!!
            assertEquals(reply, restored.reply)
            assertTrue(restored.pdf); assertTrue(restored.practice)
            assertEquals(question, restored.pending!!.first.single())
            assertEquals(photo.dataUrl, restored.pending.second.single().dataUrl)
            file.writeText("broken JSON")
            assertNull(store.load())
        } finally { file.delete() }
    }
}
