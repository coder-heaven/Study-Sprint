package com.pranav.study.cet_study_sprint

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object AiMcqAssistant {
    val prompt = """
        Read the study files I attach and create exactly 10 original multiple-choice questions for my exam preparation.

        Requirements:
        - Use only concepts supported by the attached material.
        - Mix concept, critical-thinking and competency-based questions.
        - Give exactly four options labelled A, B, C and D.
        - Include the correct answer after every question.
        - Do not use tables, columns, images or markdown formatting.
        - Do not add explanations before or between questions.
        - Keep every question and option clear enough for PDF text extraction.

        Use exactly this layout:

        1. Question text
        A. First option
        B. Second option
        C. Third option
        D. Fourth option
        Answer: B

        Continue the same layout through question 10. Then export or print the result as a text-based PDF, not a scanned-image PDF, so I can import it into Study Sprint.
    """.trimIndent()

    fun openPrompt(context: Context): Boolean {
        copyPrompt(context)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Create 10 Study Sprint MCQs")
            putExtra(Intent.EXTRA_TEXT, prompt)
        }
        return openChooser(context, share)
    }

    fun shareFiles(context: Context, selected: List<Uri>): Boolean {
        val files = selected.take(5)
        if (files.isEmpty()) return openPrompt(context)
        copyPrompt(context)
        val share = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(files.first()) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, files.first())
                clipData = ClipData.newUri(context.contentResolver, "Study source", files.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMimeType(context, files)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
                clipData = ClipData.newUri(context.contentResolver, "Study sources", files.first()).also { clip ->
                    files.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
            }
        }.apply {
            putExtra(Intent.EXTRA_SUBJECT, "Create 10 Study Sprint MCQs")
            putExtra(Intent.EXTRA_TEXT, prompt)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return openChooser(context, share)
    }

    private fun copyPrompt(context: Context) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Study Sprint MCQ prompt", prompt))
    }

    private fun commonMimeType(context: Context, files: List<Uri>): String {
        val types = files.mapNotNull { context.contentResolver.getType(it) }.distinct()
        return if (types.size == 1) types.first() else "*/*"
    }

    private fun openChooser(context: Context, share: Intent): Boolean = runCatching {
        context.startActivity(Intent.createChooser(share, "Choose an AI app"))
        true
    }.getOrDefault(false)
}
