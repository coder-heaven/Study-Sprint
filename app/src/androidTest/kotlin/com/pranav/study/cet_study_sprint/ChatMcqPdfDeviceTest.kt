package com.pranav.study.cet_study_sprint

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ChatMcqPdfDeviceTest {
    @Test fun pdfIsExtractableQuotaPersistsAndOptionalPracticeImportKeepsOtherSets() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("pdf_test_$id", mode)
            override fun getFilesDir() = File(base.cacheDir, "pdf_test_$id").apply { mkdirs() }
        }
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        prefs.edit().putString("owned_mcqs_chapter", "Existing").putString("owned_mcqs_json", "[]").commit()
        try {
            val text = (1..10).joinToString("\n\n") { "$it. Which formula gives photon energy (10⁻³ J, λ, x²)?\nA. h×f\nB. h/f\nC. f/h\nD. h+f\nAnswer: A" }
            val q = ChatMcqPdf.questions(text)
            assertTrue(q.first().question.contains("10^(-3) J"))
            assertTrue(q.first().question.contains("lambda"))
            assertEquals("h*f", q.first().options.first())
            val first = ChatMcqPdf.create(context, q)
            assertEquals(1, ChatMcqPdf.remaining(context))
            assertEquals("Existing", prefs.getString("owned_mcqs_chapter", ""))
            val imported = PdfQuestionImporter.read(context, Uri.fromFile(File(context.filesDir, "chapter_pdfs/$first")))
            assertEquals(10, imported.questions.size); assertEquals(0, imported.missingAnswers)
            val name = ChatMcqPdf.addToPractice(context, q, first)
            assertEquals(10, savedQuestions(prefs, name).size)
            assertTrue(savedQuizChapters(prefs).contains("Existing"))
            assertEquals(name, ChatMcqPdf.addToPractice(context, q, first))
            ChatMcqPdf.create(context, q)
            assertEquals(0, ChatMcqPdf.remaining(context))
            try { ChatMcqPdf.create(context, q); fail("Third daily PDF accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(2, ChatMcqPdf.remaining(context, "2099-01-01"))
            assertNotNull(ChatMcqPdf.latest(context))
        } finally { prefs.edit().clear().commit(); context.filesDir.deleteRecursively() }
    }
}
