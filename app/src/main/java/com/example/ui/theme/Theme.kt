package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SophisticatedRose,
    secondary = SophisticatedViolet,
    tertiary = BurgundyBg,
    background = ObsidianBg,
    surface = SlateCard,
    onPrimary = BurgundyText,
    onSecondary = ObsidianBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalCard,
    outline = BorderStroke
)

private val LightColorScheme = lightColorScheme(
    primary = SophisticatedRose,
    secondary = SophisticatedViolet,
    tertiary = BurgundyBg,
    background = ObsidianBg, // Maintain gorgeous dark interface for a sleek cinema/player style
    surface = SlateCard,
    onPrimary = BurgundyText,
    onSecondary = ObsidianBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalCard,
    outline = BorderStroke
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for the Sophisticated Dark look
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
