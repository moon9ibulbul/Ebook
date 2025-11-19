package com.astral.ebook.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astral.ebook.model.CoverOptions
import com.astral.ebook.model.EbookSettings
import com.astral.ebook.model.FontFamilyOption
import com.astral.ebook.model.FontOptions
import com.astral.ebook.model.FooterOptions
import com.astral.ebook.model.Margins
import com.astral.ebook.model.Metadata
import com.astral.ebook.model.Orientation
import com.astral.ebook.model.ParagraphAlignment
import com.astral.ebook.model.ParagraphOptions
import com.astral.ebook.model.ThemeOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("astral_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val TITLE = stringPreferencesKey("title")
        val SUBTITLE = stringPreferencesKey("subtitle")
        val AUTHOR = stringPreferencesKey("author")
        val TITLE_FAMILY = stringPreferencesKey("title_family")
        val HEADING_FAMILY = stringPreferencesKey("heading_family")
        val BODY_FAMILY = stringPreferencesKey("body_family")
        val TITLE_FONT_URI = stringPreferencesKey("title_font_uri")
        val HEADING_FONT_URI = stringPreferencesKey("heading_font_uri")
        val BODY_FONT_URI = stringPreferencesKey("body_font_uri")
        val BODY_SIZE = floatPreferencesKey("body_size")
        val TITLE_SIZE = floatPreferencesKey("title_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val ALIGNMENT = stringPreferencesKey("alignment")
        val FOOTER = intPreferencesKey("footer_flags")
        val FOOTER_SIZE = floatPreferencesKey("footer_size")
        val ORIENTATION = stringPreferencesKey("orientation")
        val MARGIN_TOP = floatPreferencesKey("margin_top")
        val MARGIN_BOTTOM = floatPreferencesKey("margin_bottom")
        val MARGIN_START = floatPreferencesKey("margin_start")
        val MARGIN_END = floatPreferencesKey("margin_end")
        val DARK_OVERRIDE = stringPreferencesKey("dark_override")
        val PAGE_COLOR = stringPreferencesKey("page_color")
        val TEXT_COLOR = stringPreferencesKey("text_color")
        val COVER_FULL_BLEED = booleanPreferencesKey("cover_full_bleed")
    }

    val settings: Flow<EbookSettings> = context.dataStore.data.map { prefs ->
        val metadata = Metadata(
            title = prefs[Keys.TITLE].orEmpty(),
            subtitle = prefs[Keys.SUBTITLE].orEmpty(),
            author = prefs[Keys.AUTHOR].orEmpty()
        )
        val fonts = FontOptions(
            titleFamily = prefs[Keys.TITLE_FAMILY]?.let { FontFamilyOption.valueOf(it) }
                ?: FontFamilyOption.Serif,
            headingFamily = prefs[Keys.HEADING_FAMILY]?.let { FontFamilyOption.valueOf(it) }
                ?: FontFamilyOption.Serif,
            bodyFamily = prefs[Keys.BODY_FAMILY]?.let { FontFamilyOption.valueOf(it) }
                ?: FontFamilyOption.Serif,
            titleFontUri = prefs[Keys.TITLE_FONT_URI]?.takeIf { it.isNotBlank() },
            headingFontUri = prefs[Keys.HEADING_FONT_URI]?.takeIf { it.isNotBlank() },
            bodyFontUri = prefs[Keys.BODY_FONT_URI]?.takeIf { it.isNotBlank() },
            bodySize = prefs[Keys.BODY_SIZE] ?: 12f,
            titleSize = prefs[Keys.TITLE_SIZE] ?: FontOptions().titleSize,
            lineHeight = prefs[Keys.LINE_HEIGHT] ?: 1.5f
        )
        val footerFlags = prefs[Keys.FOOTER] ?: 0b1111
        val footer = FooterOptions(
            showFooter = footerFlags and 0b001 > 0,
            showTitle = footerFlags and 0b010 > 0,
            showSubtitle = footerFlags and 0b100 > 0,
            showPageNumber = if (footerFlags > 0b111) footerFlags and 0b1000 > 0 else true,
            fontSize = prefs[Keys.FOOTER_SIZE] ?: FooterOptions().fontSize
        )
        EbookSettings(
            metadata = metadata,
            fonts = fonts,
            paragraphOptions = ParagraphOptions(
                alignment = prefs[Keys.ALIGNMENT]?.let { ParagraphAlignment.valueOf(it) }
                    ?: ParagraphAlignment.Justify
            ),
            footerOptions = footer,
            orientation = prefs[Keys.ORIENTATION]?.let { Orientation.valueOf(it) } ?: Orientation.Portrait,
            coverOptions = CoverOptions(
                fullBleed = prefs[Keys.COVER_FULL_BLEED] ?: false
            )
        ).copy(
            margins = Margins(
                top = prefs[Keys.MARGIN_TOP] ?: 60f,
                bottom = prefs[Keys.MARGIN_BOTTOM] ?: 60f,
                start = prefs[Keys.MARGIN_START] ?: 60f,
                end = prefs[Keys.MARGIN_END] ?: 60f
            ),
            themeOptions = ThemeOptions(
                useDark = when (prefs[Keys.DARK_OVERRIDE]) {
                    "true" -> true
                    "false" -> false
                    else -> null
                },
                pageBackground = prefs[Keys.PAGE_COLOR]?.let(::parseColor) ?: ThemeOptions().pageBackground,
                textColor = prefs[Keys.TEXT_COLOR]?.let(::parseColor) ?: ThemeOptions().textColor
            )
        )
    }

    suspend fun save(settings: EbookSettings) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[Keys.TITLE] = settings.metadata.title
            prefs[Keys.SUBTITLE] = settings.metadata.subtitle
            prefs[Keys.AUTHOR] = settings.metadata.author
            prefs[Keys.TITLE_FAMILY] = settings.fonts.titleFamily.name
            prefs[Keys.HEADING_FAMILY] = settings.fonts.headingFamily.name
            prefs[Keys.BODY_FAMILY] = settings.fonts.bodyFamily.name
            if (settings.fonts.titleFontUri.isNullOrBlank()) {
                prefs.remove(Keys.TITLE_FONT_URI)
            } else {
                prefs[Keys.TITLE_FONT_URI] = settings.fonts.titleFontUri!!
            }
            if (settings.fonts.headingFontUri.isNullOrBlank()) {
                prefs.remove(Keys.HEADING_FONT_URI)
            } else {
                prefs[Keys.HEADING_FONT_URI] = settings.fonts.headingFontUri!!
            }
            if (settings.fonts.bodyFontUri.isNullOrBlank()) {
                prefs.remove(Keys.BODY_FONT_URI)
            } else {
                prefs[Keys.BODY_FONT_URI] = settings.fonts.bodyFontUri!!
            }
            prefs[Keys.BODY_SIZE] = settings.fonts.bodySize
            prefs[Keys.TITLE_SIZE] = settings.fonts.titleSize
            prefs[Keys.LINE_HEIGHT] = settings.fonts.lineHeight
            prefs[Keys.ALIGNMENT] = settings.paragraphOptions.alignment.name
            prefs[Keys.FOOTER] = (if (settings.footerOptions.showFooter) 0b001 else 0) or
                (if (settings.footerOptions.showTitle) 0b010 else 0) or
                (if (settings.footerOptions.showSubtitle) 0b100 else 0) or
                (if (settings.footerOptions.showPageNumber) 0b1000 else 0)
            prefs[Keys.FOOTER_SIZE] = settings.footerOptions.fontSize
            prefs[Keys.ORIENTATION] = settings.orientation.name
            prefs[Keys.MARGIN_TOP] = settings.margins.top
            prefs[Keys.MARGIN_BOTTOM] = settings.margins.bottom
            prefs[Keys.MARGIN_START] = settings.margins.start
            prefs[Keys.MARGIN_END] = settings.margins.end
            prefs[Keys.DARK_OVERRIDE] = settings.themeOptions.useDark?.toString() ?: ""
            prefs[Keys.PAGE_COLOR] = settings.themeOptions.pageBackground.toHexString()
            prefs[Keys.TEXT_COLOR] = settings.themeOptions.textColor.toHexString()
            prefs[Keys.COVER_FULL_BLEED] = settings.coverOptions.fullBleed
        }
    }
}

private fun parseColor(input: String): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(input))

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}
