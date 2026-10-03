package com.pranav.study.cet_study_sprint

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class ChatMessage(val role: String, val content: String)
internal class ChatProblem(message: String) : IOException(message)
internal fun interface StudyChatTransport {
    suspend fun reply(key: String, messages: List<ChatMessage>): String
}

internal class StudyChatClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val endpoint: String = "https://openrouter.ai/api/v1/chat/completions"
) : StudyChatTransport {
    companion object {
        const val MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"
        const val MAX_PROMPT = 4000
        const val MAX_CONTEXT_MESSAGES = 20
        fun validKey(key: String) = key.length in 30..256 && key.startsWith("sk-or-v1-") &&
            key.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
        fun requestBody(messages: List<ChatMessage>): String {
            val turns = JSONArray().put(JSONObject().put("role", "system").put("content",
                "You are Study Sprint's helpful study tutor for Indian Class 11 and 12 students preparing for MHT-CET, JEE or NEET. " +
                "Explain concepts clearly, show concise worked solutions when asked, and create original practice questions. " +
                "Ask for missing details instead of inventing them. Be honest about uncertainty. Answer in the student's language. " +
                "Use readable plain text; mathematical expressions should be understandable without a renderer."))
            require(messages.all { it.role == "user" || it.role == "assistant" })
            messages.takeLast(MAX_CONTEXT_MESSAGES).dropWhile { it.role != "user" }.forEach {
                turns.put(JSONObject().put("role", it.role).put("content", it.content.take(16000)))
            }
            return JSONObject().put("model", MODEL).put("messages", turns).put("stream", false)
                .put("max_tokens", 4096).put("reasoning", JSONObject().put("enabled", false).put("exclude", true)).toString()
        }
        fun statusMessage(code: Int): String = when (code) {
            401 -> "Your OpenRouter key was not accepted. Update it in API key settings."
            402 -> "Your OpenRouter account needs available credits or a free-model allowance."
            403 -> "OpenRouter could not allow this request. Check your account and key permissions."
            404 -> "This Nemotron model is unavailable. Please try again later."
            429 -> "OpenRouter's request limit was reached. Wait a little, then retry."
            else -> "Nemotron is temporarily unavailable. Please try again."
        }
        fun answer(body: String): String {
            val json = JSONObject(body)
            if (json.has("error")) throw ChatProblem("Nemotron could not complete this request. Please retry.")
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
            val content = choice?.optJSONObject("message")?.opt("content") as? String
            if (content.isNullOrBlank()) throw ChatProblem("No final answer was returned. Try a shorter question or retry later.")
            return content.trim().take(16000) + if (choice?.optString("finish_reason") == "length")
                "\n\n[Response limit reached. Ask me to continue.]" else ""
        }
    }

    override suspend fun reply(key: String, messages: List<ChatMessage>): String {
        require(validKey(key))
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $key")
            .header("X-Title", "Study Sprint").post(requestBody(messages).toRequestBody("application/json".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        ChatProblem("Could not connect to Nemotron. Check your internet connection, then retry."))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw ChatProblem(statusMessage(it.code))
                            val body = it.body ?: throw ChatProblem("Nemotron returned an empty response. Please retry.")
                            val bytes = ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val chunk = ByteArray(8192)
                                while (true) {
                                    val count = input.read(chunk)
                                    if (count == -1) break
                                    if (bytes.size() + count > 1024 * 1024) throw ChatProblem("The response was too large. Ask a shorter question.")
                                    bytes.write(chunk, 0, count)
                                }
                            }
                            answer(bytes.toString("UTF-8"))
                        }
                    }
                    if (continuation.isActive) result.fold(
                        { continuation.resume(it) },
                        { continuation.resumeWithException(if (it is ChatProblem) it else ChatProblem("Nemotron returned an unreadable response. Please retry.")) }
                    )
                }
            })
        }
    }
}
