package com.astral.ebook.repository

import android.content.Context
import android.net.Uri
import com.astral.ebook.model.ParagraphAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utilities to load body content from the Storage Access Framework selections.
 *
 * TXT files are parsed as UTF-8 and support HTML markup tags for styling and alignment.
 * DOCX files rely on Apache POI (see build.gradle) to strip out paragraph text.
 */
data class DocumentContent(val paragraphs: List<FormattedParagraph>) {
    val rawText: String = paragraphs.joinToString(separator = "\n\n") { it.plainText() }
}

data class FormattedParagraph(
    val runs: List<TextRun>,
    val alignment: ParagraphAlignment? = null
) {
    fun plainText(): String = runs.joinToString(separator = "") { it.text }
}

data class TextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val imageUri: String? = null
)

object DocumentParser {
    suspend fun readBody(context: Context, uri: Uri): DocumentContent = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri) ?: ""
        return@withContext when {
            type.contains("word") || uri.toString().endsWith(".docx", true) -> parseDocx(context, uri)
            else -> parseTxt(context, uri)
        }
    }

    suspend fun readRawText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri) ?: ""
        val isDocx = type.contains("word") || uri.toString().endsWith(".docx", true)
        if (isDocx) {
            readBody(context, uri).rawText
        } else {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                } ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun parseTxt(context: Context, uri: Uri): DocumentContent {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val text = BufferedReader(InputStreamReader(input)).readText()
            val paragraphs = text.split(Regex("""\r?\n"""))
                .map { parseParagraphMarkup(it.trim('\n', '\r')) }
            return DocumentContent(paragraphs)
        }
        error("Unable to open text file")
    }

    private fun parseDocx(context: Context, uri: Uri): DocumentContent {
        context.contentResolver.openInputStream(uri)?.use { input ->
            XWPFDocument(input).use { doc ->
                val paragraphs = doc.paragraphs.map { para ->
                    val text = para.text ?: ""
                    val poiAlign = para.alignment
                    val alignment = when (poiAlign) {
                        org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER -> ParagraphAlignment.Center
                        org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT -> ParagraphAlignment.Right
                        org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT -> ParagraphAlignment.Left
                        org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH -> ParagraphAlignment.Justify
                        org.apache.poi.xwpf.usermodel.ParagraphAlignment.DISTRIBUTE -> ParagraphAlignment.Justify
                        else -> null
                    }
                    FormattedParagraph(listOf(TextRun(text.trim())), alignment)
                }
                return DocumentContent(paragraphs)
            }
        }
        error("Unable to open docx file")
    }

    internal fun parseParagraphMarkup(source: String): FormattedParagraph {
        if (source.isBlank()) return FormattedParagraph(listOf(TextRun("")))
        var working = stripIncompleteHtml(source).trim('\n', '\r')
        var alignment: ParagraphAlignment? = null

        val htmlPAlign = Regex("^<(?:p|div)\\s+(?:align=\"([a-zA-Z]+)\"|style=\"[^\"]*text-align:\\s*([a-zA-Z]+)[^\"]*\")\\s*>(.*)</(?:p|div)>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val centerTag = Regex("^<center>(.*)</center>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        val htmlPMatch = htmlPAlign.find(working)
        if (htmlPMatch != null) {
            val alignStr = htmlPMatch.groupValues[1].ifEmpty { htmlPMatch.groupValues[2] }
            alignment = alignStr.toParagraphAlignment()
            working = htmlPMatch.groupValues[3].trim()
        } else {
            val centerMatch = centerTag.find(working)
            if (centerMatch != null) {
                alignment = ParagraphAlignment.Center
                working = centerMatch.groupValues[1].trim()
            }
        }

        val runs = mutableListOf<TextRun>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var index = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                runs += TextRun(text = buffer.toString(), bold = bold, italic = italic, underline = underline, strikeThrough = strike)
                buffer.clear()
            }
        }

        val imgRegex = Regex("^<img\\s+[^>]*src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)

        while (index < working.length) {
            if (working[index] == '\\') {
                val escaped = working.getOrNull(index + 1)
                if (escaped != null && escaped in setOf('<', '\\')) {
                    buffer.append(escaped)
                    index += 2
                    continue
                }
            }

            val imgMatch = imgRegex.find(working.substring(index))
            if (imgMatch != null && imgMatch.range.first == 0) {
                flush()
                val uri = imgMatch.groupValues[1]
                runs += TextRun(text = "[Gambar]", bold = bold, italic = italic, underline = underline, strikeThrough = strike, imageUri = uri)
                index += imgMatch.value.length
                continue
            }

            when {
                // HTML tags
                working.regionMatches(index, "<b>", 0, 3, ignoreCase = true) ||
                working.regionMatches(index, "<strong>", 0, 8, ignoreCase = true) -> {
                    flush()
                    bold = true
                    index += if (working.regionMatches(index, "<b>", 0, 3, ignoreCase = true)) 3 else 8
                }
                working.regionMatches(index, "</b>", 0, 4, ignoreCase = true) ||
                working.regionMatches(index, "</strong>", 0, 9, ignoreCase = true) -> {
                    flush()
                    bold = false
                    index += if (working.regionMatches(index, "</b>", 0, 4, ignoreCase = true)) 4 else 9
                }
                working.regionMatches(index, "<i>", 0, 3, ignoreCase = true) ||
                working.regionMatches(index, "<em>", 0, 4, ignoreCase = true) -> {
                    flush()
                    italic = true
                    index += if (working.regionMatches(index, "<i>", 0, 3, ignoreCase = true)) 3 else 4
                }
                working.regionMatches(index, "</i>", 0, 4, ignoreCase = true) ||
                working.regionMatches(index, "</em>", 0, 5, ignoreCase = true) -> {
                    flush()
                    italic = false
                    index += if (working.regionMatches(index, "</i>", 0, 4, ignoreCase = true)) 4 else 5
                }
                working.regionMatches(index, "<u>", 0, 3, ignoreCase = true) -> {
                    flush()
                    underline = true
                    index += 3
                }
                working.regionMatches(index, "</u>", 0, 4, ignoreCase = true) -> {
                    flush()
                    underline = false
                    index += 4
                }
                working.regionMatches(index, "<s>", 0, 3, ignoreCase = true) ||
                working.regionMatches(index, "<del>", 0, 5, ignoreCase = true) ||
                working.regionMatches(index, "<strike>", 0, 8, ignoreCase = true) -> {
                    flush()
                    strike = true
                    index += when {
                        working.regionMatches(index, "<s>", 0, 3, ignoreCase = true) -> 3
                        working.regionMatches(index, "<del>", 0, 5, ignoreCase = true) -> 5
                        else -> 8
                    }
                }
                working.regionMatches(index, "</s>", 0, 4, ignoreCase = true) ||
                working.regionMatches(index, "</del>", 0, 6, ignoreCase = true) ||
                working.regionMatches(index, "</strike>", 0, 9, ignoreCase = true) -> {
                    flush()
                    strike = false
                    index += when {
                        working.regionMatches(index, "</s>", 0, 4, ignoreCase = true) -> 4
                        working.regionMatches(index, "</del>", 0, 6, ignoreCase = true) -> 6
                        else -> 9
                    }
                }
                else -> {
                    buffer.append(working[index])
                    index++
                }
            }
        }
        flush()
        if (runs.isEmpty()) {
            runs += TextRun(working)
        }
        return FormattedParagraph(runs, alignment)
    }
}

