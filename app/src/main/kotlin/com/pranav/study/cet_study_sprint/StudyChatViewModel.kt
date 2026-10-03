package com.pranav.study.cet_study_sprint

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(), val ready: Boolean = false,
    val keyConfigured: Boolean = false, val busy: Boolean = false,
    val keyBusy: Boolean = false, val error: String? = null, val keyError: String? = null,
    val canRetry: Boolean = false
)

internal class StudyChatViewModel(application: Application, private val vault: ChatKeyStorage,
    private val transport: StudyChatTransport) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, ChatKeyVault(application), StudyChatClient())
    private val mutable = MutableStateFlow(ChatUiState())
    val state = mutable.asStateFlow()
    private var key: String? = null
    private var history = emptyList<ChatMessage>()
    private var pending: List<ChatMessage>? = null
    private var request: Job? = null
    private var generation = 0
    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { vault.read() } }
            key = result.getOrNull()
            mutable.value = mutable.value.copy(ready = true, keyConfigured = key != null,
                keyError = if (result.isFailure) "Your saved key could not be opened. Add it again to reconnect." else null)
        }
    }
    fun saveKey(raw: String) {
        if (!mutable.value.ready || mutable.value.keyBusy) return
        val chosen = raw.trim()
        if (!StudyChatClient.validKey(chosen)) {
            mutable.value = mutable.value.copy(keyError = "Enter a valid OpenRouter API key starting with sk-or-v1-.")
            return
        }
        stopRequest()
        mutable.value = mutable.value.copy(keyBusy = true, keyError = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { vault.save(chosen) } }
            if (result.isSuccess) key = chosen
            mutable.value = mutable.value.copy(keyBusy = false, keyConfigured = key != null,
                keyError = if (result.isFailure) "Could not securely save your key. Please try again." else null)
        }
    }
    fun removeKey() {
        if (mutable.value.keyBusy || !mutable.value.ready) return
        stopRequest()
        mutable.value = mutable.value.copy(keyBusy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { vault.remove() } }
            if (result.isSuccess) { key = null; history = emptyList(); pending = null }
            mutable.value = mutable.value.copy(keyBusy = false, keyConfigured = key != null,
                messages = if (result.isSuccess) emptyList() else mutable.value.messages,
                canRetry = false, keyError = if (result.isFailure) "Could not remove the saved key. Please retry." else null)
        }
    }
    fun send(raw: String): Boolean {
        val prompt = raw.trim()
        if (prompt.isEmpty() || mutable.value.busy || mutable.value.keyBusy || key == null) return false
        if (prompt.length > StudyChatClient.MAX_PROMPT) {
            mutable.value = mutable.value.copy(error = "Keep your question within 4,000 characters.")
            return false
        }
        val user = ChatMessage("user", prompt)
        mutable.value = mutable.value.copy(messages = (mutable.value.messages + user).takeLast(80))
        pending = (history + user).takeLast(StudyChatClient.MAX_CONTEXT_MESSAGES)
        runRequest()
        return true
    }
    fun retry() { if (!mutable.value.busy && !mutable.value.keyBusy && pending != null && key != null) runRequest() }
    private fun runRequest() {
        val messages = pending ?: return
        val chosenKey = key ?: return
        val token = ++generation
        mutable.value = mutable.value.copy(busy = true, error = null, canRetry = false)
        request = viewModelScope.launch {
            try {
                val answer = transport.reply(chosenKey, messages)
                if (token == generation) {
                    val assistant = ChatMessage("assistant", answer)
                    history = (messages + assistant).takeLast(StudyChatClient.MAX_CONTEXT_MESSAGES)
                    pending = null
                    mutable.value = mutable.value.copy(messages = (mutable.value.messages + assistant).takeLast(80), busy = false)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (token == generation) mutable.value = mutable.value.copy(busy = false, canRetry = true,
                    error = if (error is ChatProblem) error.message else "Could not get a response. Please retry.")
            }
        }
    }
    private fun stopRequest() {
        generation++; request?.cancel(); request = null
        mutable.value = mutable.value.copy(busy = false)
    }
    fun stop() {
        stopRequest()
        mutable.value = mutable.value.copy(error = "Response stopped. You can retry when ready.", canRetry = pending != null)
    }
    fun clear() {
        stopRequest(); history = emptyList(); pending = null
        mutable.value = mutable.value.copy(messages = emptyList(), error = null, canRetry = false)
    }
}
