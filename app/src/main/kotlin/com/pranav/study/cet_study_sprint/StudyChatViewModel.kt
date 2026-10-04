package com.pranav.study.cet_study_sprint

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(), val photos: List<ChatPhoto> = emptyList(),
    val ready: Boolean = true, val busy: Boolean = false, val photoBusy: Boolean = false,
    val error: String? = null, val canRetry: Boolean = false
)
internal class StudyChatViewModel(application: Application, private val transport: StudyChatTransport) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, StudyChatClient())
    private val mutable = MutableStateFlow(ChatUiState())
    val state = mutable.asStateFlow()
    private var history = emptyList<ChatMessage>()
    private var pending: Pair<List<ChatMessage>, List<ChatPhoto>>? = null
    private var request: Job? = null
    private var photoRequest: Job? = null
    private var generation = 0
    init { viewModelScope.launch(Dispatchers.IO) { runCatching { ChatKeyVault(application).remove() } } }
    fun importPhotos(uris: List<Uri>) {
        if (mutable.value.busy || mutable.value.photoBusy || uris.isEmpty()) return
        val room = StudyChatClient.MAX_PHOTOS - mutable.value.photos.size
        if (room <= 0) { mutable.value = mutable.value.copy(error = "You can attach up to 4 photos. Remove one to choose another."); return }
        mutable.value = mutable.value.copy(photoBusy = true, error = null)
        photoRequest = viewModelScope.launch {
            val selected = withContext(Dispatchers.IO) { uris.take(room).map { runCatching { ChatPhotos.load(getApplication(), it) } } }
            mutable.value = mutable.value.copy(photoBusy = false, photos = mutable.value.photos + selected.mapNotNull { it.getOrNull() },
                error = if (selected.any { it.isFailure }) "Some photos could not be opened. Use clear images smaller than 12 MB." else if (uris.size > room) "Only 4 photos can be attached at a time." else null)
        }
    }
    internal fun attach(photos: List<ChatPhoto>) {
        if (!mutable.value.busy && !mutable.value.photoBusy) {
            val combined = mutable.value.photos + photos
            mutable.value = mutable.value.copy(photos = combined.take(4), error = if (combined.size > 4) "Only 4 photos can be attached at a time." else null)
        }
    }
    fun removePhoto(id: String) { if (!mutable.value.busy) mutable.value = mutable.value.copy(photos = mutable.value.photos.filterNot { it.id == id }, error = null) }
    fun send(raw: String): Boolean {
        val prompt = raw.trim().ifBlank { if (mutable.value.photos.isNotEmpty()) "Help me understand the question in these photos." else "" }
        if (prompt.isEmpty() || mutable.value.busy || mutable.value.photoBusy) return false
        if (prompt.length > StudyChatClient.MAX_PROMPT) { mutable.value = mutable.value.copy(error = "Keep your question within 4,000 characters."); return false }
        val user = ChatMessage("user", prompt, mutable.value.photos.size)
        pending = StudyChatClient.bounded(history + user) to mutable.value.photos
        mutable.value = mutable.value.copy(messages = (mutable.value.messages + user).takeLast(80), photos = emptyList())
        runRequest(); return true
    }
    fun retry() { if (!mutable.value.busy && !mutable.value.photoBusy && pending != null) runRequest() }
    private fun runRequest() {
        val chosen = pending ?: return; val token = ++generation
        mutable.value = mutable.value.copy(busy = true, error = null, canRetry = false)
        request = viewModelScope.launch {
            try {
                val reply = transport.reply(chosen.first, chosen.second)
                if (token == generation) {
                    val assistant = ChatMessage("assistant", reply.answer, model = reply.model)
                    history = StudyChatClient.bounded(chosen.first + assistant); pending = null
                    mutable.value = mutable.value.copy(messages = (mutable.value.messages + assistant).takeLast(80), busy = false)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (token == generation) mutable.value = mutable.value.copy(busy = false, canRetry = true,
                error = if (error is ChatProblem) error.message else "Study buddy could not respond. Please retry.") }
        }
    }
    private fun stopRequest() { generation++; request?.cancel(); request = null; mutable.value = mutable.value.copy(busy = false) }
    fun stop() { stopRequest(); mutable.value = mutable.value.copy(error = "Stopped waiting. You can retry when ready.", canRetry = pending != null) }
    fun clear() { stopRequest(); photoRequest?.cancel(); photoRequest = null; history = emptyList(); pending = null
        mutable.value = ChatUiState() }
}
