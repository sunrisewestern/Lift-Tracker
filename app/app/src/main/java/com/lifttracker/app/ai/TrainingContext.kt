package com.lifttracker.app.ai

import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RepType
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the AI layer reads from the app. Implemented by the view model. */
interface AiDataSource {
    val history: List<WorkoutSession>
    val routineTemplates: List<RoutineTemplate>
    val unit: WeightUnit
    val bodyWeightKg: Double?
    val bodyFatPercent: Double?
    fun muscleOf(exerciseId: String, title: String): MuscleGroup?
    fun exerciseOf(exerciseId: String, title: String): ExerciseDefinition?
}

/**
 * Turns logged workouts into compact plain text for prompts. Pure functions: everything they
 * need is passed in, so they can be unit-tested without Android.
 */
object TrainingContext {
    private const val DAY_SECONDS = 86_400L
    private val DATE = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.US)

    private fun date(epochSeconds: Long, zone: ZoneId): String =
        DATE.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    private fun w(kg: Double, unit: WeightUnit) = WorkoutMechanics.formatWeightValue(kg, unit)

    private fun rpe(v: Double?): String =
        v?.let { " @" + if (it % 1.0 == 0.0) "${it.toInt()}" else String.format(Locale.US, "%.1f", it) } ?: ""

    private fun setText(s: WorkoutSetEntry, unit: WeightUnit): String {
        val tag = if (s.setType == SetType.NORMAL) "" else "${s.setType.label.lowercase(Locale.US)} "
        val amount = if (s.repType == RepType.SECONDS) "${s.reps}s" else "${s.reps}"
        val load = if (s.weightKg > 0.0) "${w(s.weightKg, unit)}×" else ""
        // Time- and distance-tracked sets (dead hang, farmers walk) have no reps.
        if (s.reps == 0 && s.distanceMeters > 0.0) return "$tag$load${WorkoutMechanics.formatDistance(s.distanceMeters).replace(" ", "")}${rpe(s.rpe)}"
        if (s.reps == 0 && s.durationSeconds > 0) return "$tag${s.durationSeconds}s${rpe(s.rpe)}"
        return "$tag$load$amount${rpe(s.rpe)}"
    }

    /** "Bench Press: warmup 60×10, 100×6 @8, 100×5 @9" — completed sets only. */
    private fun exerciseLine(ex: WorkoutExerciseEntry, unit: WeightUnit): String? {
        val done = ex.sets.filter { it.completed }
        if (done.isEmpty()) return null
        return "${ex.exerciseTitle}: " + done.joinToString(", ") { setText(it, unit) }
    }

    private fun best1Rm(sets: List<WorkoutSetEntry>): Double = sets
        .filter { it.completed && it.setType != SetType.WARMUP && it.repType != RepType.SECONDS }
        .maxOfOrNull { WorkoutMechanics.estimateOneRepMax(it.weightKg, it.reps) } ?: 0.0

    fun profile(unit: WeightUnit, bodyWeightKg: Double?, bodyFatPercent: Double?): String = buildString {
        append("Weights below are in ${unit.label}; RPE is written as @8.")
        bodyWeightKg?.let { append(" Body weight: ${WorkoutMechanics.formatWeight(it, unit)}.") }
        bodyFatPercent?.let { append(String.format(Locale.US, " Body fat: %.1f%%.", it)) }
    }

    /** Every set of one workout, including sets that were planned but not completed. */
    fun sessionDetail(session: WorkoutSession, unit: WeightUnit, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            appendLine("Workout: ${session.title}")
            appendLine("Date: ${date(session.endTimeEpochSeconds, zone)}")
            appendLine("Duration: ${WorkoutMechanics.formatDuration(session.durationSeconds)}")
            appendLine("Working volume: ${WorkoutMechanics.formatWeight(session.totalVolumeKg, unit)}")
            if (session.averageHeartRateBpm > 0) appendLine("Average heart rate: ${session.averageHeartRateBpm} bpm")
            if (session.notes.isNotBlank()) appendLine("Notes: ${session.notes}")
            session.exercises.forEach { ex ->
                appendLine()
                append(ex.exerciseTitle)
                if (ex.notes.isNotBlank()) append(" (${ex.notes})")
                appendLine(" — rest ${ex.restTimerSeconds}s")
                ex.sets.forEach { s ->
                    append("  ${s.setNumber}. ${setText(s, unit)}")
                    appendLine(if (s.completed) "" else " (not completed)")
                }
            }
        }.trimEnd()

    /** One block per workout of the last [days] days, newest first, capped at [maxSessions]. */
    fun recentWorkouts(
        history: List<WorkoutSession>,
        days: Int,
        nowEpochSeconds: Long,
        unit: WeightUnit,
        zone: ZoneId = ZoneId.systemDefault(),
        maxSessions: Int = 24,
    ): String {
        val from = nowEpochSeconds - days * DAY_SECONDS
        val sessions = history.filter { it.endTimeEpochSeconds >= from }
            .sortedByDescending { it.endTimeEpochSeconds }
            .take(maxSessions)
        if (sessions.isEmpty()) return "No workouts logged in the last $days days."
        return sessions.joinToString("\n\n") { s ->
            buildString {
                append("${date(s.endTimeEpochSeconds, zone)} — ${s.title} ")
                append("(${s.durationSeconds / 60} min, volume ${w(s.totalVolumeKg, unit)}")
                if (s.averageHeartRateBpm > 0) append(", avg HR ${s.averageHeartRateBpm}")
                append(")")
                if (s.notes.isNotBlank()) append("\n  Notes: ${s.notes}")
                s.exercises.mapNotNull { exerciseLine(it, unit) }.forEach { append("\n  $it") }
            }
        }
    }

    /** Sessions, working volume and hard sets per muscle group for each of the last [weeks] weeks. */
    fun weeklySummary(
        history: List<WorkoutSession>,
        weeks: Int,
        nowEpochSeconds: Long,
        unit: WeightUnit,
        muscleOf: (exerciseId: String, title: String) -> MuscleGroup?,
    ): String {
        val lines = (0 until weeks).map { i ->
            val to = nowEpochSeconds - i * 7 * DAY_SECONDS
            val from = to - 7 * DAY_SECONDS
            val sessions = history.filter { it.endTimeEpochSeconds > from && it.endTimeEpochSeconds <= to }
            val label = if (i == 0) "Last 7 days" else "${i * 7 + 1}-${(i + 1) * 7} days ago"
            if (sessions.isEmpty()) return@map "$label: no workouts"
            val setsByMuscle = sortedMapOf<MuscleGroup, Int>()
            sessions.forEach { s ->
                s.exercises.forEach { ex ->
                    val hard = ex.sets.count { it.completed && it.setType != SetType.WARMUP }
                    val muscle = muscleOf(ex.exerciseId, ex.exerciseTitle)
                    if (hard > 0 && muscle != null) setsByMuscle.merge(muscle, hard, Int::plus)
                }
            }
            val muscles = setsByMuscle.entries.joinToString(", ") { "${it.key.displayName} ${it.value}" }
            "$label: ${sessions.size} workouts, volume ${w(sessions.sumOf { it.totalVolumeKg }, unit)}" +
                if (muscles.isEmpty()) "" else "; hard sets — $muscles"
        }
        return lines.joinToString("\n")
    }

    /**
     * Per exercise: how often it was trained, the best estimated 1RM, and the estimated 1RM of the
     * first and latest sessions so a trend is visible. Most-trained exercises first.
     */
    fun exerciseProgress(
        history: List<WorkoutSession>,
        unit: WeightUnit,
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = 30,
    ): String {
        data class Point(val time: Long, val e1rm: Double)
        val byExercise = LinkedHashMap<String, Pair<String, MutableList<Point>>>()
        history.sortedBy { it.endTimeEpochSeconds }.forEach { s ->
            s.exercises.groupBy { it.exerciseId }.forEach { (id, blocks) ->
                val e1rm = best1Rm(blocks.flatMap { it.sets })
                if (e1rm > 0.0) {
                    byExercise.getOrPut(id) { blocks.first().exerciseTitle to mutableListOf() }
                        .second.add(Point(s.endTimeEpochSeconds, e1rm))
                }
            }
        }
        if (byExercise.isEmpty()) return "No completed weighted sets yet."
        return byExercise.values.sortedByDescending { it.second.size }.take(limit).joinToString("\n") { (title, pts) ->
            val best = pts.maxBy { it.e1rm }
            val last = pts.last()
            buildString {
                append("$title: ${pts.size} sessions, best e1RM ${w(best.e1rm, unit)} (${date(best.time, zone)})")
                if (pts.size > 1) append(", first ${w(pts.first().e1rm, unit)} → latest ${w(last.e1rm, unit)}")
                append(", last trained ${date(last.time, zone)}")
            }
        }
    }

    /** The previous [perExercise] performances of each exercise in [session], for comparison. */
    fun previousPerformances(
        session: WorkoutSession,
        history: List<WorkoutSession>,
        unit: WeightUnit,
        zone: ZoneId = ZoneId.systemDefault(),
        perExercise: Int = 3,
    ): String {
        val earlier = history
            .filter { it.id != session.id && it.endTimeEpochSeconds < session.endTimeEpochSeconds }
            .sortedByDescending { it.endTimeEpochSeconds }
        val blocks = session.exercises.distinctBy { it.exerciseId }.mapNotNull { ex ->
            val lines = earlier.mapNotNull { s ->
                val done = s.exercises.filter { it.exerciseId == ex.exerciseId }.flatMap { it.sets }.filter { it.completed }
                if (done.isEmpty()) null
                else "  ${date(s.endTimeEpochSeconds, zone)}: " + done.joinToString(", ") { setText(it, unit) }
            }.take(perExercise)
            if (lines.isEmpty()) "${ex.exerciseTitle}: first time logged" else "${ex.exerciseTitle}:\n" + lines.joinToString("\n")
        }
        return blocks.joinToString("\n")
    }

    /** The latest [perExercise] logged performances of each of [exercises] (id to title), newest first. */
    fun performancesOf(
        exercises: List<Pair<String, String>>,
        history: List<WorkoutSession>,
        unit: WeightUnit,
        zone: ZoneId = ZoneId.systemDefault(),
        perExercise: Int = 4,
    ): String {
        val sessions = history.sortedByDescending { it.endTimeEpochSeconds }
        return exercises.distinctBy { it.first }.joinToString("\n") { (id, title) ->
            val lines = sessions.mapNotNull { s ->
                val done = s.exercises.filter { it.exerciseId == id }.flatMap { it.sets }.filter { it.completed }
                if (done.isEmpty()) null
                else "  ${date(s.endTimeEpochSeconds, zone)}: " + done.joinToString(", ") { setText(it, unit) }
            }.take(perExercise)
            if (lines.isEmpty()) "$title: never logged" else "$title:\n" + lines.joinToString("\n")
        }
    }

    fun routines(routines: List<RoutineTemplate>, unit: WeightUnit, limit: Int = 20): String {
        if (routines.isEmpty()) return "No saved routines."
        return routines.take(limit).joinToString("\n") { r ->
            val folder = if (r.folderName.isBlank()) "" else " [${r.folderName}]"
            "${r.title}$folder: " + r.exercises.joinToString("; ") { e ->
                val load = if (e.targetWeightKg > 0.0) " @ ${w(e.targetWeightKg, unit)}" else ""
                "${e.exerciseTitle} ${e.targetSets}×${e.targetReps}$load"
            }
        }
    }

    /** One routine in full: every exercise with its targets and rest. */
    fun routineDetail(r: RoutineTemplate, unit: WeightUnit): String = buildString {
        append("Routine: ${r.title}")
        if (r.folderName.isNotBlank()) append(" [${r.folderName}]")
        appendLine()
        if (r.notes.isNotBlank()) appendLine("Notes: ${r.notes}")
        r.exercises.forEachIndexed { i, e ->
            val amount = if (e.repType == RepType.SECONDS) "${e.targetReps}s" else "${e.targetReps}"
            val load = if (e.targetWeightKg > 0.0) " @ ${w(e.targetWeightKg, unit)}" else ""
            appendLine("${i + 1}. ${e.exerciseTitle}: ${e.targetSets}×$amount$load, rest ${e.restSeconds}s")
        }
    }.trimEnd()

    /**
     * The routines, exercises and workouts the athlete selected for a chat, in detail. Items that
     * no longer exist in the app are skipped. Empty when nothing is selected.
     */
    fun focusBlock(data: AiDataSource, items: List<AiContextItem>, zone: ZoneId = ZoneId.systemDefault()): String {
        val unit = data.unit
        val sections = items.mapNotNull { item ->
            when (item.kind) {
                AiContextItem.ROUTINE -> data.routineTemplates.find { it.id == item.id }?.let { r ->
                    "## " + routineDetail(r, unit) + "\n\nRecent performances of its exercises:\n" +
                        performancesOf(r.exercises.map { it.exerciseId to it.exerciseTitle }, data.history, unit, zone, 3)
                }
                AiContextItem.EXERCISE -> buildString {
                    appendLine("## Exercise: ${item.title}")
                    data.exerciseOf(item.id, item.title)?.let { def ->
                        append("Primary muscle: ${def.primaryMuscle.displayName}")
                        if (def.secondaryMuscles.isNotEmpty()) {
                            append("; secondary: ${def.secondaryMuscles.joinToString(", ") { it.displayName }}")
                        }
                        appendLine("; equipment: ${def.equipment.displayName}")
                    }
                    val inRoutines = data.routineTemplates.filter { r -> r.exercises.any { it.exerciseId == item.id } }
                    if (inRoutines.isNotEmpty()) appendLine("Used in routines: ${inRoutines.joinToString(", ") { it.title }}")
                    appendLine("Logged performances, newest first:")
                    append(performancesOf(listOf(item.id to item.title), data.history, unit, zone, 10))
                }
                AiContextItem.WORKOUT -> data.history.find { it.id == item.id }?.let { "## " + sessionDetail(it, unit, zone) }
                else -> null
            }
        }
        if (sections.isEmpty()) return ""
        return "# Focus of this chat\nThe athlete selected the items below; treat questions as being about them " +
            "unless they say otherwise.\n\n" + sections.joinToString("\n\n")
    }

    /** Everything the coach gets about the athlete's training, as one block of the system prompt. */
    fun trainingBlock(data: AiDataSource, nowEpochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            appendLine("# Athlete's training data (from the Lift Tracker app)")
            appendLine("Today is ${date(nowEpochSeconds, zone)}. ${profile(data.unit, data.bodyWeightKg, data.bodyFatPercent)}")
            appendLine()
            appendLine("## Weekly totals (last 8 weeks)")
            appendLine(weeklySummary(data.history, 8, nowEpochSeconds, data.unit, data::muscleOf))
            appendLine()
            appendLine("## Progress per exercise (estimated 1RM, Epley)")
            appendLine(exerciseProgress(data.history, data.unit, zone))
            appendLine()
            appendLine("## Workouts of the last 28 days")
            appendLine(recentWorkouts(data.history, 28, nowEpochSeconds, data.unit, zone))
            appendLine()
            appendLine("## Saved routines")
            append(routines(data.routineTemplates, data.unit))
        }
}

