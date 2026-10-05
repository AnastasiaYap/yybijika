package io.tr8.yybijika.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import io.tr8.yybijika.learn.Vitality
import io.tr8.yybijika.widget.BambooPainter

/**
 * The same plant as the widget, shown inside the app.
 *
 * Deliberately the same renderer rather than a Compose redraw of it: two
 * drawings of one plant would drift, and the whole point is that the thing on
 * the home screen and the thing in the app are one object.
 *
 * It earns its place here separately from the widget. Finishing a session and
 * watching the bamboo straighten is the moment the mechanic actually pays off,
 * and that moment happens in the app — a reward you have to leave and go and
 * look for is not much of a reward.
 */
@Composable
fun Bamboo(
    state: Vitality.State,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val density = LocalDensity.current
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val px = with(density) { height.toPx() }.toInt().coerceAtLeast(120)

    // Keyed on everything the drawing depends on, so it is not repainted on
    // every recomposition but is repainted the moment the plant changes.
    val bitmap = remember(state.joints, state.vigour, px, onSurface) {
        BambooPainter.draw(
            width = (px * 1.15f).toInt(),
            height = px,
            state = state,
            onPaper = onSurface,
        ).asImageBitmap()
    }

    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
    }
}
