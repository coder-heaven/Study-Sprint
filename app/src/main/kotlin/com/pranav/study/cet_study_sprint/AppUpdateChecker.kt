package com.pranav.study.cet_study_sprint

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val downloadUrl: String, val releaseUrl: String
)

internal object AppUpdateChecker {
    private const val API = "https://api.github.com/repos/coder-heaven/Study-Sprint/releases/latest"
    private const val RELEASES = "https://github.com/coder-heaven/Study-Sprint/releases/latest"
    private const val PREFS = "study_sprint_updates"
    private const val INTERVAL = 6L * 60L * 60L * 1000L
    private const val SNOOZE = 24L * 60L * 60L * 1000L

    suspend fun check(context: Context, force: Boolean = false): AppUpdateInfo? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val cached = cached(context)
            if (!force && now - prefs.getLong("last_check", 0) < INTERVAL) {
                return@withContext cached?.takeUnless { snoozed(context, it.tag, now) }
            }
            val fetched = runCatching { fetch() }.getOrNull()
            prefs.edit().putLong("last_check", now).apply()
            if (fetched != null) save(context, fetched)
            (fetched ?: cached)?.takeIf {
                isNewer(it.version, installed(context)) && !snoozed(context, it.tag, now)
            }
        }

    private fun fetch(): AppUpdateInfo {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Study-Sprint-Android")
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK)
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val releaseUrl = json.optString("html_url", RELEASES)
            val assets = json.optJSONArray("assets")
            var apk = ""
            if (assets != null) for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name").endsWith(".apk", true)) {
                    apk = asset.optString("browser_download_url")
                    break
                }
            }
            return AppUpdateInfo(
                tag, tag.removePrefix("v").removePrefix("V"),
                json.optString("body").trim().take(500),
                apk.ifBlank { releaseUrl }, releaseUrl
            )
        } finally {
            connection.disconnect()
        }
    }

    fun snooze(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("snoozed_tag", tag)
            .putLong("snoozed_until", System.currentTimeMillis() + SNOOZE).apply()
    }

    fun openDownload(context: Context, update: AppUpdateInfo) {
        val target = update.downloadUrl.takeIf { it.startsWith("https://github.com/") }
            ?: update.releaseUrl
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    private fun parts(value: String) =
        Regex("\\d+").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()
            .ifEmpty { listOf(0) }

    private fun installed(context: Context) =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    private fun save(context: Context, item: AppUpdateInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tag", item.tag).putString("version", item.version)
            .putString("notes", item.notes).putString("download", item.downloadUrl)
            .putString("release", item.releaseUrl).apply()
    }

    private fun cached(context: Context): AppUpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString("tag", null) ?: return null
        return AppUpdateInfo(
            tag, prefs.getString("version", tag.removePrefix("v")) ?: tag,
            prefs.getString("notes", "").orEmpty(),
            prefs.getString("download", RELEASES).orEmpty(),
            prefs.getString("release", RELEASES).orEmpty()
        ).takeIf { isNewer(it.version, installed(context)) }
    }

    private fun snoozed(context: Context, tag: String, now: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("snoozed_tag", null) == tag &&
            now < prefs.getLong("snoozed_until", 0)
    }
}

@Composable
internal fun UpdatePromptHost() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    LaunchedEffect(Unit) { update = AppUpdateChecker.check(context) }
    update?.let { item ->
        AlertDialog(
            onDismissRequest = { AppUpdateChecker.snooze(context, item.tag); update = null },
            title = { Text("Update available") },
            text = {
                Column {
                    Text("Study Sprint ${item.tag} is ready.", fontWeight = FontWeight.Bold)
                    Text(
                        item.notes.ifBlank { "Download the latest APK from the official GitHub release." },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { AppUpdateChecker.openDownload(context, item) }) {
                    Text("Download update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    AppUpdateChecker.snooze(context, item.tag)
                    update = null
                }) { Text("Later") }
            }
        )
    }
}

@Composable
internal fun UpdateSettingsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var status by remember { mutableStateOf("Check GitHub for the newest Study Sprint APK.") }
    StudyCard {
        Text("App updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(status, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        if (update != null) {
            Button(onClick = { AppUpdateChecker.openDownload(context, update!!) },
                modifier = Modifier.fillMaxWidth()) { Text("Download ${update!!.tag}") }
        } else {
            OutlinedButton(onClick = {
                scope.launch {
                    checking = true
                    update = AppUpdateChecker.check(context, true)
                    status = if (update == null) "You already have the latest version."
                    else "A newer version is available."
                    checking = false
                }
            }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                Text(if (checking) "Checking…" else "Check for updates")
            }
        }
    }
}