private fun String.toParagraphAlignment(): ParagraphAlignment = when (lowercase()) {
    "left" -> ParagraphAlignment.Left
    "right" -> ParagraphAlignment.Right
    "center" -> ParagraphAlignment.Center
    "justify" -> ParagraphAlignment.Justify
    else -> ParagraphAlignment.Left
}

/**
 * Strips incomplete opening HTML tags (e.g. `<b>` without closing `</b>`),
 * unmatched closing HTML tags (e.g. `</b>` without opening `<b>`),
 * unclosed tag fragments (e.g. `<stron`, `<str`, `<st`, `</stron`, `<b`, `</b`),
 * and unsupported HTML tags (e.g. `<stron>`, `</stron>`).
 *
 * Preserves valid matched HTML tags (`<b>...</b>`, `<i>...</i>`, `<u>...</u>`, `<s>...</s>`,
 * `<center>...</center>`, `<p align="...">...</p>`, `<img .../>`), escaped characters (`\<b\>`),
 * and plain text content.
 */
fun stripIncompleteHtml(text: String): String {
    if (text.isEmpty()) return ""

    val lines = text.split("\n")
    return lines.joinToString("\n") { processLine(it) }
}

private fun processLine(line: String): String {
    if (!line.contains('<') && !line.contains('\\')) return line

    data class TagToken(
        val category: TagCategory,
        val isClose: Boolean,
        val range: IntRange,
        val tagText: String
    )

    val tokens = mutableListOf<TagToken>()
    val invalidRanges = mutableListOf<IntRange>()

    var idx = 0
    val N = line.length

    while (idx < N) {
        val ch = line[idx]
        if (ch == '\\') {
            val escaped = line.getOrNull(idx + 1)
            if (escaped != null && escaped in setOf('<', '\\')) {
                idx += 2
                continue
            }
        }
        if (ch == '<') {
            val endAngle = line.indexOf('>', idx)
            val nextStartAngle = line.indexOf('<', idx + 1)

            val hasValidEnd = endAngle != -1 && (nextStartAngle == -1 || endAngle < nextStartAngle)

            if (!hasValidEnd) {
                val fragmentMatch = UNCLOSED_TAG_FRAGMENT_REGEX.find(line.substring(idx))
                val fragmentLen = fragmentMatch?.value?.length ?: 1
                invalidRanges.add(idx until (idx + fragmentLen))
                idx += fragmentLen
                continue
            } else {
                val tagStr = line.substring(idx, endAngle + 1)
                val isClose = tagStr.startsWith("</")

                val category = parseTagCategory(tagStr)
                if (category == null) {
                    invalidRanges.add(idx..endAngle)
                } else {
                    tokens.add(TagToken(category, isClose, idx..endAngle, tagStr))
                }
                idx = endAngle + 1
                continue
            }
        }
        idx++
    }

    val validTagRanges = mutableSetOf<IntRange>()
    val categoriesToMatch = TagCategory.values().filter { it != TagCategory.IMG }

    for (cat in categoriesToMatch) {
        val catTokens = tokens.filter { it.category == cat }
        val stack = java.util.ArrayDeque<TagToken>()
        for (token in catTokens) {
            if (!token.isClose) {
                stack.push(token)
            } else {
                if (stack.isNotEmpty()) {
                    val openToken = stack.pop()
                    validTagRanges.add(openToken.range)
                    validTagRanges.add(token.range)
                } else {
                    invalidRanges.add(token.range)
                }
            }
        }
        while (stack.isNotEmpty()) {
            val unclosedToken = stack.pop()
            invalidRanges.add(unclosedToken.range)
        }
    }

    tokens.filter { it.category == TagCategory.IMG }.forEach {
        validTagRanges.add(it.range)
    }

    if (invalidRanges.isEmpty()) return line

    val sb = StringBuilder()
    val sortedInvalid = invalidRanges.sortedBy { it.first }

    var curr = 0
    while (curr < N) {
        val matchingRange = sortedInvalid.find { curr in it }
        if (matchingRange != null) {
            curr = matchingRange.last + 1
        } else {
            sb.append(line[curr])
            curr++
        }
    }

    return sb.toString()
}

