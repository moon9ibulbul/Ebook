package com.astral.ebook.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.FontFamilyOption
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.ParagraphAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Contains PDF generation logic.
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
        val filename = "Astral_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            ?.takeIf { it.exists() || it.mkdirs() }
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val outFile = File(documentsDir, filename)
        generatePdf(context, outFile, settings, body, coverImage)
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
        val contentWidth = pageWidth.toFloat() - (margins.start + margins.end)
        val lines = buildLineContent(body, settings, palette, contentWidth)
        var lineIndex = 0
        var pageNumber = 1

        drawCoverPage(doc, pageInfo, settings, palette, backgroundColor, coverImage, context)
        drawTitlePage(doc, pageInfo, settings, palette, backgroundColor)

        var isFirstBodyPage = true

        if (lines.isEmpty()) {
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(backgroundColor)
            drawBodyHeading(canvas, settings, palette, pageInfo, contentWidth)
            val footer = buildFooterContent(settings, pageNumber)
            footer?.let { drawFooter(canvas, it, pageWidth, pageHeight, margins, palette) }
            doc.finishPage(page)
            pageNumber++
        } else {
            while (lineIndex < lines.size) {
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(backgroundColor)
                var headingOffset = 0f
                if (isFirstBodyPage) {
                    headingOffset = drawBodyHeading(canvas, settings, palette, pageInfo, contentWidth)
                    isFirstBodyPage = false
                }
                val footer = buildFooterContent(settings, pageNumber)
                val footerSpace = palette.footerSpace(footer)
                var y = margins.top + headingOffset + palette.lineHeight
                while (lineIndex < lines.size && y < pageHeight.toFloat() - margins.bottom - footerSpace) {
                    when (val line = lines[lineIndex]) {
                        is LineContent.Text -> {
                            val lineWidth = line.segments.sumOf {
                                palette.bodyPaint(
                                    it.bold,
                                    it.italic,
                                    it.underline,
                                    it.strikeThrough
                                ).measureText(it.text).toDouble()
                            }.toFloat()
                            val startX = when (line.alignment) {
                                ParagraphAlignment.Left, ParagraphAlignment.Justify -> margins.start + line.indent
                                ParagraphAlignment.Center -> margins.start + (contentWidth - lineWidth) / 2f
                                ParagraphAlignment.Right -> pageWidth.toFloat() - margins.end - lineWidth
                            }
                            var x = startX
                            line.segments.forEach { segment ->
                                val paint = palette.bodyPaint(
                                    segment.bold,
                                    segment.italic,
                                    segment.underline,
                                    segment.strikeThrough
                                )
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
                footer?.let { drawFooter(canvas, it, pageWidth, pageHeight, margins, palette) }
                doc.finishPage(page)
                pageNumber++
            }
        }

        pageNumber = drawMetadataPage(doc, pageInfo, settings, palette, backgroundColor, pageNumber)

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
        var drewImage = false
        coverImage?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                bitmap?.let {
                    val destRect = if (settings.coverOptions.fullBleed) {
                        val widthScale = pageInfo.pageWidth.toFloat() / it.width.toFloat()
                        val heightScale = pageInfo.pageHeight.toFloat() / it.height.toFloat()
                        val scale = max(widthScale, heightScale)
                        val destWidth = it.width * scale
                        val destHeight = it.height * scale
                        val left = (pageInfo.pageWidth.toFloat() - destWidth) / 2f
                        val top = (pageInfo.pageHeight.toFloat() - destHeight) / 2f
                        RectF(left, top, left + destWidth, top + destHeight)
                    } else {
                        val availableWidth = pageInfo.pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
                        val availableHeight = pageInfo.pageHeight.toFloat() - (settings.margins.top + settings.margins.bottom)
                        val scale = min(
                            availableWidth / it.width.toFloat(),
                            availableHeight / it.height.toFloat()
                        )
                        val destWidth = it.width * scale
                        val destHeight = it.height * scale
                        val left = (pageInfo.pageWidth.toFloat() - destWidth) / 2f
                        val top = (pageInfo.pageHeight.toFloat() - destHeight) / 2f
                        RectF(left, top, left + destWidth, top + destHeight)
                    }
                    canvas.drawBitmap(it, null, destRect, null)
                    it.recycle()
                    drewImage = true
                }
            }
        }
        if (!drewImage) {
            val contentWidth = pageInfo.pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
            val centerX = pageInfo.pageWidth.toFloat() / 2f
            val titleLines = wrapText(settings.metadata.title, palette.titlePaint, contentWidth)
            val subtitleLines = wrapText(settings.metadata.subtitle, palette.subtitlePaint, contentWidth)
            val titleHeight = titleLines.size * palette.titlePaint.fontSpacing
            val subtitleHeight = subtitleLines.size * palette.subtitlePaint.fontSpacing
            val spacing = if (titleLines.isNotEmpty() && subtitleLines.isNotEmpty()) palette.subtitlePaint.fontSpacing else 0f
            var y = (pageInfo.pageHeight.toFloat() - (titleHeight + subtitleHeight + spacing)) / 2f
            if (titleLines.isNotEmpty()) {
                var baseline = y + palette.titlePaint.textSize
                titleLines.forEach { line ->
                    drawCenteredText(canvas, line, centerX.toFloat(), baseline, palette.titlePaint)
                    baseline += palette.titlePaint.fontSpacing
                }
                y += titleHeight + spacing
            }
            if (subtitleLines.isNotEmpty()) {
                var baseline = y + palette.subtitlePaint.textSize
                subtitleLines.forEach { line ->
                    drawCenteredText(canvas, line, centerX.toFloat(), baseline, palette.subtitlePaint)
                    baseline += palette.subtitlePaint.fontSpacing
                }
            }
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
        val contentWidth = pageInfo.pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
        val centerX = pageInfo.pageWidth.toFloat() / 2f
        val titleLines = wrapText(settings.metadata.title, palette.titlePaint, contentWidth)
        val chapterLines = wrapText(settings.metadata.chapter, palette.subtitlePaint, contentWidth)
        val titleHeight = titleLines.size * palette.titlePaint.fontSpacing
        val chapterHeight = chapterLines.size * palette.subtitlePaint.fontSpacing
        val spacing = if (titleLines.isNotEmpty() && chapterLines.isNotEmpty()) palette.subtitlePaint.fontSpacing else 0f
        var y = (pageInfo.pageHeight.toFloat() - (titleHeight + chapterHeight + spacing)) / 2f
        if (titleLines.isNotEmpty()) {
            var baseline = y + palette.titlePaint.textSize
            titleLines.forEach { line ->
                drawCenteredText(canvas, line, centerX.toFloat(), baseline, palette.titlePaint)
                baseline += palette.titlePaint.fontSpacing
            }
            y += titleHeight + spacing
        }
        if (chapterLines.isNotEmpty()) {
            var baseline = y + palette.subtitlePaint.textSize
            chapterLines.forEach { line ->
                drawCenteredText(canvas, line, centerX.toFloat(), baseline, palette.subtitlePaint)
                baseline += palette.subtitlePaint.fontSpacing
            }
        }
        document.finishPage(page)
    }

    private fun drawBodyHeading(
        canvas: android.graphics.Canvas,
        settings: EbookSettings,
        palette: PdfPaintPalette,
        pageInfo: PdfDocument.PageInfo,
        contentWidth: Float
    ): Float {
        val subtitle = settings.metadata.subtitle
        val chapter = settings.metadata.chapter
        val headingText = when {
            chapter.isBlank() && subtitle.isBlank() -> null
            chapter.isBlank() -> subtitle
            subtitle.isBlank() -> chapter
            else -> "$chapter : $subtitle"
        } ?: return 0f
        val centerX = pageInfo.pageWidth.toFloat() / 2f
        val headingLines = wrapText(headingText, palette.titlePaint, contentWidth)
        if (headingLines.isEmpty()) return 0f
        var baseline = settings.margins.top + palette.titlePaint.textSize
        headingLines.forEach { line ->
            drawCenteredText(canvas, line, centerX, baseline, palette.titlePaint)
            baseline += palette.titlePaint.fontSpacing
        }
        return (baseline - settings.margins.top) + palette.headingGap
    }

    private fun drawMetadataPage(
        document: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        settings: EbookSettings,
        palette: PdfPaintPalette,
        backgroundColor: Int,
        pageNumber: Int
    ): Int {
        val meta = settings.metadata
        val entries = listOfNotNull(
            meta.author.takeIf { it.isNotBlank() }?.let { "Author" to it },
            meta.translator.takeIf { it.isNotBlank() }?.let { "Translator" to it },
            meta.publisher.takeIf { it.isNotBlank() }?.let { "Publisher" to it },
            meta.publicationYear.takeIf { it.isNotBlank() }?.let { "Year" to it },
            meta.language.takeIf { it.isNotBlank() }?.let { "Language" to it },
            meta.notes.takeIf { it.isNotBlank() }?.let { "Notes" to it }
        )
        if (entries.isEmpty()) return pageNumber
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(backgroundColor)
        val contentWidth = pageInfo.pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
        val centerX = pageInfo.pageWidth.toFloat() / 2f
        var y = settings.margins.top
        val titleLines = wrapText(meta.title, palette.titlePaint, contentWidth)
        if (titleLines.isNotEmpty()) {
            var baseline = y + palette.titlePaint.textSize
            titleLines.forEach { line ->
                drawCenteredText(canvas, line, centerX.toFloat(), baseline, palette.titlePaint)
                baseline += palette.titlePaint.fontSpacing
            }
            y += titleLines.size * palette.titlePaint.fontSpacing
        }
        y += palette.baseBodyPaint.fontSpacing
        val labelMaxWidth = entries.maxOf { palette.metadataLabelPaint.measureText(it.first) }
        val colonX = settings.margins.start + labelMaxWidth + 12f
        val valueStart = colonX + palette.metadataLabelPaint.measureText(":") + 8f
        val valueWidth = pageInfo.pageWidth.toFloat() - settings.margins.end - valueStart
        entries.forEach { (label, value) ->
            val labelX = (settings.margins.start + labelMaxWidth) - palette.metadataLabelPaint.measureText(label)
            val valueLines = wrapText(value, palette.baseBodyPaint, valueWidth)
            var baseline = y + palette.baseBodyPaint.textSize
            canvas.drawText(label, labelX, baseline, palette.metadataLabelPaint)
            canvas.drawText(":", colonX, baseline, palette.metadataLabelPaint)
            if (valueLines.isNotEmpty()) {
                canvas.drawText(valueLines.first(), valueStart, baseline, palette.baseBodyPaint)
                var innerBaseline = baseline + palette.baseBodyPaint.fontSpacing
                valueLines.drop(1).forEach { line ->
                    canvas.drawText(line, valueStart, innerBaseline, palette.baseBodyPaint)
                    innerBaseline += palette.baseBodyPaint.fontSpacing
                }
                y = innerBaseline
            } else {
                y = baseline + palette.baseBodyPaint.fontSpacing
            }
        }
        val footer = buildFooterContent(settings, pageNumber)
        footer?.let { drawFooter(canvas, it, pageInfo.pageWidth, pageInfo.pageHeight, settings.margins, palette) }
        document.finishPage(page)
        return pageNumber + 1
    }

    private fun drawFooter(
        canvas: android.graphics.Canvas,
        footer: FooterContent,
        pageWidth: Int,
        pageHeight: Int,
        margins: com.astral.ebook.model.Margins,
        palette: PdfPaintPalette
    ) {
        val paint = palette.footerPaint
        val textPaint = TextPaint(paint)
        val baseline = pageHeight.toFloat() - margins.bottom / 2f
        val contentWidth = pageWidth.toFloat() - (margins.start + margins.end)
        val rightText = if (footer.right.isNotBlank()) {
            TextUtils.ellipsize(footer.right, textPaint, contentWidth, TextUtils.TruncateAt.END).toString()
        } else {
            ""
        }
        val rightWidth = paint.measureText(rightText)
        if (rightText.isNotBlank()) {
            canvas.drawText(rightText, pageWidth.toFloat() - margins.end - rightWidth, baseline, paint)
        }
        if (footer.left.isNotBlank()) {
            val maxLeftWidth = (contentWidth - rightWidth - 16f).coerceAtLeast(0f)
            val leftText = TextUtils.ellipsize(footer.left, textPaint, maxLeftWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(leftText, margins.start, baseline, paint)
        }
    }

    private fun drawCenteredText(
        canvas: android.graphics.Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        paint: Paint
    ) {
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
            val alignment = paragraph.alignment ?: settings.paragraphOptions.alignment
            val paragraphLines = wrapParagraph(paragraph, palette, contentWidth, applyIndent, alignment)
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
        indentFirstLine: Boolean,
        alignment: ParagraphAlignment
    ): List<LineContent.Text> {
        if (paragraph.runs.all { it.text.isBlank() }) return emptyList()
        val lines = mutableListOf<LineContent.Text>()
        val shouldIndent = indentFirstLine && alignment.allowsIndent()
        var isFirstLine = true
        var currentIndent = if (shouldIndent) palette.indentPx else 0f
        var availableWidth = contentWidth - currentIndent
        var currentSegments = mutableListOf<TextRunSegment>()
        var currentWidth = 0f

        fun flush() {
            if (currentSegments.isNotEmpty()) {
                val indentForLine = if (isFirstLine) currentIndent else 0f
                lines += LineContent.Text(currentSegments.toList(), indentForLine, alignment)
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
                var paint = palette.bodyPaint(run.bold, run.italic, run.underline, run.strikeThrough)
                var width = paint.measureText(token)
                if (currentWidth + width > availableWidth && currentSegments.isNotEmpty()) {
                    flush()
                    token = word
                    paint = palette.bodyPaint(run.bold, run.italic, run.underline, run.strikeThrough)
                    width = paint.measureText(token)
                }
                currentSegments += TextRunSegment(token, run.bold, run.italic, run.underline, run.strikeThrough)
                currentWidth += width
            }
        }
        flush()
        return lines
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (maxWidth <= 0f) return listOf(trimmed)
        val words = trimmed.split(Regex("""\s+"""))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var currentWidth = 0f

        fun flush() {
            if (current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder()
                currentWidth = 0f
            }
        }

        words.forEach { rawWord ->
            var word = rawWord
            if (paint.measureText(word) > maxWidth) {
                flush()
                while (word.isNotEmpty()) {
                    val count = paint.breakText(word, true, maxWidth, null)
                    lines += word.substring(0, count)
                    word = word.substring(count)
                }
            } else {
                val token = if (current.isEmpty()) word else " $word"
                val width = paint.measureText(token)
                if (currentWidth + width > maxWidth) {
                    flush()
                    current.append(word)
                    currentWidth = paint.measureText(word)
                } else {
                    current.append(token)
                    currentWidth += width
                }
            }
        }
        flush()
        return lines
    }

    private fun buildFooterContent(settings: EbookSettings, pageNumber: Int): FooterContent? {
        if (!settings.footerOptions.showFooter) return null
        val leftParts = mutableListOf<String>()
        if (settings.footerOptions.showTitle && settings.metadata.title.isNotBlank()) {
            leftParts += settings.metadata.title
        }
        if (settings.footerOptions.showChapter && settings.metadata.chapter.isNotBlank()) {
            leftParts += settings.metadata.chapter
        }
        val right = if (settings.footerOptions.showPageNumber) pageNumber.toString() else ""
        if (leftParts.isEmpty() && right.isBlank()) return null
        return FooterContent(leftParts.joinToString(" · "), right)
    }
}

private data class FooterContent(val left: String, val right: String)

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
    val footerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.footerOptions.fontSize * density
        typeface = bodyTypeface
    }
    val baseBodyPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.bodySize * density
        typeface = bodyTypeface
    }
    val metadataLabelPaint: Paint = Paint(baseBodyPaint).apply {
        typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
    }
    val lineHeight: Float = baseBodyPaint.fontSpacing * settings.fonts.lineHeight
    val paragraphSpacingPx: Float = settings.paragraphOptions.extraParagraphSpacing * density
    val indentPx: Float = baseBodyPaint.textSize * settings.paragraphOptions.firstLineIndentEm
    val headingGap: Float = baseBodyPaint.fontSpacing

    private data class TextStyleKey(
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strikeThrough: Boolean
    )

    private val styleCache = mutableMapOf<TextStyleKey, Paint>()

    fun bodyPaint(bold: Boolean, italic: Boolean, underline: Boolean, strikeThrough: Boolean): Paint {
        val key = TextStyleKey(bold, italic, underline, strikeThrough)
        return styleCache.getOrPut(key) {
            Paint(baseBodyPaint).apply {
                typeface = Typeface.create(
                    bodyTypeface,
                    when {
                        bold && italic -> Typeface.BOLD_ITALIC
                        bold -> Typeface.BOLD
                        italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                )
                isUnderlineText = underline
                isStrikeThruText = strikeThrough
            }
        }
    }

    fun footerSpace(footer: FooterContent?): Float = if (footer == null) 0f else footerPaint.fontSpacing + 16f
}

private data class TextRunSegment(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikeThrough: Boolean
)

private sealed interface LineContent {
    data class Text(val segments: List<TextRunSegment>, val indent: Float, val alignment: ParagraphAlignment) : LineContent
    data class Spacer(val spacing: Float) : LineContent
}

private fun ParagraphAlignment.allowsIndent(): Boolean = this == ParagraphAlignment.Left || this == ParagraphAlignment.Justify

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
