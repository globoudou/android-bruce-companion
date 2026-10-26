package fr.globoudou.brucecompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val Mono = FontFamily.Monospace

private val Green = Color(0xFF34D399)
private val GreenDark = Color(0xFF0B5D3B)

private val DarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF05140D),
    primaryContainer = Color(0xFF0E3A27),
    onPrimaryContainer = Color(0xFF9DF6CE),
    secondaryContainer = Color(0xFF263040),
    onSecondaryContainer = Color(0xFFE6EDF3),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0F1419),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1A2129),
    onSurfaceVariant = Color(0xFF9AA7B4),
    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = Color(0xFF141B22),
    surfaceContainer = Color(0xFF182028),
    surfaceContainerHigh = Color(0xFF1D252E),
    surfaceContainerHighest = Color(0xFF232C36),
    outline = Color(0xFF3A4653),
    outlineVariant = Color(0xFF2A343F),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF1A0505),
)

private val LightScheme = lightColorScheme(
    primary = GreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF5E3),
    onPrimaryContainer = Color(0xFF053021),
    secondaryContainer = Color(0xFFDDE3E9),
    onSecondaryContainer = Color(0xFF111827),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF1F4),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F4F7),
    surfaceContainerHighest = Color(0xFFE9EDF1),
    outline = Color(0xFFB8C1CB),
    outlineVariant = Color(0xFFD5DBE1),
    error = Color(0xFFB91C1C),
    onError = Color.White,
)

@Composable
fun BruceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
