package com.astral.ebook.repository

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.FontFamilyOption
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.ParagraphAlignment
import kotlin.math.max

data class TextRunSegment(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikeThrough: Boolean
)

sealed interface LineContent {
    data class Text(val segments: List<TextRunSegment>, val indent: Float, val alignment: ParagraphAlignment) : LineContent
    data class Spacer(val spacing: Float) : LineContent
}

data class PageContent(
    val pageNumber: Int,
    val lines: List<LineContent>,
    val headingOffset: Float = 0f,
    val isFirstBodyPage: Boolean = false,
    val isTitlePage: Boolean = false,
    val isCoverPage: Boolean = false,
    val isMetadataPage: Boolean = false
)

class EbookLayoutEngine(private val context: Context, private val settings: EbookSettings) {
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
    val headingPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.headingSize * density
        typeface = headingTypeface
    }
    val subtitlePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.headingSize * density
        typeface = headingTypeface
        textSkewX = -0.1f
    }
    val chapterPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = settings.fonts.chapterSize * density
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

    private val styleCache = mutableMapOf<TextStyleKey, Paint>()

    private data class TextStyleKey(
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strikeThrough: Boolean
    )

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

    fun layoutPages(body: DocumentContent): List<PageContent> {
        val pages = mutableListOf<PageContent>()
        val (pageWidth, pageHeight) = if (settings.orientation == Orientation.Portrait) {
            settings.pagePreset.widthPx to settings.pagePreset.heightPx
        } else {
            settings.pagePreset.heightPx to settings.pagePreset.widthPx
        }
        val margins = settings.margins
        val contentWidth = pageWidth.toFloat() - (margins.start + margins.end)

        // Cover page
        pages.add(PageContent(pageNumber = 1, lines = emptyList(), isCoverPage = true))

        // Title page
        pages.add(PageContent(pageNumber = 2, lines = emptyList(), isTitlePage = true))

        val allLines = buildLineContent(body, contentWidth)
        var lineIndex = 0
        var pageNum = 3
        var isFirstBodyPage = true

        if (allLines.isEmpty()) {
            pages.add(PageContent(pageNum++, emptyList(), isFirstBodyPage = true))
        } else {
            while (lineIndex < allLines.size) {
                val footer = buildFooterContent(pageNum)
                val footerSpace = if (footer == null) 0f else footerPaint.fontSpacing + 16f
                var headingOffset = 0f
                if (isFirstBodyPage) {
                    headingOffset = calculateBodyHeadingHeight(contentWidth)
                }

                val pageLines = mutableListOf<LineContent>()
                var y = margins.top + headingOffset + lineHeight

                while (lineIndex < allLines.size && y < pageHeight.toFloat() - margins.bottom - footerSpace) {
                    val line = allLines[lineIndex]
                    pageLines.add(line)
                    when (line) {
                        is LineContent.Text -> y += lineHeight
                        is LineContent.Spacer -> y += line.spacing
                    }
                    lineIndex++
                }

                pages.add(PageContent(
                    pageNumber = pageNum,
                    lines = pageLines,
                    headingOffset = headingOffset,
                    isFirstBodyPage = isFirstBodyPage
                ))
                pageNum++
                isFirstBodyPage = false
            }
        }

        // Metadata page
        if (hasMetadata()) {
            pages.add(PageContent(pageNumber = pageNum, lines = emptyList(), isMetadataPage = true))
        }

        return pages
    }

    private fun hasMetadata(): Boolean {
        val meta = settings.metadata
        return listOf(meta.author, meta.translator, meta.publisher, meta.publicationYear, meta.language, meta.notes)
            .any { it.isNotBlank() }
    }

    fun calculateBodyHeadingHeight(contentWidth: Float): Float {
        val subtitle = settings.metadata.subtitle
        val chapter = settings.metadata.chapter
        val headingText = when {
            chapter.isBlank() && subtitle.isBlank() -> null
            chapter.isBlank() -> subtitle
            subtitle.isBlank() -> chapter
            else -> "$chapter : $subtitle"
        } ?: return 0f
        val headingLines = wrapText(headingText, headingPaint, contentWidth)
        if (headingLines.isEmpty()) return 0f
        return headingPaint.textSize + (headingLines.size * headingPaint.fontSpacing) + headingGap
    }

    private fun buildLineContent(
        body: DocumentContent,
        contentWidth: Float
    ): List<LineContent> {
        val lines = mutableListOf<LineContent>()
        body.paragraphs.forEachIndexed { index, paragraph ->
            val applyIndent = !(index == 0 && settings.paragraphOptions.skipIndentAfterHeading)
            val alignment = paragraph.alignment ?: settings.paragraphOptions.alignment
            val paragraphLines = wrapParagraph(paragraph, contentWidth, applyIndent, alignment)
            lines += paragraphLines
            if (paragraphSpacingPx > 0f) {
                lines += LineContent.Spacer(paragraphSpacingPx)
            }
        }
        if (lines.isNotEmpty() && lines.last() is LineContent.Spacer) {
            lines.removeAt(lines.lastIndex)
        }
        return lines
    }

    private fun wrapParagraph(
        paragraph: FormattedParagraph,
        contentWidth: Float,
        indentFirstLine: Boolean,
        alignment: ParagraphAlignment
    ): List<LineContent.Text> {
        if (paragraph.runs.all { it.text.isBlank() }) {
            return listOf(
                LineContent.Text(
                    listOf(TextRunSegment("", false, false, false, false)),
                    0f,
                    alignment
                )
            )
        }
        val lines = mutableListOf<LineContent.Text>()
        val shouldIndent = indentFirstLine && alignment.allowsIndent()
        var isFirstLine = true
        var currentIndent = if (shouldIndent) indentPx else 0f
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
                var paint = bodyPaint(run.bold, run.italic, run.underline, run.strikeThrough)
                var width = paint.measureText(token)
                if (currentWidth + width > availableWidth && currentSegments.isNotEmpty()) {
                    flush()
                    token = word
                    paint = bodyPaint(run.bold, run.italic, run.underline, run.strikeThrough)
                    width = paint.measureText(token)
                }
                currentSegments += TextRunSegment(token, run.bold, run.italic, run.underline, run.strikeThrough)
                currentWidth += width
            }
        }
        flush()
        return lines
    }

    fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
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

    fun buildFooterContent(pageNumber: Int): FooterContent? {
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
}

data class FooterContent(val left: String, val right: String)

private fun ParagraphAlignment.allowsIndent(): Boolean = this == ParagraphAlignment.Left || this == ParagraphAlignment.Justify
