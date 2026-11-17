package com.lifttracker.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Exercise aliases: a custom exercise can stand for another exercise ("Flat Bench" for
 * "Barbell Bench Press"). An alias and its target are one exercise for history, records and
 * the "previous" values of a set.
 */
object ExerciseAliases {
    /** alias id -> target id, for every exercise that is an alias. */
    fun aliasMap(exercises: Collection<ExerciseDefinition>): Map<String, String> =
        exercises.filter { it.aliasOfId.isNotBlank() && it.aliasOfId != it.id }.associate { it.id to it.aliasOfId }

    /** The exercise [id] finally stands for. Chains are followed; a loop ends where it started repeating. */
    fun canonicalId(id: String, aliases: Map<String, String>): String {
        var current = id
        val seen = HashSet<String>()
        while (seen.add(current)) current = aliases[current] ?: return current
        return current
    }

    /** [id], the exercise it stands for, and every other alias of that exercise. */
    fun familyIds(id: String, aliases: Map<String, String>): Set<String> {
        val root = canonicalId(id, aliases)
        return buildSet {
            add(id)
            add(root)
            aliases.keys.filterTo(this) { canonicalId(it, aliases) == root }
        }
    }

    /** True when making [id] an alias of [targetId] is allowed (no self-alias, no loop). */
    fun canAlias(id: String?, targetId: String, aliases: Map<String, String>): Boolean =
        id == null || (targetId != id && canonicalId(targetId, aliases - id) != id)
}

/** The exercises counted as one: matched by id, or by name for workouts imported under another id. */
class ExerciseFamily(val ids: Set<String>, titles: Collection<String>) {
    private val titleKeys = titles.map { it.trim().lowercase(Locale.US) }.filter { it.isNotEmpty() }.toSet()

    fun matches(entry: WorkoutExerciseEntry): Boolean =
        entry.exerciseId in ids || entry.exerciseTitle.trim().lowercase(Locale.US) in titleKeys
}

/** A muscle drawn on the body heatmap and listed in the monthly report. */
enum class BodyMuscle(val displayName: String, val group: MuscleGroup) {
    CHEST("Chest", MuscleGroup.CHEST),
    SHOULDERS("Shoulders", MuscleGroup.SHOULDERS),
    BICEPS("Biceps", MuscleGroup.ARMS),
    TRICEPS("Triceps", MuscleGroup.ARMS),
    FOREARMS("Forearms", MuscleGroup.ARMS),
    ABS("Abs", MuscleGroup.CORE),
    OBLIQUES("Obliques", MuscleGroup.CORE),
    LATS("Lats", MuscleGroup.BACK),
    UPPER_BACK("Upper Back", MuscleGroup.BACK),
    TRAPS("Traps", MuscleGroup.BACK),
    LOWER_BACK("Lower Back", MuscleGroup.BACK),
    GLUTES("Glutes", MuscleGroup.LEGS),
    QUADS("Quads", MuscleGroup.LEGS),
    HAMSTRINGS("Hamstrings", MuscleGroup.LEGS),
    ADDUCTORS("Adductors", MuscleGroup.LEGS),
    CALVES("Calves", MuscleGroup.LEGS);

