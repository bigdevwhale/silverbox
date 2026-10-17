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
import app.silverbox.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One notice when the lab hands an envelope back. The alarm is set for the next roll's ready time;
 * nothing else ever pings.
 */
object Alarms {
    private const val CHANNEL = "lab_v1"
    private const val ID = 3
    private const val ACTION = "app.silverbox.notify.READY"

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
    }

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
        if (!s.notify || !s.onboarded || next == null) {
            am.cancel(p)
            return
        }
        // The lab doesn't need to-the-second precision; this avoids the exact-alarm permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next + 1000, p)
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

    internal const val READY = ACTION
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.READY) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Alarms.fire(context)
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
