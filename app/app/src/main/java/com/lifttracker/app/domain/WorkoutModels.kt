package com.lifttracker.app.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Core domain constants & calculations extracted from the target APK:
 * - TimerNotificationModule.timerVolumeFromSetting
 * - WorkoutStorageModule$SQLiteWorkoutStorage
 * - HealthConnect
 * - WearOSConnectorModule & WearListenerService
 */
object WorkoutMechanics {
    // hasaneyldrm/exercises-dataset raw GitHub media base URL
    const val DATASET_REPO_URL: String = "https://github.com/hasaneyldrm/exercises-dataset"
    const val DATASET_RAW_BASE_URL: String = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/"
    const val DATASET_ASSET_JSON: String = "exercises_dataset.json"

    // TimerNotificationModule.timerVolumeFromSetting(String) -> Float
    const val TIMER_VOLUME_OFF: Float = 0.0f
    const val TIMER_VOLUME_LOW: Float = 0.2f
    const val TIMER_VOLUME_NORMAL: Float = 0.6f
    const val TIMER_VOLUME_HIGH: Float = 1.0f

    // TimerNotificationModule.startOrUpdateForegroundService fallback indices
    const val DEFAULT_EXERCISE_INDEX: Int = -1
    const val DEFAULT_SET_INDEX: Int = -1

    // Health Connect (values of HealthConnectClient.SDK_*)
    const val HEALTH_CONNECT_PROVIDER_PACKAGE: String = "com.google.android.apps.healthdata"
    const val HEALTH_CONNECT_SDK_UNAVAILABLE: Int = 1
    const val HEALTH_CONNECT_SDK_UPDATE_REQUIRED: Int = 2
    const val HEALTH_CONNECT_SDK_AVAILABLE: Int = 3
    const val HEALTH_CONNECT_PERMISSION_COUNT: Int = 7

    // SQLiteWorkoutStorage constants
    const val SQLITE_STORAGE_DIR: String = "BigStorage"
    const val SQLITE_DB_NAME: String = "workouts.sqlite"
    const val SQLITE_OPEN_CREATE_IF_NECESSARY: Int = 0x10000000 // 268435456
    const val SQLITE_CONFLICT_REPLACE: Int = 5

    // Epley 1RM divisor
    const val EPLEY_1RM_DIVISOR: Double = 30.0

    fun timerVolumeFromSetting(setting: String?): Float {
        return when (setting) {
            "off" -> TIMER_VOLUME_OFF
            "low" -> TIMER_VOLUME_LOW
            "normal", null -> TIMER_VOLUME_NORMAL
            "high" -> TIMER_VOLUME_HIGH
            else -> TIMER_VOLUME_HIGH
        }
    }

