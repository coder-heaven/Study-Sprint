package com.pranav.study.cet_study_sprint

/** Repair recognizable model table blocks, leaving ordinary prose/code and cell contents alone. */
internal fun normalizeChatTables(text: String): String {
    // Models sometimes wrap a whole table in ```markdown, which renders as code, not a table.
    // Only unwrap explicitly Markdown-labelled fences whose entire contents form one table.
    val markdownFence = Regex("(?m)^ {0,3}(`{3,}|~{3,})(?:markdown|md)[ \\t]*\\n([\\s\\S]*?)^ {0,3}\\1[ \\t]*$")
    val unwrapped = markdownFence.replace(text.replace("\r\n", "\n")) { match ->
        val body = match.groupValues[2].trim('\n')
        if (chatTableAt(body.lines(), 0)?.end == body.lines().size) body else match.value
    }
    val lines = unwrapped.lines()
    val result = mutableListOf<String>()
    var index = 0
    var fence: String? = null
    val fencePattern = Regex("^ {0,3}(`{3,}|~{3,}).*$")
    while (index < lines.size) {
        val line = lines[index]
        val marker = fencePattern.matchEntire(line)?.groupValues?.get(1)
        if (marker != null) {
            val open = fence
            if (open == null) fence = marker
            else if (marker[0] == open[0] && marker.length >= open.length && line.trim().all { it == open[0] }) fence = null
            result += line
            index++
            continue
        }
        val table = if (fence == null) chatTableAt(lines, index) else null
        if (table != null) {
            // A blank boundary also avoids an adjacent paragraph being swallowed as a header.
            if (result.lastOrNull()?.isNotBlank() == true) result += ""
            result += table.rows
            if (table.end < lines.size && lines[table.end].isNotBlank()) result += ""
            index = table.end
        } else {
            result += line
            index++
        }
    }
    return result.joinToString("\n")
}

private data class ChatTableBlock(val rows: List<String>, val end: Int)

private fun chatTableAt(lines: List<String>, start: Int): ChatTableBlock? {
    val header = lines.getOrNull(start)?.let(::chatTableCells) ?: return null
    var separatorIndex = start + 1
    while (lines.getOrNull(separatorIndex)?.isBlank() == true) separatorIndex++
    val separator = lines.getOrNull(separatorIndex)?.let(::chatTableCells) ?: return null
    if (separator.size != header.size || separator.any { !it.matches(Regex(":?-+:?")) }) return null
    val canonical = mutableListOf("| ${header.joinToString(" | ")} |")
    canonical += "| ${separator.joinToString(" | ") {
        when {
            it.startsWith(":") && it.endsWith(":") -> ":---:"
            it.startsWith(":") -> ":---"
            it.endsWith(":") -> "---:"
            else -> "---"
        }
    }} |"
    var end = separatorIndex + 1
    while (end < lines.size && lines[end].isBlank()) end++
    var dataRows = 0
    while (end < lines.size) {
        if (lines[end].isBlank()) break
        val cells = chatTableCells(lines[end]) ?: break
        // Do not silently remove extra cells from malformed output.
        if (cells.size > header.size) return null
        canonical += "| ${(cells + List(header.size - cells.size) { "" }).joinToString(" | ")} |"
        dataRows++
        end++
    }
    return if (dataRows == 0) null else ChatTableBlock(canonical, end)
}

/** Split only unescaped pipes: a literal \\| inside an option/cell is never a column boundary. */
private fun chatTableCells(line: String): List<String>? {
    val value = line.trim()
    // Quotes, list prefixes and fenced code are not top-level tables.
    if (value.startsWith('>') || value.startsWith("```") || value.startsWith("~~~")) return null
    val boundaries = mutableListOf<Int>()
    var slashes = 0
    value.forEachIndexed { index, char ->
        if (char == '|' && slashes % 2 == 0) boundaries += index
        slashes = if (char == '\\') slashes + 1 else 0
    }
    if (boundaries.isEmpty()) return null
    val cells = mutableListOf<String>()
    var begin = 0
    for (boundary in boundaries) {
        cells += value.substring(begin, boundary).trim()
        begin = boundary + 1
    }
    cells += value.substring(begin).trim()
    if (boundaries.first() == 0) cells.removeAt(0)
    if (boundaries.last() == value.lastIndex && cells.isNotEmpty()) cells.removeAt(cells.lastIndex)
    return cells.takeIf { it.isNotEmpty() }
}
