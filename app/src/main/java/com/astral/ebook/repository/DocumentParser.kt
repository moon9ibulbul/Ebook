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
 * TXT files are parsed as UTF-8.
 * DOCX files rely on Apache POI (see build.gradle) to strip out paragraph text.
 */
object DocumentParser {
    suspend fun readBodyText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri) ?: ""
        return@withContext when {
            type.contains("word") || uri.toString().endsWith(".docx", true) -> parseDocx(context, uri)
            else -> parseTxt(context, uri)
        }
    }

    private fun parseTxt(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            return BufferedReader(InputStreamReader(input)).readText()
        }
        error("Unable to open text file")
    }

    private fun parseDocx(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            XWPFDocument(input).use { doc ->
                return doc.paragraphs.joinToString(separator = "\n\n") { it.text }
            }
        }
        error("Unable to open docx file")
    }
}
