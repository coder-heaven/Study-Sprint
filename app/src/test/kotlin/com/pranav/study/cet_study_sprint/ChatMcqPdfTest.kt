package com.pranav.study.cet_study_sprint

import org.junit.Assert.*
import org.junit.Test

class ChatMcqPdfTest {
    private fun sample(count: Int = 10) = (1..count).joinToString("\n\n") { "$it. What is photon energy?\nA. h*f\nB. h/f\nC. f/h\nD. h+f\nAnswer: A" }
    @Test fun generatedTextRoundTripsWithoutLosingAnswers() {
        val q = ChatMcqPdf.questions(sample())
        assertEquals(10, q.size)
        assertEquals(q, ChatMcqPdf.questions(ChatMcqPdf.canonical(q)))
        assertTrue(q.all { it.answer == 0 })
    }
    @Test fun equationsAndEmphasisSurviveMcqParsing() {
        val source = sample().replace("photon energy", "**photon energy** \\(E=\\frac{hc}{\\lambda}\\)")
        val questions = ChatMcqPdf.questions(source)
        assertTrue(questions.first().question.contains("**photon energy**"))
        assertTrue(questions.first().question.contains("\\frac{hc}{\\lambda}"))
        assertEquals(questions, ChatMcqPdf.questions(ChatMcqPdf.canonical(questions)))
    }
    @Test fun incompleteOrExtraQuestionsCannotCreateAPdf() {
        for (text in listOf(sample(9), sample(11), sample().replace("Answer: A", ""), sample().replace("C. f/h\n", ""), sample().replace("Answer: A", "E. fifth option\nAnswer: A"))) {
            try { ChatMcqPdf.questions(text); fail("Invalid set accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
