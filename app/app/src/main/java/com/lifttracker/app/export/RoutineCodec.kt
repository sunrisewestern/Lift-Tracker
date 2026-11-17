package com.lifttracker.app.export

import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.RepType
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.RoutineTemplateExercise
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutSetEntry
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** The app's exercise an imported name maps to, and how that exercise is tracked. */
data class ResolvedExercise(val id: String, val measure: ExerciseMeasure)

/** Maps an exercise from a file (its id may be blank or unknown) to an exercise in this app. */
fun interface ExerciseResolver {
    fun resolve(exerciseId: String, title: String): ResolvedExercise
}

/**
 * Routine files: JSON that can be saved, copied to the clipboard or shared, and imported back.
 *
 * ```
 * { "format": "lifttracker.routines", "version": 1, "weightUnit": "kg",
 *   "routines": [ { "title": "...", "folder": "", "notes": "",
 *     "exercises": [ { "title": "Squat (Barbell)", "measure": "weight_reps", "restSeconds": 180,
 *       "sets": [ { "type": "warmup", "weightKg": 60, "reps": 8 } ] } ] } ] }
 * ```
 *
 * Import also accepts a bare array of routines, a single routine object, and the full data export
 * (which carries the same `routines` array). Sets may give `weightLbs` instead of `weightKg`.
 */
object RoutineCodec {
    const val FORMAT = "lifttracker.routines"
    const val VERSION = 1

    fun routineToJson(routine: RoutineTemplate): JSONObject {
        val exercises = JSONArray()
        routine.exercises.forEach { ex ->
            val sets = JSONArray()
            ex.sets.forEachIndexed { index, s ->
                sets.put(
                    JSONObject().apply {
                        put("index", index + 1)
                        put("type", s.setType.name.lowercase(Locale.US))
                        put("repType", s.repType.name.lowercase(Locale.US))
                        put("weightKg", round3(s.weightKg))
                        put("reps", s.reps)
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
                    put("restSeconds", ex.restSeconds)
                    put("targetSets", ex.targetSets)
                    put("targetReps", ex.targetReps)
                    put("targetWeightKg", round3(ex.targetWeightKg))
                    put("repType", ex.repType.name.lowercase(Locale.US))
                    put("sets", sets)
                }
            )
        }
        return JSONObject().apply {
            put("id", routine.id)
            put("title", routine.title)
            put("folder", routine.folderName)
            put("notes", routine.notes)
            put("exercises", exercises)
        }
    }

    fun exportRoutines(routines: List<RoutineTemplate>): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("weightUnit", "kg")
        root.put("routines", JSONArray().apply { routines.forEach { put(routineToJson(it)) } })
        return root.toString(2)
    }

    fun suggestedFileName(routines: List<RoutineTemplate>): String {
        val single = routines.singleOrNull() ?: return "lift-tracker-routines.json"
        val slug = single.title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        return "routine-${slug.ifEmpty { "export" }}.json"
    }

    /**
     * Parses routine JSON. Ids from the file are kept (callers decide whether to re-id them).
     * @throws IllegalArgumentException when the text is not routine JSON or holds no routines.
     */
    fun parseRoutines(text: String, resolver: ExerciseResolver, defaultRestSeconds: Int = 90): List<RoutineTemplate> {
        val trimmed = text.trim().removePrefix("﻿").trim()
        val array: JSONArray = try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("routines")
                        ?: if (obj.has("exercises")) JSONArray().put(obj) else null
                        ?: throw IllegalArgumentException("No routines found in this JSON.")
                }
                else -> throw IllegalArgumentException("This is not routine JSON.")
            }
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("Invalid JSON: ${e.message}")
        }
        val routines = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { parseRoutine(it, resolver, defaultRestSeconds) }
        }
        require(routines.isNotEmpty()) { "No routines found in this JSON." }
        return routines
    }

    private fun parseRoutine(obj: JSONObject, resolver: ExerciseResolver, defaultRest: Int): RoutineTemplate? {
        val title = obj.optString("title").trim()
        val exArray = obj.optJSONArray("exercises") ?: return null
        if (title.isEmpty()) return null
        val exercises = (0 until exArray.length()).mapNotNull { i ->
            val ex = exArray.optJSONObject(i) ?: return@mapNotNull null
            val exTitle = ex.optString("title").ifBlank { ex.optString("exerciseTitle") }.trim()
            if (exTitle.isEmpty()) return@mapNotNull null
            val resolved = resolver.resolve(ex.optString("exerciseId"), exTitle)
            val measure = ExerciseMeasure.fromCode(ex.optString("measure")) ?: resolved.measure
            val exRepType = enumOf<RepType>(ex.optString("repType")) ?: RepType.REPS
            val targetReps = ex.optInt("targetReps", 8)
            val targetWeight = ex.optDouble("targetWeightKg", 0.0)
            val setsArray = ex.optJSONArray("sets")
            val sets = if (setsArray != null && setsArray.length() > 0) {
                (0 until setsArray.length()).mapNotNull { j ->
                    val s = setsArray.optJSONObject(j) ?: return@mapNotNull null
                    val weightKg = when {
                        s.has("weightKg") -> s.optDouble("weightKg", 0.0)
                        s.has("weightLbs") -> s.optDouble("weightLbs", 0.0) / WeightUnit.LBS.factorFromKg
                        else -> 0.0
                    }
                    WorkoutSetEntry(
                        id = "rt_s_${i}_${j + 1}",
                        setNumber = j + 1,
                        setType = enumOf<SetType>(s.optString("type")) ?: SetType.NORMAL,
                        weightKg = weightKg.coerceAtLeast(0.0),
                        reps = s.optInt("reps", if (measure.usesReps) targetReps else 0).coerceAtLeast(0),
                        repType = enumOf<RepType>(s.optString("repType")) ?: exRepType,
                        durationSeconds = s.optInt("durationSeconds", 0).coerceAtLeast(0),
                        distanceMeters = s.optDouble("distanceMeters", 0.0).coerceAtLeast(0.0)
                    )
                }
            } else {
                (1..ex.optInt("targetSets", 3).coerceIn(1, 30)).map { n ->
                    WorkoutSetEntry(
                        id = "rt_s_${i}_$n", setNumber = n, weightKg = targetWeight.coerceAtLeast(0.0),
                        reps = targetReps.coerceAtLeast(0), repType = exRepType
                    )
                }
            }
            if (sets.isEmpty()) return@mapNotNull null
            val ref = sets.firstOrNull { it.setType != SetType.WARMUP } ?: sets.first()
            RoutineTemplateExercise(
                exerciseId = resolved.id,
                exerciseTitle = exTitle,
                targetSets = sets.size,
                targetReps = ref.reps,
                targetWeightKg = ref.weightKg,
                restSeconds = ex.optInt("restSeconds", defaultRest).coerceIn(0, 3600),
                repType = ref.repType,
                sets = sets,
                measure = measure
            )
        }
        return RoutineTemplate(
            id = obj.optString("id"),
            folderName = obj.optString("folder").trim(),
            title = title,
            notes = obj.optString("notes"),
            exercises = exercises
        )
    }

    private inline fun <reified T : Enum<T>> enumOf(value: String?): T? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        return enumValues<T>().find { it.name.equals(v, ignoreCase = true) }
    }

    private fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0
}
