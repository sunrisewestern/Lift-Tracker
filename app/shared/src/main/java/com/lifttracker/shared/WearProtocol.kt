package com.lifttracker.shared

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wearable Data Layer contract shared by the phone app (:app) and the watch app (:wear).
 *
 * State flows phone -> watch as DataItems (they are replayed to a watch that connects later);
 * commands flow watch -> phone as messages.
 */
object WearProtocol {
    /** Capability advertised by the watch app (wear/src/main/res/values/wear.xml). */
    const val CAPABILITY_WEAR_APP = "lifttracker_wear_app"

    /** Capability advertised by the phone app (app/src/main/res/values/wear.xml). */
    const val CAPABILITY_PHONE_APP = "lifttracker_phone_app"

    /** Deep link the phone uses with RemoteActivityHelper to open the watch app. */
    const val WEAR_LAUNCH_URI = "lifttracker://launchwearos"

    // --- DataItems (phone -> watch) ---
    const val DATA_WORKOUT_STATE = "/workout-state"
    const val DATA_ROUTINES = "/routines"
    const val KEY_JSON = "json"
    const val KEY_UPDATED_AT = "updatedAt"

    // --- Messages (both directions) ---
    /** Watch -> phone: start an empty workout. Phone -> watch: a workout was started on the phone. */
    const val MSG_WORKOUT_STARTED = "/workout-started"

    /** Phone -> watch: the active workout was finished or discarded. */
    const val MSG_WORKOUT_FINISHED = "/workout-finished"

    /**
     * Phone -> watch: the rest between sets just ended; the watch vibrates and/or plays a sound.
     * Payload: "vibrate=1;sound=1" (either may be 0).
     */
    const val MSG_REST_FINISHED = "/rest-finished"

    // --- Messages (watch -> phone) ---
    /** Payload: routine id (UTF-8). */
    const val MSG_START_ROUTINE = "/start-routine"
    const val MSG_COMPLETE_SET = "/complete-set"
    const val MSG_SKIP_REST = "/skip-rest"

    /** Payload: signed delta seconds as text, e.g. "15" or "-15". */
    const val MSG_ADJUST_REST = "/adjust-rest"
    const val MSG_TOGGLE_PAUSE = "/toggle-pause"
    const val MSG_FINISH_WORKOUT = "/finish-workout"

    /** Throw the active workout away without saving it. */
    const val MSG_DISCARD_WORKOUT = "/discard-workout"

    /** Payload: [WearSetEdit] JSON — new weight (in the phone's display unit) and reps for the current set. */
    const val MSG_EDIT_SET = "/edit-set"

    /** Payload: index of the exercise (in the active workout) to make current. */
    const val MSG_SELECT_EXERCISE = "/select-exercise"

    /** Payload: JSON array of {"t": epochMillis, "bpm": Int}. */
    const val MSG_HEART_RATE = "/heart-rate"

    /** Watch asks the phone to republish the current state DataItems. */
    const val MSG_REQUEST_STATE = "/request-state"

    val WATCH_TO_PHONE_PATHS = listOf(
        MSG_WORKOUT_STARTED,
        MSG_START_ROUTINE,
        MSG_COMPLETE_SET,
        MSG_SKIP_REST,
        MSG_ADJUST_REST,
        MSG_TOGGLE_PAUSE,
        MSG_FINISH_WORKOUT,
        MSG_DISCARD_WORKOUT,
        MSG_EDIT_SET,
        MSG_SELECT_EXERCISE,
        MSG_HEART_RATE,
        MSG_REQUEST_STATE
    )
}

/**
 * Snapshot of the phone's active workout, as rendered on the watch.
 * Timers are sent as absolute timestamps so the watch can tick locally without a message per second.
 */