    /**
     * Epley 1RM estimation formula: weight * (1 + reps / 30.0) for reps > 1
     */
    fun estimateOneRepMax(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || weightKg <= 0.0) return 0.0
        if (reps == 1) return weightKg
        return weightKg * (1.0 + reps.toDouble() / EPLEY_1RM_DIVISOR)
    }

    /**
     * Set volume in kg (warmup sets are excluded from working volume)
     */
    fun calculateSetVolume(weightKg: Double, reps: Int, setType: SetType, completed: Boolean): Double {
        if (!completed || setType == SetType.WARMUP) return 0.0
        return weightKg * reps.toDouble()
    }

    /**
     * Estimated workout calories burned for Health Connect TotalCaloriesBurnedRecord
     */
    fun estimateWorkoutCalories(durationSeconds: Long, completedWorkingSets: Int): Double {
        val minutes = durationSeconds.toDouble() / 60.0
        return ((minutes * 6.2) + (completedWorkingSets * 4.5)).coerceAtLeast(15.0)
    }

    fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    fun convertWeightForDisplay(weightKg: Double, unit: WeightUnit): Double {
        return when (unit) {
            WeightUnit.KG -> weightKg
            WeightUnit.LBS -> ((weightKg * unit.factorFromKg) * 2.0).roundToInt() / 2.0
        }
    }

    fun formatWeightValue(weightKg: Double, unit: WeightUnit): String {
        val converted = convertWeightForDisplay(weightKg, unit)
        return if (converted % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", converted)
        } else {
            String.format(Locale.US, "%.1f", converted)
        }
    }

    fun formatWeight(weightKg: Double, unit: WeightUnit): String {
        return "${formatWeightValue(weightKg, unit)} ${unit.label}"
    }

    /** "2:30", "0:45" — or "Off" for a disabled rest timer. */
    fun formatRest(seconds: Int): String =
        if (seconds <= 0) "Off" else String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    fun formatClock(seconds: Int): String =
        String.format(Locale.US, "%d:%02d", seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

    fun formatDistance(meters: Double): String {
        val m = meters.coerceAtLeast(0.0)
        return if (m % 1.0 == 0.0) "${m.toLong()} m" else String.format(Locale.US, "%.1f m", m)
    }

    /**
     * The completed sets of [exerciseId] in the most recent workout that has any. Exercises are
     * matched by id, or by title for workouts imported under another id.
     */
    fun lastPerformedSets(
        history: List<WorkoutSession>,
        exerciseId: String,
        exerciseTitle: String
    ): List<WorkoutSetEntry> = lastPerformedSets(history, ExerciseFamily(setOf(exerciseId), listOf(exerciseTitle)))

    /** [lastPerformedSets] for an exercise together with its aliases. */
    fun lastPerformedSets(history: List<WorkoutSession>, family: ExerciseFamily): List<WorkoutSetEntry> = history
        .sortedByDescending { it.endTimeEpochSeconds }
        .firstNotNullOfOrNull { session ->
            session.exercises
                .filter { family.matches(it) }
                .flatMap { ex -> ex.sets.filter { it.completed } }
                .takeIf { it.isNotEmpty() }
        }
        .orEmpty()

    /**
     * Pairs each planned set with the set it should start from: the set done last time at the same
     * position among warm-ups or among working sets. Extra planned sets repeat the last one of
     * their kind; a set with nothing to start from is returned unchanged and paired with null.
     * The plan keeps its own set count, set types and rep types.
     */
    fun carryOverLastSets(
        planned: List<WorkoutSetEntry>,
        last: List<WorkoutSetEntry>
    ): List<Pair<WorkoutSetEntry, WorkoutSetEntry?>> {
        val (lastWarmups, lastWorking) = last.partition { it.setType == SetType.WARMUP }
        var warmupIndex = 0
        var workingIndex = 0
        return planned.map { set ->
            val pool = if (set.setType == SetType.WARMUP) lastWarmups else lastWorking
            val position = if (set.setType == SetType.WARMUP) warmupIndex++ else workingIndex++
            val from = pool.getOrNull(position) ?: pool.lastOrNull()
            if (from == null) {
                set to null
            } else {
                set.copy(
                    weightKg = from.weightKg,
                    reps = from.reps,
                    durationSeconds = from.durationSeconds,
                    distanceMeters = from.distanceMeters
                ) to from
            }
        }
    }

    /** One-line description of a set for the way its exercise is tracked, e.g. "100 kg × 8", "0:45", "40 kg × 30 m". */
    fun formatSetSummary(set: WorkoutSetEntry, measure: ExerciseMeasure, unit: WeightUnit): String {
        val load = formatWeight(set.weightKg, unit)
        return when (measure) {
            ExerciseMeasure.WEIGHT_REPS ->
                if (set.repType == RepType.SECONDS) "$load × ${set.reps} sec" else "$load × ${set.reps}"
            ExerciseMeasure.BODYWEIGHT_REPS ->
                if (set.weightKg > 0.0) "+$load × ${set.reps}" else "${set.reps} reps"
            ExerciseMeasure.DURATION -> formatClock(set.durationSeconds)
            ExerciseMeasure.WEIGHT_DISTANCE ->
                if (set.weightKg > 0.0) "$load × ${formatDistance(set.distanceMeters)}" else formatDistance(set.distanceMeters)
        }
    }

    fun mapBodyPartToMuscleGroup(bodyPart: String, target: String = ""): MuscleGroup {
        val bp = bodyPart.trim().lowercase(Locale.US)
        val tg = target.trim().lowercase(Locale.US)
        return when {
            bp == "chest" -> MuscleGroup.CHEST
            bp == "back" -> MuscleGroup.BACK
            bp == "shoulders" || bp == "neck" -> MuscleGroup.SHOULDERS
            bp == "upper arms" || bp == "lower arms" -> MuscleGroup.ARMS
            bp == "upper legs" || bp == "lower legs" -> MuscleGroup.LEGS
            bp == "waist" -> MuscleGroup.CORE
            bp == "cardio" -> MuscleGroup.FULL_BODY
            tg.contains("pector") -> MuscleGroup.CHEST
            tg.contains("lat") || tg.contains("trap") || tg.contains("back") -> MuscleGroup.BACK
            tg.contains("delt") -> MuscleGroup.SHOULDERS
            tg.contains("bicep") || tg.contains("tricep") || tg.contains("forearm") -> MuscleGroup.ARMS
            tg.contains("glute") || tg.contains("quad") || tg.contains("hamstring") || tg.contains("calv") -> MuscleGroup.LEGS
            tg.contains("ab") || tg.contains("spine") -> MuscleGroup.CORE
            else -> MuscleGroup.FULL_BODY
        }
    }

    fun mapEquipmentString(equipment: String): EquipmentType {
        val eq = equipment.trim().lowercase(Locale.US)
        return when {
            eq.contains("barbell") -> EquipmentType.BARBELL
            eq.contains("dumbbell") -> EquipmentType.DUMBBELL
            eq.contains("cable") || eq.contains("rope") -> EquipmentType.CABLE
            eq.contains("kettlebell") -> EquipmentType.KETTLEBELL
            eq.contains("machine") || eq.contains("hammer") || eq.contains("bike") ||
                eq.contains("elliptical") || eq.contains("stepmill") || eq.contains("sled") ||
                eq.contains("skierg") || eq.contains("ergometer") -> EquipmentType.MACHINE
            else -> EquipmentType.BODYWEIGHT
        }
    }
}

