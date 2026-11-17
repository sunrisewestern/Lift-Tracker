package com.lifttracker.app.wear

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator

/** "Rest is over" on the wrist: a vibration pattern and, on watches with a speaker, a short sound. */
object WatchAlerts {
    private var ringtone: Ringtone? = null

    /** [payload] is "vibrate=1;sound=1" as sent by the phone; missing parts default to on. */
    fun restFinished(context: Context, payload: String) {
        val flags = payload.split(';').mapNotNull { part ->
            val pair = part.split('=')
            if (pair.size == 2) pair[0].trim() to (pair[1].trim() != "0") else null
        }.toMap()
        // Alarm usage so the alert still plays when the watch app is in the background.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (flags["vibrate"] != false) {
            runCatching {
                val vibrator = context.getSystemService(Vibrator::class.java)
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 600), -1), attributes)
            }
        }
        if (flags["sound"] != false) {
            runCatching {
                ringtone?.stop()
                ringtone = RingtoneManager.getRingtone(
                    context.applicationContext,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                )?.apply {
                    audioAttributes = attributes
                    play()
                }
            }
        }
    }
}
