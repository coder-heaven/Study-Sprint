package com.pranav.study.cet_study_sprint

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateCheckerTest {
    @Test fun comparesSemanticVersions() {
        assertTrue(AppUpdateChecker.isNewer("1.1.1", "1.1.0"))
        assertTrue(AppUpdateChecker.isNewer("v1.10.0", "1.9.9"))
        assertTrue(AppUpdateChecker.isNewer("v2.2.0", "1.2.0"))
        assertFalse(AppUpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(AppUpdateChecker.isNewer("1.0.9", "1.1.0"))
    }

    private fun release(notes: String = "New features"): JSONObject {
        val base = "https://github.com/coder-heaven/Study-Sprint/releases/download/v5.0.2/Study-Sprint-v5.0.2.apk"
        return JSONObject().put("tag_name", "v5.0.2").put("body", notes)
            .put("assets", JSONArray().put(JSONObject().put("name", "Study-Sprint-v5.0.2.apk").put("browser_download_url", base))
                .put(JSONObject().put("name", "Study-Sprint-v5.0.2.apk.sha256").put("browser_download_url", "$base.sha256")))
    }

    @Test fun retainsAllReleaseFeaturesPastOld500CharacterCutoff() {
        val notes = (1..80).joinToString("\n") { "- Feature $it: improvements and detailed instructions." }
        val item = AppUpdateSecurity.parseRelease(release(notes))
        assertEquals(notes, item.notes)
        assertTrue(item.notes.contains("Feature 80"))
        assertEquals(item.downloadUrl + ".sha256", item.checksumUrl)
    }

    @Test fun rejectsUnverifiedOrUnstableReleaseMetadata() {
        val missing = release().apply { getJSONArray("assets").remove(1) }
        val hostile = release().apply { getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.example/app.apk") }
        for (json in listOf(missing, hostile, release().put("draft", true), release().put("prerelease", true), release().put("tag_name", "../../app"))) {
            assertThrows(IllegalArgumentException::class.java) { AppUpdateSecurity.parseRelease(json) }
        }
    }

    @Test fun acceptsOnlyOfficialHttpsAssetsAndKnownRedirectHosts() {
        val url = AppUpdateSecurity.parseRelease(release()).downloadUrl
        assertTrue(AppUpdateSecurity.officialAsset(url))
        assertTrue(AppUpdateSecurity.allowedDownloadLocation("https://release-assets.githubusercontent.com/release-file?signature=example"))
        for (bad in listOf(url.replace("https:", "http:"), url.replace("github.com", "github.com.evil.example"),
            url.replace("coder-heaven", "other-owner"), "https://github.com@evil.example/app.apk", "$url?other=file",
            url.replace("github.com", "github.com:443"), "file:///sdcard/update.apk", "https://evil.example/update.apk")) {
            assertFalse(bad, AppUpdateSecurity.officialAsset(bad))
            assertFalse(bad, AppUpdateSecurity.allowedDownloadLocation(bad))
        }
    }

    @Test fun checksumMustNameTheExactApk() {
        val name = "Study-Sprint-v5.0.2.apk"
        val hash = "a".repeat(64)
        assertEquals(hash, AppUpdateSecurity.checksum("$hash  $name\n", name))
        assertThrows(IllegalArgumentException::class.java) { AppUpdateSecurity.checksum("$hash  other.apk", name) }
        assertThrows(IllegalArgumentException::class.java) { AppUpdateSecurity.checksum("garbage", name) }
    }

    @Test fun candidateMustMatchPackageSigningKeyAndExpectedNewerVersion() {
        val installed = ApkUpdateIdentity("com.example.study", "5.0.1", 43, setOf("trusted-signer"))
        val valid = installed.copy(versionName = "5.0.2", versionCode = 44)
        assertTrue(AppUpdateSecurity.compatible(installed, valid, "5.0.2"))
        for (invalid in listOf(valid.copy(packageName = "com.evil.app"), valid.copy(signers = setOf("other-signer")),
            valid.copy(signers = emptySet()), valid.copy(versionCode = 43), valid.copy(versionCode = 42), valid.copy(versionName = "5.1.0"))) {
            assertFalse(AppUpdateSecurity.compatible(installed, invalid, "5.0.2"))
        }
    }
}