    companion object {
        /** Maps a muscle name as the exercise dataset (or the user) writes it; null when it names no drawn muscle. */
        fun fromName(name: String): BodyMuscle? {
            val n = name.trim().lowercase(Locale.US)
            fun has(vararg parts: String) = parts.any { n.contains(it) }
            return when {
                n.isEmpty() || has("cardio") -> null
                has("lower back", "spine", "erector") -> LOWER_BACK
                has("upper back", "rhomboid") -> UPPER_BACK
                has("trap", "levator", "neck", "sternocleido") -> TRAPS
                has("latissimus") || n.startsWith("lat") -> LATS
                has("pector", "chest") -> CHEST
                has("delt", "shoulder", "rotator") -> SHOULDERS
                has("tricep") -> TRICEPS
                has("bicep", "brachialis") -> BICEPS
                has("forearm", "wrist", "grip", "hand") -> FOREARMS
                has("oblique") -> OBLIQUES
                has("abs", "abdominal", "core", "hip flexor", "serratus") -> ABS
                has("glute", "abductor") -> GLUTES
                has("adductor", "groin", "inner thigh") -> ADDUCTORS
                has("quad") -> QUADS
                has("hamstring") -> HAMSTRINGS
                has("calv", "calf", "soleus", "ankle", "shin", "feet") -> CALVES
                has("back") -> UPPER_BACK
                else -> null
            }
        }

        /** The muscles a whole muscle group stands for when an exercise names nothing more precise. */
        fun ofGroup(group: MuscleGroup): List<BodyMuscle> = when (group) {
            MuscleGroup.CHEST -> listOf(CHEST)
            MuscleGroup.BACK -> listOf(LATS, UPPER_BACK)
            MuscleGroup.LEGS -> listOf(QUADS, HAMSTRINGS, GLUTES)
            MuscleGroup.SHOULDERS -> listOf(SHOULDERS)
            MuscleGroup.ARMS -> listOf(BICEPS, TRICEPS)
            MuscleGroup.CORE -> listOf(ABS)
            MuscleGroup.FULL_BODY -> emptyList()
        }

        /**
         * How much one set of [def] counts for each muscle: 1 for the target muscle, 0.5 for each
         * secondary muscle. A target that is only known as a group is shared between its muscles.
         */
        fun loadPerSet(def: ExerciseDefinition): Map<BodyMuscle, Double> {
            val load = LinkedHashMap<BodyMuscle, Double>()
            val primary = fromName(def.targetMuscle)
            if (primary != null) {
                load[primary] = 1.0
            } else {
                val muscles = ofGroup(def.primaryMuscle)
                muscles.forEach { load[it] = 1.0 / muscles.size }
            }
            val secondary = def.secondaryTargets.mapNotNull { fromName(it) }.distinct()
                .ifEmpty { def.secondaryMuscles.flatMap { ofGroup(it).take(1) } }
            secondary.forEach { m -> if (m !in load) load[m] = 0.5 }
            return load
        }

        /** [loadPerSet] for an exercise that is no longer in the library, guessed from its name. */
        fun loadPerSet(title: String): Map<BodyMuscle, Double> {
            val muscles = ofGroup(ExerciseMatching.guessMuscle(title))
            return muscles.associateWith { 1.0 / muscles.size }
        }
    }
}

enum class StatPeriod(val label: String, val adjective: String) {
    WEEK("Week", "Weekly"),
    MONTH("Month", "Monthly"),
    QUARTER("Quarter", "Quarterly"),
    YEAR("Year", "Yearly");

