package com.lifttracker.app.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.lifttracker.shared.WearWorkoutState

/**
 * The watch's "workout in progress" notification, published as a Wear OS Ongoing Activity.
 *
 * The Ongoing Activity is what puts the workout on the watch face, in the recent apps list and, on
 * Galaxy Watches with One UI 8 Watch, in the Now Bar: the rest countdown while resting, otherwise the
 * set count and the workout time. Tapping it opens the watch app.
 *
 * It follows the phone's workout state, so it is updated from [WatchDataListenerService] even while
 * the watch app is closed. [HeartRateService] uses the same notification (same id) as its foreground
 * notification, so there is only ever one.
 */
object WatchWorkoutNotification {
    private const val CHANNEL_ID = "workout_ongoing"
    const val NOTIFICATION_ID = 7001

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.deleteNotificationChannel("workout_tracking") // previous heart-rate-only channel
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running workout on the watch face and in the Now Bar"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Builds the notification for [state] and attaches the Ongoing Activity to it. */
    fun build(context: Context, state: WearWorkoutState, nowMs: Long = System.currentTimeMillis()): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, WatchMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Timer parts are based on the elapsed-realtime clock; the phone sends wall clock times.
        val nowElapsed = SystemClock.elapsedRealtime()
        val restLeftMs = state.restRemainingSecondsAt(nowMs) * 1000L
        val elapsedMs = state.elapsedSecondsAt(nowMs) * 1000L
        val resting = restLeftMs > 0
        val sets = if (state.totalSets > 0) "${state.completedSets}/${state.totalSets}" else ""
        val title = state.title.ifBlank { "Workout" }
        val nextSet = listOf(state.exerciseTitle, state.setLabel).filter { it.isNotBlank() }.joinToString(" · ")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(if (sets.isNotEmpty()) "$title · $sets sets" else title)
            .setContentText(
                when {
                    state.paused -> "Paused"
                    resting -> "Resting" + if (nextSet.isNotBlank()) " · next: $nextSet" else ""
                    else -> nextSet.ifBlank { "All sets done" }
                }
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val status = when {
            state.paused -> Status.Builder()
                .addTemplate(if (sets.isNotEmpty()) "Paused · #sets#" else "Paused")
                .addPart("sets", Status.TextPart(sets))
                .build()
            resting -> Status.Builder()
                .addTemplate("Rest #rest#")
                .addPart("rest", Status.TimerPart(nowElapsed + restLeftMs))
                .build()
            else -> Status.Builder()
                .addTemplate(if (sets.isNotEmpty()) "#sets# · #time#" else "#time#")
                .addTemplate("#time#") // fallback when there is little room
                .addPart("sets", Status.TextPart(sets))
                .addPart("time", Status.StopwatchPart(nowElapsed - elapsedMs))
                .build()
        }
        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_workout_notification)
            .setTouchIntent(open)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setTitle(title)
            .setStatus(status)
            .build()
            .apply(context)
        return builder.build()
    }

    /** Shows or refreshes the ongoing workout; removes it when [state] is not active. */
    fun update(context: Context, state: WearWorkoutState) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!state.active) {
            // While the heart rate service runs, the notification is its own and goes when it stops.
            if (!HeartRateService.isRunning) manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canNotify(context)) return
        runCatching { manager.notify(NOTIFICATION_ID, build(context, state)) }
    }

    fun cancel(context: Context) {
        if (HeartRateService.isRunning) return
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
