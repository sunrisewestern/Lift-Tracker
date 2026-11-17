package com.lifttracker.app

import com.lifttracker.app.domain.ExerciseMatching
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.export.ExerciseResolver
import com.lifttracker.app.export.ResolvedExercise
import com.lifttracker.app.export.RoutineCodec
import com.lifttracker.app.export.WorkoutCsv
import com.lifttracker.app.storage.SQLiteWorkoutStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

class ImportExportTest {
    private val zone = ZoneId.of("America/Vancouver")
    private val resolver = ExerciseResolver { _, title ->
        ResolvedExercise(ExerciseMatching.customId(title), ExerciseMeasure.infer(title))
    }

    private val Q = "\""

    private val csv = """
        "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_lbs","reps","distance_km","duration_seconds","rpe"
        "Upper Pull","Nov 11, 2026, 5:01 PM","Nov 11, 2026, 6:01 PM","","Pull Up",,"",0,"warmup",,4,,,
        "Upper Pull","Nov 11, 2026, 5:01 PM","Nov 11, 2026, 6:01 PM","","Pull Up",,"",1,"normal",,3,,,
        "Upper Pull","Nov 11, 2026, 5:01 PM","Nov 11, 2026, 6:01 PM","","Lat Pulldown (Machine)",,"note, with comma",0,"normal",120,12,,,8.5
        "Carry day","Nov 9, 2026, 5:15 PM","Nov 9, 2026, 6:15 PM","felt ${Q}${Q}good${Q}${Q}","Farmers Walk",,"",0,"normal",100,,0.03,,
        "Carry day","Nov 9, 2026, 5:15 PM","Nov 9, 2026, 6:15 PM","felt ${Q}${Q}good${Q}${Q}","Dead Hang",,"",0,"normal",,,,45,
    """.trimIndent()

    @Test
    fun infersHowExercisesAreTracked() {
        assertEquals(ExerciseMeasure.WEIGHT_DISTANCE, ExerciseMeasure.infer("Farmers Walk"))
        assertEquals(ExerciseMeasure.DURATION, ExerciseMeasure.infer("Dead Hang"))
        assertEquals(ExerciseMeasure.BODYWEIGHT_REPS, ExerciseMeasure.infer("Chest Dip"))
        assertEquals(ExerciseMeasure.BODYWEIGHT_REPS, ExerciseMeasure.infer("Hanging Leg Raise"))
        assertEquals(ExerciseMeasure.WEIGHT_REPS, ExerciseMeasure.infer("Pull Up (Assisted)"))
        assertEquals(ExerciseMeasure.WEIGHT_REPS, ExerciseMeasure.infer("Crunch (Machine)"))
        assertEquals(ExerciseMeasure.WEIGHT_REPS, ExerciseMeasure.infer("Hang Clean (Barbell)"))
        assertEquals(ExerciseMatching.nameKey("Bench Press (Barbell)"), ExerciseMatching.nameKey("Barbell Bench Press"))
        assertEquals(ExerciseMatching.nameKey("Pull Up"), ExerciseMatching.nameKey("Pull-up"))
    }

    @Test
    fun importsWorkoutHistoryCsv() {
        val result = WorkoutCsv.importWorkouts(csv, resolver, 90, zone)
        assertEquals(2, result.sessions.size)
        val pull = result.sessions[0]
        assertEquals("Upper Pull", pull.title)
        assertEquals(3600L, pull.durationSeconds)
        assertEquals(2, pull.exercises.size)
        assertEquals(ExerciseMeasure.BODYWEIGHT_REPS, pull.exercises[0].measure)
        assertEquals(SetType.WARMUP, pull.exercises[0].sets[0].setType)
        assertEquals(0.0, pull.exercises[0].sets[0].weightKg, 0.0)
        val lat = pull.exercises[1]
        assertEquals("note, with comma", lat.notes)
        assertEquals(120.0, lat.sets[0].weightKg * WeightUnit.LBS.factorFromKg, 1e-6)
        assertEquals(8.5, lat.sets[0].rpe!!, 0.0)
        assertTrue(lat.sets[0].completed)

        val carry = result.sessions[1]
        assertEquals("felt \"good\"", carry.notes)
        assertEquals(ExerciseMeasure.WEIGHT_DISTANCE, carry.exercises[0].measure)
        assertEquals(30.0, carry.exercises[0].sets[0].distanceMeters, 1e-9)
        assertEquals(ExerciseMeasure.DURATION, carry.exercises[1].measure)
        assertEquals(45, carry.exercises[1].sets[0].durationSeconds)

        // Importing twice gives the same ids, so nothing is duplicated.
        assertEquals(result.sessions.map { it.id }, WorkoutCsv.importWorkouts(csv, resolver, 90, zone).sessions.map { it.id })
    }

