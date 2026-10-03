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
 * TXT files are parsed as UTF-8 and support lightweight markup such as *italic*, **bold**,
 * _underline_, ~strikethrough~, and [center]custom alignment[/center].
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
    val strikeThrough: Boolean = false
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
        var working = source.trim('\n', '\r')
        var alignment: ParagraphAlignment? = null
        val bracketAlign = Regex("^\\[(left|right|center|justify)](.*)\\[/\\1]$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val bracketMatch = bracketAlign.find(working)
        if (bracketMatch != null) {
            alignment = bracketMatch.groupValues[1].toParagraphAlignment()
            working = bracketMatch.groupValues[2].trim()
        } else {
            val attrAlign = Regex("^\\[align=(left|right|center|justify)](.*)\\[/align]$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val attrMatch = attrAlign.find(working)
            if (attrMatch != null) {
                alignment = attrMatch.groupValues[1].toParagraphAlignment()
                working = attrMatch.groupValues[2].trim()
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
                runs += TextRun(buffer.toString(), bold, italic, underline, strike)
                buffer.clear()
            }
        }
        while (index < working.length) {
            if (working[index] == '\\') {
                val escaped = working.getOrNull(index + 1)
                if (escaped != null && escaped in setOf('*', '_', '~', '[', '\\')) {
                    buffer.append(escaped)
                    index += 2
                    continue
                }
            }
            when {
                working.startsWith("***", index) -> {
                    flush()
                    bold = !bold
                    italic = !italic
                    index += 3
                }
                working.startsWith("**", index) -> {
                    flush()
                    bold = !bold
                    index += 2
                }
                working.startsWith("__", index) -> {
                    flush()
                    underline = !underline
                    index += 2
                }
                working.regionMatches(index, "[u]", 0, 3, ignoreCase = true) -> {
                    flush()
                    underline = true
                    index += 3
                }
                working.regionMatches(index, "[/u]", 0, 4, ignoreCase = true) -> {
                    flush()
                    underline = false
                    index += 4
                }
                working.regionMatches(index, "[s]", 0, 3, ignoreCase = true) -> {
                    flush()
                    strike = true
                    index += 3
                }
                working.regionMatches(index, "[/s]", 0, 4, ignoreCase = true) -> {
                    flush()
                    strike = false
                    index += 4
                }
                working[index] == '_' -> {
                    flush()
                    underline = !underline
                    index++
                }
                working[index] == '*' -> {
                    flush()
                    italic = !italic
                    index++
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
    else -> ParagraphAlignment.Justify
}
