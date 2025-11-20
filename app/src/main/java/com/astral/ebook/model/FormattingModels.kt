package com.astral.ebook.model

import androidx.compose.ui.graphics.Color
import java.time.Year

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
    val showChapter: Boolean = true,
    val showPageNumber: Boolean = true,
    val fontSize: Float = 9f
)

data class CoverOptions(
    val fullBleed: Boolean = false
)

data class FontOptions(
    val titleFamily: FontFamilyOption = FontFamilyOption.Serif,
    val headingFamily: FontFamilyOption = FontFamilyOption.Serif,
    val bodyFamily: FontFamilyOption = FontFamilyOption.Serif,
    val titleFontUri: String? = null,
    val headingFontUri: String? = null,
    val bodyFontUri: String? = null,
    val titleSize: Float = 32f,
    val chapterSize: Float = 18f,
    val headingSize: Float = 24f,
    val bodySize: Float = 12f,
    val lineHeight: Float = 1.5f
)

enum class FontFamilyOption { Serif, SansSerif, Custom }

enum class FontTarget { Title, Heading, Body }

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
    val chapter: String = "",
    val author: String = "",
    val translator: String = "",
    val publisher: String = "",
    val publicationYear: String = Year.now().value.toString(),
    val notes: String = "",
    val language: String = "en"
)

data class ThemeOptions(
    val useDark: Boolean? = null,
    val pageBackground: Color = Color(0xFFF8F3EB),
    val textColor: Color = Color(0xFF231F20),
    val darkTextColor: Color = Color(0xFFE5E5E5)
)

data class EbookSettings(
    val metadata: Metadata = Metadata(),
    val pagePreset: PagePreset = Presets.mobile,
    val margins: Margins = Margins(60f, 60f, 60f, 60f),
    val orientation: Orientation = Orientation.Portrait,
    val fonts: FontOptions = FontOptions(),
    val paragraphOptions: ParagraphOptions = ParagraphOptions(),
    val footerOptions: FooterOptions = FooterOptions(),
    val themeOptions: ThemeOptions = ThemeOptions(),
    val coverOptions: CoverOptions = CoverOptions()
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
