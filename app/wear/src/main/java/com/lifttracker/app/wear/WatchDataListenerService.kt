package com.lifttracker.app.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.lifttracker.shared.WearProtocol

/** Receives workout state / routine DataItems and start / finish messages from the phone. */
class WatchDataListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                WatchRepository.applyDataItem(event.dataItem)
            }
        }
        val state = WatchRepository.workoutState.value
        if (!state.active) {
            HeartRateService.stop(this)
        }
        WatchWorkoutNotification.update(this, state)
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            // Starting a foreground service from the background can be refused by the system;
            // the activity starts it again as soon as it is visible.
            WearProtocol.MSG_WORKOUT_STARTED -> HeartRateService.startIfPermitted(this)
            WearProtocol.MSG_WORKOUT_FINISHED -> {
                HeartRateService.stop(this)
                WatchWorkoutNotification.cancel(this)
            }
            WearProtocol.MSG_REST_FINISHED -> {
                WatchAlerts.restFinished(this, String(event.data ?: ByteArray(0), Charsets.UTF_8))
                // Switch the Now Bar / watch face chip from the rest countdown back to the workout.
                WatchWorkoutNotification.update(this, WatchRepository.workoutState.value)
            }
        }
    }
}
