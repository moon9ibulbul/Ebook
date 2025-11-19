package com.astral.ebook.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.graphics.toArgb
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.FontFamilyOption
import com.astral.ebook.model.FontTarget
import com.astral.ebook.model.OutputFormat
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.repository.DocumentContent
import com.astral.ebook.repository.FormattedParagraph
import com.astral.ebook.repository.TextRun
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
import kotlin.math.min

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
        body: DocumentContent,
        coverImage: Uri?
    ): File = withContext(Dispatchers.IO) {
        val ext = if (settings.outputFormat == OutputFormat.EPUB) "epub" else "pdf"
        val filename = "Astral_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.$ext"
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            ?.takeIf { it.exists() || it.mkdirs() }
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val outFile = File(documentsDir, filename)
        if (settings.outputFormat == OutputFormat.EPUB) {
            generateEpub(context, outFile, settings, body, coverImage)
        } else {
            generatePdf(context, outFile, settings, body, coverImage)
        }
        outFile
    }

    private fun generatePdf(
        context: Context,
        file: File,
        settings: EbookSettings,
        body: DocumentContent,
        coverImage: Uri?
    ) {
        val doc = PdfDocument()
        val (pageWidth, pageHeight) = if (settings.orientation == Orientation.Portrait) {
            settings.pagePreset.widthPx to settings.pagePreset.heightPx
        } else {
            settings.pagePreset.heightPx to settings.pagePreset.widthPx
        }
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val palette = PdfPaintPalette(context, settings)
        val backgroundColor = settings.themeOptions.pageBackground.toArgb()
        val margins = settings.margins
        val contentWidth = pageWidth - (margins.start + margins.end)
        val lines = buildLineContent(body, settings, palette, contentWidth)
        var lineIndex = 0
        var pageNumber = 1

        drawCoverPage(doc, pageInfo, settings, palette, backgroundColor, coverImage, context)
        drawTitlePage(doc, pageInfo, settings, palette, backgroundColor)

        if (lines.isEmpty()) {
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(backgroundColor)
            val header = buildHeaderText(settings)
            if (header.isNotBlank()) {
                canvas.drawText(
                    header,
                    margins.start,
                    margins.top + palette.headerPaint.textSize,
                    palette.headerPaint
                )
            }
            val footerText = buildFooterText(settings, pageNumber)
            if (footerText.isNotBlank()) {
                canvas.drawText(
                    footerText,
                    margins.start,
                    pageHeight - margins.bottom / 2f,
                    palette.footerPaint
                )
            }
            doc.finishPage(page)
        } else {
            while (lineIndex < lines.size) {
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(backgroundColor)
                val header = buildHeaderText(settings)
                val footerText = buildFooterText(settings, pageNumber)
                val headerSpace = palette.headerSpace(header)
                val footerSpace = palette.footerSpace(footerText)
                if (header.isNotBlank()) {
                    canvas.drawText(
                        header,
                        margins.start,
                        margins.top + palette.headerPaint.textSize,
                        palette.headerPaint
                    )
                }
                var y = margins.top + headerSpace + palette.lineHeight
                while (lineIndex < lines.size && y < pageHeight - margins.bottom - footerSpace) {
                    when (val line = lines[lineIndex]) {
                        is LineContent.Text -> {
                            var x = margins.start + line.indent
                            line.segments.forEach { segment ->
                                val paint = palette.bodyPaint(segment.bold, segment.italic)
                                canvas.drawText(segment.text, x, y, paint)
                                x += paint.measureText(segment.text)
                            }
                            y += palette.lineHeight
                        }
                        is LineContent.Spacer -> {
                            y += line.spacing
                        }
                    }
                    lineIndex++
                }
                if (footerText.isNotBlank()) {
                    canvas.drawText(
                        footerText,
                        margins.start,
                        pageHeight - margins.bottom / 2f,
                        palette.footerPaint
                    )
                }
                doc.finishPage(page)
                pageNumber++
            }
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun drawCoverPage(
        document: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        settings: EbookSettings,
        palette: PdfPaintPalette,
        backgroundColor: Int,
        coverImage: Uri?,
        context: Context
    ) {
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(backgroundColor)
        coverImage?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                bitmap?.let {
                    val availableWidth = (pageInfo.pageWidth - (settings.margins.start + settings.margins.end)).toFloat()
                    val availableHeight = pageInfo.pageHeight.toFloat() * 0.6f
                    val scale = min(
                        availableWidth / it.width.toFloat(),
                        availableHeight / it.height.toFloat()
                    )
                    val destWidth = it.width.toFloat() * scale
                    val destHeight = it.height.toFloat() * scale
                    val left = (pageInfo.pageWidth.toFloat() - destWidth) / 2f
                    val top = settings.margins.top.toFloat()
                    canvas.drawBitmap(
                        it,
                        null,
                        RectF(left, top, left + destWidth, top + destHeight),
                        null
                    )
                    it.recycle()
                }
            }
        }
        val centerX = pageInfo.pageWidth.toFloat() / 2f
        var y = pageInfo.pageHeight.toFloat() * 0.8f
        if (settings.metadata.title.isNotBlank()) {
            drawCenteredText(canvas, settings.metadata.title, centerX, y, palette.titlePaint)
            y += palette.titlePaint.fontSpacing
        }
        if (settings.metadata.subtitle.isNotBlank()) {
            drawCenteredText(canvas, settings.metadata.subtitle, centerX, y, palette.subtitlePaint)
        }
        document.finishPage(page)
    }

    private fun drawTitlePage(
        document: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        settings: EbookSettings,
        palette: PdfPaintPalette,
        backgroundColor: Int
    ) {
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(backgroundColor)
        var y = settings.margins.top + palette.titlePaint.textSize
        if (settings.metadata.title.isNotBlank()) {
            canvas.drawText(settings.metadata.title, settings.margins.start, y, palette.titlePaint)
            y += palette.titlePaint.fontSpacing
        }
        if (settings.metadata.subtitle.isNotBlank()) {
            canvas.drawText(settings.metadata.subtitle, settings.margins.start, y, palette.subtitlePaint)
            y += palette.subtitlePaint.fontSpacing
        }
        val metaInfo = listOfNotNull(
            settings.metadata.author.takeIf { it.isNotBlank() }?.let { "Author: $it" },
            settings.metadata.publisher.takeIf { it.isNotBlank() }?.let { "Publisher: $it" },
            settings.metadata.publicationYear.takeIf { it.isNotBlank() }?.let { "Year: $it" },
            settings.metadata.language.takeIf { it.isNotBlank() }?.let { "Language: $it" },
            settings.metadata.translator.takeIf { it.isNotBlank() }?.let { "Translator: $it" }
        )
        metaInfo.forEach { info ->
            canvas.drawText(info, settings.margins.start, y, palette.baseBodyPaint)
            y += palette.baseBodyPaint.fontSpacing
        }
        document.finishPage(page)
    }

    private fun drawCenteredText(canvas: android.graphics.Canvas, text: String, centerX: Float, centerY: Float, paint: Paint) {
        val width = paint.measureText(text)
        canvas.drawText(text, centerX - width / 2f, centerY, paint)
    }

    private fun buildLineContent(
        body: DocumentContent,
        settings: EbookSettings,
        palette: PdfPaintPalette,
        contentWidth: Float
    ): MutableList<LineContent> {
        val lines = mutableListOf<LineContent>()
        body.paragraphs.forEachIndexed { index, paragraph ->
            val applyIndent = !(index == 0 && settings.paragraphOptions.skipIndentAfterHeading)
            val paragraphLines = wrapParagraph(paragraph, palette, contentWidth, applyIndent)
            lines += paragraphLines
            if (palette.paragraphSpacingPx > 0f) {
                lines += LineContent.Spacer(palette.paragraphSpacingPx)
            }
        }
        if (lines.isNotEmpty() && lines.last() is LineContent.Spacer) {
            lines.removeAt(lines.lastIndex)
        }
        return lines
    }

    private fun wrapParagraph(
        paragraph: FormattedParagraph,
        palette: PdfPaintPalette,
        contentWidth: Float,
        indentFirstLine: Boolean
    ): List<LineContent.Text> {
        if (paragraph.runs.all { it.text.isBlank() }) return emptyList()
        val lines = mutableListOf<LineContent.Text>()
        var isFirstLine = true
        var currentIndent = if (indentFirstLine) palette.indentPx else 0f
        var availableWidth = contentWidth - currentIndent
        var currentWidth = 0f
        var currentSegments = mutableListOf<TextRunSegment>()

        fun flush() {
            if (currentSegments.isNotEmpty()) {
                lines += LineContent.Text(currentSegments.toList(), if (isFirstLine && indentFirstLine) palette.indentPx else 0f)
                currentSegments = mutableListOf()
                currentWidth = 0f
                isFirstLine = false
                currentIndent = 0f
                availableWidth = contentWidth
            }
        }

        paragraph.runs.forEach { run ->
            val words = run.text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            for (word in words) {
                var token = if (currentSegments.isEmpty()) word else " $word"
                var paint = palette.bodyPaint(run.bold, run.italic)
                var width = paint.measureText(token)
                if (currentWidth + width > availableWidth && currentSegments.isNotEmpty()) {
                    flush()
                    token = word
                    paint = palette.bodyPaint(run.bold, run.italic)
                    width = paint.measureText(token)
                }
                currentSegments += TextRunSegment(token, run.bold, run.italic)
                currentWidth += width
            }
        }
        flush()
        return lines
    }

    private fun buildHeaderText(settings: EbookSettings): String {
        val parts = mutableListOf<String>()
        if (settings.metadata.title.isNotBlank()) parts += settings.metadata.title
        if (settings.metadata.subtitle.isNotBlank()) parts += settings.metadata.subtitle
        return parts.joinToString(" · ")
    }

    private fun buildFooterText(settings: EbookSettings, pageNumber: Int): String {
        if (!settings.footerOptions.showFooter) return ""
        val parts = mutableListOf<String>()
        if (settings.footerOptions.showTitle && settings.metadata.title.isNotBlank()) {
            parts += settings.metadata.title
        }
        if (settings.footerOptions.showSubtitle && settings.metadata.subtitle.isNotBlank()) {
            parts += settings.metadata.subtitle
        }
        if (settings.footerOptions.showPageNumber) {
            parts += pageNumber.toString()
        }
        return parts.joinToString(" · ")
    }

    private fun generateEpub(
        context: Context,
        file: File,
        settings: EbookSettings,
        body: DocumentContent,
        coverImage: Uri?
    ) {
        val customFonts = collectCustomFonts(settings)
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

            val css = buildCss(settings, customFonts)
            zip.putNextEntry(ZipEntry("OEBPS/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/stylesheet.css"))
            zip.write(css.toByteArray())
            zip.closeEntry()

            if (customFonts.isNotEmpty()) {
                zip.putNextEntry(ZipEntry("OEBPS/fonts/"))
                zip.closeEntry()
                customFonts.forEach { font ->
                    context.contentResolver.openInputStream(font.uri)?.use { input ->
                        zip.putNextEntry(ZipEntry("OEBPS/fonts/${font.fileName}"))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                }
            }

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
            val manifest = buildOpf(settings, coverImage != null, customFonts)
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
    }

    private fun buildCss(settings: EbookSettings, customFonts: List<EmbeddedFont>): String {
        val bodyFamily = settings.fonts.bodyFamily.cssValue(FontTarget.Body, customFonts)
        val titleFamily = settings.fonts.titleFamily.cssValue(FontTarget.Title, customFonts)
        val headingFamily = settings.fonts.headingFamily.cssValue(FontTarget.Heading, customFonts)
        val fontFaces = if (customFonts.isEmpty()) "" else customFonts.joinToString("\n") { font ->
            "@font-face { font-family: '${font.cssName}'; src: url('fonts/${font.fileName}'); }"
        }
        return """
            $fontFaces
            html, body {
                width: ${settings.pagePreset.widthPx}px;
                height: ${settings.pagePreset.heightPx}px;
                margin: 0;
            }
            body {
                background: ${settings.themeOptions.pageBackground.toHex()};
                color: ${settings.themeOptions.textColor.toHex()};
                font-family: $bodyFamily;
                font-size: ${settings.fonts.bodySize}pt;
                line-height: ${settings.fonts.lineHeight};
                text-align: ${settings.paragraphOptions.alignment.css};
            }
            .page {
                box-sizing: border-box;
                padding: ${settings.margins.top}px ${settings.margins.end}px ${settings.margins.bottom}px ${settings.margins.start}px;
                width: 100%;
                height: 100%;
                page-break-after: always;
                display: flex;
                flex-direction: column;
                justify-content: space-between;
            }
            h1.title {
                font-size: ${settings.fonts.titleSize}pt;
                text-align: center;
                font-family: $titleFamily;
            }
            h2.subtitle {
                font-size: ${settings.fonts.subtitleSize}pt;
                text-align: center;
                font-style: italic;
                font-family: $headingFamily;
            }
            p {
                text-indent: ${settings.paragraphOptions.firstLineIndentEm}em;
                margin-bottom: ${settings.paragraphOptions.extraParagraphSpacing}px;
            }
            p:first-child {
                text-indent: 0;
            }
            .body-text {
                flex: 1;
            }
            footer.page-footer {
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
            .cover-page img {
                width: 100%;
                height: auto;
            }
        """.trimIndent()
    }

    private fun buildContentXhtml(settings: EbookSettings, body: DocumentContent, hasCover: Boolean): String {
        val footerBuilder: (Int) -> String = { pageIndex ->
            if (!settings.footerOptions.showFooter) "" else {
                val parts = mutableListOf<String>()
                if (settings.footerOptions.showTitle && settings.metadata.title.isNotBlank()) parts += settings.metadata.title
                if (settings.footerOptions.showSubtitle && settings.metadata.subtitle.isNotBlank()) parts += settings.metadata.subtitle
                if (settings.footerOptions.showPageNumber) parts += (pageIndex + 1).toString()
                if (parts.isEmpty()) "" else "<footer class='page-footer'>${parts.joinToString(" · ")}</footer>"
            }
        }
        val coverImage = if (hasCover) "<img src='cover.bin' alt='Cover image' />" else ""
        val titlePageAuthor = listOfNotNull(
            settings.metadata.author.takeIf { it.isNotBlank() }?.escapeHtml(),
            settings.metadata.publisher.takeIf { it.isNotBlank() }?.escapeHtml()
        ).joinToString("<br/>")
        val chunks = chunkParagraphs(body, settings).ifEmpty { listOf(emptyList()) }
        val bodyPages = chunks.mapIndexed { index, paragraphs ->
            val pageParagraphs = if (paragraphs.isEmpty()) {
                "<p>&nbsp;</p>"
            } else {
                paragraphs.joinToString(separator = "") { it.toHtmlParagraph() }
            }
            """
                <section class='page body-page'>
                    <div class='body-text'>$pageParagraphs</div>
                    ${footerBuilder(index)}
                </section>
            """.trimIndent()
        }.joinToString(separator = "")
        return """
            <?xml version='1.0' encoding='utf-8'?>
            <html xmlns='http://www.w3.org/1999/xhtml'>
                <head>
                    <title>${settings.metadata.title.escapeHtml()}</title>
                    <meta name='viewport' content='width=${settings.pagePreset.widthPx}, height=${settings.pagePreset.heightPx}' />
                    <link href='stylesheet.css' rel='stylesheet' type='text/css'/>
                </head>
                <body>
                    <section class='page cover-page'>
                        $coverImage
                        <h1 class='title'>${settings.metadata.title.escapeHtml()}</h1>
                        <h2 class='subtitle'>${settings.metadata.subtitle.escapeHtml()}</h2>
                    </section>
                    <section class='page title-page'>
                        <h1 class='title'>${settings.metadata.title.escapeHtml()}</h1>
                        <h2 class='subtitle'>${settings.metadata.subtitle.escapeHtml()}</h2>
                        ${if (titlePageAuthor.isBlank()) "" else "<p class='author'>$titlePageAuthor</p>"}
                    </section>
                    $bodyPages
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildMetadataPage(settings: EbookSettings): String {
        fun field(label: String, value: String) =
            if (value.isBlank()) "" else "<dt>$label</dt><dd>${value.escapeHtml()}</dd>"
        val meta = settings.metadata
        return """
            <?xml version='1.0' encoding='utf-8'?>
            <html xmlns='http://www.w3.org/1999/xhtml'>
                <head>
                    <title>Metadata</title>
                    <link href='stylesheet.css' rel='stylesheet' type='text/css'/>
                </head>
                <body>
                    <section class='page metadata'>
                        <h2>${meta.title.escapeHtml()}</h2>
                        <h3>${meta.subtitle.escapeHtml()}</h3>
                        <dl class='metadata'>
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

    private fun buildOpf(settings: EbookSettings, hasCover: Boolean, customFonts: List<EmbeddedFont>): String {
        val fontItems = customFonts.joinToString(separator = "\n") { font ->
            "<item id='font_${font.role.name.lowercase(Locale.US)}' href='fonts/${font.fileName}' media-type='${font.mediaType}'/>"
        }
        return """
            <?xml version='1.0' encoding='utf-8'?>
            <package version='3.0' xmlns='http://www.idpf.org/2007/opf' unique-identifier='bookid'>
                <metadata xmlns:dc='http://purl.org/dc/elements/1.1/'>
                    <dc:identifier id='bookid'>urn:uuid:${System.currentTimeMillis()}</dc:identifier>
                    <dc:title>${settings.metadata.title.escapeHtml()}</dc:title>
                    <dc:creator>${settings.metadata.author.escapeHtml()}</dc:creator>
                    <dc:language>${settings.metadata.language.escapeHtml()}</dc:language>
                    <meta property='dcterms:modified'>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())}</meta>
                    <meta property='rendition:layout'>pre-paginated</meta>
                    <meta property='rendition:orientation'>${settings.orientation.name.lowercase(Locale.US)}</meta>
                    <meta property='rendition:spread'>auto</meta>
                </metadata>
                <manifest>
                    <item id='content' href='content.xhtml' media-type='application/xhtml+xml'/>
                    <item id='metadata' href='metadata.xhtml' media-type='application/xhtml+xml'/>
                    <item id='css' href='stylesheet.css' media-type='text/css'/>
                    ${if (hasCover) "<item id='cover' href='cover.bin' media-type='image/*' properties='cover-image'/>" else ""}
                    $fontItems
                </manifest>
                <spine>
                    <itemref idref='content'/>
                    <itemref idref='metadata'/>
                </spine>
            </package>
        """.trimIndent()
    }
}

private class PdfPaintPalette(context: Context, settings: EbookSettings) {
    private val density = context.resources.displayMetrics.density
    private val textColor = settings.themeOptions.textColor.toArgb()
    private val titleTypeface = resolveTypeface(context, settings.fonts.titleFamily, settings.fonts.titleFontUri)
    private val headingTypeface = resolveTypeface(context, settings.fonts.headingFamily, settings.fonts.headingFontUri)
    private val bodyTypeface = resolveTypeface(context, settings.fonts.bodyFamily, settings.fonts.bodyFontUri)

    val titlePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.titleSize * density
        typeface = Typeface.create(titleTypeface, Typeface.BOLD)
    }
    val subtitlePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.subtitleSize * density
        typeface = headingTypeface
        textSkewX = -0.1f
    }
    val headerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.subtitleSize * density * 0.6f
        typeface = headingTypeface
    }
    val footerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.bodySize * density * 0.85f
        typeface = bodyTypeface
    }
    val baseBodyPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.bodySize * density
        typeface = bodyTypeface
    }
    private val styleCache = mutableMapOf<Pair<Boolean, Boolean>, Paint>()

    val lineHeight: Float = baseBodyPaint.fontSpacing * settings.fonts.lineHeight
    val paragraphSpacingPx: Float = settings.paragraphOptions.extraParagraphSpacing * density
    val indentPx: Float = baseBodyPaint.textSize * settings.paragraphOptions.firstLineIndentEm

    fun bodyPaint(bold: Boolean, italic: Boolean): Paint {
        val key = bold to italic
        return styleCache.getOrPut(key) {
            Paint(baseBodyPaint).apply {
                typeface = Typeface.create(bodyTypeface, when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                })
            }
        }
    }

    fun headerSpace(header: String): Float = if (header.isBlank()) 0f else headerPaint.fontSpacing + 16f
    fun footerSpace(footer: String): Float = if (footer.isBlank()) 0f else footerPaint.fontSpacing + 16f
}

