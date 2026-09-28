package com.pranav.study.cet_study_sprint

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal object ChapterFiles {
    private const val MAX_BYTES = 25L * 1024 * 1024

    fun importPdf(context: Context, uri: Uri): Pair<String, String> {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?.take(120) ?: "Chapter notes.pdf"
        require(name.endsWith(".pdf", true)) { "Choose a PDF file." }
        val dir = File(context.filesDir, "chapter_pdfs").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.pdf")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open this PDF." }
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "PDF must be smaller than 25 MB." }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "The PDF is empty." }
                }
            }
            return name to file.name
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    fun openPdf(context: Context, fileName: String) {
        require(Regex("[0-9a-fA-F-]{36}\\.pdf").matches(fileName))
        val file = File(File(context.filesDir, "chapter_pdfs"), fileName)
        require(file.isFile) { "This PDF is no longer available." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
