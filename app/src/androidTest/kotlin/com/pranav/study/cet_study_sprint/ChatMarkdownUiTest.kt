package com.pranav.study.cet_study_sprint

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatMarkdownUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun textViews(view: View): List<TextView> = if (view is TextView) listOf(view) else
        if (view is ViewGroup) (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) } else emptyList()
    private fun verify(theme: String) {
        val prefs = compose.activity.getSharedPreferences("markdown_ui_test", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", theme).commit()
        compose.setContent { StudyTheme(prefs, 0) {
            StudyCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ChatMarkdown("### Photon energy\n\nLight travels in **energy packets** called **photons**.\n\n" +
                    "\$\$E = h\\nu = \\frac{hc}{\\lambda}\$\$\n\n" +
                    "| Symbol | Meaning |\n| :--- | :--- |\n| \$E\$ | Photon energy |\n| \$h\$ | Planck constant |\n\n" +
                    "1. Higher frequency means more energy.\n2. Longer wavelength means less energy.")
            }
        } }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = textViews(compose.activity.window.decorView).first { it.text.contains("Photon energy") }
            assertFalse(view.text.contains("###")); assertFalse(view.text.contains("**"))
            val spanned = view.text as Spanned
            assertTrue(spanned.getSpans(0, spanned.length, Any::class.java).any { it.javaClass.simpleName == "StrongEmphasisSpan" })
        }
        // Assert equations actually rendered, not just their placeholder text.
        compose.waitUntil(10000) {
            var rendered = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = textViews(compose.activity.window.decorView).first { it.text.contains("Photon energy") }
                val spans = (view.text as Spanned).getSpans(0, view.text.length, AsyncDrawableSpan::class.java)
                rendered = spans.isNotEmpty() && spans.all { it.drawable.hasResult() }
            }
            rendered
        }
        saveUiProof(compose.activity, compose.onRoot().captureToImage().asAndroidBitmap(), "chat-formatted-$theme")
    }
    @Test fun markdownAndEquationInDarkMode() = verify("dark")
    @Test fun markdownAndEquationInLightMode() = verify("light")
}
