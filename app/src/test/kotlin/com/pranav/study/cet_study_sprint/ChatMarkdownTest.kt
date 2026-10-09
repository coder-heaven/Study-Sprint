package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {
    @Test fun formulaDelimitersAreNormalizedAndCodeIsPreserved() {
        assertEquals("\$\$E\$\$ = \$\$h\\nu\$\$", normalizeChatMath("\$E\$ = \\(h\\nu\\)"))
        assertEquals("\$\$E = h\\nu = \\frac{hc}{\\lambda}\$\$", normalizeChatMath("\$\$E = h\\nu = \\frac{hc}{\\lambda}\$\$"))
        assertEquals("`\$E\$` and \$\$E\$\$", normalizeChatMath("`\$E\$` and \$E\$"))
        assertEquals("Costs \$5\$", normalizeChatMath("Costs \$5\$"))
    }

    @Test fun compactPipeTablesAreCanonicalizedForMarkwon() {
        val compact = "Header|Meaning\n|-----|-----|\nA|First value\nB|Second value"
        val normalized = normalizeChatTables(compact)
        assertEquals("Header | Meaning |\n| --- | --- |\nA | First value |\nB | Second value |", normalized)
        assertFalse(normalized.contains("|-----|"))
    }

    @Test fun tableMathIsNormalizedWithoutBreakingTheTableDelimiter() {
        val normalized = normalizeChatTables(normalizeChatMath("Symbol|Formula\n|---|---|\nEnergy|\$E = h\\\\nu\$"))
        assertTrue(normalized.contains("| --- | --- |"))
        assertTrue(normalized.contains("\$\$E = h\\\\nu\$\$"))
    }

    @Test fun practiceChemistryEquationsPreserveMathAcrossQuizParsing() {
        val quiz = "1. Moles in 18 g of water using \\(n = \\frac{m}{M}\\)?\n" +
            "A. \\(0.5\\) mol\nB. \\(1\\) mol\nC. \\(2\\) mol\nD. \\(18\\) mol\nAnswer: B"
        val questions = chatQuizQuestions(quiz)
        assertEquals(1, questions.size)
        assertTrue(questions[0].question.contains("\\frac{m}{M}"))
        assertEquals("\$\$1\$\$ mol", questions[0].options[1])
        assertTrue(normalizeChatMath(questions[0].question).contains("\$\$n = \\frac{m}{M}\$\$"))
    }
}
