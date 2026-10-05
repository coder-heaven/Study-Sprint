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
        val body = JSONObject(StudyChatClient.requestBody(messages, emptyList()))
        assertFalse(body.has("model"))
        val sent = body.getJSONObject("data").getJSONArray("messages")
        assertTrue(sent.length() <= 20)
        assertEquals("user", sent.getJSONObject(0).getString("role"))
        assertEquals("Turn 40", sent.getJSONObject(sent.length() - 1).getString("content"))
        assertFalse(body.toString().contains(key))
    }
    @Test fun successfulResponseShowsFinalContentOnly() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"result":{"answer":"Frequency is cycles per second.","model":"moonshotai/kimi-k3"}}"""))
            val answer = StudyChatClient(endpoint = { server.url("/chat").toString() }, session = { ChatSession("firebase-token", "app-token") }).reply(listOf(ChatMessage("user", "Define frequency")), emptyList())
            assertEquals("Frequency is cycles per second.", answer.answer)
            assertEquals(StudyChatClient.FALLBACK_MODEL, answer.model)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer firebase-token", request.getHeader("Authorization"))
            assertEquals("app-token", request.getHeader("X-Firebase-AppCheck"))
            assertFalse(request.body.readUtf8().contains(key))
            assertEquals("POST", request.method)
        } finally { server.shutdown() }
    }
    @Test fun selectedModelAndExamReachAuthenticatedBackend() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"result":{"answer":"Use NCERT terms.","model":"z-ai/glm-5.3"}}"""))
            val client = StudyChatClient(profile = { "NEET" to "12" }, endpoint = { server.url("/chat").toString() }, session = { ChatSession("firebase-token", "app-token") })
            assertEquals(StudyChatClient.GLM_MODEL, client.replyWithModel(listOf(ChatMessage("user", "Explain mitosis")), emptyList(), StudyChatClient.GLM_MODEL).model)
            val data = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()).getJSONObject("data")
            assertEquals("NEET", data.getString("exam")); assertEquals("12", data.getString("grade"))
            assertEquals(StudyChatClient.GLM_MODEL, data.getString("model"))
        } finally { server.shutdown() }
    }
    @Test fun changingProfileExamChangesTheNextOnlineMcqRequest() = runBlocking {
        val server = MockWebServer()
        try {
            var selectedExam = "CET"
            val client = StudyChatClient(profile = { selectedExam to "12" }, endpoint = { server.url("/chat").toString() },
                session = { ChatSession("firebase-token", "app-token") })
            for (exam in listOf("CET", "JEE", "NEET")) {
                selectedExam = exam
                server.enqueue(MockResponse().setBody("""{"result":{"answer":"1. Question?\\nA. One\\nB. Two\\nC. Three\\nD. Four\\nAnswer: A","model":"tavily/search","quiz":true}}"""))
                client.replyWithOptions(listOf(ChatMessage("user", "Motion")), emptyList(), "auto", "web_mcq")
                val data = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()).getJSONObject("data")
                assertEquals(exam, data.getString("exam"))
                assertEquals("web_mcq", data.getString("mode"))
            }
        } finally { server.shutdown() }
    }
    @Test fun rateLimitMessageDoesNotExposeProviderBodyOrCredential() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(429).setBody("private provider body $key"))
            try {
                StudyChatClient(endpoint = { server.url("/chat").toString() }, session = { ChatSession("firebase-token", "app-token") }).reply(listOf(ChatMessage("user", "Hi")), emptyList())
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
            val client = StudyChatClient(endpoint = { server.url("/chat").toString() }, session = { ChatSession("firebase-token", "app-token") })
            val job = launch { client.reply(listOf(ChatMessage("user", "Hi")), emptyList()) }
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
    @Test fun newAutomaticBackupResponsesAreAcceptedWithoutExposingASelector() {
        for (model in listOf(StudyChatClient.MUSE_MODEL, StudyChatClient.MISTRAL_MODEL, StudyChatClient.SEARCH_MODEL)) {
            val reply = StudyChatClient.answer(JSONObject().put("result", JSONObject().put("answer", "Use E = hf.").put("model", model)).toString())
            assertEquals("Use E = hf.", reply.answer)
            assertEquals(model, reply.model)
            assertFalse(StudyChatClient.models.containsKey(model))
        }
    }
    @Test fun missingTavilyKeyIsActionable() {
        try {
            StudyChatClient.answer("""{"error":{"status":"FAILED_PRECONDITION","message":"Online MCQ search needs TAVILY_API_KEY in Render."}}""")
            fail("Expected setup error")
        } catch (error: ChatProblem) { assertTrue(error.message!!.contains("Tavily key in Render")) }
    }
    @Test fun onlineSearchErrorsExplainRecoveryWithoutEchoingArbitraryServerText() {
        val message = "No verified sources for this exam were found. Try another topic."
        for ((text, expected) in listOf(message to message, "private upstream credential $key" to "Study buddy could not respond. Please retry later.")) {
            try {
                StudyChatClient.answer(JSONObject().put("error", JSONObject().put("status", "UNAVAILABLE").put("message", text)).toString())
                fail("Expected search error")
            } catch (error: ChatProblem) { assertEquals(expected, error.message); assertFalse(error.message!!.contains(key)) }
        }
    }
}
