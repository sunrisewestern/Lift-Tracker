package com.lifttracker.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifttracker.app.ui.LiftTrackerRoot
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.MainTab
import com.lifttracker.app.viewmodel.ProfileLayer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders the main screens with the built-in sample data (no storage attached, so the device's
 * real workouts are never read) and saves each one as a PNG for the README.
 *
 * Pull them afterwards with:
 * adb exec-out run-as com.lifttracker.app tar cf - files/readme-screenshots | tar xf -
 * (or from /sdcard/Android/data/com.lifttracker.app/files/readme-screenshots)
 */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshotsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir: File by lazy {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "readme-screenshots").apply { mkdirs() }
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        Thread.sleep(600) // let images and animations settle
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outDir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("screenshot $name was not written", file.length() > 0)
    }

    /**
     * The built-in sample workouts are a few sessions from one week; repeat them, three a week over
     * the last eight weeks with slowly rising weights, so the dashboard, statistics and calendar
     * have something to show.
     */
    private fun spreadSampleWorkoutsOverRecentWeeks(vm: LiftTrackerViewModel) {
        val templates = vm.workoutHistory.sortedBy { it.startTimeEpochSeconds }
        if (templates.isEmpty()) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val sessions = (0 until 8).flatMap { week ->
            (0 until 3).map { i ->
                val t = templates[(week * 3 + i) % templates.size]
                val start = today.minusDays(week * 7L + i * 2L + 1L).atTime(18, 0).atZone(zone).toEpochSecond()
                val factor = 1.0 - week * 0.015
                t.copy(
                    id = "${t.id}_w${week}_$i",
                    startTimeEpochSeconds = start,
                    endTimeEpochSeconds = start + t.durationSeconds,
                    exercises = t.exercises.map { ex ->
                        ex.copy(sets = ex.sets.map { set -> set.copy(weightKg = Math.round(set.weightKg * factor * 2) / 2.0) })
                    }
                )
            }
        }
        vm.workoutHistory.clear()
        vm.workoutHistory.addAll(sessions.sortedByDescending { it.startTimeEpochSeconds })
    }

    @Test
    fun captureMainScreens() {
        // Starting a workout asks for the notification permission; a system dialog would cover the app.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val vm = LiftTrackerViewModel().apply { appContext = context }
        spreadSampleWorkoutsOverRecentWeeks(vm)
        rule.setContent { LiftTrackerRoot(vm) }

        rule.runOnIdle { vm.selectedTab = MainTab.WORKOUT }
        shoot("01_workout")

        rule.runOnIdle { vm.openActiveWorkoutLayer() }
        shoot("02_active_workout")
        rule.runOnIdle { vm.minimizeWorkoutLayer(); vm.discardActiveWorkout() }

        rule.runOnIdle { vm.selectedTab = MainTab.ROUTINES }
        shoot("03_routines")

        rule.runOnIdle { vm.selectedTab = MainTab.EXERCISES }
        shoot("04_exercises")

        rule.runOnIdle { vm.openExerciseDetailLayer("ex_bench_press") }
        shoot("05_exercise_detail")
        rule.runOnIdle { vm.isExerciseDetailLayerOpen = false }

        rule.runOnIdle { vm.selectedTab = MainTab.PROFILE }
        shoot("06_profile")

        rule.runOnIdle { vm.openProfileLayer(ProfileLayer.STATISTICS) }
        shoot("07_statistics")
        rule.runOnIdle { vm.openProfileLayer(ProfileLayer.CALENDAR) }
        shoot("08_calendar")

        rule.runOnIdle { vm.closeProfileLayer(); vm.selectedTab = MainTab.COACH }
        shoot("09_coach")

        rule.runOnIdle { vm.openSettingsLayer() }
        shoot("10_settings")
    }
}
