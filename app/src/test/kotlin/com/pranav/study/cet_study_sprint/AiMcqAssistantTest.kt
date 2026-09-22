package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertTrue
import org.junit.Test

class AiMcqAssistantTest {
    @Test
    fun promptRequestsTheImportableMcqLayout() {
        val prompt = AiMcqAssistant.prompt
        assertTrue(prompt.contains("exactly 10"))
        assertTrue(prompt.contains("A. First option"))
        assertTrue(prompt.contains("D. Fourth option"))
        assertTrue(prompt.contains("Answer: B"))
        assertTrue(prompt.contains("text-based PDF"))
    }
}