    /** First day of the period [date] falls in. */
    fun startOf(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = when (this) {
        WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        MONTH -> date.withDayOfMonth(1)
        QUARTER -> LocalDate.of(date.year, ((date.monthValue - 1) / 3) * 3 + 1, 1)
        YEAR -> LocalDate.of(date.year, 1, 1)
    }

    /** [start] moved by [n] periods. */
    fun shift(start: LocalDate, n: Long): LocalDate = when (this) {
        WEEK -> start.plusWeeks(n)
        MONTH -> start.plusMonths(n)
        QUARTER -> start.plusMonths(3 * n)
        YEAR -> start.plusYears(n)
    }
}

enum class StatMetric(val label: String) {
    WORKOUTS("Workouts"),
    DURATION("Duration"),
    VOLUME("Volume"),
    SETS("Sets"),
    REPS("Reps")
}

data class WorkoutTotals(
    val workouts: Int = 0,
    val durationSeconds: Long = 0L,
    val volumeKg: Double = 0.0,
    val sets: Int = 0,
    val reps: Int = 0
) {
    /** The metric as a number, for charts: workouts, seconds, kg, sets or reps. */
    fun value(metric: StatMetric): Double = when (metric) {
        StatMetric.WORKOUTS -> workouts.toDouble()
        StatMetric.DURATION -> durationSeconds.toDouble()
        StatMetric.VOLUME -> volumeKg
        StatMetric.SETS -> sets.toDouble()
        StatMetric.REPS -> reps.toDouble()
    }
}

/** Totals of the days from [start] up to, not including, [endExclusive]. */
data class StatBucket(val start: LocalDate, val endExclusive: LocalDate, val totals: WorkoutTotals)

/** Workout totals per period, muscle distribution, and the calendar numbers of the Profile pages. */
object WorkoutStats {
    fun dateOf(session: WorkoutSession, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(session.startTimeEpochSeconds).atZone(zone).toLocalDate()

    fun totals(sessions: Collection<WorkoutSession>): WorkoutTotals = WorkoutTotals(
        workouts = sessions.size,
        durationSeconds = sessions.sumOf { it.durationSeconds },
        volumeKg = sessions.sumOf { it.totalVolumeKg },
        sets = sessions.sumOf { it.completedSetsCount },
        reps = sessions.sumOf { s ->
            s.exercises.filter { it.measure.usesReps }.sumOf { ex -> ex.sets.filter { it.completed }.sumOf { it.reps } }
        }
    )

    fun between(
        sessions: Collection<WorkoutSession>,
        start: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId
    ): List<WorkoutSession> = sessions.filter { val d = dateOf(it, zone); !d.isBefore(start) && d.isBefore(endExclusive) }

    /** The last [count] periods, oldest first; the last one is the period [today] is in. */
    fun buckets(
        sessions: Collection<WorkoutSession>,
        period: StatPeriod,
        count: Int,
        today: LocalDate,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek
    ): List<StatBucket> {
        val currentStart = period.startOf(today, firstDayOfWeek)
        val byStart = sessions.groupBy { period.startOf(dateOf(it, zone), firstDayOfWeek) }
        return (count - 1 downTo 0).map { back ->
            val start = period.shift(currentStart, -back.toLong())
            StatBucket(start, period.shift(start, 1), totals(byStart[start].orEmpty()))
        }
    }

    /** Working sets per muscle: completed sets that are not warm-ups, weighted by [loadOf] the exercise. */
    fun muscleLoad(
        sessions: Collection<WorkoutSession>,
        loadOf: (WorkoutExerciseEntry) -> Map<BodyMuscle, Double>
    ): Map<BodyMuscle, Double> {
        val load = HashMap<BodyMuscle, Double>()
        sessions.forEach { session ->
            session.exercises.forEach { entry ->
                val sets = entry.sets.count { it.completed && it.setType != SetType.WARMUP }
                if (sets > 0) loadOf(entry).forEach { (muscle, perSet) -> load[muscle] = (load[muscle] ?: 0.0) + perSet * sets }
            }
        }
        return load
    }

    fun groupLoad(muscleLoad: Map<BodyMuscle, Double>): Map<MuscleGroup, Double> =
        muscleLoad.entries.groupBy({ it.key.group }, { it.value }).mapValues { it.value.sum() }

    fun workoutDays(sessions: Collection<WorkoutSession>, zone: ZoneId): Map<LocalDate, List<WorkoutSession>> =
        sessions.sortedBy { it.startTimeEpochSeconds }.groupBy { dateOf(it, zone) }

    /**
     * Weeks in a row with at least one workout, ending with this week. A week that has no workout
     * yet does not break the streak until it is over.
     */
    fun weekStreak(days: Collection<LocalDate>, today: LocalDate, firstDayOfWeek: DayOfWeek): Int {
        val weeks = days.map { StatPeriod.WEEK.startOf(it, firstDayOfWeek) }.toSet()
        var week = StatPeriod.WEEK.startOf(today, firstDayOfWeek)
        if (week !in weeks) week = week.minusWeeks(1)
        var streak = 0
        while (week in weeks) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    /** Full days without a workout since the last one; null when nothing was ever logged. */
    fun restDays(days: Collection<LocalDate>, today: LocalDate): Int? {
        val last = days.filter { !it.isAfter(today) }.maxOrNull() ?: return null
        return (ChronoUnit.DAYS.between(last, today) - 1).coerceAtLeast(0).toInt()
    }
}

/** A set that holds a record, and when it was done. */
data class SetRecord(val set: WorkoutSetEntry, val epochSeconds: Long, val value: Double)

/** One workout's working sets of an exercise. */
data class ExerciseSessionPoint(
    val workoutId: String,
    val epochSeconds: Long,
    val sets: List<WorkoutSetEntry>,
    val bestWeightKg: Double,
    val best1RmKg: Double,
    val volumeKg: Double,
    val totalReps: Int,
    val maxReps: Int,
    val longestSeconds: Int,
    val totalSeconds: Int,
    val longestMeters: Double,
    val totalMeters: Double
)

/** What the progression chart of an exercise can plot. */
enum class ExerciseChartMetric(val label: String) {
    ONE_REP_MAX("Est. 1RM"),
    HEAVIEST("Heaviest"),
    VOLUME("Volume"),
    REPS("Reps"),
    MAX_REPS("Best set reps"),
    DURATION("Longest"),
    DISTANCE("Distance");

    val isWeight: Boolean get() = this == ONE_REP_MAX || this == HEAVIEST || this == VOLUME

    fun value(point: ExerciseSessionPoint): Double = when (this) {
        ONE_REP_MAX -> point.best1RmKg
        HEAVIEST -> point.bestWeightKg
        VOLUME -> point.volumeKg
        REPS -> point.totalReps.toDouble()
        MAX_REPS -> point.maxReps.toDouble()
        DURATION -> point.longestSeconds.toDouble()
        DISTANCE -> point.longestMeters
    }

    companion object {
        fun forMeasure(measure: ExerciseMeasure): List<ExerciseChartMetric> = when (measure) {
            ExerciseMeasure.WEIGHT_REPS -> listOf(ONE_REP_MAX, HEAVIEST, VOLUME, REPS)
            ExerciseMeasure.BODYWEIGHT_REPS -> listOf(MAX_REPS, REPS, HEAVIEST)
            ExerciseMeasure.DURATION -> listOf(DURATION)
            ExerciseMeasure.WEIGHT_DISTANCE -> listOf(HEAVIEST, DISTANCE)
        }
    }
}

/** Everything the Summary tab of an exercise shows; built from the logged workouts only. */
data class ExerciseStats(
    val measure: ExerciseMeasure,
    /** Oldest first. */
    val points: List<ExerciseSessionPoint>,
    val heaviest: SetRecord?,
    val best1Rm: SetRecord?,
    val bestSetVolume: SetRecord?,
    val mostReps: SetRecord?,
    val longestHold: SetRecord?,
    val longestDistance: SetRecord?,
    val bestSession: ExerciseSessionPoint?,
    /** Heaviest weight lifted for each rep count, fewest reps first. */
    val repRecords: List<SetRecord>
) {
    val sessions: Int get() = points.size
    val totalSets: Int get() = points.sumOf { it.sets.size }
    val totalReps: Int get() = points.sumOf { it.totalReps }
    val totalVolumeKg: Double get() = points.sumOf { it.volumeKg }
    val totalSeconds: Int get() = points.sumOf { it.totalSeconds }
    val totalMeters: Double get() = points.sumOf { it.totalMeters }
    val firstEpochSeconds: Long? get() = points.firstOrNull()?.epochSeconds
    val lastEpochSeconds: Long? get() = points.lastOrNull()?.epochSeconds

    companion object {
        /**
         * Stats of the exercises in [family] over [history]. Only completed sets that are not
         * warm-ups count. Ties go to the earliest set, the one that first set the record.
         */
        fun compute(history: Collection<WorkoutSession>, family: ExerciseFamily, measure: ExerciseMeasure): ExerciseStats {
            val usesReps = measure.usesReps
            val points = history.sortedBy { it.endTimeEpochSeconds }.mapNotNull { session ->
                val sets = session.exercises.filter { family.matches(it) }
                    .flatMap { it.sets }
                    .filter { it.completed && it.setType != SetType.WARMUP }
                if (sets.isEmpty()) return@mapNotNull null
                ExerciseSessionPoint(
                    workoutId = session.id,
                    epochSeconds = session.endTimeEpochSeconds,
                    sets = sets,
                    bestWeightKg = sets.maxOf { it.weightKg },
                    best1RmKg = if (usesReps) sets.maxOf { WorkoutMechanics.estimateOneRepMax(it.weightKg, it.reps) } else 0.0,
                    volumeKg = if (usesReps) sets.sumOf { it.weightKg * it.reps } else 0.0,
                    totalReps = if (usesReps) sets.sumOf { it.reps } else 0,
                    maxReps = if (usesReps) sets.maxOf { it.reps } else 0,
                    longestSeconds = sets.maxOf { it.durationSeconds },
                    totalSeconds = sets.sumOf { it.durationSeconds },
                    longestMeters = sets.maxOf { it.distanceMeters },
                    totalMeters = sets.sumOf { it.distanceMeters }
                )
            }
            val allSets = points.flatMap { p -> p.sets.map { it to p.epochSeconds } }
            fun best(value: (WorkoutSetEntry) -> Double): SetRecord? =
                allSets.map { (set, at) -> SetRecord(set, at, value(set)) }.filter { it.value > 0.0 }.maxByOrNull { it.value }

            val weighted = usesReps || measure == ExerciseMeasure.WEIGHT_DISTANCE
            return ExerciseStats(
                measure = measure,
                points = points,
                heaviest = if (weighted) best { it.weightKg } else null,
                best1Rm = if (usesReps) best { WorkoutMechanics.estimateOneRepMax(it.weightKg, it.reps) } else null,
                bestSetVolume = if (usesReps) best { it.weightKg * it.reps } else null,
                mostReps = if (usesReps) best { it.reps.toDouble() } else null,
                longestHold = if (measure == ExerciseMeasure.DURATION) best { it.durationSeconds.toDouble() } else null,
                longestDistance = if (measure == ExerciseMeasure.WEIGHT_DISTANCE) best { it.distanceMeters } else null,
                bestSession = when (measure) {
                    ExerciseMeasure.WEIGHT_REPS -> points.filter { it.volumeKg > 0.0 }.maxByOrNull { it.volumeKg }
                    ExerciseMeasure.BODYWEIGHT_REPS -> points.filter { it.totalReps > 0 }.maxByOrNull { it.totalReps }
                    ExerciseMeasure.DURATION -> points.filter { it.totalSeconds > 0 }.maxByOrNull { it.totalSeconds }
                    ExerciseMeasure.WEIGHT_DISTANCE -> points.filter { it.totalMeters > 0.0 }.maxByOrNull { it.totalMeters }
                },
                repRecords = if (measure == ExerciseMeasure.WEIGHT_REPS) {
                    allSets.filter { (set, _) -> set.reps > 0 && set.weightKg > 0.0 }
                        .groupBy { it.first.reps }
                        .map { (_, sets) -> sets.maxBy { it.first.weightKg }.let { (set, at) -> SetRecord(set, at, set.weightKg) } }
                        .sortedBy { it.set.reps }
                        // A record is only worth showing when no set with more reps was at least as heavy.
                        .let { records -> records.filter { r -> records.none { it.set.reps > r.set.reps && it.value >= r.value } } }
                } else emptyList()
            )
        }
    }
}
