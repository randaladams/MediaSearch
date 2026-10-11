package com.example.mediasearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** App colors: indigo/violet "cinema" base, popcorn-amber accent, teal for TV. */
object Brand {
    val HeaderGradient = Brush.linearGradient(
        listOf(Color(0xFF3730A3), Color(0xFF6D28D9), Color(0xFFBE185D)),
    )
    val Popcorn = Color(0xFFFCD34D)
    val OnHeader = Color.White
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF134E4A),
    background = Color(0xFFF7F6FD),
    onBackground = Color(0xFF1B1A2E),
    surface = Color(0xFFF7F6FD),
    onSurface = Color(0xFF1B1A2E),
    surfaceVariant = Color(0xFFE9E8F5),
    onSurfaceVariant = Color(0xFF55536E),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1F0FA),
    outlineVariant = Color(0xFFD9D7EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFFCD34D),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF115E59),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF100F1C),
    onBackground = Color(0xFFE7E6F5),
    surface = Color(0xFF100F1C),
    onSurface = Color(0xFFE7E6F5),
    surfaceVariant = Color(0xFF2A2842),
    onSurfaceVariant = Color(0xFFB9B7D0),
    surfaceContainerLow = Color(0xFF1A1930),
    surfaceContainer = Color(0xFF1F1D36),
    outlineVariant = Color(0xFF3A3856),
)

@Composable
fun MediaSearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
