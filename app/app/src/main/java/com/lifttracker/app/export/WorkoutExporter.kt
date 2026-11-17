package com.lifttracker.app.export

import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSession
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ExportFormat(val mimeType: String, val extension: String) {
    JSON("application/json", "json"),
    CSV("text/csv", "csv")
}

/**
 * Data export API. Produces a stable, versioned representation of the user's data:
 *
 * - JSON ([exportJson]): one document with `workouts` and `routines`, weights in kilograms,
 *   times as ISO-8601 UTC plus epoch seconds.
 * - CSV ([exportWorkoutsCsv]): one row per set, suitable for spreadsheets.
 *
 * The same output is served to other apps by [ExportProvider].
 */
object WorkoutExporter {
    const val SCHEMA_VERSION = 1

    val CSV_COLUMNS = listOf(
        "workout_id", "workout_title", "start_time", "end_time", "duration_seconds", "workout_notes",
        "exercise_index", "exercise_id", "exercise_title", "exercise_notes",
        "set_index", "set_type", "rep_type", "weight_kg", "reps", "rpe", "completed", "estimated_1rm_kg",
        "exercise_measure", "set_duration_seconds", "distance_m"
    )

    fun exportJson(
        workouts: List<WorkoutSession>,
        routines: List<RoutineTemplate> = emptyList(),
        exportedAt: Instant = Instant.now(),
        includeArchived: Boolean = true
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("app", "Lift Tracker")
        root.put("exportedAt", iso(exportedAt.epochSecond))
        root.put("weightUnit", "kg")
        val workoutArray = JSONArray()
        workouts.filter { includeArchived || !it.archived }
            .sortedByDescending { it.endTimeEpochSeconds }
            .forEach { workoutArray.put(workoutToJson(it)) }
        root.put("workouts", workoutArray)
        val routineArray = JSONArray()
        routines.forEach { routineArray.put(routineToJson(it)) }
        root.put("routines", routineArray)
        return root.toString(2)
    }

    fun workoutToJson(session: WorkoutSession): JSONObject {
        val exercises = JSONArray()
        session.exercises.forEach { ex ->
            val sets = JSONArray()
            ex.sets.forEachIndexed { index, s ->
                sets.put(
                    JSONObject().apply {
                        put("index", index + 1)
                        put("type", s.setType.name.lowercase(Locale.US))
                        put("repType", s.repType.name.lowercase(Locale.US))
                        put("weightKg", s.weightKg)
                        put("reps", s.reps)
                        put("rpe", s.rpe ?: JSONObject.NULL)
                        put("completed", s.completed)
                        if (s.durationSeconds > 0) put("durationSeconds", s.durationSeconds)
                        if (s.distanceMeters > 0.0) put("distanceMeters", s.distanceMeters)
                    }
                )
            }
            exercises.put(
                JSONObject().apply {
                    put("exerciseId", ex.exerciseId)
                    put("title", ex.exerciseTitle)
                    put("measure", ex.measure.code)
                    put("notes", ex.notes)
                    put("restSeconds", ex.restTimerSeconds)
                    put("sets", sets)
                }
            )
        }
        val heartRate = JSONArray()
        session.heartRateSamples.forEach { sample ->
            heartRate.put(
                JSONObject().apply {
                    put("time", Instant.ofEpochMilli(sample.heartRateTimestampMs).toString())
                    put("bpm", sample.heartRate.toInt())
                }
            )
        }
        return JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("notes", session.notes)
            put("startTime", iso(session.startTimeEpochSeconds))
            put("endTime", iso(session.endTimeEpochSeconds))
            put("startTimeEpochSeconds", session.startTimeEpochSeconds)
            put("endTimeEpochSeconds", session.endTimeEpochSeconds)
            put("durationSeconds", session.durationSeconds)
            put("totalVolumeKg", session.totalVolumeKg)
            put("completedSets", session.completedSetsCount)
            put("caloriesKcal", session.caloriesBurnedKcal)
            put("averageHeartRateBpm", if (session.averageHeartRateBpm > 0) session.averageHeartRateBpm else JSONObject.NULL)
            put("archived", session.archived)
            put("syncedToHealthConnect", session.syncedToHealthConnect)
            put("exercises", exercises)
            put("heartRateSamples", heartRate)
        }
    }

    private fun routineToJson(routine: RoutineTemplate): JSONObject = RoutineCodec.routineToJson(routine)

    /** RFC 4180 CSV, one row per set, header row first, CRLF line endings. */
    fun exportWorkoutsCsv(workouts: List<WorkoutSession>, includeArchived: Boolean = true): String {
        val sb = StringBuilder()
        sb.append(CSV_COLUMNS.joinToString(",")).append("\r\n")
        workouts.filter { includeArchived || !it.archived }
            .sortedByDescending { it.endTimeEpochSeconds }
            .forEach { w ->
                w.exercises.forEachIndexed { exIndex, ex ->
                    ex.sets.forEachIndexed { setIndex, s ->
                        val oneRm = if (s.completed) WorkoutMechanics.estimateOneRepMax(s.weightKg, s.reps) else 0.0
                        val row = listOf(
                            w.id, w.title, iso(w.startTimeEpochSeconds), iso(w.endTimeEpochSeconds),
                            w.durationSeconds.toString(), w.notes,
                            (exIndex + 1).toString(), ex.exerciseId, ex.exerciseTitle, ex.notes,
                            (setIndex + 1).toString(), s.setType.name.lowercase(Locale.US),
                            s.repType.name.lowercase(Locale.US), number(s.weightKg), s.reps.toString(),
                            s.rpe?.let { number(it) }.orEmpty(), s.completed.toString(), number(oneRm),
                            ex.measure.code, s.durationSeconds.toString(), number(s.distanceMeters)
                        )
                        sb.append(row.joinToString(",") { csvField(it) }).append("\r\n")
                    }
                }
            }
        return sb.toString()
    }

    fun export(format: ExportFormat, workouts: List<WorkoutSession>, routines: List<RoutineTemplate>): String =
        when (format) {
            ExportFormat.JSON -> exportJson(workouts, routines)
            ExportFormat.CSV -> exportWorkoutsCsv(workouts)
        }

    fun suggestedFileName(format: ExportFormat, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = DateTimeFormatter.ISO_LOCAL_DATE.format(now.atZone(zone))
        return "lift-tracker-export-$day.${format.extension}"
    }

    internal fun csvField(value: String): String {
        // Neutralise spreadsheet formula injection from user-entered text (titles, notes).
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r" && value.toDoubleOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private fun number(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

    private fun iso(epochSeconds: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC))
}
