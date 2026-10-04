package com.pranav.study.cet_study_sprint

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class ChatMessage(val role: String, val content: String, val photoCount: Int = 0, val model: String? = null)
internal data class ChatReply(val answer: String, val model: String)
internal data class ChatSession(val idToken: String, val appToken: String)
internal class ChatProblem(message: String) : IOException(message)
internal fun interface StudyChatTransport {
    suspend fun reply(messages: List<ChatMessage>, photos: List<ChatPhoto>): ChatReply
}
internal class StudyChatClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS).callTimeout(360, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val endpoint: () -> String = { BuildConfig.STUDY_CHAT_URL },
    private val session: suspend () -> ChatSession = {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().awaitLeaderboardTask().user
            ?: throw ChatProblem("Could not connect to Study Sprint. Please retry.")
        val token = user.getIdToken(false).awaitLeaderboardTask().token
            ?: throw ChatProblem("Could not reconnect. Please retry.")
        val appToken = FirebaseAppCheck.getInstance().getAppCheckToken(false).awaitLeaderboardTask().token
        ChatSession(token, appToken)
    }
) : StudyChatTransport {
    companion object {
        const val MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"
        const val FALLBACK_MODEL = "moonshotai/kimi-k3"
        const val MAX_PROMPT = 4000
        const val MAX_CONTEXT_MESSAGES = 20
        const val MAX_PHOTOS = 4
        // Retained only for reading/removing the obsolete v4.8 personal-key vault.
        fun validKey(key: String) = key.length in 30..256 && key.startsWith("sk-or-v1-") &&
            key.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
        fun bounded(messages: List<ChatMessage>): List<ChatMessage> {
            var turns = messages.takeLast(MAX_CONTEXT_MESSAGES).dropWhile { it.role != "user" }
            while (turns.size > 1 && turns.sumOf { it.content.length } > 24000) turns = turns.drop(1).dropWhile { it.role != "user" }
            return turns
        }
        fun requestBody(messages: List<ChatMessage>, photos: List<ChatPhoto>): String {
            require(photos.size <= MAX_PHOTOS)
            require(messages.all { it.role == "user" || it.role == "assistant" })
            val turns = JSONArray()
            bounded(messages).forEach { turns.put(JSONObject().put("role", it.role).put("content", it.content)) }
            val images = JSONArray()
            photos.forEach { require(it.dataUrl.length <= 350000); images.put(it.dataUrl) }
            return JSONObject().put("data", JSONObject().put("messages", turns).put("photos", images)).toString()
        }
        fun statusMessage(code: Int): String = when (code) {
            401, 403 -> "Study Sprint could not verify this installation. Retry, or report this to the app owner."
            404 -> "Shared study chat is awaiting server activation. Please try after the app owner enables it."
            429 -> "The study chat allowance was reached. Wait a little, then retry."
            else -> "Study buddy is temporarily unavailable. Please try again later."
        }
        fun answer(body: String): ChatReply {
            val json = JSONObject(body)
            if (json.has("error")) {
                val status = json.optJSONObject("error")?.optString("status")
                throw ChatProblem(when (status) {
                    "RESOURCE_EXHAUSTED" -> "The study chat allowance is reached. Please try later."
                    "FAILED_PRECONDITION" -> "Shared study chat needs setup or attention from the app owner."
                    "INVALID_ARGUMENT" -> "This question or photo could not be accepted. Use up to 4 photos and a shorter question."
                    else -> "Study buddy could not respond. Please retry later."
                })
            }
            val result = json.optJSONObject("result") ?: json.optJSONObject("data")
            val text = result?.opt("answer") as? String
            val model = result?.optString("model")
            if (text.isNullOrBlank() || model !in listOf(MODEL, FALLBACK_MODEL)) throw ChatProblem("No readable answer was returned. Please retry.")
            return ChatReply(text.trim().take(16000), model!!)
        }
    }
    override suspend fun reply(messages: List<ChatMessage>, photos: List<ChatPhoto>): ChatReply {
        val url = endpoint()
        if (url.isBlank()) throw ChatProblem("Shared study chat is awaiting Render setup. Please update after the app owner activates it.")
        val credential = try { session() } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { throw ChatProblem("Could not verify Study Sprint. Check your connection, then retry or report the issue.") }
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${credential.idToken}")
            .header("X-Firebase-AppCheck", credential.appToken)
            .post(requestBody(messages, photos).toRequestBody("application/json".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ChatProblem("Could not reach Study buddy. Check your connection and retry."))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw ChatProblem(statusMessage(it.code))
                            val bytes = ByteArrayOutputStream()
                            (it.body ?: throw ChatProblem("The chat service returned no answer.")).byteStream().use { input ->
                                val chunk = ByteArray(8192)
                                while (true) {
                                    val count = input.read(chunk); if (count == -1) break
                                    if (bytes.size() + count > 1024 * 1024) throw ChatProblem("The answer was too large. Ask a shorter question.")
                                    bytes.write(chunk, 0, count)
                                }
                            }
                            answer(bytes.toString("UTF-8"))
                        }
                    }
                    if (continuation.isActive) result.fold({ continuation.resume(it) },
                        { continuation.resumeWithException(if (it is ChatProblem) it else ChatProblem("The chat service returned an unreadable answer.")) })
                }
            })
        }
    }
}
