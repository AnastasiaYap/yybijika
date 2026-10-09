package io.tr8.yybijika.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.tr8.yybijika.MainActivity
import io.tr8.yybijika.data.Repo
import io.tr8.yybijika.learn.Vitality

/**
 * A plant on the home screen that is doing as well as the deck is.
 *
 * This exists for retention rather than for teaching, and that is not a lesser
 * thing: an app that is not opened teaches nothing at all, however good its
 * exercises are, and "I forgot it existed" is how most learning apps actually
 * die. A widget is the gentlest form of the reminder — it is simply seen, forty
 * times a day, and never interrupts anything.
 *
 * What it shows is true. The stalk grows from words genuinely held rather than
 * from sessions attended, so it cannot be satisfied by opening the app and
 * closing it again; and it fades with absence but never dies, so coming back
 * after a fortnight is met with a plant that wants watering rather than a
 * corpse and a guilt trip.
 */
class DeckWidget : GlanceAppWidget() {

    // Several sizes rather than one: the plant is redrawn for whatever cell the
    // launcher gives it, and a bitmap stretched from 2x2 to 4x2 looks it.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = runCatching { Repo.get(context).vitality() }
            .getOrElse { Vitality.of(held = 0, due = 0, daysAway = 0) }

        provideContent {
            GlanceTheme {
                Face(state)
            }
        }
    }

    @Composable
    private fun Face(state: Vitality.State) {
        val size = LocalSize.current
        val density = androidx.glance.LocalContext.current.resources.displayMetrics.density
        // Clamped hard, and not for looks. A widget's bitmap travels to the
        // launcher through RemoteViews, which is a Binder transaction with about
        // a megabyte to play with — a 1200px square at ARGB_8888 is 5.7MB and
        // the widget simply fails to draw. 420px is 700KB and is already more
        // resolution than a 2x2 cell can show.
        val w = (size.width.value * density).toInt().coerceIn(120, 420)
        val h = (size.height.value * density).toInt().coerceIn(120, 420)

        val onPaper = GlanceTheme.colors.onSurface.getColor(
            androidx.glance.LocalContext.current
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    BambooPainter.draw(w, h, state, onPaper.toArgb())
                ),
                contentDescription = description(state),
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxSize(),
            )

            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                // Only ever the count. A widget that says "study now!" is a
                // nag with a plant drawn next to it, and gets turned off.
                if (state.due > 0) {
                    Text(
                        text = "${state.due}",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 20.sp(),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }

    /** What a screen reader says, which is also the plainest statement of state. */
    private fun description(state: Vitality.State): String = buildString {
        append(
            when {
                state.fresh -> "Your bamboo is thriving"
                state.thirsty -> "Your bamboo is fading"
                else -> "Your bamboo is well"
            }
        )
        append(". ${Vitality.caption(state)}")
        if (state.due > 0) append(", ${state.due} due")
        append(".")
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp,
)

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

class DeckWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeckWidget()
}
