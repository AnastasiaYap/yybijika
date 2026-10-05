package io.tr8.yybijika.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Redraw the plant after something has actually changed.
 *
 * The widget's own update period is six hours, which is right for the slow
 * fading but far too slow for the moment that matters most: finishing a session
 * should visibly revive the plant while the learner is still holding the phone.
 * Watering something and seeing nothing happen is worse than no plant at all.
 *
 * Failures are swallowed on purpose. Nobody has ever had a worse study session
 * because a home-screen widget did not repaint, and a crash here would land in
 * the middle of a review.
 */
object WidgetNudge {

    fun refresh(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { DeckWidget().updateAll(context.applicationContext) }
        }
    }
}
