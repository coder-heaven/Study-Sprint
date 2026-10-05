package com.pranav.study.cet_study_sprint

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

internal object ChatMcqPdf {
    const val PROMPT = "Read only the study material in these photos and create exactly 10 original MCQs for exam practice. Use only concepts visible in these photos. If unreadable, ask for clearer photos instead. Mix concept, critical thinking and competency questions. Use English. Markdown emphasis and LaTeX math enclosed in dollar delimiters are supported inside question and option text. Keep numbering, A-D labels and Answer lines plain. Give four options and the correct answer for every question. Output nothing else. Exactly this layout repeated through 10: 1. Question text\nA. First option\nB. Second option\nC. Third option\nD. Fourth option\nAnswer: B"
    fun normalize(text: String): String = MarkdownPdf.protectMath(text) { normalizePlain(it) }
    private fun normalizePlain(text: String): String {
        var clean = text.replace("```text", "").replace("```", "").replace("\u00a0", " ")
        val powers = mapOf('⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4', '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9', '⁻' to '-', '⁺' to '+')
        clean = Regex("[⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺]+").replace(clean) { match ->
            val exponent = match.value.map { powers.getValue(it) }.joinToString("")
            if (exponent.length == 1) "^$exponent" else "^($exponent)"
        }
        val symbols = mapOf('×' to "*", '÷' to "/", '−' to "-", '–' to "-", '—' to "-", '’' to "'", '‘' to "'", '“' to "\"", '”' to "\"",
            'π' to "pi", 'λ' to "lambda", 'ν' to "nu", 'μ' to "mu", 'θ' to "theta", 'Δ' to "Delta", 'Ω' to "ohm", '√' to "sqrt", '°' to " degrees", '≤' to "<=", '≥' to ">=", '≈' to "~", '²' to "^2", '³' to "^3", '⁻' to "-", '₀' to "0", '₁' to "1", '₂' to "2", '₃' to "3", '₄' to "4", '₅' to "5", '₆' to "6", '₇' to "7", '₈' to "8", '₉' to "9", '⁰' to "0", '¹' to "^1", '⁴' to "4", '⁵' to "5", '⁶' to "6", '⁷' to "7", '⁸' to "8", '⁹' to "9")
        clean = clean.map { symbols[it] ?: it.toString() }.joinToString("")
        return clean.trim()
    }
    fun questions(raw: String, count: Int = 10): List<PdfImportedMcq> {
        val text = normalize(raw)
        val starts = Regex("(?m)^\\s*(\\d{1,2})[.)]\\s+").findAll(text).map { it.groupValues[1].toInt() }.toList()
        require(count in 1..10 && starts == (1..count).toList()) { "The AI did not return the expected numbered MCQs. Retry with clearer photos." }
        val blocks = text.split(Regex("(?m)(?=^\\s*\\d{1,2}[.)]\\s+)" )).filter { it.isNotBlank() }
        require(blocks.all { block -> Regex("(?m)^\\s*\\(?([A-Z])[.)]\\s+").findAll(block).map { it.groupValues[1] }.toList() == listOf("A", "B", "C", "D") }) { "Every MCQ must have exactly four options A-D. Please retry." }
        val parsed = PdfQuestionImporter.parse(text)
        require(parsed.questions.size == count && parsed.missingAnswers == 0) { "The AI returned incomplete MCQs or answers. Retry with clearer photos." }
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
        val dir = File(context.filesDir, "chapter_pdfs").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.pdf")
        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            MarkdownPdf.write(file, questions)
            val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
            val count = if (prefs.getString("chat_pdf_day", "") == day) prefs.getInt("chat_pdf_count", 0) else 0
            check(prefs.edit().putString("chat_pdf_day", day).putInt("chat_pdf_count", count + 1).putString("chat_pdf_latest", file.name).putBoolean("chat_pdf_practice", false).commit()) { "Could not save today's PDF allowance." }
            return file.name
        } catch (error: Throwable) { file.delete(); throw error }
    }
    @Synchronized fun addToPractice(context: Context, questions: List<PdfImportedMcq>, fileName: String): String {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val name = if (fileName.startsWith("web-")) "Online MCQs ${LocalDate.now()} ${fileName.take(16)}" else "Photo MCQs ${LocalDate.now()} ${fileName.take(8)}"
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