private val UNCLOSED_TAG_FRAGMENT_REGEX = Regex(
    "^</?[a-zA-Z0-9_/-]+(?:\\s+[a-zA-Z0-9_/-]+=(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))*",
    RegexOption.IGNORE_CASE
)

private enum class TagCategory {
    BOLD, ITALIC, UNDERLINE, STRIKE, CENTER, PARA, IMG
}

private fun parseTagCategory(tagStr: String): TagCategory? {
    val lower = tagStr.lowercase()
    return when {
        lower.matches(Regex("^</?(?:b|strong)\\s*>$")) -> TagCategory.BOLD
        lower.matches(Regex("^</?(?:i|em)\\s*>$")) -> TagCategory.ITALIC
        lower.matches(Regex("^</?u\\s*>$")) -> TagCategory.UNDERLINE
        lower.matches(Regex("^</?(?:s|del|strike)\\s*>$")) -> TagCategory.STRIKE
        lower.matches(Regex("^</?center\\s*>$")) -> TagCategory.CENTER
        lower.matches(Regex("^<(?:p|div)\\s+(?:align=\"[a-zA-Z]+\"|style=\"[^\"]*text-align:\\s*[a-zA-Z]+[^\"]*\")\\s*>$")) ||
        lower.matches(Regex("^</(?:p|div)\\s*>$")) -> TagCategory.PARA
        lower.matches(Regex("^<img\\s+[^>]*src=[\"'][^\"']+[\"'][^>]*\\/?>$")) -> TagCategory.IMG
        else -> null
    }
}