data class WearWorkoutState(
    val active: Boolean = false,
    val title: String = "",
    val paused: Boolean = false,
    /** Elapsed workout seconds at [sentAtEpochMs]. */
    val elapsedSeconds: Long = 0L,
    val sentAtEpochMs: Long = 0L,
    val exerciseTitle: String = "",
    val exerciseIndex: Int = -1,
    val exerciseCount: Int = 0,
    /** e.g. "Set 2 of 4 · 102.5 kg × 6"; blank when every set is done. */
    val setLabel: String = "",
    val nextSetLabel: String = "",
    val completedSets: Int = 0,
    val totalSets: Int = 0,
    /** Epoch millis when the running rest timer ends; 0 when no rest timer is running. */
    val restEndsAtEpochMs: Long = 0L,
    /** Remaining rest seconds, used while [paused] (the timer is frozen). */
    val restRemainingSeconds: Int = 0,
    val restTargetSeconds: Int = 0,
    /** Index of the current set within the current exercise; -1 when every set is done. */
    val setIndex: Int = -1,
    /** Weight of the current set in the phone's display unit ([weightUnit]). */
    val weight: Double = 0.0,
    /** Size of one +/- weight step, in [weightUnit]. */
    val weightStep: Double = 2.5,
    val weightUnit: String = "kg",
    val reps: Int = 0,
    /** Every exercise of the active workout, in order. */
    val exercises: List<WearExerciseSummary> = emptyList()
) {
    fun elapsedSecondsAt(nowEpochMs: Long): Long {
        if (!active) return 0L
        if (paused) return elapsedSeconds
        return elapsedSeconds + ((nowEpochMs - sentAtEpochMs) / 1000L).coerceAtLeast(0L)
    }

    fun restRemainingSecondsAt(nowEpochMs: Long): Int {
        if (!active) return 0
        if (paused) return restRemainingSeconds
        if (restEndsAtEpochMs <= 0L) return 0
        return (((restEndsAtEpochMs - nowEpochMs) + 999L) / 1000L).coerceAtLeast(0L).toInt()
    }

    fun toJson(): String = JSONObject().apply {
        put("active", active)
        put("title", title)
        put("paused", paused)
        put("elapsedSeconds", elapsedSeconds)
        put("sentAtEpochMs", sentAtEpochMs)
        put("exerciseTitle", exerciseTitle)
        put("exerciseIndex", exerciseIndex)
        put("exerciseCount", exerciseCount)
        put("setLabel", setLabel)
        put("nextSetLabel", nextSetLabel)
        put("completedSets", completedSets)
        put("totalSets", totalSets)
        put("restEndsAtEpochMs", restEndsAtEpochMs)
        put("restRemainingSeconds", restRemainingSeconds)
        put("restTargetSeconds", restTargetSeconds)
        put("setIndex", setIndex)
        put("weight", weight)
        put("weightStep", weightStep)
        put("weightUnit", weightUnit)
        put("reps", reps)
        put(
            "exercises",
            JSONArray().also { arr ->
                exercises.forEach { ex ->
                    arr.put(
                        JSONObject().apply {
                            put("title", ex.title)
                            put("completedSets", ex.completedSets)
                            put("totalSets", ex.totalSets)
                        }
                    )
                }
            }
        )
    }.toString()

    companion object {
        fun fromJson(json: String?): WearWorkoutState {
            if (json.isNullOrBlank()) return WearWorkoutState()
            return runCatching {
                val o = JSONObject(json)
                WearWorkoutState(
                    active = o.optBoolean("active", false),
                    title = o.optString("title"),
                    paused = o.optBoolean("paused", false),
                    elapsedSeconds = o.optLong("elapsedSeconds", 0L),
                    sentAtEpochMs = o.optLong("sentAtEpochMs", 0L),
                    exerciseTitle = o.optString("exerciseTitle"),
                    exerciseIndex = o.optInt("exerciseIndex", -1),
                    exerciseCount = o.optInt("exerciseCount", 0),
                    setLabel = o.optString("setLabel"),
                    nextSetLabel = o.optString("nextSetLabel"),
                    completedSets = o.optInt("completedSets", 0),
                    totalSets = o.optInt("totalSets", 0),
                    restEndsAtEpochMs = o.optLong("restEndsAtEpochMs", 0L),
                    restRemainingSeconds = o.optInt("restRemainingSeconds", 0),
                    restTargetSeconds = o.optInt("restTargetSeconds", 0),
                    setIndex = o.optInt("setIndex", -1),
                    weight = o.optDouble("weight", 0.0),
                    weightStep = o.optDouble("weightStep", 2.5),
                    weightUnit = o.optString("weightUnit", "kg"),
                    reps = o.optInt("reps", 0),
                    exercises = (o.optJSONArray("exercises") ?: JSONArray()).let { arr ->
                        (0 until arr.length()).map { i ->
                            val e = arr.getJSONObject(i)
                            WearExerciseSummary(e.optString("title"), e.optInt("completedSets", 0), e.optInt("totalSets", 0))
                        }
                    }
                )
            }.getOrDefault(WearWorkoutState())
        }
    }
}

data class WearExerciseSummary(
    val title: String,
    val completedSets: Int,
    val totalSets: Int
)

/** A weight / reps change made on the watch for one set. */
data class WearSetEdit(
    val exerciseIndex: Int,
    val setIndex: Int,
    val weight: Double,
    val reps: Int
) {
    fun toJson(): String = JSONObject().apply {
        put("exerciseIndex", exerciseIndex)
        put("setIndex", setIndex)
        put("weight", weight)
        put("reps", reps)
    }.toString()

    companion object {
        fun fromJson(json: String?): WearSetEdit? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                WearSetEdit(o.getInt("exerciseIndex"), o.getInt("setIndex"), o.getDouble("weight"), o.getInt("reps"))
            }.getOrNull()
        }
    }
}

data class WearRoutineSummary(
    val id: String,
    val title: String,
    val exerciseCount: Int
) {
    companion object {
        fun listToJson(routines: List<WearRoutineSummary>): String {
            val arr = JSONArray()
            routines.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("title", r.title)
                        put("exerciseCount", r.exerciseCount)
                    }
                )
            }
            return arr.toString()
        }

        fun listFromJson(json: String?): List<WearRoutineSummary> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    WearRoutineSummary(o.optString("id"), o.optString("title"), o.optInt("exerciseCount", 0))
                }
            }.getOrDefault(emptyList())
        }
    }
}

data class WearHeartRateSample(val epochMs: Long, val bpm: Int) {
    companion object {
        fun listToJson(samples: List<WearHeartRateSample>): String {
            val arr = JSONArray()
            samples.forEach { s -> arr.put(JSONObject().apply { put("t", s.epochMs); put("bpm", s.bpm) }) }
            return arr.toString()
        }

        fun listFromJson(json: String?): List<WearHeartRateSample> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val bpm = o.optInt("bpm", 0)
                    if (bpm > 0) WearHeartRateSample(o.optLong("t"), bpm) else null
                }
            }.getOrDefault(emptyList())
        }
    }
}
