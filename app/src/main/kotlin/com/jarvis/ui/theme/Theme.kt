package com.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = Color(0xFF001A3C),
    secondary = Color(0xFFC78AFF),
    background = Color(0xFF0B0F17),
    surface = Color(0xFF121826),
    onBackground = Color(0xFFE6E8EE),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1A2233),
    onSurfaceVariant = Color(0xFFB3BBCB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5AFB),
    secondary = Color(0xFF7C3AED),
)

@Composable
fun JarvisTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
