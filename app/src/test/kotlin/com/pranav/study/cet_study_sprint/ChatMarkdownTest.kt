package com.pranav.study.cet_study_sprint
import org.junit.Assert.assertEquals
import org.junit.Test
class ChatMarkdownTest {
    @Test fun formulaDelimitersAreNormalizedAndCodeIsPreserved() {
        assertEquals("\$\$E\$\$ = \$\$h\\nu\$\$", normalizeChatMath("\$E\$ = \\(h\\nu\\)"))
        assertEquals("\$\$E = h\\nu = \\frac{hc}{\\lambda}\$\$", normalizeChatMath("\$\$E = h\\nu = \\frac{hc}{\\lambda}\$\$"))
        assertEquals("`\$E\$` and \$\$E\$\$", normalizeChatMath("`\$E\$` and \$E\$"))
        assertEquals("Costs \$5\$", normalizeChatMath("Costs \$5\$"))
    }
}
