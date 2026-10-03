package com.astral.ebook.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.ParagraphAlignment
import kotlin.math.max
import kotlin.math.min

class PageRenderer(
    private val context: Context,
    private val settings: EbookSettings,
    private val layoutEngine: EbookLayoutEngine
) {
    private val backgroundColor = settings.themeOptions.pageBackground.toArgb()

    fun drawPage(
        canvas: Canvas,
        page: PageContent,
        pageWidth: Int,
        pageHeight: Int,
        coverImage: Uri? = null
    ) {
        canvas.drawColor(backgroundColor)

        when {
            page.isCoverPage -> drawCoverPage(canvas, pageWidth, pageHeight, coverImage)
            page.isTitlePage -> drawTitlePage(canvas, pageWidth, pageHeight)
            page.isMetadataPage -> drawMetadataPage(canvas, pageWidth, pageHeight, page.pageNumber)
            else -> drawBodyPage(canvas, page, pageWidth, pageHeight)
        }
    }

    private fun drawBodyPage(canvas: Canvas, page: PageContent, pageWidth: Int, pageHeight: Int) {
        val margins = settings.margins
        val contentWidth = pageWidth.toFloat() - (margins.start + margins.end)

        var headingOffset = 0f
        if (page.isFirstBodyPage) {
            headingOffset = drawBodyHeading(canvas, pageWidth, contentWidth)
        }

        val footer = layoutEngine.buildFooterContent(page.pageNumber)

        var y = margins.top + headingOffset + layoutEngine.lineHeight
        page.lines.forEach { line ->
            when (line) {
                is LineContent.Text -> {
                    val lineWidth = line.segments.sumOf {
                        layoutEngine.bodyPaint(
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
                        val paint = layoutEngine.bodyPaint(
                            segment.bold,
                            segment.italic,
                            segment.underline,
                            segment.strikeThrough
                        )
                        canvas.drawText(segment.text, x, y, paint)
                        x += paint.measureText(segment.text)
                    }
                    y += layoutEngine.lineHeight
                }
                is LineContent.Spacer -> {
                    y += line.spacing
                }
            }
        }

        footer?.let { drawFooter(canvas, it, pageWidth, pageHeight) }
    }

    private fun drawCoverPage(canvas: Canvas, pageWidth: Int, pageHeight: Int, coverImage: Uri?) {
        var drewImage = false
        coverImage?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    bitmap?.let {
                        val destRect = if (settings.coverOptions.fullBleed) {
                            val widthScale = pageWidth.toFloat() / it.width.toFloat()
                            val heightScale = pageHeight.toFloat() / it.height.toFloat()
                            val scale = max(widthScale, heightScale)
                            val destWidth = it.width * scale
                            val destHeight = it.height * scale
                            val left = (pageWidth.toFloat() - destWidth) / 2f
                            val top = (pageHeight.toFloat() - destHeight) / 2f
                            RectF(left, top, left + destWidth, top + destHeight)
                        } else {
                            val availableWidth = pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
                            val availableHeight = pageHeight.toFloat() - (settings.margins.top + settings.margins.bottom)
                            val scale = min(
                                availableWidth / it.width.toFloat(),
                                availableHeight / it.height.toFloat()
                            )
                            val destWidth = it.width * scale
                            val destHeight = it.height * scale
                            val left = (pageWidth.toFloat() - destWidth) / 2f
                            val top = (pageHeight.toFloat() - destHeight) / 2f
                            RectF(left, top, left + destWidth, top + destHeight)
                        }
                        canvas.drawBitmap(it, null, destRect, null)
                        it.recycle()
                        drewImage = true
                    }
                }
            } catch (_: Exception) {}
        }
        if (!drewImage) {
            val contentWidth = pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
            val centerX = pageWidth.toFloat() / 2f
            val titleLines = layoutEngine.wrapText(settings.metadata.title, layoutEngine.titlePaint, contentWidth)
            val subtitleLines = layoutEngine.wrapText(settings.metadata.subtitle, layoutEngine.subtitlePaint, contentWidth)
            val titleHeight = titleLines.size * layoutEngine.titlePaint.fontSpacing
            val subtitleHeight = subtitleLines.size * layoutEngine.subtitlePaint.fontSpacing
            val spacing = if (titleLines.isNotEmpty() && subtitleLines.isNotEmpty()) layoutEngine.subtitlePaint.fontSpacing else 0f
            var y = (pageHeight.toFloat() - (titleHeight + subtitleHeight + spacing)) / 2f
            if (titleLines.isNotEmpty()) {
                var baseline = y + layoutEngine.titlePaint.textSize
                titleLines.forEach { line ->
                    drawCenteredText(canvas, line, centerX, baseline, layoutEngine.titlePaint)
                    baseline += layoutEngine.titlePaint.fontSpacing
                }
                y += titleHeight + spacing
            }
            if (subtitleLines.isNotEmpty()) {
                var baseline = y + layoutEngine.subtitlePaint.textSize
                subtitleLines.forEach { line ->
                    drawCenteredText(canvas, line, centerX, baseline, layoutEngine.subtitlePaint)
                    baseline += layoutEngine.subtitlePaint.fontSpacing
                }
            }
        }
    }

    private fun drawTitlePage(canvas: Canvas, pageWidth: Int, pageHeight: Int) {
        val contentWidth = pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
        val centerX = pageWidth.toFloat() / 2f
        val titleLines = layoutEngine.wrapText(settings.metadata.title, layoutEngine.titlePaint, contentWidth)
        val chapterLines = layoutEngine.wrapText(settings.metadata.chapter, layoutEngine.chapterPaint, contentWidth)
        val titleHeight = titleLines.size * layoutEngine.titlePaint.fontSpacing
        val chapterHeight = chapterLines.size * layoutEngine.chapterPaint.fontSpacing
        val spacing = if (titleLines.isNotEmpty() && chapterLines.isNotEmpty()) layoutEngine.chapterPaint.fontSpacing else 0f
        var y = (pageHeight.toFloat() - (titleHeight + chapterHeight + spacing)) / 2f
        if (titleLines.isNotEmpty()) {
            var baseline = y + layoutEngine.titlePaint.textSize
            titleLines.forEach { line ->
                drawCenteredText(canvas, line, centerX, baseline, layoutEngine.titlePaint)
                baseline += layoutEngine.titlePaint.fontSpacing
            }
            y += titleHeight + spacing
        }
        if (chapterLines.isNotEmpty()) {
            var baseline = y + layoutEngine.chapterPaint.textSize
            chapterLines.forEach { line ->
                drawCenteredText(canvas, line, centerX, baseline, layoutEngine.chapterPaint)
                baseline += layoutEngine.chapterPaint.fontSpacing
            }
        }
    }

    private fun drawBodyHeading(canvas: Canvas, pageWidth: Int, contentWidth: Float): Float {
        val subtitle = settings.metadata.subtitle
        val chapter = settings.metadata.chapter
        val headingText = when {
            chapter.isBlank() && subtitle.isBlank() -> null
            chapter.isBlank() -> subtitle
            subtitle.isBlank() -> chapter
            else -> "$chapter : $subtitle"
        } ?: return 0f
        val centerX = pageWidth.toFloat() / 2f
        val headingLines = layoutEngine.wrapText(headingText, layoutEngine.headingPaint, contentWidth)
        if (headingLines.isEmpty()) return 0f
        var baseline = settings.margins.top + layoutEngine.headingPaint.textSize
        headingLines.forEach { line ->
            drawCenteredText(canvas, line, centerX, baseline, layoutEngine.headingPaint)
            baseline += layoutEngine.headingPaint.fontSpacing
        }
        return layoutEngine.calculateBodyHeadingHeight(contentWidth)
    }

    private fun drawMetadataPage(canvas: Canvas, pageWidth: Int, pageHeight: Int, pageNumber: Int) {
        val meta = settings.metadata
        val entries = listOfNotNull(
            meta.author.takeIf { it.isNotBlank() }?.let { "Author" to it },
            meta.translator.takeIf { it.isNotBlank() }?.let { "Translator" to it },
            meta.publisher.takeIf { it.isNotBlank() }?.let { "Publisher" to it },
            meta.publicationYear.takeIf { it.isNotBlank() }?.let { "Year" to it },
            meta.language.takeIf { it.isNotBlank() }?.let { "Language" to it },
            meta.notes.takeIf { it.isNotBlank() }?.let { "Notes" to it }
        )
        val contentWidth = pageWidth.toFloat() - (settings.margins.start + settings.margins.end)
        val centerX = pageWidth.toFloat() / 2f
        var y = settings.margins.top
        val titleLines = layoutEngine.wrapText(meta.title, layoutEngine.titlePaint, contentWidth)
        if (titleLines.isNotEmpty()) {
            var baseline = y + layoutEngine.titlePaint.textSize
            titleLines.forEach { line ->
                drawCenteredText(canvas, line, centerX, baseline, layoutEngine.titlePaint)
                baseline += layoutEngine.titlePaint.fontSpacing
            }
            y += titleLines.size * layoutEngine.titlePaint.fontSpacing
        }
        y += layoutEngine.baseBodyPaint.fontSpacing
        if (entries.isNotEmpty()) {
            val labelMaxWidth = entries.maxOf { layoutEngine.metadataLabelPaint.measureText(it.first) }
            val colonX = settings.margins.start + labelMaxWidth + 12f
            val valueStart = colonX + layoutEngine.metadataLabelPaint.measureText(":") + 8f
            val valueWidth = pageWidth.toFloat() - settings.margins.end - valueStart
            entries.forEach { (label, value) ->
                val labelX = (settings.margins.start + labelMaxWidth) - layoutEngine.metadataLabelPaint.measureText(label)
                val valueLines = layoutEngine.wrapText(value, layoutEngine.baseBodyPaint, valueWidth)
                var baseline = y + layoutEngine.baseBodyPaint.textSize
                canvas.drawText(label, labelX, baseline, layoutEngine.metadataLabelPaint)
                canvas.drawText(":", colonX, baseline, layoutEngine.metadataLabelPaint)
                if (valueLines.isNotEmpty()) {
                    canvas.drawText(valueLines.first(), valueStart, baseline, layoutEngine.baseBodyPaint)
                    var innerBaseline = baseline + layoutEngine.baseBodyPaint.fontSpacing
                    valueLines.drop(1).forEach { line ->
                        canvas.drawText(line, valueStart, innerBaseline, layoutEngine.baseBodyPaint)
                        innerBaseline += layoutEngine.baseBodyPaint.fontSpacing
                    }
                    y = innerBaseline
                } else {
                    y = baseline + layoutEngine.baseBodyPaint.fontSpacing
                }
            }
        }
        val footer = layoutEngine.buildFooterContent(pageNumber)
        footer?.let { drawFooter(canvas, it, pageWidth, pageHeight) }
    }

    private fun drawFooter(canvas: Canvas, footer: FooterContent, pageWidth: Int, pageHeight: Int) {
        val paint = layoutEngine.footerPaint
        val textPaint = TextPaint(paint)
        val margins = settings.margins
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

    private fun drawCenteredText(canvas: Canvas, text: String, centerX: Float, centerY: Float, paint: Paint) {
        val width = paint.measureText(text)
        canvas.drawText(text, centerX - width / 2f, centerY, paint)
    }
}