enum class WeightUnit(val label: String, val factorFromKg: Double) {
    KG("kg", 1.0),
    LBS("lbs", 2.20462)
}

enum class SetType(val code: String, val label: String) {
    WARMUP("W", "Warmup"),
    NORMAL("N", "Normal"),
    FAILURE("F", "Failure"),
    DROPSET("D", "Dropset")
}

enum class RepType(val code: String, val label: String, val unitLabel: String) {
    REPS("REPS", "Fixed Reps", "reps"),
    REP_RANGE("RANGE", "Rep Range", "reps"),
    AMRAP("AMRAP", "AMRAP (To Failure)", "AMRAP"),
    SECONDS("SEC", "Duration (Seconds)", "sec")
}

/**
 * How an exercise is tracked. Decides which inputs a set shows:
 * weight + reps, reps only (bodyweight, added weight optional), time only, or weight + distance.
 */
enum class ExerciseMeasure(val code: String, val label: String, val shortLabel: String) {
    WEIGHT_REPS("weight_reps", "Weight & Reps", "Weight × Reps"),
    BODYWEIGHT_REPS("bodyweight_reps", "Bodyweight Reps (weight optional)", "Bodyweight"),
    DURATION("duration", "Duration", "Duration"),
    WEIGHT_DISTANCE("weight_distance", "Weight & Distance", "Weight × Distance");

