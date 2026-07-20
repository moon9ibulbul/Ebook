package com.astral.ebook.model

import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

fun EbookSettings.toBundle(): Bundle {
    val b = Bundle()

    // Metadata
    val metaB = Bundle().apply {
        putString("title", metadata.title)
        putString("subtitle", metadata.subtitle)
        putString("chapter", metadata.chapter)
        putString("author", metadata.author)
        putString("translator", metadata.translator)
        putString("publisher", metadata.publisher)
        putString("publicationYear", metadata.publicationYear)
        putString("notes", metadata.notes)
        putString("language", metadata.language)
    }
    b.putBundle("metadata", metaB)

    // PagePreset
    val presetB = Bundle().apply {
        putString("name", pagePreset.name)
        putInt("widthPx", pagePreset.widthPx)
        putInt("heightPx", pagePreset.heightPx)
        putFloat("marginTop", pagePreset.marginTop)
        putFloat("marginBottom", pagePreset.marginBottom)
        putFloat("marginStart", pagePreset.marginStart)
        putFloat("marginEnd", pagePreset.marginEnd)
    }
    b.putBundle("pagePreset", presetB)

    // Margins
    val marginsB = Bundle().apply {
        putFloat("top", margins.top)
        putFloat("bottom", margins.bottom)
        putFloat("start", margins.start)
        putFloat("end", margins.end)
    }
    b.putBundle("margins", marginsB)

    // Orientation
    b.putString("orientation", orientation.name)

    // Fonts
    val fontsB = Bundle().apply {
        putString("titleFamily", fonts.titleFamily.name)
        putString("headingFamily", fonts.headingFamily.name)
        putString("bodyFamily", fonts.bodyFamily.name)
        putString("titleFontUri", fonts.titleFontUri)
        putString("headingFontUri", fonts.headingFontUri)
        putString("bodyFontUri", fonts.bodyFontUri)
        putFloat("titleSize", fonts.titleSize)
        putFloat("chapterSize", fonts.chapterSize)
        putFloat("headingSize", fonts.headingSize)
        putFloat("bodySize", fonts.bodySize)
        putFloat("lineHeight", fonts.lineHeight)
    }
    b.putBundle("fonts", fontsB)

    // ParagraphOptions
    val paraB = Bundle().apply {
        putString("alignment", paragraphOptions.alignment.name)
        putFloat("firstLineIndentEm", paragraphOptions.firstLineIndentEm)
        putBoolean("skipIndentAfterHeading", paragraphOptions.skipIndentAfterHeading)
        putFloat("extraParagraphSpacing", paragraphOptions.extraParagraphSpacing)
    }
    b.putBundle("paragraphOptions", paraB)

    // FooterOptions
    val footerB = Bundle().apply {
        putBoolean("showFooter", footerOptions.showFooter)
        putBoolean("showTitle", footerOptions.showTitle)
        putBoolean("showChapter", footerOptions.showChapter)
        putBoolean("showPageNumber", footerOptions.showPageNumber)
        putFloat("fontSize", footerOptions.fontSize)
    }
    b.putBundle("footerOptions", footerB)

    // ThemeOptions
    val themeB = Bundle().apply {
        putInt("useDark", when (themeOptions.useDark) {
            true -> 1
            false -> 0
            null -> -1
        })
        putInt("pageBackground", themeOptions.pageBackground.toArgb())
        putInt("textColor", themeOptions.textColor.toArgb())
        putInt("darkTextColor", themeOptions.darkTextColor.toArgb())
    }
    b.putBundle("themeOptions", themeB)

    // CoverOptions
    val coverB = Bundle().apply {
        putBoolean("fullBleed", coverOptions.fullBleed)
    }
    b.putBundle("coverOptions", coverB)

    return b
}

