package com.astral.ebook.repository

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.Orientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EbookGenerator {
    suspend fun generate(
        context: Context,
        settings: EbookSettings,
        body: DocumentContent,
        coverImage: Uri?
    ): File = withContext(Dispatchers.IO) {
        val filename = "Astral_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            ?.takeIf { it.exists() || it.mkdirs() }
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val outFile = File(documentsDir, filename)

        val engine = EbookLayoutEngine(context, settings)
        val renderer = PageRenderer(context, settings, engine)
        val pages = engine.layoutPages(body)

        generatePdf(outFile, settings, pages, renderer, coverImage)
        outFile
    }

    private fun generatePdf(
        file: File,
        settings: EbookSettings,
        pages: List<PageContent>,
        renderer: PageRenderer,
        coverImage: Uri?
    ) {
        val doc = PdfDocument()
        val (pageWidth, pageHeight) = if (settings.orientation == Orientation.Portrait) {
            settings.pagePreset.widthPx to settings.pagePreset.heightPx
        } else {
            settings.pagePreset.heightPx to settings.pagePreset.widthPx
        }

        pages.forEach { pageContent ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageContent.pageNumber).create()
            val page = doc.startPage(pageInfo)
            renderer.drawPage(page.canvas, pageContent, pageWidth, pageHeight, coverImage)
            doc.finishPage(page)
        }

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}
