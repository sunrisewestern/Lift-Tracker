package com.lifttracker.app.domain

import java.util.Locale

/**
 * Matches exercise names written differently by different apps, e.g. "Bench Press (Barbell)" and
 * "Barbell Bench Press", or "Pull Up" and "Pull-up", and builds definitions for names with no match.
 */
object ExerciseMatching {
    private val WORD = Regex("[a-z0-9]+")
    private val SYNONYMS = mapOf("machine" to "lever", "triceps" to "tricep", "biceps" to "bicep")

    /** Order-independent key: lowercase words, simple plurals and equipment synonyms folded. */
    fun nameKey(title: String): Set<String> =
        WORD.findAll(title.lowercase(Locale.US).replace("'", ""))
            .map { it.value }
            .map { if (it.length > 3) it.trimEnd('s') else it }
            .map { SYNONYMS[it] ?: it }
            .toSet()

    fun customId(title: String): String =
        "custom_" + title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun guessMuscle(title: String): MuscleGroup {
        val t = title.lowercase(Locale.US)
        fun has(vararg words: String) = words.any { t.contains(it) }
        return when {
            has("leg raise", "knee raise", "crunch", "plank", "sit up", "sit-up", "twist", "ab wheel", "woodchop") -> MuscleGroup.CORE
            has("squat", "leg ", "lunge", "calf", "deadlift", "hip thrust", "glute", "hamstring", "step up") -> MuscleGroup.LEGS
            has("lateral raise", "shoulder", "overhead press", "face pull", "delt", "arnold", "upright row", "shrug") -> MuscleGroup.SHOULDERS
            has("curl", "tricep", "skullcrusher", "pushdown", "kickback") -> MuscleGroup.ARMS
            has("row", "pulldown", "pull up", "pull-up", "chin up", "chin-up", "lat ", "hang") -> MuscleGroup.BACK
            has("bench", "chest", "fly", "push up", "push-up", "dip", "pec", "hex press") -> MuscleGroup.CHEST
            else -> MuscleGroup.FULL_BODY
        }
    }

    fun customDefinition(title: String): ExerciseDefinition = ExerciseDefinition(
        id = customId(title),
        title = title,
        primaryMuscle = guessMuscle(title),
        secondaryMuscles = emptyList(),
        equipment = WorkoutMechanics.mapEquipmentString(title),
        summaryNotes = "Custom exercise added from an import.",
        instructions = emptyList(),
        coachingCues = emptyList(),
        isCustom = true
    )
}
