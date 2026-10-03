package com.pranav.study.cet_study_sprint

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class StudyChatClientTest {
    private val key = "sk-or-v1-" + "a".repeat(64)
    @Test fun requestUsesRequestedFreeModelAndBoundedUserAssistantContext() {
        val messages = (0..40).map { ChatMessage(if (it % 2 == 0) "user" else "assistant", "Turn $it") }
        val body = JSONObject(StudyChatClient.requestBody(messages))
        assertEquals("nvidia/nemotron-3-ultra-550b-a55b:free", body.getString("model"))
        assertFalse(body.getBoolean("stream"))
        val sent = body.getJSONArray("messages")
        assertTrue(sent.length() <= 21)
        assertEquals("system", sent.getJSONObject(0).getString("role"))
        assertEquals("user", sent.getJSONObject(1).getString("role"))
        assertEquals("Turn 40", sent.getJSONObject(sent.length() - 1).getString("content"))
        assertTrue(body.getJSONObject("reasoning").getBoolean("exclude"))
        assertFalse(body.toString().contains(key))
    }
    @Test fun successfulResponseShowsFinalContentOnly() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Frequency is cycles per second.","reasoning":"private reasoning"},"finish_reason":"stop"}]}"""))
            val answer = StudyChatClient(endpoint = server.url("/chat").toString()).reply(key, listOf(ChatMessage("user", "Define frequency")))
            assertEquals("Frequency is cycles per second.", answer)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer $key", request.getHeader("Authorization"))
            assertEquals("POST", request.method)
        } finally { server.shutdown() }
    }
    @Test fun rateLimitMessageDoesNotExposeProviderBodyOrCredential() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(429).setBody("private provider body $key"))
            try {
                StudyChatClient(endpoint = server.url("/chat").toString()).reply(key, listOf(ChatMessage("user", "Hi")))
                fail("Expected rate-limit handling")
            } catch (error: ChatProblem) {
                assertEquals(StudyChatClient.statusMessage(429), error.message)
                assertFalse(error.message!!.contains(key))
            }
        } finally { server.shutdown() }
    }
    @Test fun emptyFinalContentNeverFallsBackToReasoning() {
        try {
            StudyChatClient.answer("""{"choices":[{"message":{"content":null,"reasoning":"private reasoning"},"finish_reason":"length"}]}""")
            fail("Expected empty-answer handling")
        } catch (error: ChatProblem) { assertFalse(error.message!!.contains("private reasoning")) }
    }
    @Test fun cancellationStopsAnOutstandingHttpCall() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = StudyChatClient(endpoint = server.url("/chat").toString())
            val job = launch { client.reply(key, listOf(ChatMessage("user", "Hi"))) }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        } finally { server.shutdown() }
    }
    @Test fun credentialValidationRejectsWhitespaceAndNonAsciiHeaders() {
        assertTrue(StudyChatClient.validKey(key))
        assertFalse(StudyChatClient.validKey("$key\n"))
        assertFalse(StudyChatClient.validKey("sk-or-v1-" + "é".repeat(64)))
        assertFalse(StudyChatClient.validKey("not-an-openrouter-key"))
    }
}