    val usesReps: Boolean get() = this == WEIGHT_REPS || this == BODYWEIGHT_REPS
    val usesWeight: Boolean get() = this != DURATION
    val weightOptional: Boolean get() = this == BODYWEIGHT_REPS

    companion object {
        fun fromCode(code: String?): ExerciseMeasure? {
            val c = code?.trim()?.lowercase(Locale.US) ?: return null
            return entries.find { it.code == c || it.name.lowercase(Locale.US) == c }
        }

        private val LOADED_TAG = Regex(
            "\\b(barbell|dumbbell|machine|cable|kettlebell|smith|assisted|weighted|plate|plates|lever|band|sled|ez)\\b"
        )
        private val CARRY = Regex("\\b(farmer|farmers|farmer's|carry|yoke)\\b")
        private val HOLD = Regex("\\b(dead hang|active hang|bar hang|plank|wall sit|l-sit|l sit|hold|isometric)\\b")
        private val BODYWEIGHT = Regex(
            "\\b(pull[- ]?ups?|chin[- ]?ups?|dips?|push[- ]?ups?|sit[- ]?ups?|muscle[- ]?ups?|leg raises?|knee raises?|" +
                "crunch|crunches|burpees?|pistol squat|inverted row|mountain climbers?|jumping jacks?|toes to bar)\\b"
        )

        /** Best guess from the exercise name (and equipment, when known). The user can override it per exercise. */
        fun infer(title: String, equipment: EquipmentType? = null): ExerciseMeasure {
            val t = title.lowercase(Locale.US)
            if (CARRY.containsMatchIn(t)) return WEIGHT_DISTANCE
            if (t.trim() == "hang" || HOLD.containsMatchIn(t)) return DURATION
            if (LOADED_TAG.containsMatchIn(t)) return WEIGHT_REPS
            if (BODYWEIGHT.containsMatchIn(t)) return BODYWEIGHT_REPS
            return if (equipment == EquipmentType.BODYWEIGHT) BODYWEIGHT_REPS else WEIGHT_REPS
        }
    }
}

