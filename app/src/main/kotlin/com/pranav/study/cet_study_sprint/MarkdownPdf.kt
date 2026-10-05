package com.pranav.study.cet_study_sprint

import android.graphics.Bitmap
import android.graphics.Canvas
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import org.commonmark.node.*
import org.commonmark.parser.Parser
import ru.noties.jlatexmath.JLatexMathDrawable
import java.io.File

/** Offline PDF rendering: selectable text and styled Markdown, with bounded LaTeX images. */
internal object MarkdownPdf {
    private val mathOrCode = Regex("```[\\s\\S]*?```|`[^`\\n]*`|\\$\\$([\\s\\S]*?)\\$\\$")
    internal fun protectMath(text: String, transform: (String) -> String): String {
        val values = mutableListOf<String>()
        val protected = mathOrCode.replace(normalizeChatMath(text)) { match ->
            if (match.groupValues[1].isEmpty()) match.value else {
                val token = "STUDYSPRINTMATH${values.size}TOKEN"
                values += match.value
                token
            }
        }
        var result = transform(protected)
        values.forEachIndexed { index, value -> result = result.replace("STUDYSPRINTMATH${index}TOKEN", value) }
        return result
    }

    fun write(file: File, questions: List<PdfImportedMcq>) {
        PDDocument().use { document ->
            Writer(document).use { writer ->
                writer.markdown("# Study Sprint\nPhoto practice MCQs\n\nVerify each answer before relying on it.")
                questions.forEachIndexed { index, question ->
                    writer.markdown("${index + 1}. ${question.question}")
                    question.options.forEachIndexed { n, option -> writer.markdown("${'A' + n}. $option") }
                    writer.markdown("**Answer: ${'A' + requireNotNull(question.answer)}**")
                    writer.newLine(8f)
                }
            }
            document.save(file)
        }
    }

    private class Writer(private val document: PDDocument) : AutoCloseable {
        private val width = PDRectangle.A4.width - 96f
        private var stream: PDPageContentStream? = null
        private var y = 0f
        private var used = 0f
        private val line = mutableListOf<Piece>()
        private data class Piece(val text: String, val font: PDType1Font, val size: Float,
            val width: Float, val height: Float, val bitmap: Bitmap? = null)

