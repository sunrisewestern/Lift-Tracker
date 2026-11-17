package com.lifttracker.app

import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RepType
import com.lifttracker.app.domain.SampleCatalog
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.ui.theme.ThemeConfig
import com.lifttracker.app.ui.theme.ThemeMode
import com.lifttracker.app.ui.theme.ThemePreset
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.MainTab
import com.lifttracker.app.viewmodel.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMechanicsTest {

    @Test
    fun timerVolumeMappingMatchesTargetDex() {
        assertEquals(0.0f, WorkoutMechanics.timerVolumeFromSetting("off"), 0.0001f)
        assertEquals(0.2f, WorkoutMechanics.timerVolumeFromSetting("low"), 0.0001f)
        assertEquals(0.6f, WorkoutMechanics.timerVolumeFromSetting("normal"), 0.0001f)
        assertEquals(0.6f, WorkoutMechanics.timerVolumeFromSetting(null), 0.0001f)
        assertEquals(1.0f, WorkoutMechanics.timerVolumeFromSetting("high"), 0.0001f)
    }

    @Test
    fun epleyOneRepMaxAndSetVolumeCalculations() {
        val oneRepMax = WorkoutMechanics.estimateOneRepMax(100.0, 6)
        assertEquals(120.0, oneRepMax, 0.01)

        val warmupVol = WorkoutMechanics.calculateSetVolume(60.0, 10, SetType.WARMUP, true)
        assertEquals(0.0, warmupVol, 0.01)

        val workingVol = WorkoutMechanics.calculateSetVolume(100.0, 6, SetType.NORMAL, true)
        assertEquals(600.0, workingVol, 0.01)
    }

    @Test
    fun viewModelInitializesAllFeatureDomainsIncludingSettings() {
        val vm = LiftTrackerViewModel()
        assertTrue(vm.exercises.size >= 8)
        assertTrue(vm.routines.size >= 4)
        assertEquals(7, vm.healthPermissions.size)
        // Integrations report nothing until the real services are queried.
        assertTrue(!vm.wearWatchStatus.hasConnectedWatch)
        assertTrue(vm.healthPermissions.none { it.granted })
        assertTrue(!vm.isHealthConnectAvailable)
        assertTrue(vm.getExerciseHistory("ex_bench_press").isNotEmpty())
        assertEquals(5, MainTab.entries.size)
        assertTrue(vm.expandedMuscleDrawers.isEmpty())
        assertEquals(5, WorkoutMechanics.SQLITE_CONFLICT_REPLACE)
        assertEquals(SampleCatalog.defaultHealthConnectPermissions.size, WorkoutMechanics.HEALTH_CONNECT_PERMISSION_COUNT)
    }

    @Test
    fun activeWorkoutLayerStartsFromEmptyOrRoutineAndClosesOnMinimizeFinishDiscard() {
        val vm = LiftTrackerViewModel()
        assertFalse(vm.isWorkoutLayerOpen)
        assertFalse(vm.isWorkoutActive)

        vm.startEmptyWorkoutSession("Quick Arm Pump")
        assertTrue(vm.isWorkoutActive)
        assertTrue(vm.isWorkoutLayerOpen)
        assertEquals("Quick Arm Pump", vm.activeWorkoutTitle)
        assertTrue(vm.activeExercises.isEmpty())

        vm.addExerciseToActiveWorkout(vm.exercises.first())
        assertEquals(1, vm.activeExercises.size)

        vm.minimizeWorkoutLayer()
        assertTrue(vm.isWorkoutActive)
        assertFalse(vm.isWorkoutLayerOpen)

        vm.openActiveWorkoutLayer()
        assertTrue(vm.isWorkoutLayerOpen)

        val beforeHistorySize = vm.workoutHistory.size
        vm.finishActiveWorkout()
        assertFalse(vm.isWorkoutLayerOpen)
        assertFalse(vm.isWorkoutActive)
        assertEquals(beforeHistorySize + 1, vm.workoutHistory.size)

        vm.startRoutineAsWorkout(vm.routines.first())
        assertTrue(vm.isWorkoutActive)
        assertTrue(vm.isWorkoutLayerOpen)
        assertTrue(vm.activeExercises.isNotEmpty())

        vm.discardActiveWorkout()
        assertFalse(vm.isWorkoutActive)
        assertFalse(vm.isWorkoutLayerOpen)
        assertTrue(vm.activeExercises.isEmpty())
    }

    @Test
    fun exerciseDrawerAndDetailLayerPreserveState() {
        val vm = LiftTrackerViewModel()
        assertFalse(vm.isExerciseDetailLayerOpen)
        val initialChestOpen = vm.expandedMuscleDrawers.contains(MuscleGroup.CHEST)
        vm.toggleMuscleDrawer(MuscleGroup.CHEST)
        assertEquals(!initialChestOpen, vm.expandedMuscleDrawers.contains(MuscleGroup.CHEST))

        vm.openExerciseDetailLayer("ex_deadlift")
        assertTrue(vm.isExerciseDetailLayerOpen)
        assertEquals("ex_deadlift", vm.selectedExerciseId)

        vm.closeExerciseDetailLayer()
        assertFalse(vm.isExerciseDetailLayerOpen)
        assertEquals("ex_deadlift", vm.selectedExerciseId)
    }

    @Test
    fun routineEditorDefaultsNoFolderAndConvertsSessionsToRoutines() {
        val vm = LiftTrackerViewModel()
        val initialRoutineCount = vm.routines.size

        // New routine via RoutineEditorLayer defaults to no folder and only requires name
        vm.openNewRoutineEditor()
        assertTrue(vm.isRoutineEditorLayerOpen)
        assertEquals("", vm.routineEditorFolder)
        assertFalse(vm.saveRoutineDraft()) // empty name rejected

        vm.routineEditorTitle = "Upper Express"
        vm.addExerciseToRoutineDraft(vm.exercises.first())
        vm.addSetToRoutineDraftExercise(0)
        assertEquals(4, vm.routineEditorExercises[0].sets.size)
        assertTrue(vm.saveRoutineDraft())
        assertFalse(vm.isRoutineEditorLayerOpen)
        assertEquals(initialRoutineCount + 1, vm.routines.size)
        assertEquals("", vm.routines.first().folderName)
        assertEquals("Upper Express", vm.routines.first().title)

        // Convert current workout session to routine
        vm.startEmptyWorkoutSession("Friday Heavy Push")
        vm.addExerciseToActiveWorkout(vm.exercises.first())
        val savedFromActive = vm.saveCurrentWorkoutAsRoutine()
        assertNotNull(savedFromActive)
        assertEquals("", savedFromActive?.folderName)
        assertEquals(initialRoutineCount + 2, vm.routines.size)

        // Convert previous workout session to routine
        val previousSession = vm.workoutHistory.first()
        val convertedFromHistory = vm.convertWorkoutSessionToRoutine(previousSession)
        assertEquals("", convertedFromHistory.folderName)
        assertEquals("${previousSession.title} (Routine)", convertedFromHistory.title)
        assertEquals(initialRoutineCount + 3, vm.routines.size)
    }

    @Test
    fun setTypeDropdownAndOptionalSkippableRpeWorkCorrectly() {
        val vm = LiftTrackerViewModel()
        vm.startRoutineAsWorkout(vm.routines.first())

        // Default RPE is null (skippable even when showRpeColumn = true)
        assertTrue(vm.showRpeColumn)
        assertNull(vm.activeExercises[0].sets[0].rpe)

        vm.updateSetType(0, 0, SetType.DROPSET)
        assertEquals(SetType.DROPSET, vm.activeExercises[0].sets[0].setType)

        vm.updateSetRpe(0, 0, 8.5)
        assertEquals(8.5, vm.activeExercises[0].sets[0].rpe ?: 0.0, 0.01)

        // Skip RPE again
        vm.updateSetRpe(0, 0, null)
        assertNull(vm.activeExercises[0].sets[0].rpe)

        vm.updateShowRpeColumn(false)
        assertFalse(vm.showRpeColumn)
    }

    @Test
    fun unitAwareWeightStepsAndSliderVolumeUpdateSettings() {
        val vm = LiftTrackerViewModel()
        assertEquals("100 kg", WorkoutMechanics.formatWeight(100.0, vm.weightUnit))
        assertEquals(2.5, vm.activeWeightStepDisplayValue, 0.01)

        vm.updateWeightUnit(WeightUnit.LBS)
        assertEquals(WeightUnit.LBS, vm.weightUnit)
        assertEquals("220.5 lbs", WorkoutMechanics.formatWeight(100.0, vm.weightUnit))
        assertEquals(5.0, vm.activeWeightStepDisplayValue, 0.01)

        vm.updateWeightStepInCurrentUnit(10.0)
        assertEquals(10.0, vm.weightIncrementLbs, 0.01)
        assertEquals(10.0 / WeightUnit.LBS.factorFromKg, vm.activeWeightStepDeltaKg, 0.001)

        // Slider volume updates float and maps setting label
        vm.updateRestTimerVolumeFloat(0.9f)
        assertEquals(0.9f, vm.restTimerVolumeFloat, 0.0001f)
        assertEquals("high", vm.restTimerVolumeSetting)

        vm.updateRestTimerVolumeFloat(0.0f)
        assertEquals("off", vm.restTimerVolumeSetting)

        vm.updateDefaultRestTimer(120)
        assertEquals(120, vm.defaultRestTimerSeconds)
    }

    @Test
    fun sampleCatalogContainsExercisesDatasetMetadataAndMappings() {
        val bench = SampleCatalog.defaultExercises.first { it.id == "ex_bench_press" }
        assertEquals("0025", bench.datasetId)
        assertEquals("Barbell Bench Press", bench.title)
        assertEquals("images/0025-EIeI8Vf.jpg", bench.imagePath)
        assertEquals("videos/0025-EIeI8Vf.gif", bench.gifPath)
        assertTrue(bench.imageUrl.startsWith(WorkoutMechanics.DATASET_RAW_BASE_URL))
        assertTrue(bench.gifUrl.startsWith(WorkoutMechanics.DATASET_RAW_BASE_URL))
        assertTrue(bench.instructions.isNotEmpty())

        assertEquals(MuscleGroup.LEGS, WorkoutMechanics.mapBodyPartToMuscleGroup("upper legs", "glutes"))
        assertEquals(MuscleGroup.ARMS, WorkoutMechanics.mapBodyPartToMuscleGroup("upper arms", "biceps"))
        assertEquals(EquipmentType.BARBELL, WorkoutMechanics.mapEquipmentString("barbell"))
        assertEquals(EquipmentType.BODYWEIGHT, WorkoutMechanics.mapEquipmentString("body weight"))
    }

    @Test
    fun routineEditChangeFolderReorderAndDeleteWork() {
        val vm = LiftTrackerViewModel()
        val firstNoFolder = vm.routines.first { it.folderName.isBlank() }
        val secondNoFolder = vm.routines.filter { it.folderName.isBlank() }[1]

        // Reorder down within uncategorized group
        vm.moveRoutine(firstNoFolder.id, 1)
        val noFolderAfterMove = vm.routines.filter { it.folderName.isBlank() }
        assertEquals(secondNoFolder.id, noFolderAfterMove[0].id)
        assertEquals(firstNoFolder.id, noFolderAfterMove[1].id)

        // Edit routine in-place
        vm.openEditRoutineEditor(firstNoFolder)
        assertEquals(firstNoFolder.id, vm.editingRoutineId)
        vm.routineEditorTitle = "Updated Push Day"
        vm.setRoutineDraftWeightInCurrentUnit(0, 0, 105.0)
        vm.setRoutineDraftReps(0, 0, 7)
        assertTrue(vm.saveRoutineDraft())
        assertFalse(vm.isRoutineEditorLayerOpen)
        val updated = vm.routines.first { it.id == firstNoFolder.id }
        assertEquals("Updated Push Day", updated.title)
        assertEquals(105.0, updated.exercises[0].targetWeightKg, 0.01)
        assertEquals(7, updated.exercises[0].targetReps)

        // Change folder
        vm.changeRoutineFolder(firstNoFolder.id, "Strength Block")
        assertEquals("Strength Block", vm.routines.first { it.id == firstNoFolder.id }.folderName)
        assertTrue(vm.expandedRoutineFolders.contains("Strength Block"))

        // Delete routine
        val beforeDelete = vm.routines.size
        vm.deleteRoutine(firstNoFolder.id)
        assertEquals(beforeDelete - 1, vm.routines.size)
        assertTrue(vm.routines.none { it.id == firstNoFolder.id })
    }

    @Test
    fun workoutSessionArchiveUnarchiveAndDeleteWork() {
        val vm = LiftTrackerViewModel()
        val initialCount = vm.workoutHistory.size
        val targetId = vm.workoutHistory.first().id
        assertFalse(vm.workoutHistory.first().archived)

        vm.toggleArchiveWorkoutSession(targetId)
        assertTrue(vm.workoutHistory.first { it.id == targetId }.archived)

        vm.toggleArchiveWorkoutSession(targetId)
        assertFalse(vm.workoutHistory.first { it.id == targetId }.archived)

        vm.deleteWorkoutSession(targetId)
        assertEquals(initialCount - 1, vm.workoutHistory.size)
        assertTrue(vm.workoutHistory.none { it.id == targetId })
    }

    @Test
    fun directNumericWeightAndRepsInputWorkInKgAndLbs() {
        val vm = LiftTrackerViewModel()
        vm.startRoutineAsWorkout(vm.routines.first())

        vm.updateWeightUnit(WeightUnit.KG)
        vm.setSetWeightInCurrentUnit(0, 0, 112.5)
        vm.setSetReps(0, 0, 12)
        assertEquals(112.5, vm.activeExercises[0].sets[0].weightKg, 0.01)
        assertEquals(12, vm.activeExercises[0].sets[0].reps)

        vm.updateWeightUnit(WeightUnit.LBS)
        vm.setSetWeightInCurrentUnit(0, 0, 225.0)
        assertEquals(225.0 / WeightUnit.LBS.factorFromKg, vm.activeExercises[0].sets[0].weightKg, 0.01)
        assertEquals("225", WorkoutMechanics.formatWeightValue(vm.activeExercises[0].sets[0].weightKg, WeightUnit.LBS))
    }

    @Test
    fun workoutDetailLayerRoutineDrawersRepTypesAndDragReorderWork() {
        val vm = LiftTrackerViewModel()

        // 1. Workout session detail layer open/close
        val sessionId = vm.workoutHistory.first().id
        assertFalse(vm.isWorkoutSessionDetailLayerOpen)
        vm.openWorkoutSessionDetailLayer(sessionId)
        assertTrue(vm.isWorkoutSessionDetailLayerOpen)
        assertEquals(sessionId, vm.selectedWorkoutSessionId)
        vm.closeWorkoutSessionDetailLayer()
        assertFalse(vm.isWorkoutSessionDetailLayerOpen)

        // 2. Routine drawer toggle
        val rtId = vm.routines.first().id
        assertFalse(vm.expandedRoutineDrawerIds.contains(rtId))
        vm.toggleRoutineDrawer(rtId)
        assertTrue(vm.expandedRoutineDrawerIds.contains(rtId))
        vm.toggleRoutineDrawer(rtId)
        assertFalse(vm.expandedRoutineDrawerIds.contains(rtId))

        // 3. Routine editor set type & rep type + drag reorder exercises
        vm.openNewRoutineEditor("Custom Hypertrophy")
        vm.addExerciseToRoutineDraft(vm.exercises[0])
        vm.addExerciseToRoutineDraft(vm.exercises[1])
        val firstExId = vm.routineEditorExercises[0].exerciseId
        val secondExId = vm.routineEditorExercises[1].exerciseId
        vm.moveRoutineDraftExercise(0, 1)
        assertEquals(secondExId, vm.routineEditorExercises[0].exerciseId)
        assertEquals(firstExId, vm.routineEditorExercises[1].exerciseId)

        vm.updateRoutineDraftSetType(0, 0, SetType.WARMUP)
        vm.updateRoutineDraftRepType(0, 0, RepType.REP_RANGE)
        vm.updateRoutineDraftRepType(0, 1, RepType.AMRAP)
        assertTrue(vm.saveRoutineDraft())

        val savedRoutine = vm.routines.first()
        assertEquals(SetType.WARMUP, savedRoutine.exercises[0].sets[0].setType)
        assertEquals(RepType.REP_RANGE, savedRoutine.exercises[0].sets[0].repType)
        assertEquals(RepType.AMRAP, savedRoutine.exercises[0].sets[1].repType)

        // 4. Starting routine preserves set types & rep types and supports active exercise drag reorder
        vm.startRoutineAsWorkout(savedRoutine)
        assertEquals(SetType.WARMUP, vm.activeExercises[0].sets[0].setType)
        assertEquals(RepType.REP_RANGE, vm.activeExercises[0].sets[0].repType)
        assertEquals(RepType.AMRAP, vm.activeExercises[0].sets[1].repType)

        val actFirst = vm.activeExercises[0].exerciseId
        val actSecond = vm.activeExercises[1].exerciseId
        vm.moveActiveWorkoutExercise(0, 1)
        assertEquals(actSecond, vm.activeExercises[0].exerciseId)
        assertEquals(actFirst, vm.activeExercises[1].exerciseId)
    }

    @Test
    fun themeConfigAndAppearanceSettingsLayerUpdateProperly() {
        val vm = LiftTrackerViewModel()
        assertEquals(ThemeMode.DARK, vm.themeConfig.mode)
        assertEquals(ThemePreset.GLASS_SPECTRUM, vm.themeConfig.preset)
        assertFalse(vm.themeConfig.amoledBlack)
        assertNull(vm.activeSettingsSubScreen)

        vm.openSettingsLayer(SettingsSection.APPEARANCE)
        assertTrue(vm.isSettingsLayerOpen)
        assertEquals(SettingsSection.APPEARANCE, vm.activeSettingsSubScreen)

        val customHex = ThemeConfig.parseHex("#FF5722")
        assertNotNull(customHex)
        assertEquals("#FF5722", ThemeConfig.toHex(customHex!!))
        assertNull(ThemeConfig.parseHex("invalid"))

        val updated = vm.themeConfig.copy(
            mode = ThemeMode.LIGHT,
            preset = ThemePreset.CUSTOM,
            amoledBlack = true,
            customPrimaryArgb = 0xFF00E5BE.toInt()
        )
        vm.updateThemeConfig(updated)
        assertEquals(ThemeMode.LIGHT, vm.themeConfig.mode)
        assertEquals(ThemePreset.CUSTOM, vm.themeConfig.preset)
        assertTrue(vm.themeConfig.amoledBlack)
        assertEquals(0xFF00E5BE.toInt(), vm.themeConfig.customPrimaryArgb)

        vm.openSettingsSubScreen(null)
        assertTrue(vm.isSettingsLayerOpen)
        assertNull(vm.activeSettingsSubScreen)

        vm.closeSettingsLayer()
        assertFalse(vm.isSettingsLayerOpen)
    }

    @Test
    fun routineStartsFromLastUsedWeightAndRepsPerSet() {
        val vm = LiftTrackerViewModel()
        val routine = vm.routines.first { it.exercises.isNotEmpty() }
        val exerciseId = routine.exercises[0].exerciseId

        // Log the routine once with new numbers on the first two sets.
        vm.startRoutineAsWorkout(routine)
        val plannedSets = vm.activeExercises[0].sets.size
        vm.setSetWeightInCurrentUnit(0, 0, 111.0)
        vm.setSetReps(0, 0, 6)
        vm.toggleSetCompleted(0, 0)
        if (plannedSets > 1) {
            vm.setSetWeightInCurrentUnit(0, 1, 113.5)
            vm.setSetReps(0, 1, 5)
            vm.toggleSetCompleted(0, 1)
        }
        vm.finishActiveWorkout()

        // The routine itself is unchanged, but the next session starts from what was done.
        assertEquals(routine, vm.routines.first { it.id == routine.id })
        vm.startRoutineAsWorkout(routine)
        val next = vm.activeExercises[0]
        assertEquals(exerciseId, next.exerciseId)
        assertEquals(plannedSets, next.sets.size)
        assertEquals(111.0, next.sets[0].weightKg, 0.01)
        assertEquals(6, next.sets[0].reps)
        assertFalse(next.sets[0].completed)
        if (plannedSets > 1) {
            assertEquals(113.5, next.sets[1].weightKg, 0.01)
            assertEquals(5, next.sets[1].reps)
        }
        // Sets beyond the ones done last time repeat the last one.
        val lastDone = if (plannedSets > 1) 113.5 else 111.0
        assertEquals(lastDone, next.sets.last { it.setType != SetType.WARMUP }.weightKg, 0.01)
        vm.discardActiveWorkout()

        // A freestyle add of the same exercise also starts from the last numbers.
        vm.startEmptyWorkoutSession()
        vm.addExerciseToActiveWorkout(vm.exercises.first { it.id == exerciseId })
        assertEquals(111.0, vm.activeExercises[0].sets[0].weightKg, 0.01)
        vm.discardActiveWorkout()
    }

    @Test
    fun exerciseDetailAndWorkoutDrawerReturnToThePreviousPage() {
        val vm = LiftTrackerViewModel()
        val routine = vm.routines.first { it.exercises.isNotEmpty() }

        // Starting a routine from the Routines tab and minimizing lands back on Routines.
        vm.selectedTab = MainTab.ROUTINES
        vm.startRoutineAsWorkout(routine)
        assertTrue(vm.isWorkoutLayerOpen)
        vm.minimizeWorkoutLayer()
        assertEquals(MainTab.ROUTINES, vm.selectedTab)

        // Exercise detail opened from the workout returns to the workout, on the same tab.
        vm.openActiveWorkoutLayer()
        vm.openExerciseDetailLayer(routine.exercises[0].exerciseId)
        assertFalse(vm.isWorkoutLayerOpen)
        vm.closeExerciseDetailLayer()
        assertTrue(vm.isWorkoutLayerOpen)
        assertEquals(MainTab.ROUTINES, vm.selectedTab)

        // Opened from a logged workout, it closes back onto that workout's detail.
        vm.minimizeWorkoutLayer()
        vm.openWorkoutSessionDetailLayer(vm.workoutHistory.first().id)
        vm.openExerciseDetailLayer(routine.exercises[0].exerciseId)
        vm.closeExerciseDetailLayer()
        assertTrue(vm.isWorkoutSessionDetailLayerOpen)
        assertFalse(vm.isWorkoutLayerOpen)
        assertEquals(MainTab.ROUTINES, vm.selectedTab)
        vm.discardActiveWorkout()
    }

    @Test
    fun removeSetInActiveSessionAndRoutineDraftRenumbersAndGuardsLastSet() {
        val vm = LiftTrackerViewModel()

        // Active workout session set removal
        vm.startEmptyWorkoutSession()
        vm.addExerciseToActiveWorkout(vm.exercises.first())
        vm.addSetToActiveExercise(0)
        // Ensure at least 3 sets
        while (vm.activeExercises[0].sets.size < 3) {
            vm.addSetToActiveExercise(0)
        }
        val initialIds = vm.activeExercises[0].sets.map { it.id }
        vm.removeSetFromActiveExercise(0, 1) // remove middle set
        val afterActiveRemove = vm.activeExercises[0].sets
        assertEquals(initialIds.size - 1, afterActiveRemove.size)
        assertFalse(afterActiveRemove.any { it.id == initialIds[1] })
        assertEquals((1..afterActiveRemove.size).toList(), afterActiveRemove.map { it.setNumber })

        // Cannot remove the sole remaining set
        while (vm.activeExercises[0].sets.size > 1) {
            vm.removeSetFromActiveExercise(0, 0)
        }
        assertEquals(1, vm.activeExercises[0].sets.size)
        vm.removeSetFromActiveExercise(0, 0)
        assertEquals(1, vm.activeExercises[0].sets.size)
        vm.discardActiveWorkout()

        // Routine draft set removal
        vm.openNewRoutineEditor("Set Removal Test")
        vm.addExerciseToRoutineDraft(vm.exercises.first())
        while (vm.routineEditorExercises[0].sets.size < 3) {
            vm.addSetToRoutineDraftExercise(0)
        }
        val draftIds = vm.routineEditorExercises[0].sets.map { it.id }
        vm.removeSetFromRoutineDraftExercise(0, 1)
        val afterDraftRemove = vm.routineEditorExercises[0].sets
        assertEquals(draftIds.size - 1, afterDraftRemove.size)
        assertFalse(afterDraftRemove.any { it.id == draftIds[1] })
        assertEquals((1..afterDraftRemove.size).toList(), afterDraftRemove.map { it.setNumber })

        while (vm.routineEditorExercises[0].sets.size > 1) {
            vm.removeSetFromRoutineDraftExercise(0, 0)
        }
        assertEquals(1, vm.routineEditorExercises[0].sets.size)
        vm.removeSetFromRoutineDraftExercise(0, 0)
        assertEquals(1, vm.routineEditorExercises[0].sets.size)
        vm.closeRoutineEditorLayer()
    }

    @Test
    fun routineEditorPromptsConfirmationOnlyWhenRealChangesExist() {
        val vm = LiftTrackerViewModel()
        val originalRoutine = vm.routines.first { it.exercises.isNotEmpty() }

        // 1. Opening and closing without edits closes immediately without confirmation
        vm.openEditRoutineEditor(originalRoutine)
        assertFalse(vm.hasUnsavedRoutineDraftChanges())
        vm.requestCloseRoutineEditorLayer()
        assertFalse(vm.isRoutineEditorLayerOpen)
        assertNull(vm.pendingConfirmation)

        // 2. Editing and reverting back to original is not a real change
        vm.openEditRoutineEditor(originalRoutine)
        vm.routineEditorTitle = "${originalRoutine.title} Edited"
        assertTrue(vm.hasUnsavedRoutineDraftChanges())
        vm.routineEditorTitle = originalRoutine.title
        assertFalse(vm.hasUnsavedRoutineDraftChanges())

        // 3. Real change (e.g. modifying reps) prompts confirmation before discarding
        val origReps = vm.routineEditorExercises[0].sets[0].reps
        vm.setRoutineDraftReps(0, 0, origReps + 2)
        assertTrue(vm.hasUnsavedRoutineDraftChanges())
        vm.requestCloseRoutineEditorLayer()
        assertTrue(vm.isRoutineEditorLayerOpen)
        assertNotNull(vm.pendingConfirmation)

        // Dismissing keeps editor open; confirming closes and preserves original routine
        vm.dismissPendingConfirmation()
        assertTrue(vm.isRoutineEditorLayerOpen)
        vm.requestCloseRoutineEditorLayer()
        vm.confirmPendingAction()
        assertFalse(vm.isRoutineEditorLayerOpen)
        val preservedEx = vm.routines.first { it.id == originalRoutine.id }.exercises[0]
        assertEquals(origReps, preservedEx.sets.firstOrNull()?.reps ?: preservedEx.targetReps)

        // 4. New empty routine closes immediately; typing a name prompts confirmation
        vm.openNewRoutineEditor()
        assertFalse(vm.hasUnsavedRoutineDraftChanges())
        vm.requestCloseRoutineEditorLayer()
        assertFalse(vm.isRoutineEditorLayerOpen)
        assertNull(vm.pendingConfirmation)

        vm.openNewRoutineEditor()
        vm.routineEditorTitle = "Brand New Routine"
        assertTrue(vm.hasUnsavedRoutineDraftChanges())
        vm.requestCloseRoutineEditorLayer()
        assertTrue(vm.isRoutineEditorLayerOpen)
        assertNotNull(vm.pendingConfirmation)
        vm.confirmPendingAction()
        assertFalse(vm.isRoutineEditorLayerOpen)
    }
}