private data class TextRunSegment(val text: String, val bold: Boolean, val italic: Boolean)

private sealed interface LineContent {
    data class Text(val segments: List<TextRunSegment>, val indent: Float) : LineContent
    data class Spacer(val spacing: Float) : LineContent
}

private data class EmbeddedFont(
    val role: FontTarget,
    val uri: Uri,
    val fileName: String,
    val cssName: String,
    val mediaType: String
)

private fun collectCustomFonts(settings: EbookSettings): List<EmbeddedFont> {
    val fonts = mutableListOf<EmbeddedFont>()
    fun add(role: FontTarget, option: FontFamilyOption, uriString: String?, prefix: String) {
        if (option == FontFamilyOption.Custom && !uriString.isNullOrBlank()) {
            val uri = Uri.parse(uriString)
            val extension = uri.lastPathSegment?.substringAfterLast('.', "ttf") ?: "ttf"
            val mediaType = if (extension.equals("otf", true)) "font/otf" else "font/ttf"
            val cssName = "Astral${prefix.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }}Font"
            fonts += EmbeddedFont(role, uri, "$prefix.$extension", cssName, mediaType)
        }
    }
    add(FontTarget.Title, settings.fonts.titleFamily, settings.fonts.titleFontUri, "title")
    add(FontTarget.Heading, settings.fonts.headingFamily, settings.fonts.headingFontUri, "heading")
    add(FontTarget.Body, settings.fonts.bodyFamily, settings.fonts.bodyFontUri, "body")
    return fonts
}

