package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfQuestionImporterTest {
    @Test
    fun parsesInlineAnswer() {
        val result = PdfQuestionImporter.parse(
            """
            1. What is 2 + 2?
            A. 2
            B. 3
            C. 4
            D. 5
            Answer: C
            """.trimIndent()
        )
        assertEquals(1, result.questions.size)
        assertEquals("What is 2 + 2?", result.questions[0].question)
        assertEquals(listOf("2", "3", "4", "5"), result.questions[0].options)
        assertEquals(2, result.questions[0].answer)
    }

    @Test
    fun parsesParenthesizedOptionsAndAnswerKey() {
        val result = PdfQuestionImporter.parse(
            """
            Q1) SI unit of force is
            (A) Joule
            (B) Newton
            (C) Watt
            (D) Pascal

            2. Water freezes at
            A) 0 C
            B) 10 C
            C) 50 C
            D) 100 C

            Answer Key: 1-B, 2-A
            """.trimIndent()
        )
        assertEquals(2, result.questions.size)
        assertEquals(1, result.questions[0].answer)
        assertEquals(0, result.questions[1].answer)
        assertEquals(0, result.missingAnswers)
    }

    @Test
    fun keepsMissingAnswerForReview() {
        val result = PdfQuestionImporter.parse(
            """
            1. Pick one
            A. First
            B. Second
            C. Third
            D. Fourth
            """.trimIndent()
        )
        assertNull(result.questions[0].answer)
        assertEquals(1, result.missingAnswers)
    }
}