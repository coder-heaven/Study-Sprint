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

internal data class ChatMessage(val role: String, val content: String, val photoCount: Int = 0, val model: String? = null, val quiz: Boolean = false, val sources: String = "", val suggestions: String = "")
internal data class ChatReply(val answer: String, val model: String, val quiz: Boolean = false, val sources: String = "", val suggestions: String = "")
internal data class ChatSession(val idToken: String, val appToken: String)
internal class ChatProblem(message: String) : IOException(message)
internal fun interface StudyChatTransport {
    suspend fun reply(messages: List<ChatMessage>, photos: List<ChatPhoto>): ChatReply
    suspend fun replyWithOptions(messages: List<ChatMessage>, photos: List<ChatPhoto>, model: String, mode: String): ChatReply = replyWithModel(messages, photos, model)
    suspend fun replyWithModel(messages: List<ChatMessage>, photos: List<ChatPhoto>, model: String): ChatReply = reply(messages, photos)
}
internal class StudyChatClient(
    private val profile: () -> Pair<String, String> = { "CET" to "11" },
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
        const val GPT_MODEL = "openai/gpt-oss-20b"
        const val GEMINI_MODEL = "gemini-2.5-flash"
        const val MUSE_MODEL = "meta/muse-glimmer-30b"
        const val MISTRAL_MODEL = "mistral-small-latest"
        private val responseModels get() = models.keys + setOf(GEMINI_MODEL, MUSE_MODEL, MISTRAL_MODEL)
        const val GLM_MODEL = "z-ai/glm-5.3"
        val models = linkedMapOf("auto" to "Auto", MODEL to "Nemotron", FALLBACK_MODEL to "Kimi K3", GPT_MODEL to "GPT-OSS 20B", GLM_MODEL to "GLM 5.3")
        fun modelName(model: String?) = if (model == GEMINI_MODEL) "Gemini Flash" else models[model] ?: "AI"
        fun introduction(model: String, exam: String) = when (model) {
            MODEL -> "Nemotron helps explain $exam concepts and work through study questions."
            FALLBACK_MODEL -> "Kimi K3 can read your study photos and help with $exam questions and photo MCQs."
            GPT_MODEL -> "GPT-OSS 20B helps with text-based $exam questions, worked solutions and revision."
            GLM_MODEL -> "GLM 5.3 helps break down text-based $exam concepts and practise question-solving."
            else -> "Auto chooses an available study model for $exam. Photos prefer Gemini Flash when configured, with Kimi K3 backup."
        }
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
        fun requestBody(messages: List<ChatMessage>, photos: List<ChatPhoto>, model: String = "auto", exam: String = "CET", grade: String = "11", mode: String = "chat"): String {
            require(photos.size <= MAX_PHOTOS)
            require(messages.all { it.role == "user" || it.role == "assistant" })
            val turns = JSONArray()
            bounded(messages).forEach { turns.put(JSONObject().put("role", it.role).put("content", it.content)) }
            val images = JSONArray()
            photos.forEach { require(it.dataUrl.length <= 350000); images.put(it.dataUrl) }
            return JSONObject().put("data", JSONObject().put("messages", turns).put("photos", images).put("model", model).put("exam", exam).put("grade", grade).put("mode", mode)).toString()
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
                val message = json.optJSONObject("error")?.optString("message")
                val searchErrors = setOf("No complete four-option MCQs were found. Try another topic.",
                    "No verified sources for this exam were found. Try another topic.",
                    "Google study service could not respond. Retry later.",
                    "Google study service could not return a complete answer. Retry later.",
                    "Online study search allowance is reached. Try again later.",
                    "Google study search needs configuration by the app owner.")
                if (message in searchErrors) throw ChatProblem(requireNotNull(message))
                throw ChatProblem(when (status) {
                    "RESOURCE_EXHAUSTED" -> "The study chat allowance is reached. Please try later."
                    "FAILED_PRECONDITION" -> if (json.optJSONObject("error")?.optString("message") == "Online MCQ search needs GEMINI_API_KEY in Render.") "Online MCQ search needs Google setup by the app owner. Normal study chat is still available." else "Shared study chat needs setup or attention from the app owner."
                    "INVALID_ARGUMENT" -> "This question or photo could not be accepted. Use up to 4 photos and a shorter question."
                    else -> "Study buddy could not respond. Please retry later."
                })
            }
            val result = (json.optJSONObject("result") ?: json.optJSONObject("data")) ?: throw ChatProblem("The chat service returned no result.")
            val text = result?.opt("answer") as? String
            val model = result?.optString("model")
            if (text.isNullOrBlank() || model !in responseModels) throw ChatProblem("No readable answer was returned. Please retry.")
            val sources = result.optJSONArray("sources")
            val links = (0 until (sources?.length() ?: 0)).take(8).mapNotNull { i ->
                val source = sources!!.optJSONObject(i) ?: return@mapNotNull null
                val url = source.optString("url"); val title = source.optString("title").replace("[", "").replace("]", "")
                if (url.startsWith("https://")) "[$title]($url)" else null
            }.joinToString("\n\n")
            return ChatReply(text.trim().take(16000), model!!, result.optBoolean("quiz"), links, result.optString("suggestions").take(32768))
        }
    }
    override suspend fun reply(messages: List<ChatMessage>, photos: List<ChatPhoto>): ChatReply = replyWithModel(messages, photos, "auto")
    override suspend fun replyWithModel(messages: List<ChatMessage>, photos: List<ChatPhoto>, model: String): ChatReply = replyWithOptions(messages, photos, model, "chat")
    override suspend fun replyWithOptions(messages: List<ChatMessage>, photos: List<ChatPhoto>, model: String, mode: String): ChatReply {
        val url = endpoint()
        if (url.isBlank()) throw ChatProblem("Shared study chat is awaiting Render setup. Please update after the app owner activates it.")
        val credential = try { session() } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { throw ChatProblem("Could not verify Study Sprint. Check your connection, then retry or report the issue.") }
        val (exam, grade) = profile()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${credential.idToken}")
            .header("X-Firebase-AppCheck", credential.appToken)
            .post(requestBody(messages, photos, model, exam, grade, mode).toRequestBody("application/json".toMediaType())).build()
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
                            if (!it.isSuccessful && it.code != 503) throw ChatProblem(statusMessage(it.code))
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
