package com.lifttracker.app.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.lifttracker.shared.WearProtocol
import com.lifttracker.shared.WearRoutineSummary
import com.lifttracker.shared.WearWorkoutState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.tasks.await

/** A message received from the watch, forwarded by [PhoneWearListenerService]. */
data class WearIncomingMessage(
    val path: String,
    val data: String,
    val sourceNodeId: String
)

/**
 * Process-wide hand-off between [PhoneWearListenerService] (which Play services starts on demand)
 * and the ViewModel that owns the active workout.
 */
object WearEventBus {
    private val _messages = MutableSharedFlow<WearIncomingMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<WearIncomingMessage> = _messages

    /** True while a ViewModel is collecting, i.e. watch commands will actually be handled. */
    val hasActiveCollector: Boolean
        get() = _messages.subscriptionCount.value > 0

    fun publish(message: WearIncomingMessage) {
        _messages.tryEmit(message)
    }
}

data class WearNodeInfo(val id: String, val displayName: String, val isNearby: Boolean)

data class WearConnectionSnapshot(
    val connectedNodes: List<WearNodeInfo>,
    /** Nodes (connected or not) that have the Lift Tracker watch app installed. */
    val nodesWithApp: List<WearNodeInfo>,
    /** Nodes with the watch app that can be messaged right now. */
    val reachableNodesWithApp: List<WearNodeInfo>
)

/** Real Wearable Data Layer transport for the phone app. All calls throw if Play services is unavailable. */
class WearConnector(context: Context) {
    private val appContext = context.applicationContext
    private val nodeClient by lazy { Wearable.getNodeClient(appContext) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(appContext) }
    private val messageClient by lazy { Wearable.getMessageClient(appContext) }
    private val dataClient by lazy { Wearable.getDataClient(appContext) }
    private val remoteActivityHelper by lazy { RemoteActivityHelper(appContext) }

    suspend fun queryConnection(): WearConnectionSnapshot {
        val connected = nodeClient.connectedNodes.await()
            .map { WearNodeInfo(it.id, it.displayName, it.isNearby) }
        val withApp = capabilityClient
            .getCapability(WearProtocol.CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL)
            .await().nodes.map { WearNodeInfo(it.id, it.displayName, it.isNearby) }
        val reachable = capabilityClient
            .getCapability(WearProtocol.CAPABILITY_WEAR_APP, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.map { WearNodeInfo(it.id, it.displayName, it.isNearby) }
        return WearConnectionSnapshot(connected, withApp, reachable)
    }

    /** Sends [path] to every reachable watch that has the app. Returns the node ids it was sent to. */
    suspend fun sendMessage(path: String, payload: String): List<String> {
        val nodes = capabilityClient
            .getCapability(WearProtocol.CAPABILITY_WEAR_APP, CapabilityClient.FILTER_REACHABLE)
            .await().nodes
        val bytes = payload.toByteArray(Charsets.UTF_8)
        nodes.forEach { node -> messageClient.sendMessage(node.id, path, bytes).await() }
        return nodes.map { it.id }
    }

    suspend fun publishWorkoutState(state: WearWorkoutState) {
        putJson(WearProtocol.DATA_WORKOUT_STATE, state.toJson())
    }

    suspend fun publishRoutines(routines: List<WearRoutineSummary>) {
        putJson(WearProtocol.DATA_ROUTINES, WearRoutineSummary.listToJson(routines))
    }

    private suspend fun putJson(path: String, json: String) {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString(WearProtocol.KEY_JSON, json)
            // DataItems are only delivered when their content changes; the timestamp forces a resend.
            dataMap.putLong(WearProtocol.KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    /** Opens the watch app on every connected watch. Returns the number of watches asked. */
    suspend fun launchAppOnWear(): Int {
        val nodes = nodeClient.connectedNodes.await()
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(WearProtocol.WEAR_LAUNCH_URI))
        nodes.forEach { node -> remoteActivityHelper.startRemoteActivity(intent, node.id) }
        return nodes.size
    }
}
