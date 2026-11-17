package com.lifttracker.app.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.lifttracker.app.MainActivity
import com.lifttracker.shared.WearProtocol

/**
 * Receives watch -> phone messages. While the app UI is alive they are forwarded to the ViewModel
 * through [WearEventBus]; if the watch starts a workout while the app is not running, a notification
 * invites the user to open it (the active workout lives in the UI process).
 */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val message = WearIncomingMessage(
            path = event.path,
            data = String(event.data ?: ByteArray(0), Charsets.UTF_8),
            sourceNodeId = event.sourceNodeId
        )
        if (WearEventBus.hasActiveCollector) {
            WearEventBus.publish(message)
        } else if (event.path == WearProtocol.MSG_WORKOUT_STARTED || event.path == WearProtocol.MSG_START_ROUTINE) {
            showOpenAppNotification()
        }
    }

    private fun showOpenAppNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Watch workouts", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse("lifttracker://app/workout"), this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Workout requested from your watch")
            .setContentText("Open Lift Tracker on your phone to start logging.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "wear_workout"
        const val NOTIFICATION_ID = 4101
    }
}
