package com.pranav.study.cet_study_sprint

import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/** Only official release assets can become an install candidate. */
internal object AppUpdateSecurity {
    const val RELEASES = "https://github.com/coder-heaven/Study-Sprint/releases/latest"
    private const val ASSETS = "/coder-heaven/Study-Sprint/releases/download/"
    const val MAX_APK_BYTES = 150L * 1024 * 1024

    fun parseRelease(json: JSONObject): AppUpdateInfo {
        require(!json.optBoolean("draft") && !json.optBoolean("prerelease")) { "Not a stable release" }
        val tag = json.getString("tag_name")
        require(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+").matches(tag)) { "Invalid release version" }
        val name = "Study-Sprint-$tag.apk"
        val base = "https://github.com$ASSETS$tag/"
        val assets = json.getJSONArray("assets")
        var apk = ""
        var checksum = ""
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            when (asset.optString("name")) {
                name -> apk = asset.optString("browser_download_url")
                "$name.sha256" -> checksum = asset.optString("browser_download_url")
            }
        }
        require(apk == base + name && checksum == base + "$name.sha256") { "Missing official APK or checksum" }
        return AppUpdateInfo(tag, tag.drop(1), json.optString("body").trim(), apk,
            "https://github.com/coder-heaven/Study-Sprint/releases/tag/$tag", checksum)
    }

    fun officialAsset(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null &&
            Regex("${Regex.escape(ASSETS)}v[0-9]+\\.[0-9]+\\.[0-9]+/Study-Sprint-v[0-9]+\\.[0-9]+\\.[0-9]+\\.apk(?:\\.sha256)?").matches(uri.rawPath)
    }.getOrDefault(false)

    fun allowedDownloadLocation(url: String): Boolean = runCatching {
        if (officialAsset(url)) return true
        val uri = URI(url)
        uri.scheme == "https" && uri.port == -1 && uri.userInfo == null &&
            uri.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)

    fun checksum(text: String, fileName: String): String {
        val fields = text.trim().split(Regex("\\s+"))
        require(fields.size == 2 && Regex("[a-fA-F0-9]{64}").matches(fields[0]) &&
            fields[1].removePrefix("*") == fileName) { "Invalid update checksum" }
        return fields[0].lowercase()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun compatible(installed: ApkUpdateIdentity, candidate: ApkUpdateIdentity, version: String): Boolean =
        candidate.packageName == installed.packageName && candidate.versionName == version &&
            candidate.versionCode > installed.versionCode && installed.signers.isNotEmpty() &&
            candidate.signers == installed.signers
}

internal data class ApkUpdateIdentity(
    val packageName: String, val versionName: String, val versionCode: Long, val signers: Set<String>
)
