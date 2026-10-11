package com.pranav.study.cet_study_sprint

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal fun updateInstallIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun packageInstallPermissionIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))

private fun canInstallPackages(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

private fun installPermissionMessage(): String =
    "Update paused. Allow Study Sprint to install this update, then return here and tap Install update again."

@Composable
internal fun UpdateInstallAction(update: AppUpdateInfo, model: AppUpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by model.state.collectAsStateWithLifecycle()
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }
    var openingInstaller by remember { mutableStateOf(false) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Do not claim installation based on an Activity result. The next version check
        // reads the actual installed version; cancellation leaves the APK ready to retry.
        model.message(if (it.resultCode == Activity.RESULT_CANCELED) {
            "Installation was cancelled. Your downloaded update is still ready; tap Install update to retry."
        } else {
            "Android closed the installer. If the update did not finish, tap Install update to retry."
        })
    }
    fun openInstaller() {
        if (openingInstaller) return
        val file = state.file ?: return
        if (!canInstallPackages(context)) {
            model.message(installPermissionMessage())
            return
        }
        openingInstaller = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { verifyUpdateApk(context, file, update) }
                installer.launch(updateInstallIntent(context, file))
                model.message("Confirm Install on Android’s update screen. Your saved study data is kept.")
            } catch (_: ActivityNotFoundException) {
                model.message("Android could not find an installer for this APK. Keep the update downloaded and try again after checking your device’s installation settings.")
            } catch (_: SecurityException) {
                model.message(installPermissionMessage())
            } catch (_: Exception) {
                model.message("Android could not open this verified update. Tap Install update to retry, or check your device’s installation restrictions.")
            } finally { openingInstaller = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        waitingForPermission = false
        if (canInstallPackages(context)) openInstaller()
        else model.message(installPermissionMessage())
    }
    LaunchedEffect(state.requestInstall, state.tag) {
        if (!state.requestInstall || state.tag != update.tag) return@LaunchedEffect
        model.consumeInstallRequest()
        if (canInstallPackages(context)) openInstaller()
        else {
            try {
                waitingForPermission = true
                model.message("Enable Allow from this source for Study Sprint, then return here. Android will still ask you to confirm Install.")
                permission.launch(packageInstallPermissionIntent(context))
            } catch (_: ActivityNotFoundException) {
                waitingForPermission = false
                model.message("Open Android Settings → Special app access → Install unknown apps → Study Sprint, allow updates, then retry.")
            } catch (_: Exception) {
                waitingForPermission = false
                model.message("Android could not open install settings. Open Settings → Special app access → Install unknown apps → Study Sprint, allow updates, then retry.")
            }
        }
    }
    val busy = state.phase == UpdatePhase.DOWNLOADING || state.phase == UpdatePhase.VERIFYING
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busy) {
            if (state.total > 0 && state.phase == UpdatePhase.DOWNLOADING) {
                LinearProgressIndicator(progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Downloading update · ${(state.downloaded * 100 / state.total).coerceIn(0, 100)}%")
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(if (state.phase == UpdatePhase.VERIFYING) "Verifying official update…" else "Preparing update download…")
            }
            TextButton(onClick = model::cancel, modifier = Modifier.testTag("cancel_update_download")) { Text("Cancel download") }
        } else {
            Button(onClick = { model.prepare(update) }, enabled = !openingInstaller && !waitingForPermission,
                modifier = Modifier.fillMaxWidth().testTag("install_update")) {
                Text(if (state.tag == update.tag && state.file != null) "Install update" else "Update now")
            }
        }
        Text("The update downloads here—no browser or file manager needed. Android requires your installation confirmation; this app cannot silently install itself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("update_install_status"))
    }
}
