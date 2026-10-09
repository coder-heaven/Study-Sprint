package com.pranav.study.cet_study_sprint

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal enum class UpdatePhase { IDLE, DOWNLOADING, VERIFYING, READY }
internal data class UpdateInstallState(
    val tag: String = "", val phase: UpdatePhase = UpdatePhase.IDLE,
    val downloaded: Long = 0, val total: Long = -1,
    val file: File? = null, val requestInstall: Boolean = false, val message: String = ""
)

internal class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(UpdateInstallState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun prepare(update: AppUpdateInfo) {
        if (job?.isCompleted == false) return
        if (mutable.value.tag == update.tag && mutable.value.file?.isFile == true) {
            mutable.value = mutable.value.copy(requestInstall = true, message = "")
            return
        }
        job = viewModelScope.launch {
            mutable.value = UpdateInstallState(tag = update.tag, phase = UpdatePhase.DOWNLOADING)
            try {
                val file = withContext(Dispatchers.IO) {
                    require(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+").matches(update.tag) &&
                        AppUpdateSecurity.officialAsset(update.downloadUrl) &&
                        update.checksumUrl == update.downloadUrl + ".sha256")
                    val directory = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
                    val destination = File(directory, "Study-Sprint-${update.tag}.apk")
                    // Keep only this candidate. Never delete any study data or shared documents.
                    directory.listFiles()?.filter { it != destination }?.forEach { it.delete() }
                    val downloaded = AppUpdateDownload().download(update, destination) { bytes, total ->
                        mutable.value = mutable.value.copy(downloaded = bytes, total = total)
                    }
                    mutable.value = mutable.value.copy(phase = UpdatePhase.VERIFYING)
                    try { verifyUpdateApk(getApplication(), downloaded, update) }
                    catch (error: Exception) { downloaded.delete(); throw error }
                    downloaded
                }
                mutable.value = mutable.value.copy(phase = UpdatePhase.READY, file = file, requestInstall = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutable.value = UpdateInstallState(tag = update.tag,
                    message = "Could not download or verify the official update. Check your connection and free storage, then retry.")
            }
        }
    }

    fun consumeInstallRequest() { mutable.value = mutable.value.copy(requestInstall = false) }
    fun message(value: String) { mutable.value = mutable.value.copy(message = value) }
    fun cancel() {
        job?.cancel()
        mutable.value = UpdateInstallState(tag = mutable.value.tag, message = "Download cancelled. Tap Update to retry.")
    }
}

@Suppress("DEPRECATION")
internal fun verifyUpdateApk(context: Context, file: File, update: AppUpdateInfo) {
    val manager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val installed = manager.getPackageInfo(context.packageName, flags)
    val candidate = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags)) { "Not a valid APK" }
    require(AppUpdateSecurity.compatible(installed.updateIdentity(), candidate.updateIdentity(), update.version)) {
        "Update package, version or signing certificate does not match this app"
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.updateIdentity(): ApkUpdateIdentity {
    val certificates = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
    return ApkUpdateIdentity(packageName, versionName.orEmpty(),
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong(),
        certificates?.map { AppUpdateSecurity.sha256(it.toByteArray()) }?.toSet().orEmpty())
}
