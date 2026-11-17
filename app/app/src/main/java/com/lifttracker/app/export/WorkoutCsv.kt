package com.lifttracker.app.export

import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.RepType
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.abs

/**
 * Workout history as CSV, one row per set, in the widely used strength-log layout:
 *
 * `title, start_time, end_time, description, exercise_title, superset_id, exercise_notes, set_index,
 *  set_type, weight_kg | weight_lbs, reps, distance_km, duration_seconds, rpe`
 *
 * Times are local, written like `Nov 13, 2026, 7:20 PM`. [importWorkouts] reads this layout and
 * also the app's own detailed CSV ([WorkoutExporter.exportWorkoutsCsv]).
 */
object WorkoutCsv {

    data class ImportResult(
        val sessions: List<WorkoutSession>,
        /** How each imported exercise is tracked, worked out from the columns its sets fill in. */
        val measuresByExerciseId: Map<String, ExerciseMeasure>,
        val skippedRows: Int
    )

    private val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)
    private val TIME_PARSERS: List<DateTimeFormatter> = listOf(
        "MMM d, yyyy, h:mm a", "d MMM yyyy, HH:mm", "MMM d, yyyy, HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.US) }

    fun hevyColumns(unit: WeightUnit): List<String> = listOf(
        "title", "start_time", "end_time", "description", "exercise_title", "superset_id", "exercise_notes",
        "set_index", "set_type", "weight_${unit.label}", "reps", "distance_km", "duration_seconds", "rpe"
    )

    /** Completed sets only (sets that were planned but never done are not history). */
    fun exportWorkouts(
        workouts: List<WorkoutSession>,
        unit: WeightUnit,
        zone: ZoneId = ZoneId.systemDefault(),
        includeArchived: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.append(hevyColumns(unit).joinToString(",") { "\"$it\"" }).append('\n')
        workouts.filter { includeArchived || !it.archived }
            .sortedByDescending { it.endTimeEpochSeconds }
            .forEach { w ->
                val start = displayTime(w.startTimeEpochSeconds, zone)
                val end = displayTime(w.endTimeEpochSeconds, zone)
                w.exercises.forEach { ex ->
                    ex.sets.filter { it.completed }.forEachIndexed { index, s ->
                        val legacySeconds = ex.measure == ExerciseMeasure.WEIGHT_REPS && s.repType == RepType.SECONDS
                        val weight = if (ex.measure.usesWeight && s.weightKg > 0.0) num(s.weightKg * unit.factorFromKg) else ""
                        val reps = if (ex.measure.usesReps && !legacySeconds) s.reps.toString() else ""
                        val distance = if (ex.measure == ExerciseMeasure.WEIGHT_DISTANCE) num(s.distanceMeters / 1000.0, 4) else ""
                        val duration = when {
                            ex.measure == ExerciseMeasure.DURATION -> s.durationSeconds.toString()
                            legacySeconds -> s.reps.toString()
                            else -> ""
                        }
                        val cells = listOf(
                            q(w.title), q(start), q(end), q(w.notes), q(ex.exerciseTitle), "", q(ex.notes),
                            index.toString(), q(s.setType.name.lowercase(Locale.US)), weight, reps, distance, duration,
                            s.rpe?.let { num(it) }.orEmpty()
                        )
                        sb.append(cells.joinToString(",")).append('\n')
                    }
                }
            }
        return sb.toString()
    }

    /**
     * @throws IllegalArgumentException when the text has no recognisable workout columns.
     */
    fun importWorkouts(
        text: String,
        resolver: ExerciseResolver,
        defaultRestSeconds: Int = 90,
        zone: ZoneId = ZoneId.systemDefault()
    ): ImportResult {
        val rows = parseCsv(text)
        require(rows.size >= 2) { "The file has no workout rows." }
        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        fun col(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1

        val ownFormat = col("workout_id") >= 0
        val cId = col("workout_id")
        val cTitle = col("title", "workout_title")
        val cStart = col("start_time")
        val cEnd = col("end_time")
        val cNotes = col("description", "workout_notes")
        val cExId = col("exercise_id")
        val cExTitle = col("exercise_title")
        val cExNotes = col("exercise_notes")
        val cSetIndex = col("set_index")
        val cSetType = col("set_type")
        val cWeightKg = col("weight_kg")
        val cWeightLbs = col("weight_lbs")
        val cReps = col("reps")
        val cRepType = col("rep_type")
        val cDistKm = col("distance_km")
        val cDistM = col("distance_m", "distance_meters")
        val cDistMi = col("distance_miles")
        val cDuration = if (ownFormat) col("set_duration_seconds") else col("duration_seconds")
        val cRpe = col("rpe")
        val cCompleted = col("completed")
        val cMeasure = col("exercise_measure")
        require(cExTitle >= 0 && cStart >= 0 && cTitle >= 0) {
            "Not a workout CSV: expected title, start_time and exercise_title columns."
        }

        class RawSet(val row: List<String>, val weightKg: Double, val reps: Int, val meters: Double, val seconds: Int)
        class RawExercise(val title: String, val id: String, val notes: String, val sets: MutableList<RawSet> = mutableListOf())
        class RawWorkout(
            val id: String, val title: String, val start: Long, val end: Long, val notes: String,
            val exercises: MutableList<RawExercise> = mutableListOf()
        )

        fun cell(row: List<String>, index: Int): String = if (index in row.indices) row[index].trim() else ""
        fun number(row: List<String>, index: Int): Double = cell(row, index).replace(",", ".").toDoubleOrNull() ?: 0.0

        val workouts = LinkedHashMap<String, RawWorkout>()
        var skipped = 0
        var lastSetIndex = -1
        for (row in rows.drop(1)) {
            if (row.all { it.isBlank() }) continue
            val exTitle = cell(row, cExTitle)
            val start = parseTime(cell(row, cStart), zone)
            if (exTitle.isEmpty() || start == null) {
                skipped++
                continue
            }
            val title = cell(row, cTitle).ifEmpty { "Workout" }
            val key = cell(row, cId).ifEmpty { "$title|$start" }
            val workout = workouts.getOrPut(key) {
                lastSetIndex = -1
                val end = parseTime(cell(row, cEnd), zone)?.takeIf { it >= start } ?: (start + 3600L)
                val id = cell(row, cId).ifEmpty { "imp_${start}_${abs(title.hashCode()).toString(36)}" }
                RawWorkout(id, title, start, end, cell(row, cNotes))
            }
            val setIndex = cell(row, cSetIndex).toIntOrNull() ?: (lastSetIndex + 1)
            val current = workout.exercises.lastOrNull()
            val exercise = if (current != null && current.title == exTitle && setIndex > lastSetIndex) {
                current
            } else {
                RawExercise(exTitle, cell(row, cExId), cell(row, cExNotes)).also { workout.exercises.add(it) }
            }
            lastSetIndex = setIndex
            val weightKg = when {
                cWeightKg >= 0 -> number(row, cWeightKg)
                cWeightLbs >= 0 -> number(row, cWeightLbs) / WeightUnit.LBS.factorFromKg
                else -> 0.0
            }
            val meters = when {
                cDistKm >= 0 && cell(row, cDistKm).isNotEmpty() -> number(row, cDistKm) * 1000.0
                cDistM >= 0 && cell(row, cDistM).isNotEmpty() -> number(row, cDistM)
                cDistMi >= 0 && cell(row, cDistMi).isNotEmpty() -> number(row, cDistMi) * 1609.344
                else -> 0.0
            }
            exercise.sets.add(
                RawSet(row, weightKg.coerceAtLeast(0.0), number(row, cReps).toInt().coerceAtLeast(0),
                    Math.round(meters * 10.0) / 10.0, number(row, cDuration).toInt().coerceAtLeast(0))
            )
        }
        require(workouts.isNotEmpty()) { "No workouts could be read from this file." }

        // How each exercise is tracked: an explicit column wins, otherwise look at which columns its sets use.
        val allExercises = workouts.values.flatMap { it.exercises }
        val measureByTitle = allExercises.groupBy { it.title }.mapValues { (_, blocks) ->
            val sets = blocks.flatMap { it.sets }
            val explicit = sets.firstNotNullOfOrNull { ExerciseMeasure.fromCode(cell(it.row, cMeasure)) }
            explicit ?: when {
                sets.any { it.meters > 0.0 } -> ExerciseMeasure.WEIGHT_DISTANCE
                sets.any { it.seconds > 0 } && sets.none { it.reps > 0 } -> ExerciseMeasure.DURATION
                sets.count { it.weightKg > 0.0 } * 2 < sets.size -> ExerciseMeasure.BODYWEIGHT_REPS
                else -> ExerciseMeasure.WEIGHT_REPS
            }
        }
        val resolvedByTitle = HashMap<String, ResolvedExercise>()
        val measuresById = LinkedHashMap<String, ExerciseMeasure>()

        val sessions = workouts.values.map { w ->
            val exercises = w.exercises.mapIndexed { exIdx, ex ->
                val resolved = resolvedByTitle.getOrPut(ex.title) { resolver.resolve(ex.id, ex.title) }
                val measure = measureByTitle.getValue(ex.title)
                measuresById[resolved.id] = measure
                WorkoutExerciseEntry(
                    id = "${w.id}_e$exIdx",
                    exerciseId = resolved.id,
                    exerciseTitle = ex.title,
                    notes = ex.notes,
                    restTimerSeconds = defaultRestSeconds,
                    measure = measure,
                    sets = ex.sets.mapIndexed { setIdx, s ->
                        val legacySeconds = cell(s.row, cRepType).equals("seconds", ignoreCase = true)
                        WorkoutSetEntry(
                            id = "${w.id}_e${exIdx}_s$setIdx",
                            setNumber = setIdx + 1,
                            setType = setTypeOf(cell(s.row, cSetType)),
                            weightKg = s.weightKg,
                            reps = s.reps,
                            rpe = cell(s.row, cRpe).toDoubleOrNull()?.coerceIn(1.0, 10.0),
                            completed = cCompleted < 0 || !cell(s.row, cCompleted).equals("false", ignoreCase = true),
                            repType = if (legacySeconds) RepType.SECONDS else RepType.REPS,
                            durationSeconds = s.seconds,
                            distanceMeters = s.meters
                        )
                    }
                )
            }
            val workingSets = exercises.sumOf { ex -> ex.sets.count { it.completed && it.setType != SetType.WARMUP } }
            WorkoutSession(
                id = w.id,
                title = w.title,
                startTimeEpochSeconds = w.start,
                endTimeEpochSeconds = w.end,
                notes = w.notes,
                exercises = exercises,
                caloriesBurnedKcal = WorkoutMechanics.estimateWorkoutCalories(w.end - w.start, workingSets)
            )
        }
        return ImportResult(sessions, measuresById, skipped)
    }

    private fun setTypeOf(value: String): SetType = when (value.lowercase(Locale.US)) {
        "warmup", "warm_up", "warm-up", "w" -> SetType.WARMUP
        "failure", "f" -> SetType.FAILURE
        "dropset", "drop_set", "drop", "d" -> SetType.DROPSET
        else -> SetType.NORMAL
    }

    internal fun parseTime(value: String, zone: ZoneId): Long? {
        // Some exports put a narrow no-break space before AM/PM.
        val clean = value.replace(' ', ' ').replace(' ', ' ').trim()
        if (clean.isEmpty()) return null
        runCatching { return Instant.parse(clean).epochSecond }
        for (parser in TIME_PARSERS) {
            runCatching { return LocalDateTime.parse(clean, parser).atZone(zone).toEpochSecond() }
        }
        return null
    }

    private fun displayTime(epochSeconds: Long, zone: ZoneId): String =
        DISPLAY_TIME.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    private fun q(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun num(value: Double, decimals: Int = 2): String {
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    /** RFC 4180 parser: quoted fields, doubled quotes, commas and line breaks inside quotes, CRLF or LF. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = if (text.startsWith("﻿")) 1 else 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field.setLength(0)
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