fun Bundle.toEbookSettings(): EbookSettings {
    val metaB = getBundle("metadata")!!
    val metadata = Metadata(
        title = metaB.getString("title", ""),
        subtitle = metaB.getString("subtitle", ""),
        chapter = metaB.getString("chapter", ""),
        author = metaB.getString("author", ""),
        translator = metaB.getString("translator", ""),
        publisher = metaB.getString("publisher", ""),
        publicationYear = metaB.getString("publicationYear", ""),
        notes = metaB.getString("notes", ""),
        language = metaB.getString("language", "en")
    )

    val presetB = getBundle("pagePreset")!!
    val pagePreset = PagePreset(
        name = presetB.getString("name", "Mobile"),
        widthPx = presetB.getInt("widthPx", 1080),
        heightPx = presetB.getInt("heightPx", 1600),
        marginTop = presetB.getFloat("marginTop", 60f),
        marginBottom = presetB.getFloat("marginBottom", 60f),
        marginStart = presetB.getFloat("marginStart", 60f),
        marginEnd = presetB.getFloat("marginEnd", 60f)
    )

    val marginsB = getBundle("margins")!!
    val margins = Margins(
        top = marginsB.getFloat("top", 60f),
        bottom = marginsB.getFloat("bottom", 60f),
        start = marginsB.getFloat("start", 60f),
        end = marginsB.getFloat("end", 60f)
    )

    val orientation = Orientation.valueOf(getString("orientation", "Portrait"))

    val fontsB = getBundle("fonts")!!
    val fonts = FontOptions(
        titleFamily = FontFamilyOption.valueOf(fontsB.getString("titleFamily", "Serif")),
        headingFamily = FontFamilyOption.valueOf(fontsB.getString("headingFamily", "Serif")),
        bodyFamily = FontFamilyOption.valueOf(fontsB.getString("bodyFamily", "Serif")),
        titleFontUri = fontsB.getString("titleFontUri"),
        headingFontUri = fontsB.getString("headingFontUri"),
        bodyFontUri = fontsB.getString("bodyFontUri"),
        titleSize = fontsB.getFloat("titleSize", 32f),
        chapterSize = fontsB.getFloat("chapterSize", 18f),
        headingSize = fontsB.getFloat("headingSize", 24f),
        bodySize = fontsB.getFloat("bodySize", 12f),
        lineHeight = fontsB.getFloat("lineHeight", 1.5f)
    )

    val paraB = getBundle("paragraphOptions")!!
    val paragraphOptions = ParagraphOptions(
        alignment = ParagraphAlignment.valueOf(paraB.getString("alignment", "Justify")),
        firstLineIndentEm = paraB.getFloat("firstLineIndentEm", 1.4f),
        skipIndentAfterHeading = paraB.getBoolean("skipIndentAfterHeading", true),
        extraParagraphSpacing = paraB.getFloat("extraParagraphSpacing", 0f)
    )

    val footerB = getBundle("footerOptions")!!
    val footerOptions = FooterOptions(
        showFooter = footerB.getBoolean("showFooter", true),
        showTitle = footerB.getBoolean("showTitle", true),
        showChapter = footerB.getBoolean("showChapter", true),
        showPageNumber = footerB.getBoolean("showPageNumber", true),
        fontSize = footerB.getFloat("fontSize", 9f)
    )

    val themeB = getBundle("themeOptions")!!
    val useDarkVal = themeB.getInt("useDark", -1)
    val themeOptions = ThemeOptions(
        useDark = when (useDarkVal) {
            1 -> true
            0 -> false
            else -> null
        },
        pageBackground = Color(themeB.getInt("pageBackground")),
        textColor = Color(themeB.getInt("textColor")),
        darkTextColor = Color(themeB.getInt("darkTextColor"))
    )

    val coverB = getBundle("coverOptions")!!
    val coverOptions = CoverOptions(
        fullBleed = coverB.getBoolean("fullBleed", false)
    )

    return EbookSettings(
        metadata = metadata,
        pagePreset = pagePreset,
        margins = margins,
        orientation = orientation,
        fonts = fonts,
        paragraphOptions = paragraphOptions,
        footerOptions = footerOptions,
        themeOptions = themeOptions,
        coverOptions = coverOptions
    )
}
