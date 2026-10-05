package io.tr8.yybijika.remind

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.tr8.yybijika.MainActivity
import io.tr8.yybijika.R

/** Builds and posts the one notification this app sends. */
object Notifier {

    const val CHANNEL = "reviews"
    private const val ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL,
            "Daily reminder",
            // Default rather than high: this is worth a glance, never worth
            // interrupting a conversation for, and a channel that buzzes gets
            // silenced wholesale.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A daily nudge when reviews are waiting"
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, body: String) {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bamboo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Posting without permission throws on Android 13+; the caller may have
        // been revoked since the setting was switched on.
        runCatching {
            NotificationManagerCompat.from(context).notify(ID, notification)
        }
    }
}
