package com.lifttracker.app.wear

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.lifttracker.shared.WearProtocol
import com.lifttracker.shared.WearRoutineSummary
import com.lifttracker.shared.WearWorkoutState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** In-memory mirror of what the phone published, shared by the activity and the services. */
object WatchRepository {
    private val _workoutState = MutableStateFlow(WearWorkoutState())
    val workoutState: StateFlow<WearWorkoutState> = _workoutState

    private val _routines = MutableStateFlow<List<WearRoutineSummary>>(emptyList())
    val routines: StateFlow<List<WearRoutineSummary>> = _routines

    /** Latest heart rate measured on this watch; 0 when not measuring. */
    val heartRateBpm = MutableStateFlow(0)

    /** Null until the first attempt to reach the phone has finished. */
    val phoneReachable = MutableStateFlow<Boolean?>(null)

    fun applyDataItem(item: DataItem) {
        val path = item.uri.path ?: return
        val json = DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON)
        when (path) {
            WearProtocol.DATA_WORKOUT_STATE -> _workoutState.value = WearWorkoutState.fromJson(json)
            WearProtocol.DATA_ROUTINES -> _routines.value = WearRoutineSummary.listFromJson(json)
        }
    }

    /** Loads the DataItems the phone already synced, so the UI is correct before any message round trip. */
    suspend fun loadSyncedState(context: Context) {
        val buffer = Wearable.getDataClient(context).dataItems.await()
        try {
            buffer.forEach { applyDataItem(it) }
        } finally {
            buffer.release()
        }
    }

    /** Sends a command to the phone. Returns false when no phone could be reached. */
    suspend fun sendToPhone(context: Context, path: String, payload: String = ""): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            val capable = Wearable.getCapabilityClient(appContext)
                .getCapability(WearProtocol.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                .await().nodes
            val targets = capable.ifEmpty { Wearable.getNodeClient(appContext).connectedNodes.await() }
            val bytes = payload.toByteArray(Charsets.UTF_8)
            val messageClient = Wearable.getMessageClient(appContext)
            targets.forEach { node -> messageClient.sendMessage(node.id, path, bytes).await() }
            targets.isNotEmpty()
        }.getOrDefault(false).also { phoneReachable.value = it }
    }
}
