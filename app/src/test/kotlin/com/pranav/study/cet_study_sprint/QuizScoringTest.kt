package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizScoringTest {
    @Test
    fun cetUsesOneMarkWithoutNegativeMarking() {
        val scheme = markingSchemeFor("CET")
        assertEquals(1, scheme.score(isCorrect = true))
        assertEquals(0, scheme.score(isCorrect = false))
    }

    @Test
    fun jeeUsesFourMarksAndOneNegativeMark() {
        val scheme = markingSchemeFor("JEE")
        assertEquals(4, scheme.score(isCorrect = true))
        assertEquals(-1, scheme.score(isCorrect = false))
    }

    @Test
    fun neetUsesFourMarksAndOneNegativeMark() {
        val scheme = markingSchemeFor("NEET")
        assertEquals(4, scheme.score(isCorrect = true))
        assertEquals(-1, scheme.score(isCorrect = false))
    }
}
