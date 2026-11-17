package com.lifttracker.app

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.ui.ExerciseEditorLayerScreen
import com.lifttracker.app.ui.ProfileScreen
import com.lifttracker.app.ui.theme.LiftTrackerTheme
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses a view model with only the built-in sample data (no storage attached), so the device's
 * real workouts and exercises are never read or changed.
 */
@RunWith(AndroidJUnit4::class)
class AliasFillAndProfileTest {
    @get:Rule
    val rule = createComposeRule()

    private fun sampleViewModel() = LiftTrackerViewModel().apply {
        // Only for loading bundled pictures from assets; no database or settings are attached.
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun aliasFillCopiesDetailsAndPictureButKeepsTheName() {
        val vm = sampleViewModel()
        vm.assist.openNewExercise(title = "Flat bench")
        vm.assist.updateExercise { it.copy(aliasOfId = "ex_bench_press", primaryMuscle = MuscleGroup.LEGS, equipment = EquipmentType.DUMBBELL) }

        rule.setContent { LiftTrackerTheme { ExerciseEditorLayerScreen(vm) } }
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag("alias_fill_button"))
        rule.onNodeWithTag("alias_fill_button").assertIsDisplayed().performClick()

        rule.waitUntil(timeoutMillis = 10_000) { vm.assist.exerciseEditor?.newImage != null && vm.assist.exerciseBusy == null }
        val state = vm.assist.exerciseEditor!!
        assertEquals("Flat bench", state.title)
        assertEquals("ex_bench_press", state.aliasOfId)
        assertEquals(MuscleGroup.CHEST, state.primaryMuscle)
        assertEquals(EquipmentType.BARBELL, state.equipment)
        assertEquals("pectorals", state.targetMuscle)
        assertTrue(state.instructionsText.isNotBlank())
        assertNotNull(state.newImage)
    }

    @Test
    fun profileShowsCachedDashboard() {
        val vm = sampleViewModel()
        rule.setContent { LiftTrackerTheme { ProfileScreen(vm) } }
        rule.onNodeWithText("Your training").assertIsDisplayed()
        rule.onNodeWithText("Workout summary").assertIsDisplayed()
        rule.onNodeWithText("${vm.workoutHistory.size} workouts", substring = true).assertIsDisplayed()
    }
}
