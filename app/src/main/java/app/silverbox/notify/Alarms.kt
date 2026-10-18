package app.silverbox.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.silverbox.MainActivity
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Pickup
import app.silverbox.domain.Format
import app.silverbox.domain.Reminders
import app.silverbox.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Two kinds of notice: one when the lab hands an envelope back, and a few nudges a day to take a
 * picture (as many as the keeper chose, inside their window). Each has its own alarm and channel.
 */
object Alarms {
    private const val CHANNEL = "lab_v1"
    private const val ID = 3
    private const val ACTION = "app.silverbox.notify.READY"
    private const val REMIND_CHANNEL = "remind_v1"
    private const val REMIND_ID = 4
    private const val REMIND_ACTION = "app.silverbox.notify.REMIND"

    /** Opens the app straight into the camera, from the nudge's button. */
    const val SHOOT = "app.silverbox.SHOOT"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val l = context.localized()
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, l.getString(R.string.channel_lab), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = l.getString(R.string.channel_lab_desc)
                }
            )
        }
        if (nm.getNotificationChannel(REMIND_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(REMIND_CHANNEL, l.getString(R.string.channel_remind), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = l.getString(R.string.channel_remind_desc)
                }
            )
        }
    }

    private fun remindPi(context: Context) = PendingIntent.getBroadcast(
        context, 2,
        Intent(context, AlarmReceiver::class.java).setAction(REMIND_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    suspend fun reschedule(context: Context) {
        val c = context.container
        val s = c.settings.now()
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        val now = System.currentTimeMillis()
        val next = c.db.dao().rolls().mapNotNull { it.readyAt }.filter { it > now }.minOrNull()
        // Neither needs to-the-second precision; this avoids the exact-alarm permission.
        if (!s.notify || !s.onboarded || next == null) am.cancel(p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next + 1000, p)

        val r = remindPi(context)
        val nudge = if (s.onboarded) Reminders.next(now, s.reminders, s.remindFrom, s.remindTo, ZoneId.systemDefault()) else null
        if (nudge == null) am.cancel(r) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nudge, r)
    }

    /** A nudge to take a picture, with something worth a frame from the shot list. */
    suspend fun remind(context: Context) {
        val c = context.container
        val s = c.settings.now()
        if (s.reminders <= 0 || !canNotify(context)) return
        val now = System.currentTimeMillis()
        val dao = c.db.dao()
        val roll = dao.loaded(Format.PHOTO.name)
        val frames = roll?.let { dao.framesOf(it.id) }.orEmpty()
        // Just shot something, or out shooting right now: no need to ask.
        if (frames.any { now - it.takenAt < Reminders.QUIET_AFTER_SHOT }) return
        if (s.shootingSince > 0 && now - s.shootingSince < Pickup.SESSION) return
        val l = context.localized()
        val prompts = l.resources.getStringArray(R.array.prompts)
        val prompt = prompts[java.util.Random(now / 60_000).nextInt(prompts.size)]
        val left = roll?.let { it.capacity - frames.size } ?: 0
        val (title, text) = when {
            roll == null -> l.getString(R.string.remind_empty_title) to l.getString(R.string.remind_empty_text)
            left <= 0 -> l.getString(R.string.remind_full_title) to l.getString(R.string.remind_full_text)
            else -> l.getString(R.string.remind_title) to l.resources.getQuantityString(R.plurals.remind_text, left, left, prompt.replaceFirstChar { it.lowercase() })
        }
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val b = NotificationCompat.Builder(context, REMIND_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(PendingIntent.getActivity(context, 101, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
        if (roll != null && left > 0) {
            b.addAction(
                0, l.getString(R.string.shoot),
                PendingIntent.getActivity(context, 102, Intent(open).setAction(SHOOT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        try {
            NotificationManagerCompat.from(context).notify(REMIND_ID, b.build())
        } catch (_: SecurityException) {
        }
    }

    suspend fun fire(context: Context) {
        val c = context.container
        val s = c.settings.now()
        if (!s.notify || !canNotify(context)) return
        val now = System.currentTimeMillis()
        val waiting = c.db.dao().rolls().filter { it.waiting(now) }
        if (waiting.isEmpty()) return
        val l = context.localized()
        val title = l.getString(R.string.notif_title)
        val text = if (waiting.size == 1) {
            val r = waiting[0]
            l.getString(if (r.movie) R.string.notif_film else R.string.notif_prints, r.order)
        } else l.resources.getQuantityString(R.plurals.notif_many, waiting.size, waiting.size)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 100,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    fun clearRemind(context: Context) = NotificationManagerCompat.from(context).cancel(REMIND_ID)

    internal const val READY = ACTION
    internal const val REMIND = REMIND_ACTION
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.READY && intent.action != Alarms.REMIND) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                if (intent.action == Alarms.REMIND) Alarms.remind(context) else Alarms.fire(context)
                context.container.changed()
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                context.container.changed()
            } finally {
                pending.finish()
            }
        }
    }
}
