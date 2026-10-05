package io.tr8.yybijika.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.tr8.yybijika.data.Repo
import io.tr8.yybijika.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Keeps one alarm in the future, and never more than one.
 *
 * A single self-rescheduling alarm rather than a repeating one, because a
 * repeating alarm fixed to an interval drifts: it fires a few seconds late each
 * day, and a month later the evening reminder arrives at breakfast. Each firing
 * computes the next occurrence of the chosen *time of day* from scratch.
 */
object ReminderScheduler {

    private const val REQUEST = 7001

    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Schedule the next one, or clear it if reminders are off. */
    fun sync(context: Context) {
        val settings = Settings(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return

        if (!settings.remindEnabled) {
            alarms.cancel(intent(context))
            return
        }

        val at = Reminder.nextOccurrenceMillis(settings.remindAt)
        // Inexact on purpose. An exact alarm needs a special permission on
        // recent Android and is meant for things that genuinely cannot slip —
        // an alarm clock, a timer. A study nudge that arrives within the hour
        // is doing its job, and asking for the stronger permission to deliver
        // it would be overreach.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context))
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, at, intent(context))
            }
        }
    }
}

/**
 * The alarm landing: decide, maybe post, and always schedule the next one.
 *
 * Rescheduling happens whatever the decision, so a quiet day — nothing due, or
 * the work already done — does not silently end the reminders for good.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        try {
            val settings = Settings(app)
            if (settings.remindEnabled) {
                runBlocking {
                    val repo = Repo.get(app)
                    val due = withContext(Dispatchers.IO) { repo.dueCount() }
                    val vitality = withContext(Dispatchers.IO) { repo.vitality() }
                    val studied = withContext(Dispatchers.IO) { repo.studiedToday() }
                    val today = Reminder.today()

                    val decision = Reminder.decide(
                        due = due,
                        studiedToday = studied,
                        lastRemindedDay = settings.lastRemindedDay.takeIf { it >= 0 },
                        today = today,
                    )
                    if (decision.show) {
                        val (title, body) = Reminder.wording(due, vitality.daysAway)
                        Notifier.show(app, title, body)
                        settings.lastRemindedDay = today
                    }
                }
            }
        } catch (_: Exception) {
            // A failed reminder is not worth a crash in the background.
        } finally {
            ReminderScheduler.sync(app)
            pending.finish()
        }
    }
}

/** Alarms do not survive a reboot, so the schedule is rebuilt on boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.sync(context.applicationContext)
        }
    }
}
