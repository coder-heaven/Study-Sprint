package com.pranav.study.cet_study_sprint

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdateInfo(
    val tag: String, val version: String, val notes: String,
    val downloadUrl: String, val releaseUrl: String, val checksumUrl: String = ""
)

internal object AppUpdateChecker {
    private const val API = "https://api.github.com/repos/coder-heaven/Study-Sprint/releases/latest"
    private const val PREFS = "study_sprint_updates"
    private const val INTERVAL = 6L * 60L * 60L * 1000L
    private const val SNOOZE = 24L * 60L * 60L * 1000L

    suspend fun check(context: Context, force: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cached = cached(context)
        if (!force && now - prefs.getLong("last_check", 0) < INTERVAL) {
            return@withContext cached?.takeUnless { snoozed(context, it.tag, now) }
        }
        val fetched = runCatching { fetch() }.getOrNull()
        prefs.edit().putLong("last_check", now).putBoolean("check_failed", fetched == null).apply()
        if (fetched != null) save(context, fetched)
        (fetched ?: cached)?.takeIf { isNewer(it.version, installed(context)) && !snoozed(context, it.tag, now) }
    }

    /** Preserve the existing required-update policy; installation always needs Android consent. */
    suspend fun required(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val fetched = runCatching { fetch() }.getOrNull()
        if (fetched != null) save(context, fetched)
        (fetched ?: cached(context))?.takeIf { isNewer(it.version, installed(context)) }
    }

    private fun fetch(): AppUpdateInfo {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Study-Sprint-Android")
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK)
            val body = connection.inputStream.use { readUpdateBytes(it, 1024 * 1024) }
            return AppUpdateSecurity.parseRelease(JSONObject(body.toString(Charsets.UTF_8)))
        } finally { connection.disconnect() }
    }

    fun checkFailed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("check_failed", false)

    fun snooze(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("snoozed_tag", tag).putLong("snoozed_until", System.currentTimeMillis() + SNOOZE).apply()
    }

    internal fun isNewer(available: String, installed: String): Boolean {
        val a = parts(available)
        val b = parts(installed)
        repeat(maxOf(a.size, b.size)) { index ->
            val next = a.getOrElse(index) { 0 }
            val current = b.getOrElse(index) { 0 }
            if (next != current) return next > current
        }
        return false
    }

    private fun parts(value: String) = Regex("\\d+").findAll(value)
        .mapNotNull { it.value.toIntOrNull() }.toList().ifEmpty { listOf(0) }
    private fun installed(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    private fun save(context: Context, item: AppUpdateInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tag", item.tag).putString("version", item.version).putString("notes", item.notes)
            .putString("download", item.downloadUrl).putString("release", item.releaseUrl)
            .putString("checksum", item.checksumUrl).apply()
    }

    private fun cached(context: Context): AppUpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString("tag", null) ?: return null
        val download = prefs.getString("download", "").orEmpty()
        if (!AppUpdateSecurity.officialAsset(download)) return null
        // Migrate older caches that did not store the companion checksum URL.
        val checksum = prefs.getString("checksum", null) ?: "$download.sha256"
        return AppUpdateInfo(tag, prefs.getString("version", tag.removePrefix("v")) ?: tag,
            prefs.getString("notes", "").orEmpty(), download,
            prefs.getString("release", AppUpdateSecurity.RELEASES).orEmpty(), checksum)
            .takeIf { isNewer(it.version, installed(context)) }
    }

    private fun snoozed(context: Context, tag: String, now: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("snoozed_tag", null) == tag && now < prefs.getLong("snoozed_until", 0)
    }
}

@Composable
internal fun UpdateReleaseNotes(notes: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("update_notes")) {
        Text(notes.ifBlank { "A new official Study Sprint update is available." },
            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        Text("End of update features", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 12.dp).testTag("update_notes_end"))
    }
}

@Composable
internal fun RequiredUpdateScreen(update: AppUpdateInfo, onRetry: () -> Unit, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // All headings/features can scroll, leaving install controls reachable on small screens.
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("required_update_notes")) {
            Text("Update available", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Study Sprint ${update.tag} is ready. Update to continue using the app.")
            Spacer(Modifier.height(16.dp))
            Text("What’s new", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(update.notes.ifBlank { "A new official update is available." })
            Text("End of update features", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 12.dp).testTag("update_notes_end"))
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) { action() }
        TextButton(onClick = onRetry, modifier = Modifier.testTag("update_check_again")) { Text("Check again") }
    }
}

@Composable
internal fun RequiredUpdateGate(revision: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(true) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision, retry) {
        // Do not destroy activity-result launchers while permission/installer screens return.
        update = AppUpdateChecker.required(context)
        checking = false
    }
    val item = update
    if (checking) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (item != null) {
        androidx.activity.compose.BackHandler(enabled = true) { }
        RequiredUpdateScreen(item, onRetry = { retry++ }) { UpdateInstallAction(item) }
    } else content()
}

@Composable
internal fun UpdatePromptDialog(item: AppUpdateInfo, onDismiss: () -> Unit, action: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Update available · ${item.tag}") },
        text = { UpdateReleaseNotes(item.notes, Modifier.heightIn(max = 280.dp)) },
        confirmButton = action,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } })
}

@Composable
internal fun UpdatePromptHost() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    LaunchedEffect(Unit) { update = AppUpdateChecker.check(context, force = true) }
    update?.let { item ->
        UpdatePromptDialog(item, onDismiss = { AppUpdateChecker.snooze(context, item.tag); update = null }) {
            UpdateInstallAction(item)
        }
    }
}

@Composable
internal fun UpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var status by remember { mutableStateOf("Check for official Study Sprint updates. Downloads stay inside the app.") }
    StudyCard {
        Text("App updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        update?.let { item ->
            UpdateReleaseNotes(item.notes, Modifier.heightIn(max = 240.dp))
            Spacer(Modifier.height(12.dp))
            UpdateInstallAction(item)
        }
        OutlinedButton(onClick = {
            scope.launch {
                checking = true
                update = AppUpdateChecker.check(context, true)
                status = when {
                    update != null -> "A newer version is available."
                    AppUpdateChecker.checkFailed(context) -> "Could not check for updates. Check your connection and retry."
                    else -> "You already have the latest version."
                }
                checking = false
            }
        }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
            Text(if (checking) "Checking…" else "Check for updates")
        }
    }
}
