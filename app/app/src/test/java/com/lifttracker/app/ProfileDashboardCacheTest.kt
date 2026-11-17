package com.lifttracker.app

import com.lifttracker.app.domain.StatPeriod
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class ProfileDashboardCacheTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun dashboardIsReusedUntilHistoryPeriodOrDayChanges() {
        val vm = LiftTrackerViewModel()
        val first = vm.profileDashboard(today, zone, DayOfWeek.MONDAY, 12)
        assertSame(first, vm.profileDashboard(today, zone, DayOfWeek.MONDAY, 12))
        assertEquals(vm.workoutHistory.size, first.all.workouts)

        assertNotSame(first, vm.profileDashboard(today, zone, DayOfWeek.SUNDAY, 12))
        assertNotSame(first, vm.profileDashboard(today, java.time.ZoneId.of("Asia/Tokyo"), DayOfWeek.MONDAY, 12))

        val next = vm.profileDashboard(today.plusDays(1), zone, DayOfWeek.MONDAY, 12)
        assertNotSame(first, next)

        vm.profilePeriod = StatPeriod.WEEK
        val week = vm.profileDashboard(today.plusDays(1), zone, DayOfWeek.MONDAY, 12)
        assertNotSame(next, week)
        assertSame(week, vm.profileDashboard(today.plusDays(1), zone, DayOfWeek.MONDAY, 12))

        vm.workoutHistory.add(0, vm.workoutHistory.first().copy(id = "extra"))
        val more = vm.profileDashboard(today.plusDays(1), zone, DayOfWeek.MONDAY, 12)
        assertNotSame(week, more)
        assertEquals(week.all.workouts + 1, more.all.workouts)
    }

    @Test
    fun muscleLoadMatchesTheUncachedComputation() {
        val vm = LiftTrackerViewModel()
        val d = vm.profileDashboard(today, zone, DayOfWeek.MONDAY, 12)
        d.shown.forEach { bucket ->
            val sessions = com.lifttracker.app.domain.WorkoutStats.between(vm.workoutHistory, bucket.start, bucket.endExclusive, zone)
            assertEquals(com.lifttracker.app.domain.WorkoutStats.muscleLoad(sessions, vm::muscleLoadOf), d.muscleLoad(bucket))
        }
    }
}
