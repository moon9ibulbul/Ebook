package com.astral.ebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    surface = CreamPaper,
    background = CreamPaper,
    onSurface = InkDark
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimary,
    surface = PaperDark,
    background = PaperDark,
    onSurface = InkLight
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