enum class MuscleGroup(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    LEGS("Legs"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    CORE("Core"),
    FULL_BODY("Full Body")
}

enum class EquipmentType(val displayName: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell")
}

data class ExerciseDefinition(
    val id: String,
    val datasetId: String = "",
    val title: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: EquipmentType,
    val targetMuscle: String = "",
    val bodyPart: String = "",
    val imagePath: String = "",
    val gifPath: String = "",
    val summaryNotes: String,
    val instructions: List<String>,
    val coachingCues: List<String>,
    /** Created by the user (or by an import); stored on the phone, editable, and part of backups. */
    val isCustom: Boolean = false,
    /** Secondary muscles by name, as the dataset lists them ("triceps", "rear deltoids"). */
    val secondaryTargets: List<String> = emptyList(),
    /** For a custom exercise that is another name for an existing one: that exercise's id. See [ExerciseAliases]. */
    val aliasOfId: String = ""
) {
    /** "#0025" for dataset exercises, "Custom" for the user's own. */
    val sourceLabel: String
        get() = if (isCustom || datasetId.isBlank()) "Custom" else "#$datasetId"
    val imageUrl: String
        get() = if (imagePath.isNotBlank()) "${WorkoutMechanics.DATASET_RAW_BASE_URL}$imagePath" else ""
    val gifUrl: String
        get() = if (gifPath.isNotBlank()) "${WorkoutMechanics.DATASET_RAW_BASE_URL}$gifPath" else ""
}

data class WorkoutSetEntry(
    val id: String,
    val setNumber: Int,
    val setType: SetType = SetType.NORMAL,
    val previousText: String = "—",
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val completed: Boolean = false,
    val repType: RepType = RepType.REPS,
    /** Used by [ExerciseMeasure.DURATION] exercises (e.g. dead hang). */
    val durationSeconds: Int = 0,
    /** Used by [ExerciseMeasure.WEIGHT_DISTANCE] exercises (e.g. farmers walk). */
    val distanceMeters: Double = 0.0
)

data class WorkoutExerciseEntry(
    val id: String,
    val exerciseId: String,
    val exerciseTitle: String,
    val notes: String = "",
    /** Rest after each set of this exercise; 0 turns the rest timer off for it. */
    val restTimerSeconds: Int = 90,
    val sets: List<WorkoutSetEntry>,
    val measure: ExerciseMeasure = ExerciseMeasure.WEIGHT_REPS
)

data class WorkoutSession(
    val id: String,
    val title: String,
    val startTimeEpochSeconds: Long,
    val endTimeEpochSeconds: Long,
    val notes: String = "",
    val exercises: List<WorkoutExerciseEntry>,
    /** 0 when no heart rate was recorded for the session. */
    val averageHeartRateBpm: Int = 0,
    val caloriesBurnedKcal: Double = 0.0,
    val syncedToHealthConnect: Boolean = false,
    val archived: Boolean = false,
    /** Heart rate samples streamed from the watch during the session. */
    val heartRateSamples: List<HeartRateSamplePoint> = emptyList()
) {
    val durationSeconds: Long
        get() = (endTimeEpochSeconds - startTimeEpochSeconds).coerceAtLeast(0L)

    val totalVolumeKg: Double
        get() = exercises.sumOf { ex ->
            ex.sets.sumOf { s -> WorkoutMechanics.calculateSetVolume(s.weightKg, s.reps, s.setType, s.completed) }
        }

    val completedSetsCount: Int
        get() = exercises.sumOf { ex -> ex.sets.count { it.completed } }
}

data class ExerciseHistoryPoint(
    val workoutId: String,
    val workoutTitle: String,
    val dateLabel: String,
    val timestampEpochSeconds: Long,
    val bestWeightKg: Double,
    val best1RmKg: Double,
    val sessionVolumeKg: Double,
    val setsSummary: List<String>,
    /** The name(s) the exercise was logged under in that workout; differs from the exercise's own for an alias. */
    val loggedAs: String = ""
)

data class RoutineTemplateExercise(
    val exerciseId: String,
    val exerciseTitle: String,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double,
    val restSeconds: Int,
    val repType: RepType = RepType.REPS,
    val sets: List<WorkoutSetEntry> = emptyList(),
    val measure: ExerciseMeasure = ExerciseMeasure.WEIGHT_REPS
)

data class RoutineTemplate(
    val id: String,
    val folderName: String = "",
    val title: String,
    val notes: String = "",
    val exercises: List<RoutineTemplateExercise>
)

data class HealthConnectPermissionItem(
    val key: String,
    val androidPermission: String,
    val recordClassName: String,
    val description: String,
    val granted: Boolean
)

data class BodyMeasurementRecord(
    val id: String,
    val recordTypeKey: String, // "write_weight" / "read_weight" or "write_bodyFat" / "read_bodyFat"
    val value: Double,
    val unit: String,
    val dateLabel: String,
    val epochSeconds: Long,
    val source: String
)

data class HeartRateSamplePoint(
    val heartRateTimestampMs: Long,
    val heartRate: Double
)

data class HealthConnectExportLog(
    val id: String,
    val recordType: String,
    val summary: String,
    val details: String,
    val timestampLabel: String
)

data class WearWatchStatus(
    val hasConnectedWatch: Boolean,
    val hasWatchAppInstalled: Boolean,
    val isWatchWithAppReachable: Boolean,
    val watchNodeId: String = "",
    val watchModelName: String = "No watch connected"
)

data class WearMessageLog(
    val id: String,
    val direction: String, // "PHONE -> WATCH" or "WATCH -> PHONE"
    val path: String,
    val payload: String,
    val sourceNodeId: String,
    val timestampLabel: String
)
