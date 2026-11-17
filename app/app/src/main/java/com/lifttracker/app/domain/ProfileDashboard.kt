package com.lifttracker.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the Profile tab shows for one workout history, period and day, computed once.
 * The view model keeps the last one, so opening the tab or tapping a bar doesn't recompute it.
 */
class ProfileDashboard(
    val sessions: List<WorkoutSession>,
    val period: StatPeriod,
    val today: LocalDate,
    val zone: ZoneId,
    val firstDayOfWeek: DayOfWeek,
    val bucketCount: Int,
    private val loadOf: (WorkoutExerciseEntry) -> Map<BodyMuscle, Double>,
) {
    init {
        require(bucketCount >= 1) { "bucketCount must be at least 1" }
    }

    /** [bucketCount] periods plus one older one, oldest first, so the first bar has something to compare with. */
    val buckets: List<StatBucket> = WorkoutStats.buckets(sessions, period, bucketCount + 1, today, zone, firstDayOfWeek)
    val shown: List<StatBucket> get() = buckets.subList(1, buckets.size)
    val all: WorkoutTotals = WorkoutStats.totals(sessions)
    private val days: Set<LocalDate> = WorkoutStats.workoutDays(sessions, zone).keys
    val weekStreak: Int = WorkoutStats.weekStreak(days, today, firstDayOfWeek)
    val restDays: Int? = WorkoutStats.restDays(days, today)

    private val muscleLoads = HashMap<LocalDate, Map<BodyMuscle, Double>>()

    /** Working sets per muscle in [bucket]; worked out the first time a bar is shown, then kept. */
    fun muscleLoad(bucket: StatBucket): Map<BodyMuscle, Double> = muscleLoads.getOrPut(bucket.start) {
        WorkoutStats.muscleLoad(WorkoutStats.between(sessions, bucket.start, bucket.endExclusive, zone), loadOf)
    }
}