private fun chunkParagraphs(body: DocumentContent, settings: EbookSettings): List<List<FormattedParagraph>> {
    val usableHeight = settings.pagePreset.heightPx - (settings.margins.top + settings.margins.bottom)
    val estimatedLineHeight = settings.fonts.bodySize * settings.fonts.lineHeight * 2
    val approxLines = (usableHeight / estimatedLineHeight).toInt().coerceAtLeast(4)
    val paragraphsPerPage = (approxLines / 4).coerceAtLeast(1)
    return body.paragraphs.chunked(paragraphsPerPage)
}

private fun FormattedParagraph.toHtmlParagraph(): String {
    val content = runs.joinToString(separator = "") { it.toHtmlSpan() }
    return if (content.isBlank()) "<p>&nbsp;</p>" else "<p>$content</p>"
}

private fun TextRun.toHtmlSpan(): String {
    val escaped = text.escapeHtml()
    return when {
        bold && italic -> "<strong><em>$escaped</em></strong>"
        bold -> "<strong>$escaped</strong>"
        italic -> "<em>$escaped</em>"
        else -> escaped
    }
}

private fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private val ParagraphAlignment.css: String
    get() = when (this) {
        ParagraphAlignment.Left -> "left"
        ParagraphAlignment.Center -> "center"
        ParagraphAlignment.Right -> "right"
        ParagraphAlignment.Justify -> "justify"
    }