        init { page() }
        private fun page() {
            stream?.close()
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            stream = PDPageContentStream(document, page)
            y = page.mediaBox.height - 48f
        }
        fun newLine(gap: Float = 3f) {
            if (line.isEmpty()) { y -= gap; return }
            val height = line.maxOf { it.height }.coerceAtLeast(14f)
            if (y - height < 48f) page()
            var x = 48f
            line.forEach { piece ->
                val baseline = y - height + 4f
                if (piece.bitmap != null) {
                    stream!!.drawImage(LosslessFactory.createFromImage(document, piece.bitmap), x, baseline,
                        piece.width, piece.height - 4f)
                    // Keep the original formula in the text layer for PDF import and selection.
                    stream!!.setRenderingMode(RenderingMode.NEITHER)
                }
                stream!!.beginText()
                stream!!.setFont(piece.font, piece.size)
                stream!!.newLineAtOffset(x, baseline)
                stream!!.showText(piece.text)
                stream!!.endText()
                stream!!.setRenderingMode(RenderingMode.FILL)
                piece.bitmap?.recycle()
                x += piece.width
            }
            y -= height + gap
            line.clear(); used = 0f
        }
        private fun add(piece: Piece) {
            if (used + piece.width > width && line.isNotEmpty()) newLine()
            line += piece; used += piece.width
        }
        private fun text(value: String, bold: Boolean, italic: Boolean, code: Boolean, size: Float) {
            val font = when {
                code -> PDType1Font.COURIER
                bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                bold -> PDType1Font.HELVETICA_BOLD
                italic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
            val safe = ChatMcqPdf.normalize(value).map { if (it.code in 32..126) it else '?' }.joinToString("")
            // Retain spacing between AST nodes; normalize() trims only its own output.
            val spaced = (if (value.startsWith(' ')) " " else "") + safe + (if (value.endsWith(' ') && safe.isNotEmpty()) " " else "")
            Regex("\\S+\\s*|\\s+").findAll(spaced).forEach { token ->
                var rest = token.value
                while (rest.isNotEmpty()) {
                    var count = rest.length
                    while (count > 1 && font.getStringWidth(rest.take(count)) / 1000f * size > width) count--
                    val part = rest.take(count)
                    add(Piece(part, font, size, font.getStringWidth(part) / 1000f * size, size + 4f))
                    rest = rest.drop(count)
                }
            }
        }
        private fun math(source: String, size: Float) {
            val ascii = source.map { if (it.code in 32..126) it else '?' }.joinToString("")
            val drawable = if (source.length <= 2048) runCatching {
                JLatexMathDrawable.builder(source).textSize(size * 3f).padding(3).build()
            }.getOrNull() else null
            if (drawable == null || drawable.intrinsicWidth !in 1..4096 || drawable.intrinsicHeight !in 1..2048) {
                text("[$ascii]", false, false, true, size); return
            }
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            val scale = minOf(1f / 3f, width / bitmap.width, 140f / bitmap.height)
            add(Piece(ascii, PDType1Font.HELVETICA, 1f, bitmap.width * scale, bitmap.height * scale + 4f, bitmap))
        }
        fun markdown(source: String) {
            val formulas = mutableListOf<String>()
            val protected = mathOrCode.replace(normalizeChatMath(source)) { match ->
                if (match.groupValues[1].isEmpty()) match.value else {
                    val token = "STUDYSPRINTMATH${formulas.size}TOKEN"
                    formulas += match.groupValues[1].trim()
                    token
                }
            }
            fun children(node: Node, bold: Boolean = false, italic: Boolean = false, size: Float = 10f) {
                var child = node.firstChild
                while (child != null) {
                    val current = child
                    when (current) {
                        is Text -> {
                            var start = 0
                            Regex("STUDYSPRINTMATH(\\d+)TOKEN").findAll(current.literal).forEach { match ->
                                text(current.literal.substring(start, match.range.first), bold, italic, false, size)
                                math(formulas[match.groupValues[1].toInt()], size)
                                start = match.range.last + 1
                            }
                            text(current.literal.substring(start), bold, italic, false, size)
                        }
                        is StrongEmphasis -> children(current, true, italic, size)
                        is Emphasis -> children(current, bold, true, size)
                        is Code -> text(current.literal, false, false, true, size)
                        is Heading -> { newLine(); children(current, true, false, (20 - current.level * 2).coerceAtLeast(11).toFloat()); newLine(6f) }
                        is Paragraph -> { children(current, bold, italic, size); newLine() }
                        is SoftLineBreak, is HardLineBreak -> newLine()
                        is FencedCodeBlock -> current.literal.lines().forEach { text(it, false, false, true, size); newLine() }
                        is IndentedCodeBlock -> current.literal.lines().forEach { text(it, false, false, true, size); newLine() }
                        is ListItem -> {
                            val parent = current.parent
                            val label = if (parent is OrderedList) {
                                var n = parent.startNumber; var previous = current.previous
                                while (previous != null) { n++; previous = previous.previous }
                                "$n. "
                            } else "- "
                            text(label, bold, italic, false, size); children(current, bold, italic, size)
                        }
                        is ThematicBreak -> newLine(8f)
                        // Links display their labels; no remote images, HTML or network access.
                        is HtmlBlock, is HtmlInline, is Image -> Unit
                        else -> children(current, bold, italic, size)
                    }
                    child = current.next
                }
            }
            children(Parser.builder().build().parse(protected))
            newLine()
        }
        override fun close() { try { newLine() } finally { line.forEach { it.bitmap?.recycle() }; stream?.close() } }
    }
}
