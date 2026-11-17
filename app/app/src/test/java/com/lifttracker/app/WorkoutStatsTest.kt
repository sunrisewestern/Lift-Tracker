package com.lifttracker.app

import com.fittribe.musclemap.Muscle
import com.lifttracker.app.domain.BodyMuscle
import com.lifttracker.app.domain.CustomExerciseCodec
import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.ExerciseAliases
import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.domain.ExerciseFamily
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.ExerciseStats
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.StatMetric
import com.lifttracker.app.domain.StatPeriod
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import com.lifttracker.app.domain.WorkoutStats
import com.lifttracker.app.ui.bodyRegionShares
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.MainTab
import com.lifttracker.app.viewmodel.ProfileLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class WorkoutStatsTest {
    private val zone = ZoneOffset.UTC

    private fun set(weight: Double, reps: Int, type: SetType = SetType.NORMAL, done: Boolean = true, seconds: Int = 0) =
        WorkoutSetEntry(id = "s", setNumber = 1, setType = type, weightKg = weight, reps = reps, completed = done, durationSeconds = seconds)

    private fun entry(id: String, title: String, vararg sets: WorkoutSetEntry, measure: ExerciseMeasure = ExerciseMeasure.WEIGHT_REPS) =
        WorkoutExerciseEntry(id = "e_$id", exerciseId = id, exerciseTitle = title, sets = sets.toList(), measure = measure)

    /** A one-hour workout at noon UTC on [date]. */
    private fun session(date: LocalDate, vararg entries: WorkoutExerciseEntry): WorkoutSession {
        val start = date.atTime(12, 0).toEpochSecond(ZoneOffset.UTC)
        return WorkoutSession(id = "wk_$date", title = "W $date", startTimeEpochSeconds = start, endTimeEpochSeconds = start + 3600, exercises = entries.toList())
    }

    private fun custom(id: String, title: String, aliasOf: String = "") = ExerciseDefinition(
        id = id, title = title, primaryMuscle = MuscleGroup.CHEST, secondaryMuscles = emptyList(),
        equipment = EquipmentType.BARBELL, summaryNotes = "", instructions = emptyList(), coachingCues = emptyList(),
        isCustom = true, aliasOfId = aliasOf,
    )

    // ------------------------------------------------------------ aliases

    @Test
    fun aliasesResolveToOneFamilyAndRefuseLoops() {
        val aliases = mapOf("a" to "bench", "b" to "a")
        assertEquals("bench", ExerciseAliases.canonicalId("b", aliases))
        assertEquals("bench", ExerciseAliases.canonicalId("bench", aliases))
        assertEquals(setOf("a", "b", "bench"), ExerciseAliases.familyIds("a", aliases))
        assertEquals(setOf("a", "b", "bench"), ExerciseAliases.familyIds("bench", aliases))
        assertEquals(setOf("squat"), ExerciseAliases.familyIds("squat", aliases))

        assertFalse(ExerciseAliases.canAlias("a", "a", aliases))
        // "bench" may not become an alias of its own alias.
        assertFalse(ExerciseAliases.canAlias("bench", "b", aliases))
        assertTrue(ExerciseAliases.canAlias("b", "bench", aliases))
        assertTrue(ExerciseAliases.canAlias(null, "a", aliases))
        // A loop in stored data must not hang.
        assertTrue(ExerciseAliases.canonicalId("x", mapOf("x" to "y", "y" to "x")) in setOf("x", "y"))
    }

    @Test
    fun aliasSurvivesTheCustomExerciseCodec() {
        val decoded = CustomExerciseCodec.decode(CustomExerciseCodec.encode(listOf(custom("custom_flat", "Flat Bench", "ex_bench_press"), custom("custom_own", "Own"))))
        assertEquals("ex_bench_press", decoded[0].aliasOfId)
        assertEquals("", decoded[1].aliasOfId)
        assertEquals(mapOf("custom_flat" to "ex_bench_press"), ExerciseAliases.aliasMap(decoded))
    }

    @Test
    fun viewModelCountsAnAliasAsTheSameExercise() {
        val vm = LiftTrackerViewModel()
        val before = vm.exerciseStats("ex_bench_press")
        assertTrue(before.sessions > 0)
        val alias = vm.saveCustomExercise(custom("custom_flat_bench", "Flat Bench", "ex_bench_press"), ExerciseMeasure.WEIGHT_REPS)

        assertEquals("ex_bench_press", vm.canonicalExerciseId(alias.id))
        assertEquals("ex_bench_press", vm.aliasTargetOf(alias)?.id)
        assertEquals(listOf("Flat Bench"), vm.otherNamesOf("ex_bench_press").map { it.title })
        // The alias shows the target's history, records and "previous" sets.
        assertEquals(before.sessions, vm.exerciseStats(alias.id).sessions)
        assertEquals(before.best1Rm?.value, vm.exerciseStats(alias.id).best1Rm?.value)
        assertEquals(vm.getExerciseHistory("ex_bench_press").size, vm.getExerciseHistory(alias.id).size)
        assertTrue(vm.lastPerformedSets(alias.id, alias.title).isNotEmpty())
        // It can't be offered as a target that would loop.
        assertTrue(vm.aliasCandidates("ex_bench_press").none { it.id == alias.id })
        assertTrue(vm.aliasCandidates(alias.id).none { it.id == alias.id })

        // A workout logged under the alias counts for the target.
        vm.startEmptyWorkoutSession("Alias day")
        vm.addExerciseToActiveWorkout(alias)
        vm.activeExercises[0].sets.indices.forEach { vm.toggleSetCompleted(0, it) }
        vm.finishActiveWorkout()
        assertEquals(before.sessions + 1, vm.exerciseStats("ex_bench_press").sessions)
        assertTrue(vm.getExerciseHistory("ex_bench_press").first().loggedAs.contains("Flat Bench"))

        vm.deleteCustomExercise(alias.id)
        assertEquals(emptyList<String>(), vm.otherNamesOf("ex_bench_press").map { it.title })
    }

    // ------------------------------------------------------------ exercise records

    @Test
    fun exerciseStatsComeFromLoggedSetsOnly() {
        val d = LocalDate.of(2026, 10, 1)
        val history = listOf(
            session(d, entry("bench", "Bench", set(60.0, 10, SetType.WARMUP), set(100.0, 5), set(90.0, 8), set(120.0, 1, done = false))),
            session(d.plusDays(7), entry("alias", "Flat Bench", set(105.0, 3), set(90.0, 10))),
            session(d.plusDays(8), entry("squat", "Squat", set(140.0, 5))),
        )
        val stats = ExerciseStats.compute(history, ExerciseFamily(setOf("bench", "alias"), emptyList()), ExerciseMeasure.WEIGHT_REPS)

        assertEquals(2, stats.sessions)
        assertEquals(4, stats.totalSets) // warm-up and the unfinished set are left out
        assertEquals(105.0, stats.heaviest!!.value, 0.001)
        assertEquals(120.0, stats.best1Rm!!.value, 0.001) // 90 × 10 → 90 × (1 + 10/30)
        assertEquals(900.0, stats.bestSetVolume!!.value, 0.001)
        assertEquals(10, stats.mostReps!!.set.reps)
        assertEquals(100.0 * 5 + 90.0 * 8, stats.bestSession!!.volumeKg, 0.001)
        assertEquals(26, stats.totalReps)
        // 100 × 5 is beaten by 90 × 8? No: lighter. 90 × 8 is beaten by 90 × 10 (more reps, same weight).
        assertEquals(listOf(3 to 105.0, 5 to 100.0, 10 to 90.0), stats.repRecords.map { it.set.reps to it.value })
        assertEquals(listOf(d, d.plusDays(7)).map { it.atTime(13, 0).toEpochSecond(ZoneOffset.UTC) }, stats.points.map { it.epochSeconds })

        val none = ExerciseStats.compute(history, ExerciseFamily(setOf("curl"), emptyList()), ExerciseMeasure.WEIGHT_REPS)
        assertEquals(0, none.sessions)
        assertNull(none.heaviest)
        assertNull(none.best1Rm)
        assertNull(none.bestSession)

        // Matching by name covers workouts imported under another id.
        assertEquals(1, ExerciseStats.compute(history, ExerciseFamily(setOf("x"), listOf("squat")), ExerciseMeasure.WEIGHT_REPS).sessions)
    }

    @Test
    fun durationExercisesRecordTheLongestHold() {
        val d = LocalDate.of(2026, 10, 1)
        val history = listOf(
            session(d, entry("hang", "Dead Hang", set(0.0, 0, seconds = 40), set(0.0, 0, seconds = 55), measure = ExerciseMeasure.DURATION)),
        )
        val stats = ExerciseStats.compute(history, ExerciseFamily(setOf("hang"), emptyList()), ExerciseMeasure.DURATION)
        assertEquals(55, stats.longestHold!!.set.durationSeconds)
        assertEquals(95, stats.bestSession!!.totalSeconds)
        assertNull(stats.best1Rm)
        assertNull(stats.heaviest)
    }

    @Test
    fun differentExercisesInTheSampleDataHaveDifferentSummaries() {
        val vm = LiftTrackerViewModel()
        val logged = vm.workoutHistory.flatMap { it.exercises }.map { it.exerciseId }.distinct()
        assertTrue(logged.size >= 2)
        val summaries = logged.map { id -> vm.exerciseStats(id).let { Triple(it.heaviest?.value, it.best1Rm?.value, it.totalVolumeKg) } }
        assertTrue("every logged exercise used to show the same placeholder numbers", summaries.distinct().size > 1)
        val neverLogged = vm.exercises.first { it.id !in logged }
        assertEquals(0, vm.exerciseStats(neverLogged.id).sessions)
    }

    // ------------------------------------------------------------ periods, streak, muscles

    @Test
    fun bucketsTotalsAndComparisons() {
        val sessions = listOf(
            session(LocalDate.of(2026, 9, 30), entry("bench", "Bench", set(100.0, 5))),
            session(LocalDate.of(2026, 10, 2), entry("bench", "Bench", set(100.0, 5), set(100.0, 5))),
            session(LocalDate.of(2026, 10, 18), entry("bench", "Bench", set(50.0, 10, SetType.WARMUP), set(80.0, 8))),
            session(LocalDate.of(2026, 11, 4), entry("hang", "Hang", set(0.0, 0, seconds = 30), measure = ExerciseMeasure.DURATION)),
        )
        val today = LocalDate.of(2026, 11, 17)
        val months = WorkoutStats.buckets(sessions, StatPeriod.MONTH, 3, today, zone, DayOfWeek.SUNDAY)
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), months.map { it.start })
        assertEquals(listOf(1, 2, 1), months.map { it.totals.workouts })
        val october = months[1].totals
        assertEquals(7200L, october.durationSeconds)
        assertEquals(4, october.sets) // the warm-up is a set, but adds no volume
        assertEquals(1000.0 + 640.0, october.volumeKg, 0.001)
        assertEquals(28, october.reps)
        assertEquals(0, months[2].totals.reps) // a timed hold has no reps
        assertEquals(2.0, october.value(StatMetric.WORKOUTS), 0.0)

        val quarters = WorkoutStats.buckets(sessions, StatPeriod.QUARTER, 2, today, zone, DayOfWeek.SUNDAY)
        assertEquals(listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 1)), quarters.map { it.start })
        assertEquals(listOf(1, 3), quarters.map { it.totals.workouts })
        assertEquals(4, WorkoutStats.buckets(sessions, StatPeriod.YEAR, 1, today, zone, DayOfWeek.SUNDAY).single().totals.workouts)

        // Nov 17 2026 is a Tuesday; its Sunday-first week starts Nov 15, its Monday-first week Nov 16.
        assertEquals(LocalDate.of(2026, 11, 15), StatPeriod.WEEK.startOf(today, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 16), StatPeriod.WEEK.startOf(today, DayOfWeek.MONDAY))
        assertEquals(2, WorkoutStats.between(sessions, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), zone).size)
    }

    @Test
    fun weekStreakAndRestDays() {
        val today = LocalDate.of(2026, 11, 17) // Tuesday
        // Workouts in the two weeks before this one; nothing yet this week.
        val days = listOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13))
        assertEquals(2, WorkoutStats.weekStreak(days, today, DayOfWeek.SUNDAY))
        assertEquals(3, WorkoutStats.restDays(days, today)) // 14th, 15th, 16th
        assertEquals(3, WorkoutStats.weekStreak(days + today, today, DayOfWeek.SUNDAY))
        assertEquals(0, WorkoutStats.restDays(days + today, today))
        // A skipped week ends the streak.
        assertEquals(1, WorkoutStats.weekStreak(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 10)), today, DayOfWeek.SUNDAY))
        assertEquals(0, WorkoutStats.weekStreak(listOf(LocalDate.of(2026, 10, 20)), today, DayOfWeek.SUNDAY))
        assertEquals(0, WorkoutStats.weekStreak(emptyList(), today, DayOfWeek.SUNDAY))
        assertNull(WorkoutStats.restDays(emptyList(), today))
    }

    @Test
    fun muscleLoadCountsWorkingSetsWithHalfForSecondaryMuscles() {
        assertEquals(BodyMuscle.CHEST, BodyMuscle.fromName("pectorals"))
        assertEquals(BodyMuscle.LATS, BodyMuscle.fromName("latissimus dorsi"))
        assertEquals(BodyMuscle.LOWER_BACK, BodyMuscle.fromName("lower back"))
        assertEquals(BodyMuscle.UPPER_BACK, BodyMuscle.fromName("rhomboids"))
        assertEquals(BodyMuscle.SHOULDERS, BodyMuscle.fromName("rear deltoids"))
        assertEquals(BodyMuscle.ABS, BodyMuscle.fromName("hip flexors"))
        assertNull(BodyMuscle.fromName("cardiovascular system"))

        val bench = custom("bench", "Bench").copy(targetMuscle = "pectorals", secondaryTargets = listOf("triceps", "shoulders"))
        assertEquals(mapOf(BodyMuscle.CHEST to 1.0, BodyMuscle.TRICEPS to 0.5, BodyMuscle.SHOULDERS to 0.5), BodyMuscle.loadPerSet(bench))
        // Only a group is known: its muscles share the set.
        val legs = custom("legs", "Leg thing").copy(primaryMuscle = MuscleGroup.LEGS)
        assertEquals(1.0, BodyMuscle.loadPerSet(legs).values.sum(), 0.001)

        val sessions = listOf(
            session(LocalDate.of(2026, 10, 2), entry("bench", "Bench", set(60.0, 10, SetType.WARMUP), set(100.0, 5), set(100.0, 5), set(100.0, 5, done = false))),
        )
        val load = WorkoutStats.muscleLoad(sessions) { BodyMuscle.loadPerSet(bench) }
        assertEquals(2.0, load[BodyMuscle.CHEST]!!, 0.001)
        assertEquals(1.0, load[BodyMuscle.TRICEPS]!!, 0.001)
        val groups = WorkoutStats.groupLoad(load)
        assertEquals(2.0, groups[MuscleGroup.CHEST]!!, 0.001)
        assertEquals(1.0, groups[MuscleGroup.ARMS]!!, 0.001)
        assertEquals(1.0, groups[MuscleGroup.SHOULDERS]!!, 0.001)
    }

    // ------------------------------------------------------------ profile navigation

    @Test
    fun profileTabAndItsPages() {
        val vm = LiftTrackerViewModel()
        assertTrue(MainTab.PROFILE in MainTab.entries)
        vm.selectedTab = MainTab.PROFILE
        vm.openProfileLayer(ProfileLayer.STATISTICS)
        vm.openMonthlyReport(LocalDate.of(2026, 10, 18))
        assertEquals(ProfileLayer.MONTHLY_REPORT, vm.profileLayer)
        assertEquals(LocalDate.of(2026, 10, 1), vm.reportMonth)
        vm.closeProfileLayer()
        assertEquals(ProfileLayer.STATISTICS, vm.profileLayer)
        vm.closeProfileLayer()
        assertNull(vm.profileLayer)
        vm.openProfileLayer(ProfileLayer.CALENDAR)
        vm.closeProfileLayer()
        assertNull(vm.profileLayer)
        assertEquals(MainTab.PROFILE, vm.selectedTab)

        // The sample history lands on the muscle charts.
        val load = WorkoutStats.muscleLoad(vm.workoutHistory.toList(), vm::muscleLoadOf)
        assertTrue(load.values.sum() > 0.0)

        // "Your exercises" in Settings → Exercise Library folds.
        assertTrue(vm.isUserExercisesExpanded)
        vm.toggleUserExercisesExpanded()
        assertFalse(vm.isUserExercisesExpanded)
    }

    @Test
    fun restTimerControlsUsedByTheMinimizedBar() {
        val vm = LiftTrackerViewModel()
        vm.startEmptyWorkoutSession()
        vm.minimizeWorkoutLayer()
        vm.restTimerRemainingSeconds = 60
        vm.adjustRestTimer(15)
        assertEquals(75, vm.restTimerRemainingSeconds)
        vm.adjustRestTimer(-15)
        vm.adjustRestTimer(-100)
        assertEquals(0, vm.restTimerRemainingSeconds)
        vm.restTimerRemainingSeconds = 30
        vm.skipRestTimer()
        assertEquals(0, vm.restTimerRemainingSeconds)
        assertFalse(vm.isWorkoutLayerOpen)
        vm.discardActiveWorkout()
    }

    @Test
    fun heatmapRegionsCoverEveryMuscleAndScaleToTheMostTrained() {
        val shares = bodyRegionShares(mapOf(BodyMuscle.CHEST to 8.0, BodyMuscle.LATS to 4.0, BodyMuscle.UPPER_BACK to 2.0))
        assertEquals(1f, shares.getValue(Muscle.Chest), 0.0001f)
        // Lats and upper back share one drawn region, coloured by the more trained of the two.
        assertEquals(0.5f, shares.getValue(Muscle.UpperBack), 0.0001f)
        // Untrained muscles are still listed, so the adductors never inherit the hamstrings' colour.
        assertEquals(0f, shares.getValue(Muscle.Adductors), 0.0001f)
        assertEquals(BodyMuscle.entries.map { it.name }.size - 1, shares.size) // lats + upper back share one region
        assertTrue(bodyRegionShares(emptyMap()).values.all { it == 0f })
    }
}
