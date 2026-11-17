package com.lifttracker.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lifttracker.app.MainActivity
import com.lifttracker.app.wear.WearConnector
import com.lifttracker.shared.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Rest is over" alert. The alert is scheduled with [AlarmManager] for the moment the rest ends, so
 * it fires on time with the screen off or the app in the background, and is delivered as a
 * notification that plays the sound the user picked.
 *
 * A notification channel's sound cannot be changed once the channel exists, so the channel id is
 * derived from the sound + vibration choice and channels for earlier choices are deleted.
 */
object RestTimerAlerts {
    const val PREFS = "lifttracker_settings"
    const val KEY_ENABLED = "rest_alert_enabled"
    /** Absent = the system default notification sound; empty = silent; otherwise a sound URI. */
    const val KEY_SOUND_URI = "rest_alert_sound_uri"
    const val KEY_VIBRATE = "rest_alert_vibrate"
    const val KEY_VOLUME = "rest_timer_volume_float"
    const val KEY_WATCH_VIBRATE = "watch_alert_vibrate"
    const val KEY_WATCH_SOUND = "watch_alert_sound"
    const val KEY_ONGOING = "workout_notification_enabled"

    private const val CHANNEL_PREFIX = "rest_timer_"
    private const val NOTIFICATION_ID = 4101
    private const val REQUEST_ALARM = 4102
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_TEXT = "text"

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** False when the user turned notifications off for the app, or muted the timer channel, in system settings. */
    fun canNotify(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return hasNotificationPermission(context) && manager.areNotificationsEnabled()
    }

    fun canScheduleExact(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    }

    /** The sound the alert will play, or null for silent. */
    fun soundUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getFloat(KEY_VOLUME, 0.6f) <= 0.02f) return null
        val stored = prefs.getString(KEY_SOUND_URI, null)
        return when {
            stored == null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            stored.isEmpty() -> null
            else -> Uri.parse(stored)
        }
    }

    /** Name of a sound for display, e.g. "Chime". */
    fun soundTitle(context: Context, storedUri: String?): String = when {
        storedUri == null -> "Default notification sound"
        storedUri.isEmpty() -> "Silent"
        else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(storedUri))?.getTitle(context) }
            .getOrNull() ?: "Custom sound"
    }

    private fun ensureChannel(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sound = soundUri(context)
        val vibrate = prefs.getBoolean(KEY_VIBRATE, true)
        val id = CHANNEL_PREFIX + Integer.toHexString("${sound ?: "silent"}|$vibrate".hashCode())
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(id, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alert when the rest between sets is over"
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 350, 150, 350)
            }
            manager.createNotificationChannel(channel)
        }
        return id
    }

    /** Posts the alert right away. Returns false when notifications are blocked. */
    fun show(context: Context, title: String, text: String): Boolean {
        if (!canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ensureChannel(context))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000L)
            .setContentIntent(open)
            .build()
        // Cancel first so a repeat alert makes sound again instead of silently updating.
        manager.cancel(NOTIFICATION_ID)
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun alarmIntent(context: Context, title: String?, text: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_ALARM,
            Intent(context, RestTimerReceiver::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Schedules (or re-schedules) the alert [seconds] from now and clears any alert still showing. */
    fun schedule(context: Context, seconds: Int, title: String, text: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        if (seconds <= 0 || !prefs.getBoolean(KEY_ENABLED, true)) {
            alarms.cancel(alarmIntent(context, null, null))
            return
        }
        val at = SystemClock.elapsedRealtime() + seconds * 1000L
        val intent = alarmIntent(context, title, text)
        runCatching {
            if (canScheduleExact(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent)
            }
        }.onFailure { alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent) }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context, null, null))
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    internal fun onAlarm(context: Context, intent: Intent) {
        show(
            context,
            intent.getStringExtra(EXTRA_TITLE) ?: "Rest is over",
            intent.getStringExtra(EXTRA_TEXT) ?: "Time for your next set."
        )
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimerAlerts.onAlarm(context, intent)
        // Alert the watch too. Sending is asynchronous, so keep the receiver alive until it is done.
        val prefs = context.getSharedPreferences(RestTimerAlerts.PREFS, Context.MODE_PRIVATE)
        val vibrate = prefs.getBoolean(RestTimerAlerts.KEY_WATCH_VIBRATE, true)
        val sound = prefs.getBoolean(RestTimerAlerts.KEY_WATCH_SOUND, true)
        if (!vibrate && !sound) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    runCatching {
                        WearConnector(context.applicationContext).sendMessage(
                            WearProtocol.MSG_REST_FINISHED,
                            "vibrate=${if (vibrate) 1 else 0};sound=${if (sound) 1 else 0}"
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
