package com.lifttracker.app

import com.lifttracker.app.domain.HeartRateSamplePoint
import com.lifttracker.app.domain.SampleCatalog
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import com.lifttracker.app.export.ExportFormat
import com.lifttracker.app.export.WorkoutExporter
import com.lifttracker.app.storage.SQLiteWorkoutStorage
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.SettingsSection
import com.lifttracker.app.wear.WearIncomingMessage
import com.lifttracker.shared.WearHeartRateSample
import com.lifttracker.shared.WearProtocol
import com.lifttracker.shared.WearRoutineSummary
import com.lifttracker.shared.WearSetEdit
import com.lifttracker.shared.WearWorkoutState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class IntegrationsTest {

    private fun session(
        title: String = "Push Day",
        notes: String = ""
    ) = WorkoutSession(
        id = "wk_1",
        title = title,
        startTimeEpochSeconds = 1_763_200_800L, // 2025-11-15T10:00:00Z
        endTimeEpochSeconds = 1_763_204_400L,
        notes = notes,
        exercises = listOf(
            WorkoutExerciseEntry(
                id = "e1",
                exerciseId = "ex_bench_press",
                exerciseTitle = "Barbell Bench Press",
                sets = listOf(
                    WorkoutSetEntry("s1", 1, SetType.WARMUP, "—", 60.0, 10, null, true),
                    WorkoutSetEntry("s2", 2, SetType.NORMAL, "—", 102.5, 6, 8.5, true),
                    WorkoutSetEntry("s3", 3, SetType.NORMAL, "—", 105.0, 5, null, false)
                )
            )
        ),
        caloriesBurnedKcal = 400.0,
        heartRateSamples = listOf(HeartRateSamplePoint(1_763_200_900_000L, 121.0))
    )

    // --- Export ---

    @Test
    fun jsonExportContainsWorkoutsRoutinesAndSchemaVersion() {
        val json = JSONObject(
            WorkoutExporter.exportJson(
                listOf(session()),
                SampleCatalog.defaultRoutines,
                exportedAt = Instant.parse("2026-11-15T00:00:00Z")
            )
        )
        assertEquals(WorkoutExporter.SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals("2026-11-15T00:00:00Z", json.getString("exportedAt"))
        assertEquals(SampleCatalog.defaultRoutines.size, json.getJSONArray("routines").length())
        val workout = json.getJSONArray("workouts").getJSONObject(0)
        assertEquals("2025-11-15T10:00:00Z", workout.getString("startTime"))
        assertEquals(3600, workout.getLong("durationSeconds"))
        assertEquals(615.0, workout.getDouble("totalVolumeKg"), 0.001) // warmup and unfinished sets excluded
        assertTrue(workout.isNull("averageHeartRateBpm"))
        val sets = workout.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
        assertEquals(3, sets.length())
        assertEquals("warmup", sets.getJSONObject(0).getString("type"))
        assertEquals(8.5, sets.getJSONObject(1).getDouble("rpe"), 0.0)
        assertEquals(121, workout.getJSONArray("heartRateSamples").getJSONObject(0).getInt("bpm"))
    }

    @Test
    fun csvExportHasOneRowPerSetAndEscapesUserText() {
        val csv = WorkoutExporter.exportWorkoutsCsv(listOf(session(title = "Push, \"heavy\"", notes = "=SUM(A1)")))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(WorkoutExporter.CSV_COLUMNS.joinToString(","), lines[0])
        assertEquals(4, lines.size)
        assertTrue(lines[1].startsWith("wk_1,\"Push, \"\"heavy\"\"\",2025-11-15T10:00:00Z,"))
        assertTrue("formula-looking notes are neutralised", lines[1].contains(",'=SUM(A1),"))
        assertTrue(lines[2].endsWith(",2,normal,reps,102.50,6,8.50,true,123,weight_reps,0,0"))
        assertEquals("-5", WorkoutExporter.csvField("-5"))
        assertEquals(ExportFormat.CSV.extension, "csv")
        assertTrue(WorkoutExporter.suggestedFileName(ExportFormat.JSON, Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC)
            .endsWith("2026-11-15.json"))
    }

    @Test
    fun archivedWorkoutsCanBeLeftOutOfExports() {
        val archived = session().copy(id = "wk_2", archived = true)
        val csv = WorkoutExporter.exportWorkoutsCsv(listOf(session(), archived), includeArchived = false)
        assertFalse(csv.contains("wk_2"))
        val json = JSONObject(WorkoutExporter.exportJson(listOf(session(), archived), includeArchived = false))
        assertEquals(1, json.getJSONArray("workouts").length())
    }

    @Test
    fun storageRoundTripKeepsHeartRate() {
        val original = session()
        val restored = SQLiteWorkoutStorage.deserializeWorkoutSession(SQLiteWorkoutStorage.serializeWorkoutSession(original))
        assertEquals(original, restored)
    }

    private fun watch(path: String, data: String = "") = WearIncomingMessage(path, data, "node1")

    // --- Confirmation of destructive actions ---

    @Test
    fun destructiveActionsOnlyRunAfterConfirmation() {
        val vm = LiftTrackerViewModel()
        val count = vm.workoutHistory.size
        assertTrue(count > 0)
        vm.requestConfirmation("Clear?", "msg", "Clear") { vm.clearWorkoutHistory() }
        assertNotNull(vm.pendingConfirmation)
        assertEquals(count, vm.workoutHistory.size)
        vm.dismissPendingConfirmation()
        assertNull(vm.pendingConfirmation)
        vm.confirmPendingAction()
        assertEquals("dismissed requests never run", count, vm.workoutHistory.size)
        vm.requestConfirmation("Clear?", "msg", "Clear") { vm.clearWorkoutHistory() }
        vm.confirmPendingAction()
        assertTrue(vm.workoutHistory.isEmpty())
        assertNull(vm.pendingConfirmation)
    }

    @Test
    fun watchCanDiscardTheWorkoutWithoutSavingIt() {
        val vm = LiftTrackerViewModel()
        val count = vm.workoutHistory.size
        vm.startRoutineAsWorkout(vm.routines.first())
        vm.handleWatchMessage(watch(WearProtocol.MSG_COMPLETE_SET))
        vm.handleWatchMessage(watch(WearProtocol.MSG_DISCARD_WORKOUT))
        assertFalse(vm.isWorkoutActive)
        assertEquals(count, vm.workoutHistory.size)
        assertFalse(vm.buildWearWorkoutState().active)
    }

    // --- Wear OS protocol and watch commands ---

    @Test
    fun wearProtocolJsonRoundTripsAndTimersTickLocally() {
        val state = WearWorkoutState(
            active = true, title = "Push", elapsedSeconds = 100, sentAtEpochMs = 10_000L,
            exerciseTitle = "Bench", setLabel = "Set 1/3", restEndsAtEpochMs = 40_000L, restTargetSeconds = 90
        )
        assertEquals(state, WearWorkoutState.fromJson(state.toJson()))
        assertEquals(105L, state.elapsedSecondsAt(15_000L))
        assertEquals(25, state.restRemainingSecondsAt(15_000L))
        assertEquals(0, state.restRemainingSecondsAt(50_000L))
        val paused = state.copy(paused = true, restEndsAtEpochMs = 0L, restRemainingSeconds = 30)
        assertEquals(100L, paused.elapsedSecondsAt(99_000L))
        assertEquals(30, paused.restRemainingSecondsAt(99_000L))
        assertFalse(WearWorkoutState.fromJson("not json").active)

        val routines = listOf(WearRoutineSummary("rt_1", "Push \"A\"", 5))
        assertEquals(routines, WearRoutineSummary.listFromJson(WearRoutineSummary.listToJson(routines)))
        val samples = listOf(WearHeartRateSample(1000L, 120), WearHeartRateSample(2000L, 125))
        assertEquals(samples, WearHeartRateSample.listFromJson(WearHeartRateSample.listToJson(samples)))
    }

    @Test
    fun watchCommandsDriveTheActiveWorkout() {
        val vm = LiftTrackerViewModel()
        assertFalse(vm.buildWearWorkoutState().active)

        val routine = vm.routines.first()
        vm.handleWatchMessage(watch(WearProtocol.MSG_START_ROUTINE, routine.id))
        assertTrue(vm.isWorkoutActive)
        assertEquals(routine.title, vm.activeWorkoutTitle)

        val before = vm.buildWearWorkoutState(nowEpochMs = 1_000_000L)
        assertTrue(before.active)
        assertEquals(vm.activeExercises.first().exerciseTitle, before.exerciseTitle)
        assertTrue(before.setLabel.startsWith("Set 1/"))
        assertEquals(0, before.completedSets)

        vm.handleWatchMessage(watch(WearProtocol.MSG_COMPLETE_SET))
        assertTrue(vm.activeExercises.first().sets.first().completed)
        val after = vm.buildWearWorkoutState(nowEpochMs = 1_000_000L)
        assertEquals(1, after.completedSets)
        assertTrue(after.setLabel.startsWith("Set 2/"))
        assertTrue("completing a set starts the rest timer", after.restEndsAtEpochMs > 1_000_000L)

        val rest = vm.restTimerRemainingSeconds
        vm.handleWatchMessage(watch(WearProtocol.MSG_ADJUST_REST, "15"))
        assertEquals(rest + 15, vm.restTimerRemainingSeconds)
        vm.handleWatchMessage(watch(WearProtocol.MSG_SKIP_REST))
        assertEquals(0, vm.restTimerRemainingSeconds)

        vm.handleWatchMessage(watch(WearProtocol.MSG_TOGGLE_PAUSE))
        assertTrue(vm.isTimerPaused)
        assertEquals(0L, vm.buildWearWorkoutState().restEndsAtEpochMs)
        vm.handleWatchMessage(watch(WearProtocol.MSG_TOGGLE_PAUSE))

        val now = System.currentTimeMillis()
        vm.handleWatchMessage(
            watch(
                WearProtocol.MSG_HEART_RATE,
                WearHeartRateSample.listToJson(listOf(WearHeartRateSample(now - 2000, 120), WearHeartRateSample(now - 1000, 130)))
            )
        )
        assertEquals(130, vm.liveHeartRateBpm)

        val historySize = vm.workoutHistory.size
        vm.handleWatchMessage(watch(WearProtocol.MSG_FINISH_WORKOUT))
        assertFalse(vm.isWorkoutActive)
        assertEquals(historySize + 1, vm.workoutHistory.size)
        val saved = vm.workoutHistory.first()
        assertEquals(2, saved.heartRateSamples.size)
        assertEquals(125, saved.averageHeartRateBpm)
        assertFalse("nothing is marked synced without a real Health Connect write", saved.syncedToHealthConnect)
        assertEquals(0, vm.liveHeartRateBpm)
    }

    @Test
    fun watchCanEditTheCurrentSetAndSwitchExercise() {
        val vm = LiftTrackerViewModel()
        vm.startRoutineAsWorkout(vm.routines.first { it.exercises.size >= 2 })
        val state = vm.buildWearWorkoutState()
        assertEquals(0, state.exerciseIndex)
        assertEquals(0, state.setIndex)
        assertEquals(vm.activeExercises.size, state.exercises.size)
        assertEquals(vm.activeExercises[0].sets[0].weightKg, state.weight, 0.001)
        assertEquals(vm.activeExercises[0].sets[0].reps, state.reps)
        assertEquals("kg", state.weightUnit)
        assertEquals(state, WearWorkoutState.fromJson(state.toJson()))

        vm.handleWatchMessage(watch(WearProtocol.MSG_EDIT_SET, WearSetEdit(0, 0, state.weight + 5.0, state.reps + 2).toJson()))
        assertEquals(state.weight + 5.0, vm.activeExercises[0].sets[0].weightKg, 0.001)
        assertEquals(state.reps + 2, vm.activeExercises[0].sets[0].reps)

        // An edit for a set that is no longer current is dropped.
        vm.handleWatchMessage(watch(WearProtocol.MSG_EDIT_SET, WearSetEdit(1, 0, 999.0, 99).toJson()))
        assertTrue(vm.activeExercises[1].sets[0].weightKg != 999.0)
        vm.handleWatchMessage(watch(WearProtocol.MSG_EDIT_SET, "garbage"))

        vm.handleWatchMessage(watch(WearProtocol.MSG_SELECT_EXERCISE, "1"))
        val switched = vm.buildWearWorkoutState()
        assertEquals(1, switched.exerciseIndex)
        assertEquals(vm.activeExercises[1].exerciseTitle, switched.exerciseTitle)
        vm.handleWatchMessage(watch(WearProtocol.MSG_COMPLETE_SET))
        assertTrue(vm.activeExercises[1].sets[0].completed)
        assertFalse(vm.activeExercises[0].sets[0].completed)
        assertEquals(1, vm.buildWearWorkoutState().exercises[1].completedSets)

        vm.handleWatchMessage(watch(WearProtocol.MSG_SELECT_EXERCISE, "42"))
        assertEquals(1, vm.buildWearWorkoutState().exerciseIndex)
    }

    @Test
    fun settingsLayerAndFoldersStartClosed() {
        val vm = LiftTrackerViewModel()
        assertFalse(vm.isSettingsLayerOpen)
        assertTrue(vm.expandedSettingsSections.isEmpty())
        assertTrue(vm.expandedMuscleDrawers.isEmpty())
        vm.openSettingsLayer(SettingsSection.HEALTH_CONNECT)
        assertTrue(vm.isSettingsLayerOpen)
        assertEquals(listOf(SettingsSection.HEALTH_CONNECT), vm.expandedSettingsSections.toList())
        vm.toggleSettingsSection(SettingsSection.HEALTH_CONNECT)
        vm.toggleSettingsSection(SettingsSection.WORKOUT)
        assertEquals(listOf(SettingsSection.WORKOUT), vm.expandedSettingsSections.toList())
        vm.closeSettingsLayer()
        assertFalse(vm.isSettingsLayerOpen)
    }

    @Test
    fun routineNotesAreSavedFromTheEditor() {
        val vm = LiftTrackerViewModel()
        vm.openNewRoutineEditor("Pull Day")
        vm.routineEditorNotes = "  Strict form, 2 min rests  "
        assertTrue(vm.saveRoutineDraft())
        assertEquals("Strict form, 2 min rests", vm.routines.first().notes)
        vm.openEditRoutineEditor(vm.routines.first())
        assertEquals("Strict form, 2 min rests", vm.routineEditorNotes)
    }

    @Test
    fun watchCanStartAnEmptyWorkoutAndCommandsAreIgnoredWhenIdle() {
        val vm = LiftTrackerViewModel()
        vm.handleWatchMessage(watch(WearProtocol.MSG_COMPLETE_SET))
        vm.handleWatchMessage(watch(WearProtocol.MSG_FINISH_WORKOUT))
        assertFalse(vm.isWorkoutActive)
        val historySize = vm.workoutHistory.size
        vm.handleWatchMessage(watch(WearProtocol.MSG_WORKOUT_STARTED))
        assertTrue(vm.isWorkoutActive)
        assertTrue(vm.activeExercises.isEmpty())
        assertEquals("", vm.buildWearWorkoutState().setLabel)
        vm.handleWatchMessage(watch(WearProtocol.MSG_FINISH_WORKOUT))
        assertEquals("an empty workout is not saved", historySize, vm.workoutHistory.size)
    }

    @Test
    fun viewModelExportMatchesExporter() {
        val vm = LiftTrackerViewModel()
        val json = JSONObject(vm.buildExport(ExportFormat.JSON))
        assertEquals(vm.workoutHistory.size, json.getJSONArray("workouts").length())
        assertEquals(vm.routines.size, json.getJSONArray("routines").length())
        val expectedRows = vm.workoutHistory.sumOf { w -> w.exercises.sumOf { it.sets.size } }
        assertEquals(expectedRows + 1, vm.buildExport(ExportFormat.CSV).trimEnd().split("\r\n").size)
    }
}
