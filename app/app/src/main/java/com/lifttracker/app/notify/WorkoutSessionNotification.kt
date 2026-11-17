package com.lifttracker.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lifttracker.app.MainActivity
import com.lifttracker.app.wear.WearEventBus
import com.lifttracker.app.wear.WearIncomingMessage
import com.lifttracker.shared.WearProtocol
import com.lifttracker.shared.WearWorkoutState

/**
 * The notification that stays up for as long as a workout is running: what set is next, the rest
 * countdown, and buttons to complete the set, skip the rest or move it by 15 seconds.
 *
 * It belongs to [WorkoutSessionService], a foreground service, which also keeps the app (and with it
 * the workout and its timers) alive while the screen is off.
 */
object WorkoutSessionNotification {
    private const val CHANNEL_ID = "workout_session_v2"
    const val NOTIFICATION_ID = 4201
    const val ACTION_COMPLETE_SET = "com.lifttracker.app.action.COMPLETE_SET"
    const val ACTION_SKIP_REST = "com.lifttracker.app.action.SKIP_REST"
    const val ACTION_REST_MINUS = "com.lifttracker.app.action.REST_MINUS_15"
    const val ACTION_REST_PLUS = "com.lifttracker.app.action.REST_PLUS_15"

    @Volatile private var latest: Notification? = null
    @Volatile private var latestState: WearWorkoutState? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var restEndElapsedMs: Long = 0L
    @Volatile private var restEndWallMs: Long = 0L
    @Volatile private var showing = false

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val restTickRunnable = object : Runnable {
        override fun run() {
            val ctx = appContext ?: return
            val state = latestState ?: return
            if (!showing || !state.active || state.paused || restEndElapsedMs <= 0L) return

            val nowElapsed = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()
            val leftMs = restEndElapsedMs - nowElapsed
            val remainingSec = ((leftMs + 999L) / 1000L).toInt().coerceAtLeast(0)

            val updated = if (remainingSec > 0) {
                state.copy(restRemainingSeconds = remainingSec, restEndsAtEpochMs = restEndWallMs)
            } else {
                restEndElapsedMs = 0L
                restEndWallMs = 0L
                state.copy(
                    restRemainingSeconds = 0,
                    restEndsAtEpochMs = 0L,
                    elapsedSeconds = state.elapsedSecondsAt(nowWall),
                    sentAtEpochMs = nowWall
                )
            }
            latestState = updated
            val notification = build(ctx, updated, nowWall)
            latest = notification
            ctx.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)

            if (remainingSec > 0) {
                val delayMs = (leftMs - (remainingSec - 1) * 1000L + 20L).coerceIn(100L, 1050L)
                mainHandler.postDelayed(this, delayMs)
            }
        }
    }

    private fun stopRestTicker() {
        mainHandler.removeCallbacks(restTickRunnable)
    }

    private fun scheduleRestTicker(leftMs: Long) {
        stopRestTicker()
        if (leftMs <= 0L) return
        val remainingSec = ((leftMs + 999L) / 1000L).toInt().coerceAtLeast(1)
        val delayMs = (leftMs - (remainingSec - 1) * 1000L + 20L).coerceIn(100L, 1050L)
        mainHandler.postDelayed(restTickRunnable, delayMs)
    }

    internal fun formatRestCountdown(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hrs = safe / 3600
        val mins = (safe % 3600) / 60
        val secs = safe % 60
        return if (hrs > 0) String.format("%d:%02d:%02d", hrs, mins, secs) else String.format("%02d:%02d", mins, secs)
    }

    private fun formatRestTitle(seconds: Int): CharSequence {
        val timer = formatRestCountdown(seconds)
        val label = "Rest  $timer"
        return SpannableString(label).apply {
            val start = label.length - timer.length
            setSpan(RelativeSizeSpan(1.45f), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel("workout_session") // first version: low importance, shown collapsed
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                // Default importance keeps it in the main list with its buttons showing; it is still
                // completely silent, so updating it never makes a sound or a buzz.
                NotificationChannel(CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Shows the running workout with set and rest timer controls"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, WorkoutActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun build(context: Context, state: WearWorkoutState, nowMs: Long = System.currentTimeMillis()): Notification {
        ensureChannel(context)
        val resting = state.restRemainingSeconds > 0
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextSet = listOf(state.exerciseTitle, state.setLabel).filter { it.isNotBlank() }.joinToString(" · ")
        val workoutSummary = "${state.title.ifBlank { "Workout" }} · ${state.completedSets}/${state.totalSets} sets"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(workoutSummary)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(true)
            // Android 16 Live Update: promoted to the status bar chip, the top of the lock screen
            // and, on Samsung phones with One UI 8+, the Now Bar. Ignored on older Android versions.
            .setRequestPromotedOngoing(true)
        if (state.totalSets > 0) {
            // Set progress; shown as a bar in the shade and the Now Bar, a plain progress bar before Android 16.
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(state.totalSets)))
                    .setProgress(state.completedSets.coerceIn(0, state.totalSets))
            )
        }
        when {
            state.paused -> builder
                .setContentText("Paused" + if (nextSet.isNotBlank()) " · next: $nextSet" else "")
                .setShortCriticalText("Paused")
                .setShowWhen(false)
            resting -> {
                val endWallMs = if (state.restEndsAtEpochMs > nowMs) {
                    state.restEndsAtEpochMs
                } else {
                    nowMs + state.restRemainingSeconds * 1000L
                }
                builder
                    // Prominent countdown in the title (larger than the header chronometer) while keeping
                    // the chronometer active for the status bar chip / Now Bar.
                    .setContentTitle(formatRestTitle(state.restRemainingSeconds))
                    .setContentText(if (nextSet.isNotBlank()) "Next: $nextSet" else "Rest between sets")
                    .setSubText(workoutSummary)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(endWallMs)
            }
            else -> builder
                .setContentText(nextSet.ifBlank { "All sets done — finish the workout in the app" })
                .setUsesChronometer(true)
                .setWhen(nowMs - state.elapsedSeconds * 1000L)
        }
        if (!state.paused) {
            if (resting) {
                builder.addAction(0, "−15s", action(context, ACTION_REST_MINUS, 11))
                builder.addAction(0, "Skip rest", action(context, ACTION_SKIP_REST, 13))
                builder.addAction(0, "+15s", action(context, ACTION_REST_PLUS, 12))
            } else if (state.setIndex >= 0) {
                builder.addAction(0, "Complete set", action(context, ACTION_COMPLETE_SET, 14))
            }
        }
        return builder.build()
    }

    /** Shows or refreshes the notification for a running workout; removes it when [state] is not active. */
    fun update(context: Context, state: WearWorkoutState) {
        val enabled = context.getSharedPreferences(RestTimerAlerts.PREFS, Context.MODE_PRIVATE)
            .getBoolean(RestTimerAlerts.KEY_ONGOING, true)
        if (!state.active || !enabled || !RestTimerAlerts.canNotify(context)) {
            stop(context)
            return
        }
        val appCtx = context.applicationContext
        appContext = appCtx
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        if (state.restRemainingSeconds > 0 && !state.paused) {
            val endWall = if (state.restEndsAtEpochMs > nowWall) {
                state.restEndsAtEpochMs
            } else {
                nowWall + state.restRemainingSeconds * 1000L
            }
            val leftMs = (endWall - nowWall).coerceAtLeast(1L)
            restEndWallMs = endWall
            restEndElapsedMs = nowElapsed + leftMs
            latestState = state.copy(restEndsAtEpochMs = endWall)
            val notification = build(appCtx, latestState!!, nowWall)
            latest = notification
            if (!showing) {
                showing = true
                runCatching { ContextCompat.startForegroundService(appCtx, Intent(appCtx, WorkoutSessionService::class.java)) }
            }
            appCtx.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
            scheduleRestTicker(leftMs)
            return
        }

        stopRestTicker()
        restEndElapsedMs = 0L
        restEndWallMs = 0L
        latestState = state
        val notification = build(appCtx, state, nowWall)
        latest = notification
        if (!showing) {
            showing = true
            // Refused when the workout was started from the watch while the app is in the background;
            // the plain notification below still appears, just without the service behind it.
            runCatching { ContextCompat.startForegroundService(appCtx, Intent(appCtx, WorkoutSessionService::class.java)) }
        }
        appCtx.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    fun stop(context: Context) {
        stopRestTicker()
        restEndElapsedMs = 0L
        restEndWallMs = 0L
        latestState = null
        if (!showing && latest == null) return
        showing = false
        latest = null
        context.stopService(Intent(context, WorkoutSessionService::class.java))
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    internal fun current(context: Context): Notification =
        latest ?: build(context, WearWorkoutState(active = true, title = "Workout"))

    /** True on Android 16+, where the notification can become a Live Update (status bar chip / Samsung Now Bar). */
    val liveUpdatesSupported: Boolean get() = Build.VERSION.SDK_INT >= 36

    /** Whether the user lets the app's Live Updates be promoted; always false before Android 16. */
    fun canShowLiveUpdates(context: Context): Boolean =
        NotificationManagerCompat.from(context).canPostPromotedNotifications()

    /** The system page where the user allows or blocks Live Updates (and so the Now Bar) for this app. */
    fun liveUpdatesSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 36) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
}

class WorkoutSessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching {
            ServiceCompat.startForeground(
                this, WorkoutSessionNotification.NOTIFICATION_ID, WorkoutSessionNotification.current(this), type
            )
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    /** The app was swiped away: the workout lives in the app's memory, so the notification goes with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        WorkoutSessionNotification.stop(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        WorkoutSessionNotification.stop(this)
        super.onDestroy()
    }
}

/** Notification buttons. They are handled by the same code as the watch's buttons. */
class WorkoutActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (path, data) = when (intent.action) {
            WorkoutSessionNotification.ACTION_COMPLETE_SET -> WearProtocol.MSG_COMPLETE_SET to ""
            WorkoutSessionNotification.ACTION_SKIP_REST -> WearProtocol.MSG_SKIP_REST to ""
            WorkoutSessionNotification.ACTION_REST_MINUS -> WearProtocol.MSG_ADJUST_REST to "-15"
            WorkoutSessionNotification.ACTION_REST_PLUS -> WearProtocol.MSG_ADJUST_REST to "15"
            else -> return
        }
        if (WearEventBus.hasActiveCollector) {
            WearEventBus.publish(WearIncomingMessage(path = path, data = data, sourceNodeId = "notification"))
        } else {
            // The app was closed, so there is no workout left to control.
            WorkoutSessionNotification.stop(context)
        }
    }
}
