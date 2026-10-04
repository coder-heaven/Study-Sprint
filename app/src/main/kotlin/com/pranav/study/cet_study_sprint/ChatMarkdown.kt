package com.pranav.study.cet_study_sprint

import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.res.ResourcesCompat
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

/** Normalize common model math delimiters to Markwon's double-dollar syntax. */
internal fun normalizeChatMath(text: String): String {
    val code = Regex("```[\\s\\S]*?```|`[^`\\n]*`")
    fun math(part: String): String = part
        .replace(Regex("\\\\\\(([\\s\\S]*?)\\\\\\)")) { "\$\$${it.groupValues[1]}\$\$" }
        .replace(Regex("\\\\\\[([\\s\\S]*?)\\\\\\]")) { "\$\$\n${it.groupValues[1]}\n\$\$" }
        .replace(Regex("(?<!\\$)\\$([^\\$\\n]+)\\$(?!\\$)")) {
            val body = it.groupValues[1]
            if (body.matches(Regex("[0-9., ]+"))) it.value else "\$\$$body\$\$"
        }
    val result = StringBuilder(); var start = 0
    code.findAll(text).forEach { result.append(math(text.substring(start, it.range.first))); result.append(it.value); start = it.range.last + 1 }
    return result.append(math(text.substring(start))).toString()
}

@Composable
internal fun ChatMarkdown(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val border = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val header = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val size = with(LocalDensity.current) { 16.sp.toPx() }
    val markdown = remember(context, foreground, border, header, size) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(size) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().textColor(foreground)
            })
            .usePlugin(TablePlugin.create(TableTheme.builder().tableBorderColor(border)
                .tableBorderWidth(1).tableCellPadding(12).tableHeaderRowBackgroundColor(header).build()))
            .build()
    }
    val source = remember(text) { normalizeChatMath(text) }
    val accessible = remember(markdown, source) { markdown.toMarkdown(source).toString() }
    AndroidView(modifier = modifier.semantics { this.text = AnnotatedString(accessible) }, factory = { TextView(it).apply {
        textSize = 16f
        typeface = ResourcesCompat.getFont(it, R.font.poppins_regular)
        setTextIsSelectable(true)
        setLineSpacing(4f, 1.15f)
        // No HTML, WebView, image/network loader or automatic link navigation.
    } }, update = { view ->
        view.setTextColor(foreground)
        if (view.tag != source || view.getTag(R.id.chat_markdown_theme) != foreground) {
            markdown.setMarkdown(view, source)
            view.tag = source
            view.setTag(R.id.chat_markdown_theme, foreground)
        }
    })
}
