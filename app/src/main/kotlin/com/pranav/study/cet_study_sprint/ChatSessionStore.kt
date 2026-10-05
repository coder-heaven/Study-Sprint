package com.pranav.study.cet_study_sprint

import android.app.Application
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class SavedChatSession(val messages: List<ChatMessage>, val photos: List<ChatPhoto>, val model: String,
    val pending: Pair<List<ChatMessage>, List<ChatPhoto>>?, val pendingModel: String, val pdf: Boolean, val practice: Boolean, val reply: ChatReply?, val mode: String = "chat", val last: Pair<List<ChatMessage>, List<ChatPhoto>>? = null, val lastMode: String = "chat", val lastModel: String = "auto")
/** Local-only recovery file, excluded from Android backup. Contains no credentials. */
internal class ChatSessionStore(application: Application, file: File = File(application.noBackupFilesDir, "study-chat.json")) {
    private val atomic = AtomicFile(file)
    private fun messages(values: List<ChatMessage>) = JSONArray().apply { values.takeLast(80).forEach {
        put(JSONObject().put("role", it.role).put("content", it.content).put("photos", it.photoCount).put("model", it.model).put("quiz", it.quiz).put("sources", it.sources).put("suggestions", it.suggestions))
    } }
    private fun photos(values: List<ChatPhoto>) = JSONArray().apply { values.take(4).forEach {
        put(JSONObject().put("id", it.id).put("url", it.dataUrl).put("thumbnail", Base64.encodeToString(it.thumbnail, Base64.NO_WRAP)))
    } }
    private fun readMessages(values: JSONArray?) = (maxOf(0, (values?.length() ?: 0) - 80) until (values?.length() ?: 0)).map { index ->
        val v = values!!.getJSONObject(index)
        ChatMessage(v.getString("role"), v.getString("content").take(16000), v.optInt("photos"), v.optString("model").takeIf { it in StudyChatClient.models || it == StudyChatClient.SEARCH_MODEL || it == "gemini-2.5-flash" }, v.optBoolean("quiz"), v.optString("sources").take(8000), v.optString("suggestions").take(32768))
    }
    private fun readPhotos(values: JSONArray?) = (0 until (values?.length() ?: 0)).take(4).map { index ->
        val v = values!!.getJSONObject(index); val url = v.getString("url")
        require(url.length <= 350000)
        ChatPhoto(v.getString("id"), url, Base64.decode(v.getString("thumbnail"), Base64.NO_WRAP))
    }
    @Synchronized fun save(state: ChatUiState, pending: Pair<List<ChatMessage>, List<ChatPhoto>>?, pendingModel: String,
        pdf: Boolean, practice: Boolean, reply: ChatReply?, mode: String = "chat", last: Pair<List<ChatMessage>, List<ChatPhoto>>? = null, lastMode: String = "chat", lastModel: String = "auto") {
        val value = JSONObject().put("messages", messages(state.messages)).put("photos", photos(state.photos)).put("model", state.selectedModel)
            .put("pendingModel", pendingModel).put("pdf", pdf).put("practice", practice).put("mode", mode).put("lastMode", lastMode).put("lastModel", lastModel)
        if (last != null) value.put("last", JSONObject().put("messages", messages(last.first)).put("photos", photos(last.second)))
        if (pending != null) value.put("pending", JSONObject().put("messages", messages(pending.first)).put("photos", photos(pending.second)))
        if (reply != null) value.put("reply", JSONObject().put("answer", reply.answer).put("model", reply.model).put("quiz", reply.quiz).put("sources", reply.sources).put("suggestions", reply.suggestions))
        val output = atomic.startWrite()
        try { output.write(value.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }
    @Synchronized fun load(): SavedChatSession? = runCatching {
        require(atomic.baseFile.length() <= 8 * 1024 * 1024)
        val json = JSONObject(String(atomic.readFully(), Charsets.UTF_8)); val pending = json.optJSONObject("pending")
        val reply = json.optJSONObject("reply")
        SavedChatSession(readMessages(json.optJSONArray("messages")), readPhotos(json.optJSONArray("photos")),
            json.optString("model", "auto").takeIf { it in StudyChatClient.models } ?: "auto",
            pending?.let { readMessages(it.optJSONArray("messages")) to readPhotos(it.optJSONArray("photos")) },
            json.optString("pendingModel", "auto"), json.optBoolean("pdf"), json.optBoolean("practice"),
            reply?.let { ChatReply(it.getString("answer"), it.getString("model"), it.optBoolean("quiz"), it.optString("sources"), it.optString("suggestions")) },
            json.optString("mode", "chat"), json.optJSONObject("last")?.let { readMessages(it.optJSONArray("messages")) to readPhotos(it.optJSONArray("photos")) }, json.optString("lastMode", "chat"), json.optString("lastModel", "auto"))
    }.getOrNull()
}
