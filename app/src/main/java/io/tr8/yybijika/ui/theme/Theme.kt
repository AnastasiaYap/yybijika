package io.tr8.yybijika.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Ink-on-paper palette, carried over from yyhsk: a warm off-white ground with a
// deep cinnabar accent, so long reading sessions stay comfortable.
private val Cinnabar = Color(0xFF8C2F26)
private val CinnabarLight = Color(0xFFC8503C)
private val Ink = Color(0xFF241F1C)
private val Paper = Color(0xFFFAF6F0)
private val PaperDim = Color(0xFFF0E9DF)
private val InkDark = Color(0xFFF0EAE0)
private val PaperDark = Color(0xFF16130F)
private val PaperDarkDim = Color(0xFF221D18)

private val LightColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DDD6),
    onPrimaryContainer = Color(0xFF3A100B),
    secondary = Color(0xFF4A6572),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Color(0xFF5A5148),
)

private val DarkColors = darkColorScheme(
    primary = CinnabarLight,
    onPrimary = Color(0xFF3A100B),
    primaryContainer = Color(0xFF5E2018),
    onPrimaryContainer = Color(0xFFF5DDD6),
    secondary = Color(0xFFA0B4D0),
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = PaperDarkDim,
    onSurfaceVariant = Color(0xFFB8AEA2),
)

@Composable
fun YybijikaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You recolours the whole app from the wallpaper. That fights the
    // ink-on-paper look this app is built around, so it stays off by default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
