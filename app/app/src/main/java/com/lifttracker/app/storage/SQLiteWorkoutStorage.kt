package com.lifttracker.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.HeartRateSamplePoint
import com.lifttracker.app.domain.RepType
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * SQLite storage for finished workouts:
 * - Directory: context.getDir("BigStorage", Context.MODE_PRIVATE)
 * - Database: workouts.sqlite opened with flag 0x10000000 (CREATE_IF_NECESSARY)
 * - Schema: CREATE TABLE IF NOT EXISTS workouts (id TEXT PRIMARY KEY, end_time INTEGER, json TEXT)
 * - Upsert: insertWithOnConflict("workouts", null, values, 5) where 5 = CONFLICT_REPLACE
 */
class SQLiteWorkoutStorage(private val context: Context) {

    data class StoredWorkoutRow(
        val id: String,
        val endTime: Long,
        val json: String
    )

    private fun openDatabase(): SQLiteDatabase {
        val dir: File = context.getDir(WorkoutMechanics.SQLITE_STORAGE_DIR, Context.MODE_PRIVATE)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val dbFile = File(dir, WorkoutMechanics.SQLITE_DB_NAME)
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            WorkoutMechanics.SQLITE_OPEN_CREATE_IF_NECESSARY
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS workouts (id TEXT PRIMARY KEY, end_time INTEGER, json TEXT)")
        return db
    }