private fun FontFamilyOption.cssValue(role: FontTarget, fonts: List<EmbeddedFont>): String {
    return when (this) {
        FontFamilyOption.Serif -> "'Literata', 'Merriweather', serif"
        FontFamilyOption.SansSerif -> "'Inter', 'Roboto', sans-serif"
        FontFamilyOption.Custom -> {
            val cssName = fonts.firstOrNull { it.role == role }?.cssName ?: "Literata"
            "'$cssName', serif"
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toHex(): String {
    val int = (this.alpha * 255).toInt() shl 24 or
        ((this.red * 255).toInt() shl 16) or
        ((this.green * 255).toInt() shl 8) or
        (this.blue * 255).toInt()
    return String.format("#%06X", int and 0xFFFFFF)
}

private fun resolveTypeface(context: Context, option: FontFamilyOption, uriString: String?): Typeface {
    if (option == FontFamilyOption.Custom && !uriString.isNullOrBlank()) {
        loadTypeface(context, Uri.parse(uriString))?.let { return it }
    }
    return when (option) {
        FontFamilyOption.SansSerif -> Typeface.SANS_SERIF
        FontFamilyOption.Serif, FontFamilyOption.Custom -> Typeface.SERIF
    }
}

private fun loadTypeface(context: Context, uri: Uri): Typeface? {
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            Typeface.Builder(pfd.fileDescriptor).build()
        }
    } catch (_: Throwable) {
        null
    }
}
