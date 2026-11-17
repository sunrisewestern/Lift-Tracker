package com.lifttracker.app.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lifttracker.shared.WearHeartRateSample
import com.lifttracker.shared.WearProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Tracks heart rate for the duration of a phone workout using a Health Services exercise, and
 * streams the samples to the phone in small batches.
 */
class HeartRateService : LifecycleService() {

    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }
    private val pending = mutableListOf<WearHeartRateSample>()
    private var exerciseStarted = false

    private val callback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) = Unit
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
            val samples = update.latestMetrics.getData(DataType.HEART_RATE_BPM).mapNotNull { point ->
                val bpm = point.value.toInt()
                if (bpm > 0) WearHeartRateSample(point.getTimeInstant(bootInstant).toEpochMilli(), bpm) else null
            }
            if (samples.isEmpty()) return
            WatchRepository.heartRateBpm.value = samples.last().bpm
            synchronized(pending) { pending += samples }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startInForeground()
        lifecycleScope.launch { startExercise() }
        lifecycleScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    private fun startInForeground() {
        // Same notification as the watch's ongoing workout (Ongoing Activity / Now Bar), so there is only one.
        val notification = WatchWorkoutNotification.build(this, WatchRepository.workoutState.value.let {
            if (it.active) it else it.copy(active = true)
        })
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun startExercise() {
        runCatching {
            val info = exerciseClient.getCurrentExerciseInfoAsync().await()
            if (info.exerciseTrackedStatus == ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS) {
                // Never end another app's exercise; the workout simply has no heart rate.
                stopSelf()
                return
            }
            val capabilities = exerciseClient.getCapabilitiesAsync().await()
            val type = listOf(ExerciseType.STRENGTH_TRAINING, ExerciseType.WORKOUT).firstOrNull { candidate ->
                candidate in capabilities.supportedExerciseTypes &&
                    DataType.HEART_RATE_BPM in capabilities.getExerciseTypeCapabilities(candidate).supportedDataTypes
            }
            if (type == null) {
                stopSelf()
                return
            }
            exerciseClient.setUpdateCallback(callback)
            if (info.exerciseTrackedStatus != ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                val config = ExerciseConfig.builder(type)
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                    .setIsAutoPauseAndResumeEnabled(false)
                    .setIsGpsEnabled(false)
                    .build()
                exerciseClient.startExerciseAsync(config).await()
            }
            exerciseStarted = true
        }.onFailure { stopSelf() }
    }

    private suspend fun flush() {
        val batch = synchronized(pending) {
            if (pending.isEmpty()) return
            pending.toList().also { pending.clear() }
        }
        val sent = WatchRepository.sendToPhone(this, WearProtocol.MSG_HEART_RATE, WearHeartRateSample.listToJson(batch))
        if (!sent) {
            // Keep a bounded backlog while the phone is out of range.
            synchronized(pending) {
                pending.addAll(0, batch)
                while (pending.size > MAX_BACKLOG) pending.removeAt(0)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        WatchRepository.heartRateBpm.value = 0
        if (exerciseStarted) {
            runCatching { exerciseClient.endExerciseAsync() }
        }
        runCatching { exerciseClient.clearUpdateCallbackAsync(callback) }
        // Keep the ongoing workout on screen if only heart rate tracking stopped; removed otherwise.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        WatchWorkoutNotification.update(this, WatchRepository.workoutState.value)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = WatchWorkoutNotification.NOTIFICATION_ID
        private const val FLUSH_INTERVAL_MS = 5_000L
        private const val MAX_BACKLOG = 2_000

        @Volatile
        var isRunning = false
            private set

        fun heartRatePermission(): String =
            if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, heartRatePermission()) == PackageManager.PERMISSION_GRANTED

        fun startIfPermitted(context: Context) {
            if (isRunning || !hasPermission(context)) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, HeartRateService::class.java))
            }
        }

        fun stop(context: Context) {
            if (isRunning) context.stopService(Intent(context, HeartRateService::class.java))
        }
    }
}
