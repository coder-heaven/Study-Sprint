package com.pranav.study.cet_study_sprint

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Uses only Android 8-compatible APIs and rejects oversized responses. */
internal fun readUpdateBytes(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        require(output.size() + count <= limit) { "Update response is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
