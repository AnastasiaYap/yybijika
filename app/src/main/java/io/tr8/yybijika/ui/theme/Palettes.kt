package io.tr8.yybijika.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palettes the app can wear.
 *
 * All of them share one constraint that is not decoration: the ground is a warm
 * off-white rather than pure white, and the text is a soft near-black rather
 * than pure black. Hanzi carry a lot of strokes in a small box, and maximum
 * contrast makes them shimmer over a long session — which is exactly the
 * session this app is for.
 *
 * So what changes between palettes is the accent and the temperature of the
 * paper, not the contrast. A theme you cannot read for forty minutes is not a
 * theme, whatever it looks like in a screenshot.
 */
enum class Palette(val label: String, val swatch: Color) {

    /** The original: cinnabar seal ink on warm paper. */
    CINNABAR("Cinnabar", Color(0xFF8C2F26)) {
        override val light = lightColorScheme(
            primary = Color(0xFF8C2F26),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF5DDD6),
            onPrimaryContainer = Color(0xFF3A100B),
            secondary = Color(0xFF4A6572),
            secondaryContainer = Color(0xFFEBDDD6),
            onSecondaryContainer = Color(0xFF3A1B10),
            tertiary = Color(0xFF7A5A46),
            tertiaryContainer = Color(0xFFF0E2D8),
            background = Color(0xFFFAF6F0),
            onBackground = Color(0xFF241F1C),
            surface = Color(0xFFFAF6F0),
            onSurface = Color(0xFF241F1C),
            surfaceVariant = Color(0xFFF0E9DF),
            surfaceContainerLowest = Color(0xFFFFFDFA),
            surfaceContainerLow = Color(0xFFF7F2EA),
            surfaceContainer = Color(0xFFF2ECE2),
            surfaceContainerHigh = Color(0xFFECE4D9),
            surfaceContainerHighest = Color(0xFFE6DDD0),
            onSurfaceVariant = Color(0xFF5A5148),
        )
        override val dark = darkColorScheme(
            primary = Color(0xFFC8503C),
            onPrimary = Color(0xFF3A100B),
            primaryContainer = Color(0xFF5E2018),
            onPrimaryContainer = Color(0xFFF5DDD6),
            secondary = Color(0xFFA0B4D0),
            secondaryContainer = Color(0xFF4A3227),
            onSecondaryContainer = Color(0xFFEBDDD6),
            tertiary = Color(0xFFD9BCA6),
            tertiaryContainer = Color(0xFF4A3227),
            background = Color(0xFF16130F),
            onBackground = Color(0xFFF0EAE0),
            surface = Color(0xFF16130F),
            onSurface = Color(0xFFF0EAE0),
            surfaceVariant = Color(0xFF221D18),
            surfaceContainerLowest = Color(0xFF0F0D0A),
            surfaceContainerLow = Color(0xFF1B1814),
            surfaceContainer = Color(0xFF1F1B16),
            surfaceContainerHigh = Color(0xFF29241E),
            surfaceContainerHighest = Color(0xFF342E26),
            onSurfaceVariant = Color(0xFFB8AEA2),
        )
    },

    /**
     * Peach blossom — 桃花. A true pink, kept deep enough to read.
     *
     * The trap with pink is that the pleasant shades of it are pale, and pale
     * pink on off-white leaves hanzi floating. The accent here is a rose that
     * holds its own against the paper; the softness lives in the containers and
     * the ground, which is where it can be soft without costing legibility.
     */
    PEACH("Peach blossom", Color(0xFFB03060)) {
        override val light = lightColorScheme(
            primary = Color(0xFFB03060),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFBDCE6),
            onPrimaryContainer = Color(0xFF4A0D25),
            secondary = Color(0xFF7A5C6B),
            secondaryContainer = Color(0xFFF7DDE6),
            onSecondaryContainer = Color(0xFF44121F),
            tertiary = Color(0xFF8A5A6B),
            tertiaryContainer = Color(0xFFFAE4EC),
            background = Color(0xFFFEF7F8),
            onBackground = Color(0xFF2A1E22),
            surface = Color(0xFFFEF7F8),
            onSurface = Color(0xFF2A1E22),
            surfaceVariant = Color(0xFFF7E8ED),
            surfaceContainerLowest = Color(0xFFFFFCFD),
            surfaceContainerLow = Color(0xFFFBF3F5),
            surfaceContainer = Color(0xFFF8EDF1),
            surfaceContainerHigh = Color(0xFFF3E4EA),
            surfaceContainerHighest = Color(0xFFEEDBE3),
            onSurfaceVariant = Color(0xFF6B545C),
        )
        override val dark = darkColorScheme(
            primary = Color(0xFFF2A0BD),
            onPrimary = Color(0xFF4A0D25),
            primaryContainer = Color(0xFF7A2447),
            onPrimaryContainer = Color(0xFFFBDCE6),
            secondary = Color(0xFFD5B8C4),
            secondaryContainer = Color(0xFF5C2438),
            onSecondaryContainer = Color(0xFFF7DDE6),
            tertiary = Color(0xFFE5B8C8),
            tertiaryContainer = Color(0xFF5C2438),
            background = Color(0xFF1A1216),
            onBackground = Color(0xFFF3E6EA),
            surface = Color(0xFF1A1216),
            onSurface = Color(0xFFF3E6EA),
            surfaceVariant = Color(0xFF291C22),
            surfaceContainerLowest = Color(0xFF120C0F),
            surfaceContainerLow = Color(0xFF1F171A),
            surfaceContainer = Color(0xFF241A1E),
            surfaceContainerHigh = Color(0xFF2E2228),
            surfaceContainerHighest = Color(0xFF392A31),
            onSurfaceVariant = Color(0xFFC4AEB6),
        )
    },

    /** Celadon — 青瓷. Green glaze on pale stone, the quietest of the set. */
    CELADON("Celadon", Color(0xFF3E6B57)) {
        override val light = lightColorScheme(
            primary = Color(0xFF3E6B57),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD6E8DE),
            onPrimaryContainer = Color(0xFF11291E),
            secondary = Color(0xFF5E6B63),
            secondaryContainer = Color(0xFFD8E6DD),
            onSecondaryContainer = Color(0xFF17291F),
            tertiary = Color(0xFF52786A),
            tertiaryContainer = Color(0xFFE2EFE8),
            background = Color(0xFFF7FAF7),
            onBackground = Color(0xFF1C2220),
            surface = Color(0xFFF7FAF7),
            onSurface = Color(0xFF1C2220),
            surfaceVariant = Color(0xFFE6EDE8),
            surfaceContainerLowest = Color(0xFFFCFEFC),
            surfaceContainerLow = Color(0xFFF2F7F3),
            surfaceContainer = Color(0xFFEDF3EE),
            surfaceContainerHigh = Color(0xFFE5ECE7),
            surfaceContainerHighest = Color(0xFFDCE5DF),
            onSurfaceVariant = Color(0xFF4E5A53),
        )
        override val dark = darkColorScheme(
            primary = Color(0xFF8CC7AA),
            onPrimary = Color(0xFF11291E),
            primaryContainer = Color(0xFF2A4C3B),
            onPrimaryContainer = Color(0xFFD6E8DE),
            secondary = Color(0xFFB0C4B8),
            secondaryContainer = Color(0xFF2C4438),
            onSecondaryContainer = Color(0xFFD8E6DD),
            tertiary = Color(0xFFAFD0C0),
            tertiaryContainer = Color(0xFF2C4438),
            background = Color(0xFF111614),
            onBackground = Color(0xFFE4EDE8),
            surface = Color(0xFF111614),
            onSurface = Color(0xFFE4EDE8),
            surfaceVariant = Color(0xFF1C2420),
            surfaceContainerLowest = Color(0xFF0C100E),
            surfaceContainerLow = Color(0xFF171C19),
            surfaceContainer = Color(0xFF1B211D),
            surfaceContainerHigh = Color(0xFF242B27),
            surfaceContainerHighest = Color(0xFF2E3631),
            onSurfaceVariant = Color(0xFFAABAB2),
        )
    },

    /** Indigo — 靛青. Cloth-dyer's blue, the coolest and highest contrast. */
    INDIGO("Indigo", Color(0xFF2E4A7D)) {
        override val light = lightColorScheme(
            primary = Color(0xFF2E4A7D),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E2F5),
            onPrimaryContainer = Color(0xFF0D1B33),
            secondary = Color(0xFF5A6478),
            secondaryContainer = Color(0xFFDCE2EF),
            onSecondaryContainer = Color(0xFF141C2B),
            tertiary = Color(0xFF4E5E80),
            tertiaryContainer = Color(0xFFE4EAF6),
            background = Color(0xFFF7F8FC),
            onBackground = Color(0xFF1C1F26),
            surface = Color(0xFFF7F8FC),
            onSurface = Color(0xFF1C1F26),
            surfaceVariant = Color(0xFFE7EAF2),
            surfaceContainerLowest = Color(0xFFFDFDFF),
            surfaceContainerLow = Color(0xFFF3F5FA),
            surfaceContainer = Color(0xFFEEF1F8),
            surfaceContainerHigh = Color(0xFFE6EAF3),
            surfaceContainerHighest = Color(0xFFDDE3EE),
            onSurfaceVariant = Color(0xFF4E5666),
        )
        override val dark = darkColorScheme(
            primary = Color(0xFF9DB6E8),
            onPrimary = Color(0xFF0D1B33),
            primaryContainer = Color(0xFF243B63),
            onPrimaryContainer = Color(0xFFD9E2F5),
            secondary = Color(0xFFB4BCCC),
            secondaryContainer = Color(0xFF27334D),
            onSecondaryContainer = Color(0xFFDCE2EF),
            tertiary = Color(0xFFB6C3E0),
            tertiaryContainer = Color(0xFF27334D),
            background = Color(0xFF101318),
            onBackground = Color(0xFFE6E9F0),
            surface = Color(0xFF101318),
            onSurface = Color(0xFFE6E9F0),
            surfaceVariant = Color(0xFF1B2029),
            surfaceContainerLowest = Color(0xFF0B0D11),
            surfaceContainerLow = Color(0xFF161A20),
            surfaceContainer = Color(0xFF1A1F26),
            surfaceContainerHigh = Color(0xFF232932),
            surfaceContainerHighest = Color(0xFF2C333E),
            onSurfaceVariant = Color(0xFFAEB5C2),
        )
    },

    /** Ink only — 水墨. No accent at all; everything is paper and charcoal. */
    INK("Ink wash", Color(0xFF3A3632)) {
        override val light = lightColorScheme(
            primary = Color(0xFF3A3632),
            onPrimary = Color(0xFFFAF8F5),
            primaryContainer = Color(0xFFE4DFD8),
            onPrimaryContainer = Color(0xFF1C1A17),
            secondary = Color(0xFF6B655D),
            secondaryContainer = Color(0xFFE6E1DA),
            onSecondaryContainer = Color(0xFF201D19),
            tertiary = Color(0xFF605A51),
            tertiaryContainer = Color(0xFFEDE8E1),
            background = Color(0xFFFAF8F5),
            onBackground = Color(0xFF221F1C),
            surface = Color(0xFFFAF8F5),
            onSurface = Color(0xFF221F1C),
            surfaceVariant = Color(0xFFEDE9E3),
            surfaceContainerLowest = Color(0xFFFFFEFC),
            surfaceContainerLow = Color(0xFFF5F2EE),
            surfaceContainer = Color(0xFFF0ECE7),
            surfaceContainerHigh = Color(0xFFE9E4DD),
            surfaceContainerHighest = Color(0xFFE2DCD3),
            onSurfaceVariant = Color(0xFF585249),
        )
        override val dark = darkColorScheme(
            primary = Color(0xFFCFC8BE),
            onPrimary = Color(0xFF1C1A17),
            primaryContainer = Color(0xFF433E38),
            onPrimaryContainer = Color(0xFFE4DFD8),
            secondary = Color(0xFFA8A29A),
            secondaryContainer = Color(0xFF3A352F),
            onSecondaryContainer = Color(0xFFE6E1DA),
            tertiary = Color(0xFFC6BFB5),
            tertiaryContainer = Color(0xFF3A352F),
            background = Color(0xFF14120F),
            onBackground = Color(0xFFEDE9E3),
            surface = Color(0xFF14120F),
            onSurface = Color(0xFFEDE9E3),
            surfaceVariant = Color(0xFF211E1A),
            surfaceContainerLowest = Color(0xFF0E0C0A),
            surfaceContainerLow = Color(0xFF1A1815),
            surfaceContainer = Color(0xFF1E1B18),
            surfaceContainerHigh = Color(0xFF272320),
            surfaceContainerHighest = Color(0xFF312C28),
            onSurfaceVariant = Color(0xFFB2ABA2),
        );
    };

    abstract val light: ColorScheme
    abstract val dark: ColorScheme

    fun scheme(dark: Boolean): ColorScheme = if (dark) this.dark else light

    companion object {
        fun of(name: String?): Palette =
            entries.firstOrNull { it.name == name } ?: CINNABAR
    }
}
