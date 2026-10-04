package com.pranav.study.cet_study_sprint

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

internal object ChatMcqPdf {
    const val PROMPT = "Read only the study material in these photos and create exactly 10 original MCQs for exam practice. Use only concepts visible in these photos. If unreadable, ask for clearer photos instead. Mix concept, critical thinking and competency questions. Use English and plain ASCII math (for example x^2, sqrt(x), pi); no LaTeX or markdown. Give four options and the correct answer for every question. Output nothing else. Exactly this layout repeated through 10: 1. Question text\nA. First option\nB. Second option\nC. Third option\nD. Fourth option\nAnswer: B"
    fun questions(text: String): List<PdfImportedMcq> {
        val starts = Regex("(?m)^\\s*(\\d{1,2})[.)]\\s+").findAll(text).map { it.groupValues[1].toInt() }.toList()
        require(starts == (1..10).toList()) { "The AI did not return exactly 10 numbered MCQs. Retry with clearer photos." }
        val parsed = PdfQuestionImporter.parse(text)
        require(parsed.questions.size == 10 && parsed.missingAnswers == 0) { "The AI returned incomplete MCQs or answers. Retry with clearer photos." }
        return parsed.questions
    }
    fun canonical(questions: List<PdfImportedMcq>): String = questions.mapIndexed { i, q ->
        "${i + 1}. ${q.question}\n" + q.options.mapIndexed { n, option -> "${'A' + n}. $option" }.joinToString("\n") + "\nAnswer: ${'A' + requireNotNull(q.answer)}"
    }.joinToString("\n\n")
    fun remaining(context: Context, day: String = LocalDate.now().toString()): Int {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        return if (prefs.getString("chat_pdf_day", "") == day) (2 - prefs.getInt("chat_pdf_count", 0)).coerceIn(0, 2) else 2
    }
    fun latest(context: Context): String? {
        val name = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE).getString("chat_pdf_latest", null) ?: return null
        return name.takeIf { Regex("[0-9a-fA-F-]{36}\\.pdf").matches(it) && File(context.filesDir, "chapter_pdfs/$it").isFile }
    }
    @Synchronized fun create(context: Context, questions: List<PdfImportedMcq>): String {
        val day = LocalDate.now().toString()
        require(remaining(context, day) > 0) { "You have generated 2 PDFs today. Try after midnight." }
        val text = canonical(questions)
        require(text.all { it == '\n' || it.code in 32..126 }) { "The AI used unsupported symbols. Retry using plain English and ASCII math." }
        val dir = File(context.filesDir, "chapter_pdfs").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.pdf")
        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            PDDocument().use { doc ->
                val font = PDType1Font.HELVETICA
                var stream: PDPageContentStream? = null; var y = 0f
                fun page() {
                    stream?.close()
                    val page = PDPage(PDRectangle.A4); doc.addPage(page)
                    stream = PDPageContentStream(doc, page); y = page.mediaBox.height - 48f
                }
                page()
                try {
                    ("Study Sprint - Photo practice MCQs\nAI-generated: verify each answer before relying on it.\n\n" + text).lines().forEach { line ->
                        val words = line.split(' '); var current = ""
                        val wrapped = mutableListOf<String>()
                        words.forEach { word ->
                            var rest = word
                            // Split exceptionally long tokens so they cannot escape the page.
                            while (rest.length > 65) { if (current.isNotEmpty()) { wrapped += current; current = "" }; wrapped += rest.take(65); rest = rest.drop(65) }
                            val next = if (current.isEmpty()) rest else "$current $rest"
                            if (font.getStringWidth(next) / 1000 * 10 > PDRectangle.A4.width - 96 && current.isNotEmpty()) { wrapped += current; current = rest } else current = next
                        }
                        wrapped += current
                        wrapped.forEach { row ->
                            if (y < 48f) page()
                            stream!!.beginText(); stream!!.setFont(font, 10f); stream!!.newLineAtOffset(48f, y); stream!!.showText(row); stream!!.endText(); y -= 16f
                        }
                    }
                } finally { stream?.close() }
                doc.save(file)
            }
            val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
            val count = if (prefs.getString("chat_pdf_day", "") == day) prefs.getInt("chat_pdf_count", 0) else 0
            check(prefs.edit().putString("chat_pdf_day", day).putInt("chat_pdf_count", count + 1).putString("chat_pdf_latest", file.name).putBoolean("chat_pdf_practice", false).commit()) { "Could not save today's PDF allowance." }
            return file.name
        } catch (error: Throwable) { file.delete(); throw error }
    }
    @Synchronized fun addToPractice(context: Context, questions: List<PdfImportedMcq>, fileName: String): String {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val name = "Photo MCQs ${LocalDate.now()} ${fileName.take(8)}"
        val sets = runCatching { JSONObject(prefs.getString("owned_mcq_sets", "{}")) }.getOrDefault(JSONObject())
        if (sets.has(name)) return name // Reopening never duplicates a generated set.
        val old = prefs.getString("owned_mcqs_chapter", "My saved MCQs").orEmpty().ifBlank { "My saved MCQs" }
        if (!sets.has(old) && prefs.contains("owned_mcqs_json")) sets.put(old, JSONArray(prefs.getString("owned_mcqs_json", "[]")))
        val json = JSONArray()
        questions.forEach { q -> json.put(JSONObject().put("question", q.question).put("options", JSONArray(q.options)).put("answer", requireNotNull(q.answer))) }
        sets.put(name, json)
        check(prefs.edit().putString("owned_mcq_sets", sets.toString()).putString("owned_mcqs_chapter", name).putString("owned_mcqs_json", json.toString()).putBoolean("chat_pdf_practice", true).commit()) { "Could not save MCQs to practice." }
        return name
    }
}
