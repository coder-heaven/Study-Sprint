package com.pranav.study.cet_study_sprint

import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** A bounded foreground download. Partial or unverified files are never installable. */
internal class AppUpdateDownload(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES).followRedirects(false).followSslRedirects(false).build(),
    private val allowedLocation: (String) -> Boolean = AppUpdateSecurity::allowedDownloadLocation
) {
    private fun response(url: String): Response {
        var location = url
        repeat(6) {
            require(allowedLocation(location)) { "Untrusted update download location" }
            val response = client.newCall(Request.Builder().url(location).build()).execute()
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                val next = response.header("Location")?.let { response.request.url.resolve(it) }
                response.close()
                require(next != null) { "Invalid update redirect" }
                location = next.toString()
            } else {
                if (!response.isSuccessful) {
                    response.close()
                    throw IOException("Update download failed")
                }
                return response
            }
        }
        throw IOException("Too many update redirects")
    }

    suspend fun download(update: AppUpdateInfo, destination: File, progress: (Long, Long) -> Unit): File {
        require(allowedLocation(update.downloadUrl) && allowedLocation(update.checksumUrl))
        val checksum = response(update.checksumUrl).use { reply ->
            val body = requireNotNull(reply.body)
            val bytes = body.byteStream().use { readUpdateBytes(it, 4096) }
            AppUpdateSecurity.checksum(bytes.toString(Charsets.UTF_8), destination.name)
        }
        if (destination.isFile && digest(destination) == checksum) return destination
        destination.delete()
        val part = File(destination.parentFile, destination.name + ".part")
        try {
            response(update.downloadUrl).use { reply ->
                val body = requireNotNull(reply.body)
                val length = body.contentLength()
                require(length <= AppUpdateSecurity.MAX_APK_BYTES) { "Update is too large" }
                val hash = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var nextProgress = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            downloaded += count
                            require(downloaded <= AppUpdateSecurity.MAX_APK_BYTES) { "Update is too large" }
                            output.write(buffer, 0, count)
                            hash.update(buffer, 0, count)
                            if (downloaded >= nextProgress) {
                                progress(downloaded, length)
                                nextProgress = downloaded + 256 * 1024
                            }
                        }
                        require(downloaded > 0 && (length < 0 || downloaded == length)) { "Incomplete update download" }
                        require(hex(hash.digest()) == checksum) { "Update checksum mismatch" }
                    }
                }
            }
            coroutineContext.ensureActive()
            check(part.renameTo(destination)) { "Could not save update" }
            return destination
        } finally { part.delete() }
    }

    private suspend fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                hash.update(buffer, 0, count)
            }
        }
        return hex(hash.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
