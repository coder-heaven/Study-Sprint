package com.pranav.study.cet_study_sprint

import android.app.Application
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class SavedChatSession(val messages: List<ChatMessage>, val photos: List<ChatPhoto>, val model: String,
    val pending: Pair<List<ChatMessage>, List<ChatPhoto>>?, val pendingModel: String, val pdf: Boolean, val practice: Boolean, val reply: ChatReply?)
/** Local-only recovery file, excluded from Android backup. Contains no credentials. */
internal class ChatSessionStore(application: Application, file: File = File(application.noBackupFilesDir, "study-chat.json")) {
    private val atomic = AtomicFile(file)
    private fun messages(values: List<ChatMessage>) = JSONArray().apply { values.takeLast(80).forEach {
        put(JSONObject().put("role", it.role).put("content", it.content).put("photos", it.photoCount).put("model", it.model))
    } }
    private fun photos(values: List<ChatPhoto>) = JSONArray().apply { values.take(4).forEach {
        put(JSONObject().put("id", it.id).put("url", it.dataUrl).put("thumbnail", Base64.encodeToString(it.thumbnail, Base64.NO_WRAP)))
    } }
    private fun readMessages(values: JSONArray?) = (maxOf(0, (values?.length() ?: 0) - 80) until (values?.length() ?: 0)).map { index ->
        val v = values!!.getJSONObject(index)
        ChatMessage(v.getString("role"), v.getString("content").take(16000), v.optInt("photos"), v.optString("model").takeIf { it in StudyChatClient.models })
    }
    private fun readPhotos(values: JSONArray?) = (0 until (values?.length() ?: 0)).take(4).map { index ->
        val v = values!!.getJSONObject(index); val url = v.getString("url")
        require(url.length <= 350000)
        ChatPhoto(v.getString("id"), url, Base64.decode(v.getString("thumbnail"), Base64.NO_WRAP))
    }
    @Synchronized fun save(state: ChatUiState, pending: Pair<List<ChatMessage>, List<ChatPhoto>>?, pendingModel: String,
        pdf: Boolean, practice: Boolean, reply: ChatReply?) {
        val value = JSONObject().put("messages", messages(state.messages)).put("photos", photos(state.photos)).put("model", state.selectedModel)
            .put("pendingModel", pendingModel).put("pdf", pdf).put("practice", practice)
        if (pending != null) value.put("pending", JSONObject().put("messages", messages(pending.first)).put("photos", photos(pending.second)))
        if (reply != null) value.put("reply", JSONObject().put("answer", reply.answer).put("model", reply.model))
        val output = atomic.startWrite()
        try { output.write(value.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }
    @Synchronized fun load(): SavedChatSession? = runCatching {
        require(atomic.baseFile.length() <= 4 * 1024 * 1024)
        val json = JSONObject(String(atomic.readFully(), Charsets.UTF_8)); val pending = json.optJSONObject("pending")
        val reply = json.optJSONObject("reply")
        SavedChatSession(readMessages(json.optJSONArray("messages")), readPhotos(json.optJSONArray("photos")),
            json.optString("model", "auto").takeIf { it in StudyChatClient.models } ?: "auto",
            pending?.let { readMessages(it.optJSONArray("messages")) to readPhotos(it.optJSONArray("photos")) },
            json.optString("pendingModel", "auto"), json.optBoolean("pdf"), json.optBoolean("practice"),
            reply?.let { ChatReply(it.getString("answer"), it.getString("model")) })
    }.getOrNull()
}