    fun storeWorkouts(rows: List<StoredWorkoutRow>) {
        val db = openDatabase()
        db.beginTransaction()
        try {
            for (row in rows) {
                val values = ContentValues().apply {
                    put("id", row.id)
                    put("end_time", row.endTime)
                    put("json", row.json)
                }
                db.insertWithOnConflict(
                    "workouts",
                    null,
                    values,
                    WorkoutMechanics.SQLITE_CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun fetchWorkouts(): List<StoredWorkoutRow> {
        val db = openDatabase()
        val result = mutableListOf<StoredWorkoutRow>()
        try {
            val cursor = db.rawQuery(
                "SELECT id, end_time, json FROM workouts ORDER BY end_time DESC",
                null
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndexOrThrow("id")
                val endIdx = c.getColumnIndexOrThrow("end_time")
                val jsonIdx = c.getColumnIndexOrThrow("json")
                while (c.moveToNext()) {
                    result.add(
                        StoredWorkoutRow(
                            id = c.getString(idIdx),
                            endTime = c.getLong(endIdx),
                            json = c.getString(jsonIdx)
                        )
                    )
                }
            }
        } finally {
            db.close()
        }
        return result
    }

    fun deleteWorkouts(ids: List<String>) {
        val db = openDatabase()
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete("workouts", "id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun clearWorkouts() {
        val db = openDatabase()
        try {
            db.delete("workouts", null, null)
        } finally {
            db.close()
        }
    }

    fun saveWorkoutSessions(sessions: List<WorkoutSession>) {
        storeWorkouts(sessions.map { serializeWorkoutSession(it) })
    }

    fun loadWorkoutSessions(): List<WorkoutSession> {
        return fetchWorkouts().mapNotNull { deserializeWorkoutSession(it) }
    }

    companion object {
        fun serializeWorkoutSession(session: WorkoutSession): StoredWorkoutRow {
            val exercisesArray = JSONArray()
            for (ex in session.exercises) {
                val setsArray = JSONArray()
                for (s in ex.sets) {
                    val sObj = JSONObject().apply {
                        put("id", s.id)
                        put("setNumber", s.setNumber)
                        put("setType", s.setType.name)
                        put("repType", s.repType.name)
                        put("previousText", s.previousText)
                        put("weightKg", s.weightKg)
                        put("reps", s.reps)
                        if (s.rpe != null) put("rpe", s.rpe)
                        put("completed", s.completed)
                        if (s.durationSeconds > 0) put("durationSeconds", s.durationSeconds)
                        if (s.distanceMeters > 0.0) put("distanceMeters", s.distanceMeters)
                    }
                    setsArray.put(sObj)
                }
                val exObj = JSONObject().apply {
                    put("id", ex.id)
                    put("exerciseId", ex.exerciseId)
                    put("exerciseTitle", ex.exerciseTitle)
                    put("notes", ex.notes)
                    put("restTimerSeconds", ex.restTimerSeconds)
                    put("measure", ex.measure.code)
                    put("sets", setsArray)
                }
                exercisesArray.put(exObj)
            }
            val root = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("startTimeEpochSeconds", session.startTimeEpochSeconds)
                put("endTimeEpochSeconds", session.endTimeEpochSeconds)
                put("notes", session.notes)
                put("averageHeartRateBpm", session.averageHeartRateBpm)
                put("caloriesBurnedKcal", session.caloriesBurnedKcal)
                put("syncedToHealthConnect", session.syncedToHealthConnect)
                put("archived", session.archived)
                if (session.heartRateSamples.isNotEmpty()) {
                    val hrArray = JSONArray()
                    for (sample in session.heartRateSamples) {
                        hrArray.put(JSONArray().put(sample.heartRateTimestampMs).put(sample.heartRate))
                    }
                    put("heartRateSamples", hrArray)
                }
                put("exercises", exercisesArray)
            }
            return StoredWorkoutRow(
                id = session.id,
                endTime = session.endTimeEpochSeconds,
                json = root.toString()
            )
        }

        fun deserializeWorkoutSession(row: StoredWorkoutRow): WorkoutSession? {
            return runCatching {
                val root = JSONObject(row.json)
                val exArray = root.optJSONArray("exercises") ?: JSONArray()
                val exercises = mutableListOf<WorkoutExerciseEntry>()
                for (i in 0 until exArray.length()) {
                    val exObj = exArray.getJSONObject(i)
                    val setsArray = exObj.optJSONArray("sets") ?: JSONArray()
                    val sets = mutableListOf<WorkoutSetEntry>()
                    for (j in 0 until setsArray.length()) {
                        val sObj = setsArray.getJSONObject(j)
                        val setType = SetType.entries.find { it.name == sObj.optString("setType") } ?: SetType.NORMAL
                        val repType = RepType.entries.find { it.name == sObj.optString("repType") } ?: RepType.REPS
                        val rpeVal = if (sObj.has("rpe") && !sObj.isNull("rpe")) sObj.optDouble("rpe") else null
                        sets.add(
                            WorkoutSetEntry(
                                id = sObj.optString("id"),
                                setNumber = sObj.optInt("setNumber", j + 1),
                                setType = setType,
                                repType = repType,
                                previousText = sObj.optString("previousText", "—"),
                                weightKg = sObj.optDouble("weightKg", 0.0),
                                reps = sObj.optInt("reps", 0),
                                rpe = rpeVal,
                                completed = sObj.optBoolean("completed", false),
                                durationSeconds = sObj.optInt("durationSeconds", 0),
                                distanceMeters = sObj.optDouble("distanceMeters", 0.0)
                            )
                        )
                    }
                    exercises.add(
                        WorkoutExerciseEntry(
                            id = exObj.optString("id"),
                            exerciseId = exObj.optString("exerciseId"),
                            exerciseTitle = exObj.optString("exerciseTitle"),
                            notes = exObj.optString("notes"),
                            restTimerSeconds = exObj.optInt("restTimerSeconds", 90),
                            sets = sets,
                            measure = ExerciseMeasure.fromCode(exObj.optString("measure")) ?: ExerciseMeasure.WEIGHT_REPS
                        )
                    )
                }
                val hrArray = root.optJSONArray("heartRateSamples") ?: JSONArray()
                val heartRateSamples = (0 until hrArray.length()).mapNotNull { i ->
                    val pair = hrArray.optJSONArray(i) ?: return@mapNotNull null
                    HeartRateSamplePoint(pair.optLong(0), pair.optDouble(1, 0.0))
                }
                WorkoutSession(
                    id = root.optString("id", row.id),
                    title = root.optString("title", "Workout Session"),
                    startTimeEpochSeconds = root.optLong("startTimeEpochSeconds", row.endTime),
                    endTimeEpochSeconds = root.optLong("endTimeEpochSeconds", row.endTime),
                    notes = root.optString("notes"),
                    exercises = exercises,
                    averageHeartRateBpm = root.optInt("averageHeartRateBpm", 0),
                    caloriesBurnedKcal = root.optDouble("caloriesBurnedKcal", 0.0),
                    syncedToHealthConnect = root.optBoolean("syncedToHealthConnect", false),
                    archived = root.optBoolean("archived", false),
                    heartRateSamples = heartRateSamples
                )
            }.getOrNull()
        }
    }
}