/** Built-in prompts. */
object CoachPrompts {
    const val SYSTEM =
        "You are a strength coach inside Lift Tracker, a workout logging app. The person you are talking to " +
            "is the athlete whose training data you are given. Base what you say on that data: point at the " +
            "actual sessions, loads, reps and RPE behind each observation, and say so when the data is too thin " +
            "to tell. Give concrete next steps (exercise, sets, reps, load) in the athlete's weight unit. " +
            "Answer in the language the athlete writes in, in Markdown, and keep it short enough to read on a phone. " +
            "You are not a doctor: for pain or injury, suggest seeing a professional."

    fun system(extra: String, vararg blocks: String): String =
        (listOf(SYSTEM, extra.trim()) + blocks.map { it.trim() }).filter { it.isNotEmpty() }.joinToString("\n\n")

    fun analyzeWorkout(sessionDetail: String, previous: String, weekly: String, profile: String): String =
        "Analyze this workout. Cover: how it went compared with my previous sessions of the same exercises " +
            "(progress, stalls, drops), whether effort and volume were appropriate, anything that stands out, " +
            "and what to change next time I run it (specific loads and reps).\n\n" +
            "$profile\n\n# This workout\n$sessionDetail\n\n# Previous performances of these exercises\n$previous\n\n" +
            "# Weekly totals (last 4 weeks)\n$weekly"

    val QUICK_PROMPTS = listOf(
        "Review my last 4 weeks" to
            "Review my training over the last 4 weeks: volume and frequency per muscle group, progress on my " +
            "main lifts, and the top 3 things I should change.",
        "Plan my next workout" to
            "Based on what I trained recently and how recovered each muscle group should be, plan my next " +
            "workout: exercises, sets, reps and loads.",
        "Where am I stalling?" to
            "Which lifts have stalled or regressed, why do you think that is, and how should I break through?",
        "Am I balanced?" to
            "Is my training balanced between push, pull and legs? Point out neglected muscle groups and how to fix it.",
    )
}
