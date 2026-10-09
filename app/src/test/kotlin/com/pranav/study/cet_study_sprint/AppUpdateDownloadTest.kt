package com.pranav.study.cet_study_sprint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppUpdateDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val name = "Study-Sprint-v5.0.2.apk"
    private val bytes = "example update payload".toByteArray()
    private fun info(server: MockWebServer) = AppUpdateInfo("v5.0.2", "5.0.2", "Features",
        server.url("/$name").toString(), "", server.url("/$name.sha256").toString())
    private fun downloader(server: MockWebServer) = AppUpdateDownload(allowedLocation = { it.startsWith(server.url("/").toString()) })
    private fun checksum(payload: ByteArray = bytes) = MockResponse().setBody("${AppUpdateSecurity.sha256(payload)}  $name\n")

    @Test fun downloadChecksHashAndReusesVerifiedCacheWithoutFetchingApkAgain() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(checksum())
            server.enqueue(MockResponse().setBody(String(bytes)))
            server.enqueue(checksum())
            val file = File(temporary.root, name)
            var progress = 0L
            downloader(server).download(info(server), file) { count, _ -> progress = count }
            assertArrayEquals(bytes, file.readBytes())
            assertTrue(progress > 0)
            downloader(server).download(info(server), file) { _, _ -> fail("Unexpected redownload") }
            assertEquals(3, server.requestCount)
            assertFalse(File(temporary.root, "$name.part").exists())
        }
    }

    @Test fun checksumMismatchDeletesPartialDownloadAndAllowsCleanRetry() = runBlocking {
        MockWebServer().use { server ->
            val file = File(temporary.root, name)
            server.enqueue(checksum())
            server.enqueue(MockResponse().setBody("corrupted update"))
            try { downloader(server).download(info(server), file) { _, _ -> }; fail("Accepted corrupted APK") }
            catch (_: IllegalArgumentException) { }
            assertFalse(file.exists())
            assertFalse(File(temporary.root, "$name.part").exists())
            server.enqueue(checksum())
            server.enqueue(MockResponse().setBody(String(bytes)))
            downloader(server).download(info(server), file) { _, _ -> }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun networkFailureAndOversizedDownloadNeverBecomeInstallCandidates() = runBlocking {
        for (response in listOf(MockResponse().setResponseCode(503), MockResponse().setBody("x").setHeader("Content-Length", AppUpdateSecurity.MAX_APK_BYTES + 1))) {
            MockWebServer().use { server ->
                server.enqueue(checksum())
                server.enqueue(response)
                val file = File(temporary.root, name)
                try { downloader(server).download(info(server), file) { _, _ -> }; fail("Accepted failed download") }
                catch (_: Exception) { }
                assertFalse(file.exists())
                assertFalse(File(temporary.root, "$name.part").exists())
            }
        }
    }

    @Test fun redirectsCannotLeaveTrustedLocation() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.example/update"))
            val file = File(temporary.root, name)
            try { downloader(server).download(info(server), file) { _, _ -> }; fail("Followed untrusted redirect") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1, server.requestCount)
            assertFalse(file.exists())
        }
    }

    @Test fun cancellationCleansPartialFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(checksum())
            server.enqueue(MockResponse().setBody("x".repeat(512 * 1024)))
            val file = File(temporary.root, name)
            coroutineScope {
                val task = launch(Dispatchers.IO) {
                    downloader(server).download(info(server), file) { _, _ -> cancel() }
                }
                task.join()
                assertTrue(task.isCancelled)
            }
            assertFalse(file.exists())
            assertFalse(File(temporary.root, "$name.part").exists())
        }
    }
}
