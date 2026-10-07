package com.astral.ebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = SlateLightSurface,
    primaryContainer = IndigoLightContainer,
    onPrimaryContainer = IndigoOnLightContainer,
    secondary = IndigoPrimaryVariant,
    onSecondary = SlateLightSurface,
    background = SlateLightBackground,
    onBackground = SlateLightText,
    surface = SlateLightSurface,
    onSurface = SlateLightText,
    surfaceVariant = SlateLightBackground,
    onSurfaceVariant = SlateLightTextMuted,
    outline = SlateLightOutline,
    outlineVariant = SlateLightOutline
)

private val DarkColors = darkColorScheme(
    primary = IndigoPrimaryVariant,
    onPrimary = SlateLightSurface,
    primaryContainer = IndigoDarkContainer,
    onPrimaryContainer = IndigoOnDarkContainer,
    secondary = IndigoPrimary,
    onSecondary = SlateLightSurface,
    background = SlateDarkBackground,
    onBackground = SlateDarkText,
    surface = SlateDarkSurface,
    onSurface = SlateDarkText,
    surfaceVariant = SlateDarkCard,
    onSurfaceVariant = SlateDarkTextMuted,
    outline = SlateDarkOutline,
    outlineVariant = SlateDarkOutline
)

@Composable
fun AstralEbookTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
