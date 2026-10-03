package com.pranav.study.cet_study_sprint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun StudyChatScreen(model: StudyChatViewModel, go: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    StudyChatContent(state, model::send, model::retry, model::stop, model::clear,
        model::saveKey, model::removeKey) { go("privacy") }
}

@Composable
internal fun StudyChatContent(
    state: ChatUiState, send: (String) -> Boolean, retry: () -> Unit, stop: () -> Unit,
    clear: () -> Unit, saveKey: (String) -> Unit, removeKey: () -> Unit, privacy: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var keyDialog by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { if (send(draft)) { draft = ""; keyboard?.hide() } }
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.busy, state.error) {
        if (list.layoutInfo.totalItemsCount > 0) list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1)
    }
    Column(Modifier.fillMaxSize().imePadding().testTag("study_chat")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Study buddy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Nemotron · Personal chat", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { keyDialog = true }, enabled = state.ready && !state.keyBusy,
                modifier = Modifier.testTag("chat_key_settings")) { Text(if (state.keyConfigured) "API key" else "Connect") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.messages.isEmpty()) item {
                StudyCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureArtwork(R.drawable.art_study_3d, 52.dp)
                        Text("Let's learn together", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Ask a concept question, work through a problem, or practise for CET, JEE and NEET.")
                    if (!state.keyConfigured) Button(onClick = { keyDialog = true }, enabled = state.ready,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Connect your API key") }
                    Text("When you send a question, your message and recent chat go to OpenRouter and its model provider. Notes, photos and app usage are never added automatically.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = privacy) { Text("Privacy details") }
                    listOf("Explain photon energy simply", "Give me one mole-concept MCQ", "Help me plan a 25-minute study session").forEach { prompt ->
                        OutlinedButton(onClick = { draft = prompt }, modifier = Modifier.fillMaxWidth()) { Text(prompt) }
                    }
                }
            }
            items(state.messages.size) { index ->
                val message = state.messages[index]
                val user = message.role == "user"
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (user) "You" else "Study buddy", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (state.error != null) item {
                Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("chat_error"))
                if (state.canRetry) OutlinedButton(onClick = retry, enabled = !state.busy && !state.keyBusy,
                    modifier = Modifier.testTag("chat_retry")) { Text("Retry") }
            }
        }
        if (state.busy) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Preparing your answer…", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = stop) { Text("Stop") }
        }
        if (state.messages.isNotEmpty()) TextButton(onClick = clear, modifier = Modifier.align(Alignment.End)) { Text("New chat") }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft, { draft = it.take(StudyChatClient.MAX_PROMPT + 1) },
                        label = { Text("Ask a study question") }, maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier.weight(1f).testTag("chat_input"))
                    Button(onClick = { submit() }, enabled = state.ready && state.keyConfigured &&
                        !state.busy && !state.keyBusy && draft.isNotBlank(),
                        modifier = Modifier.heightIn(min = 56.dp).testTag("chat_send"), contentPadding = PaddingValues(12.dp)) { Text("Send") }
                }
                Text("AI answers can be wrong. Check important answers. Chats stay in this session only.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (keyDialog) ChatKeyDialog(state, saveKey, removeKey) { keyDialog = false }
}

@Composable
private fun ChatKeyDialog(state: ChatUiState, save: (String) -> Unit, remove: () -> Unit, close: () -> Unit) {
    // Never put credentials in saved instance state, preferences, logging or backups.
    var key by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.keyBusy, state.keyConfigured, state.keyError) {
        if (submitted && !state.keyBusy && state.keyConfigured && state.keyError == null) { key = ""; close() }
    }
    AlertDialog(onDismissRequest = { if (!state.keyBusy) close() }, title = { Text("Connect Nemotron") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use your personal OpenRouter API key. It is encrypted on this phone and excluded from Android backups.")
                OutlinedTextField(key, { key = it }, label = { Text("OpenRouter API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("chat_key_input"))
                Text("Model: Nemotron 3 Ultra (free). Availability and request limits depend on OpenRouter.", style = MaterialTheme.typography.bodySmall)
                if (state.keyError != null) Text(state.keyError, color = MaterialTheme.colorScheme.error)
                if (state.keyConfigured) TextButton(onClick = { key = ""; remove() }, enabled = !state.keyBusy,
                    modifier = Modifier.testTag("chat_remove_key")) { Text("Remove saved key and clear chat") }
            }
        }, confirmButton = {
            TextButton(onClick = { keyboard?.hide(); submitted = true; save(key) }, enabled = key.isNotBlank() && !state.keyBusy,
                modifier = Modifier.testTag("chat_save_key")) { Text(if (state.keyBusy) "Saving…" else "Save key") }
        }, dismissButton = { TextButton(onClick = close, enabled = !state.keyBusy) { Text("Close") } })
}
