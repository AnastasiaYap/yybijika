package io.tr8.yybijika.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Whether the app follows the phone, or is told. */
enum class ThemeMode(val label: String) {
    SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark");

    companion object {
        fun of(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

@Composable
fun YybijikaTheme(
    palette: Palette = Palette.CINNABAR,
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Material You recolours the whole app from the wallpaper, which would
    // throw away the palette the reader just chose, so it is never used.
    val colors = palette.scheme(darkTheme)

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
