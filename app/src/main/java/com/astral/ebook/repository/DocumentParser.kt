package com.astral.ebook.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utilities to load body content from the Storage Access Framework selections.
 *
 * TXT files are parsed as UTF-8 and support lightweight markup such as *italic* and **bold**.
 * DOCX files rely on Apache POI (see build.gradle) to strip out paragraph text.
 */
data class DocumentContent(val paragraphs: List<FormattedParagraph>) {
    val rawText: String = paragraphs.joinToString(separator = "\n\n") { it.plainText() }
}

data class FormattedParagraph(val runs: List<TextRun>) {
    fun plainText(): String = runs.joinToString(separator = "") { it.text }
}

data class TextRun(val text: String, val bold: Boolean = false, val italic: Boolean = false)

object DocumentParser {
    suspend fun readBody(context: Context, uri: Uri): DocumentContent = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri) ?: ""
        return@withContext when {
            type.contains("word") || uri.toString().endsWith(".docx", true) -> parseDocx(context, uri)
            else -> parseTxt(context, uri)
        }
    }

    private fun parseTxt(context: Context, uri: Uri): DocumentContent {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val text = BufferedReader(InputStreamReader(input)).readText()
            val paragraphs = text.split(Regex("""\r?\n\s*\r?\n"""))
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
                    FormattedParagraph(listOf(TextRun(text.trim())))
                }
                return DocumentContent(paragraphs)
            }
        }
        error("Unable to open docx file")
    }

    private fun parseParagraphMarkup(source: String): FormattedParagraph {
        if (source.isBlank()) return FormattedParagraph(listOf(TextRun("")))
        val runs = mutableListOf<TextRun>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var index = 0
        fun flush() {
            if (buffer.isNotEmpty()) {
                runs += TextRun(buffer.toString(), bold = bold, italic = italic)
                buffer.clear()
            }
        }
        while (index < source.length) {
            when {
                source.startsWith("***", index) -> {
                    flush()
                    bold = !bold
                    italic = !italic
                    index += 3
                }
                source.startsWith("**", index) -> {
                    flush()
                    bold = !bold
                    index += 2
                }
                source[index] == '*' -> {
                    flush()
                    italic = !italic
                    index++
                }
                else -> {
                    buffer.append(source[index])
                    index++
                }
            }
        }
        flush()
        if (runs.isEmpty()) {
            runs += TextRun(source)
        }
        return FormattedParagraph(runs)
    }
}
