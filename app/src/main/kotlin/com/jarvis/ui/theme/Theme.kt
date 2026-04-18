package com.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jarvis.llm.ThemeMode

// Dark — the "space-blue" native look.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = Color(0xFF001A3C),
    secondary = Color(0xFFC78AFF),
    background = Color(0xFF05080F),
    surface = Color(0xFF121826),
    onBackground = Color(0xFFE6E8EE),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1A2233),
    onSurfaceVariant = Color(0xFFB3BBCB),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF2B0A0A),
    errorContainer = Color(0xFF4B1A1A),
    onErrorContainer = Color(0xFFFFD8D8),
)

// Light — off-white with deep blue accents; still feels premium.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5AFB),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7C3AED),
    background = Color(0xFFF7F9FD),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF101424),
    onSurface = Color(0xFF101424),
    surfaceVariant = Color(0xFFECEFF7),
    onSurfaceVariant = Color(0xFF4B5268),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * A richer, multi-stop diagonal gradient for screen backgrounds. Uses the
 * current scheme's background + surfaceVariant so it auto-adapts to theme.
 */
data class JarvisGradient(
    val background: Brush,
    val accentGlow: Brush,
)

val LocalJarvisGradient = staticCompositionLocalOf {
    JarvisGradient(
        background = Brush.verticalGradient(listOf(Color.Black, Color.Black)),
        accentGlow = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent)),
    )
}

@Composable
fun JarvisTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkColors else LightColors

    val gradient = if (dark) {
        JarvisGradient(
            background = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0C1428),
                    Color(0xFF070B18),
                    Color(0xFF03050B),
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            ),
            accentGlow = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6EA8FE).copy(alpha = 0.14f),
                    Color.Transparent,
                ),
                radius = 1600f,
            ),
        )
    } else {
        JarvisGradient(
            background = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF5F8FD),
                    Color(0xFFEEF2FA),
                    Color(0xFFE6EBF4),
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            ),
            accentGlow = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF2E5AFB).copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                radius = 1200f,
            ),
        )
    }

    CompositionLocalProvider(LocalJarvisGradient provides gradient) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