    @Test
    fun historyCsvAndStorageRoundTrip() {
        val first = WorkoutCsv.importWorkouts(csv, resolver, 90, zone).sessions
        val exported = WorkoutCsv.exportWorkouts(first, WeightUnit.LBS, zone)
        assertTrue(exported.lines().first().contains("\"weight_lbs\""))
        val second = WorkoutCsv.importWorkouts(exported, resolver, 90, zone).sessions
        assertEquals(first, second)

        val stored = first.map { SQLiteWorkoutStorage.deserializeWorkoutSession(SQLiteWorkoutStorage.serializeWorkoutSession(it)) }
        assertEquals(first, stored)
    }

    @Test
    fun routinesRoundTripThroughJson() {
        val json = """
            {"format":"lifttracker.routines","version":1,"routines":[{"title":"Carry","folder":"F","notes":"n","exercises":[
              {"title":"Farmers Walk","restSeconds":0,"sets":[{"weightLbs":100,"distanceMeters":30}]},
              {"title":"Dead Hang","restSeconds":105,"sets":[{"durationSeconds":45},{"durationSeconds":40}]},
              {"title":"Squat (Barbell)","restSeconds":180,"sets":[{"type":"warmup","weightKg":20,"reps":8},{"weightKg":84,"reps":8}]}]}]}
        """.trimIndent()
        val routines = RoutineCodec.parseRoutines(json, resolver)
        val r = routines.single()
        assertEquals(listOf(ExerciseMeasure.WEIGHT_DISTANCE, ExerciseMeasure.DURATION, ExerciseMeasure.WEIGHT_REPS), r.exercises.map { it.measure })
        assertEquals(listOf(0, 105, 180), r.exercises.map { it.restSeconds })
        assertEquals(100.0, r.exercises[0].sets[0].weightKg * WeightUnit.LBS.factorFromKg, 1e-6)
        assertEquals(84.0, r.exercises[2].targetWeightKg, 0.0)
        assertEquals(2, r.exercises[1].targetSets)

        val again = RoutineCodec.parseRoutines(RoutineCodec.exportRoutines(routines), resolver)
        assertEquals(RoutineCodec.exportRoutines(routines), RoutineCodec.exportRoutines(again))
        // A single routine object and a bare array are accepted too.
        val single = RoutineCodec.routineToJson(r).toString()
        assertEquals(1, RoutineCodec.parseRoutines(single, resolver).size)
        assertEquals(1, RoutineCodec.parseRoutines("[$single]", resolver).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonRoutineText() {
        RoutineCodec.parseRoutines("hello", resolver)
    }

    /** Runs against real files when their paths are given: -DsampleCsv=… -DsampleRoutines=… */
    @Test
    fun importsSampleFilesWhenProvided() {
        val csvPath = System.getProperty("sampleCsv")
        val routinesPath = System.getProperty("sampleRoutines")
        assumeTrue(csvPath != null || routinesPath != null)
        csvPath?.let {
            val result = WorkoutCsv.importWorkouts(File(it).readText(), resolver, 90, zone)
            val sets = result.sessions.sumOf { s -> s.exercises.sumOf { e -> e.sets.size } }
            println("SAMPLE CSV: ${result.sessions.size} workouts, $sets sets, skipped ${result.skippedRows}")
            println("SAMPLE CSV measures: " + result.measuresByExerciseId.filterValues { m -> m != ExerciseMeasure.WEIGHT_REPS })
            val back = WorkoutCsv.importWorkouts(WorkoutCsv.exportWorkouts(result.sessions, WeightUnit.LBS, zone), resolver, 90, zone)
            // Real weights (e.g. 45.5 lbs) are written with two decimals, so compare within rounding.
            assertEquals(result.sessions.map { s -> s.id }.sorted(), back.sessions.map { s -> s.id }.sorted())
            assertEquals(sets, back.sessions.sumOf { s -> s.exercises.sumOf { e -> e.sets.size } })
            assertEquals(result.sessions.sumOf { s -> s.totalVolumeKg }, back.sessions.sumOf { s -> s.totalVolumeKg }, 50.0)
        }
        routinesPath?.let {
            val routines = RoutineCodec.parseRoutines(File(it).readText(), resolver)
            println("SAMPLE ROUTINES: " + routines.joinToString { r -> "${r.title} (${r.exercises.size} exercises, ${r.exercises.sumOf { e -> e.sets.size }} sets)" })
        }
    }
}
