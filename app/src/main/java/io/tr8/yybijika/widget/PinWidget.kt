package io.tr8.yybijika.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Offer to put the bamboo on the home screen, from inside the app.
 *
 * The alternative is telling somebody to long-press their wallpaper, find
 * Widgets, scroll to an app whose name is in Chinese and drag a tile out — which
 * is where a feature meant to be seen every day quietly dies. One tap and a
 * system confirmation is the difference between a widget that exists and a
 * widget that gets used.
 *
 * [isSupported] is not a formality: most launchers honour pinning, some do not,
 * and offering a button that silently does nothing is worse than not offering
 * it. Where it is unsupported the app says so and explains the manual route.
 */
object PinWidget {

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** @return false when the launcher declined or cannot pin. */
    fun request(context: Context): Boolean {
        if (!isSupported(context)) return false
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, DeckWidgetReceiver::class.java)

        // A callback so the plant is drawn immediately on landing rather than
        // waiting for the first periodic update, which could be hours away.
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DeckWidgetReceiver::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return runCatching {
            manager.requestPinAppWidget(provider, null, callback)
        }.getOrDefault(false)
    }
}
