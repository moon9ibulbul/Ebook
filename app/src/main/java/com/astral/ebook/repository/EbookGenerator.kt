package com.astral.ebook.repository

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.OutputFormat
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.ParagraphAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Contains EPUB and PDF generation logic.
 *
 * Default values for fonts, margins, colors, etc. are defined inside [EbookSettings].
 * Adjust those defaults inside model/FormattingModels.kt to tweak presets globally.
 */
object EbookGenerator {
    suspend fun generate(
        context: Context,
        settings: EbookSettings,
        body: String,
        coverImage: Uri?
    ): File = withContext(Dispatchers.IO) {
        val ext = if (settings.outputFormat == OutputFormat.EPUB) "epub" else "pdf"
        val filename = "Astral_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.$ext"
        val outFile = File(context.getExternalFilesDir(null), filename)
        if (settings.outputFormat == OutputFormat.EPUB) {
            generateEpub(context, outFile, settings, body, coverImage)
        } else {
            generatePdf(context, outFile, settings, body)
        }
        outFile
    }

    private fun generatePdf(
        context: Context,
        file: File,
        settings: EbookSettings,
        body: String
    ) {
        val doc = PdfDocument()
        val (pageWidth, pageHeight) = if (settings.orientation == Orientation.Portrait) {
            settings.pagePreset.widthPx to settings.pagePreset.heightPx
        } else {
            settings.pagePreset.heightPx to settings.pagePreset.widthPx
        }
        val pageInfo = PdfDocument.PageInfo.Builder(
            pageWidth,
            pageHeight,
            1
        ).create()
        val textPaint = Paint().apply {
            textSize = settings.fonts.bodySize * context.resources.displayMetrics.density
            color = 0xFF000000.toInt()
        }
        val contentWidth = pageWidth - (settings.margins.start + settings.margins.end)
        val paragraphs = body.split(Regex("""\n\s*\n"""))
        val indentPx = textPaint.textSize * settings.paragraphOptions.firstLineIndentEm
        val paragraphSpacing = settings.paragraphOptions.extraParagraphSpacing *
            context.resources.displayMetrics.density
        val lineTuples = mutableListOf<Pair<String, Float>>()
        paragraphs.forEachIndexed { index, paragraph ->
            val applyIndent = !(index == 0 && settings.paragraphOptions.skipIndentAfterHeading)
            lineTuples += wrapParagraph(
                paragraph,
                textPaint,
                contentWidth,
                indentPx,
                applyIndent
            )
            lineTuples += "" to 0f // paragraph break marker
        }
        var lineIndex = 0
        var pageNumber = 1
        val lineHeight = textPaint.fontSpacing * settings.fonts.lineHeight
        while (lineIndex < lineTuples.size) {
            val page = doc.startPage(pageInfo)
            var y = settings.margins.top + lineHeight
            while (lineIndex < lineTuples.size && y < pageHeight - settings.margins.bottom - lineHeight) {
                val (line, indent) = lineTuples[lineIndex]
                if (line.isNotEmpty()) {
                    page.canvas.drawText(line, settings.margins.start + indent, y, textPaint)
                    y += lineHeight
                } else {
                    y += paragraphSpacing
                }
                lineIndex++
            }
            if (settings.footerOptions.showFooter) {
                val footerText = buildString {
                    if (settings.footerOptions.showTitle) append(settings.metadata.title)
                    if (settings.footerOptions.showSubtitle && settings.metadata.subtitle.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(settings.metadata.subtitle)
                    }
                    if (settings.footerOptions.showPageNumber) {
                        if (isNotEmpty()) append(" · ")
                        append(pageNumber)
                    }
                }
                page.canvas.drawText(
                    footerText,
                    settings.margins.start,
                    pageHeight - settings.margins.bottom,
                    Paint(textPaint).apply { textSize = textSize * 0.85f }
                )
            }
            doc.finishPage(page)
            pageNumber++
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun wrapParagraph(
        text: String,
        paint: Paint,
        width: Float,
        indentPx: Float,
        indentFirstLine: Boolean
    ): List<Pair<String, Float>> {
        if (text.isBlank()) return emptyList()
        val words = text.trim().split(Regex("""\s+"""))
        val lines = mutableListOf<Pair<String, Float>>()
        var current = StringBuilder()
        var firstLine = true
        var availableWidth = width - if (indentFirstLine) indentPx else 0f
        for (word in words) {
            val tentative = if (current.isEmpty()) word else current.toString() + " " + word
            if (paint.measureText(tentative) > availableWidth && current.isNotEmpty()) {
                lines += current.toString() to if (firstLine && indentFirstLine) indentPx else 0f
                current = StringBuilder(word)
                firstLine = false
                availableWidth = width
            } else {
                if (current.isEmpty()) current.append(word) else current.append(" ").append(word)
            }
        }
        if (current.isNotEmpty()) {
            lines += current.toString() to if (firstLine && indentFirstLine) indentPx else 0f
        }
        return lines
    }

    private fun generateEpub(
        context: Context,
        file: File,
        settings: EbookSettings,
        body: String,
        coverImage: Uri?
    ) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version='1.0' encoding='UTF-8'?>
                <container version='1.0' xmlns='urn:oasis:names:tc:opendocument:xmlns:container'>
                    <rootfiles>
                        <rootfile full-path='OEBPS/content.opf' media-type='application/oebps-package+xml'/>
                    </rootfiles>
                </container>""".trimIndent().toByteArray()
            )
            zip.closeEntry()

            val css = buildCss(settings)
            zip.putNextEntry(ZipEntry("OEBPS/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/stylesheet.css"))
            zip.write(css.toByteArray())
            zip.closeEntry()

            val chapters = buildContentXhtml(settings, body, coverImage != null)
            zip.putNextEntry(ZipEntry("OEBPS/content.xhtml"))
            zip.write(chapters.toByteArray())
            zip.closeEntry()

            val metadataPage = buildMetadataPage(settings)
            zip.putNextEntry(ZipEntry("OEBPS/metadata.xhtml"))
            zip.write(metadataPage.toByteArray())
            zip.closeEntry()

            coverImage?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    zip.putNextEntry(ZipEntry("OEBPS/cover.bin"))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val manifest = buildOpf(settings, coverImage != null)
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
    }

    private fun buildCss(settings: EbookSettings): String = """
        body {
            background: ${settings.themeOptions.pageBackground.toHex()};
            color: ${settings.themeOptions.textColor.toHex()};
            font-family: ${settings.fonts.bodyFamily.css};
            font-size: ${settings.fonts.bodySize}pt;
            line-height: ${settings.fonts.lineHeight};
            text-align: ${settings.paragraphOptions.alignment.css};
            margin: ${settings.margins.top}px ${settings.margins.end}px ${settings.margins.bottom}px ${settings.margins.start}px;
        }
        h1.title {
            font-size: ${settings.fonts.titleSize}pt;
            text-align: center;
        }
        h2.subtitle {
            font-size: ${settings.fonts.subtitleSize}pt;
            text-align: center;
            font-style: italic;
        }
        p {
            text-indent: ${settings.paragraphOptions.firstLineIndentEm}em;
            margin-bottom: ${settings.paragraphOptions.extraParagraphSpacing}px;
        }
        h3 + p {
            text-indent: 0;
        }
        h3.heading {
            font-size: ${settings.fonts.headingSize}pt;
            text-align: center;
            margin-top: 25vh;
        }
        footer {
            font-size: 10pt;
            text-align: center;
            color: #666666;
        }
        .metadata dt {
            font-weight: bold;
        }
        .metadata dd {
            margin: 0 0 12px 0;
        }
    """.trimIndent()

    private fun buildContentXhtml(settings: EbookSettings, body: String, hasCover: Boolean): String {
        val paragraphs = body.split("\n\n").joinToString(separator = "") { para ->
            "<p>${para.trim()}</p>"
        }
        val footer = if (settings.footerOptions.showFooter) {
            "<footer>${settings.metadata.title} · ${settings.metadata.subtitle}</footer>"
        } else ""
        val coverImage = if (hasCover) "<img src='cover.bin' alt='Cover image' style='width:100%;height:auto;'/>" else ""
        return """
            <?xml version='1.0' encoding='utf-8'?>
            <html xmlns='http://www.w3.org/1999/xhtml'>
                <head>
                    <title>${settings.metadata.title}</title>
                    <link href='stylesheet.css' rel='stylesheet' type='text/css'/>
                </head>
                <body>
                    <section class='cover'>
                        $coverImage
                        <h1 class='title'>${settings.metadata.title}</h1>
                        <h2 class='subtitle'>${settings.metadata.subtitle}</h2>
                    </section>
                    <section class='title-page'>
                        <h1 class='title'>${settings.metadata.title}</h1>
                        <h2 class='subtitle'>${settings.metadata.subtitle}</h2>
                        <p>${settings.metadata.author}</p>
                    </section>
                    <section class='content'>
                        <h3 class='heading'>${settings.metadata.subtitle}</h3>
                        $paragraphs
                        $footer
                    </section>
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildMetadataPage(settings: EbookSettings): String {
        fun field(label: String, value: String) =
            if (value.isBlank()) "" else "<dt>$label</dt><dd>$value</dd>"
        val meta = settings.metadata
        return """
            <?xml version='1.0' encoding='utf-8'?>
            <html xmlns='http://www.w3.org/1999/xhtml'>
                <head>
                    <title>Metadata</title>
                    <link href='stylesheet.css' rel='stylesheet' type='text/css'/>
                </head>
                <body>
                    <section class='metadata'>
                        <h2>${meta.title}</h2>
                        <h3>${meta.subtitle}</h3>
                        <dl>
                            ${field("Author", meta.author)}
                            ${field("Translator", meta.translator)}
                            ${field("Publisher", meta.publisher)}
                            ${field("Year", meta.publicationYear)}
                            ${field("Language", meta.language)}
                            ${field("Notes", meta.notes)}
                        </dl>
                    </section>
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildOpf(settings: EbookSettings, hasCover: Boolean): String {
        return """
            <?xml version='1.0' encoding='utf-8'?>
            <package version='3.0' xmlns='http://www.idpf.org/2007/opf' unique-identifier='bookid'>
                <metadata xmlns:dc='http://purl.org/dc/elements/1.1/'>
                    <dc:identifier id='bookid'>urn:uuid:${System.currentTimeMillis()}</dc:identifier>
                    <dc:title>${settings.metadata.title}</dc:title>
                    <dc:creator>${settings.metadata.author}</dc:creator>
                    <dc:language>${settings.metadata.language}</dc:language>
                    <meta property='dcterms:modified'>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())}</meta>
                </metadata>
                <manifest>
                    <item id='content' href='content.xhtml' media-type='application/xhtml+xml'/>
                    <item id='metadata' href='metadata.xhtml' media-type='application/xhtml+xml'/>
                    <item id='css' href='stylesheet.css' media-type='text/css'/>
                    ${if (hasCover) "<item id='cover' href='cover.bin' media-type='image/*' properties='cover-image'/>" else ""}
                </manifest>
                <spine>
                    <itemref idref='content'/>
                    <itemref idref='metadata'/>
                </spine>
            </package>
        """.trimIndent()
    }
}

private val ParagraphAlignment.css: String
    get() = when (this) {
        ParagraphAlignment.Left -> "left"
        ParagraphAlignment.Center -> "center"
        ParagraphAlignment.Right -> "right"
        ParagraphAlignment.Justify -> "justify"
    }

private val com.astral.ebook.model.FontFamilyOption.css: String
    get() = when (this) {
        com.astral.ebook.model.FontFamilyOption.Serif -> "'Literata', 'Merriweather', serif"
        com.astral.ebook.model.FontFamilyOption.SansSerif -> "'Inter', 'Roboto', sans-serif"
    }

private fun Int.toHexString() = String.format("#%06X", 0xFFFFFF and this)

private fun androidx.compose.ui.graphics.Color.toHex(): String {
    val int = (this.alpha * 255).toInt() shl 24 or
        ((this.red * 255).toInt() shl 16) or
        ((this.green * 255).toInt() shl 8) or
        (this.blue * 255).toInt()
    return String.format("#%06X", int and 0xFFFFFF)
}
