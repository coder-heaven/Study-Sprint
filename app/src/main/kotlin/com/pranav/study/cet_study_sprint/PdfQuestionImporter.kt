package com.pranav.study.cet_study_sprint

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PdfImportedMcq(
    val question: String,
    val options: List<String>,
    val answer: Int?
)

internal data class PdfImportResult(
    val questions: List<PdfImportedMcq>,
    val missingAnswers: Int
)

internal object PdfQuestionImporter {
    suspend fun read(context: Context, uri: Uri): PdfImportResult = withContext(Dispatchers.IO) {
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(size <= 20L * 1024L * 1024L || size < 0L) { "Choose a PDF smaller than 20 MB." }
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                require(document.numberOfPages > 0) { "The PDF has no pages." }
                PDFTextStripper().apply {
                    sortByPosition = true
                    startPage = 1
                    endPage = document.numberOfPages.coerceAtMost(30)
                }.getText(document)
            }
        } ?: error("The selected PDF could not be opened.")
        require(text.isNotBlank()) {
            "No readable text was found. Use a text-based PDF rather than a scanned-image PDF."
        }
        parse(text)
    }

    internal fun parse(rawText: String): PdfImportResult {
        val normalized = rawText.replace('\u00A0', ' ')
        val keyMarker = Regex("(?im)^\\s*(?:answer\\s*key|answers|correct\\s*answers?)\\s*[:\\-]?")
            .find(normalized)
        val questionPart = keyMarker?.let { normalized.substring(0, it.range.first) } ?: normalized
        val answerPart = keyMarker?.let { normalized.substring(it.range.last + 1) }.orEmpty()
        val answerKey = Regex("(?i)(\\d{1,2})\\s*[.):\\-]?\\s*([A-D])\\b")
            .findAll(answerPart)
            .associate { it.groupValues[1].toInt() to (it.groupValues[2].uppercase()[0] - 'A') }

        data class Draft(
            val number: Int,
            var question: String,
            val options: LinkedHashMap<Char, String> = linkedMapOf(),
            var answer: Int? = null
        )

        val questionRegex = Regex("(?i)^\\s*(?:Q(?:uestion)?\\s*)?(\\d{1,2})[.)]\\s+(.+?)\\s*$")
        val optionRegex = Regex("^\\s*(?:\\(([A-Da-d])\\)|([A-Da-d])[.):\\-])\\s+(.+?)\\s*$")
        val answerRegex = Regex("(?i)^\\s*(?:answer|ans)\\s*[:\\-]?\\s*\\(?([A-D])\\)?\\s*$")
        val parsed = mutableListOf<Draft>()
        var current: Draft? = null

        fun finish() {
            current?.let { if (it.question.isNotBlank()) parsed += it }
            current = null
        }

        questionPart.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            val questionMatch = questionRegex.matchEntire(line)
            val optionMatch = optionRegex.matchEntire(line)
            val answerMatch = answerRegex.matchEntire(line)
            when {
                questionMatch != null -> {
                    finish()
                    current = Draft(questionMatch.groupValues[1].toInt(), questionMatch.groupValues[2].trim())
                }
                current != null && optionMatch != null -> {
                    val letter = optionMatch.groupValues[1].ifBlank { optionMatch.groupValues[2] }.uppercase()[0]
                    current!!.options[letter] = optionMatch.groupValues[3].trim()
                }
                current != null && answerMatch != null -> {
                    current!!.answer = answerMatch.groupValues[1].uppercase()[0] - 'A'
                }
                current != null && current!!.options.isEmpty() -> {
                    current!!.question = (current!!.question + " " + line).trim()
                }
                current != null && current!!.options.isNotEmpty() -> {
                    val key = current!!.options.keys.last()
                    current!!.options[key] = (current!!.options.getValue(key) + " " + line).trim()
                }
            }
        }
        finish()

        val questions = parsed.mapNotNull { draft ->
            val options = ('A'..'D').mapNotNull { draft.options[it] }
            if (options.size != 4) null else PdfImportedMcq(
                question = draft.question,
                options = options,
                answer = draft.answer ?: answerKey[draft.number]
            )
        }.take(10)
        require(questions.isNotEmpty()) {
            "No complete MCQs were detected. Use numbered questions with A, B, C and D options."
        }
        return PdfImportResult(questions, questions.count { it.answer == null })
    }
}