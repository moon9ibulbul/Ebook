package com.astral.ebook.model

import androidx.compose.ui.graphics.Color
import java.time.Year

enum class OutputFormat { EPUB, PDF }

data class PagePreset(
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val marginTop: Float,
    val marginBottom: Float,
    val marginStart: Float,
    val marginEnd: Float
)

data class FooterOptions(
    val showFooter: Boolean = true,
    val showTitle: Boolean = true,
    val showSubtitle: Boolean = true,
    val showPageNumber: Boolean = true
)

data class FontOptions(
    val titleFamily: FontFamilyOption = FontFamilyOption.Serif,
    val headingFamily: FontFamilyOption = FontFamilyOption.Serif,
    val bodyFamily: FontFamilyOption = FontFamilyOption.Serif,
    val titleSize: Float = 42f,
    val subtitleSize: Float = 18f,
    val headingSize: Float = 24f,
    val bodySize: Float = 12f,
    val lineHeight: Float = 1.5f
)

enum class FontFamilyOption { Serif, SansSerif }

data class ParagraphOptions(
    val alignment: ParagraphAlignment = ParagraphAlignment.Justify,
    val firstLineIndentEm: Float = 1.4f,
    val skipIndentAfterHeading: Boolean = true,
    val extraParagraphSpacing: Float = 0f
)

enum class ParagraphAlignment { Left, Center, Right, Justify }

data class Metadata(
    val title: String = "",
    val subtitle: String = "",
    val author: String = "",
    val translator: String = "",
    val publisher: String = "",
    val publicationYear: String = Year.now().value.toString(),
    val notes: String = "",
    val language: String = "en"
)

data class ThemeOptions(
    val useDark: Boolean? = null,
    val pageBackground: Color = Color(0xFFFAFAF7),
    val textColor: Color = Color(0xFF111111),
    val darkTextColor: Color = Color(0xFFE5E5E5)
)

data class EbookSettings(
    val metadata: Metadata = Metadata(),
    val outputFormat: OutputFormat = OutputFormat.EPUB,
    val pagePreset: PagePreset = Presets.mobile,
    val margins: Margins = Margins(60f, 60f, 60f, 60f),
    val orientation: Orientation = Orientation.Portrait,
    val fonts: FontOptions = FontOptions(),
    val paragraphOptions: ParagraphOptions = ParagraphOptions(),
    val footerOptions: FooterOptions = FooterOptions(),
    val themeOptions: ThemeOptions = ThemeOptions()
)

enum class Orientation { Portrait, Landscape }

data class Margins(
    val top: Float,
    val bottom: Float,
    val start: Float,
    val end: Float
)

object Presets {
    val mobile = PagePreset(
        name = "Mobile",
        widthPx = 1080,
        heightPx = 1600,
        marginTop = 60f,
        marginBottom = 60f,
        marginStart = 60f,
        marginEnd = 60f
    )

    val a5 = PagePreset(
        name = "A5",
        widthPx = 1748,
        heightPx = 2480,
        marginTop = 18f,
        marginBottom = 18f,
        marginStart = 18f,
        marginEnd = 12f
    )

    val compact = PagePreset(
        name = "Compact novel",
        widthPx = 1417,
        heightPx = 2126,
        marginTop = 18f,
        marginBottom = 18f,
        marginStart = 18f,
        marginEnd = 12f
    )

    val presets = listOf(mobile, a5, compact)
}
