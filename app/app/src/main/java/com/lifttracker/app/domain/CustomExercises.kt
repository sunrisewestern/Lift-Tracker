package com.lifttracker.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's own exercises as JSON (the `custom_exercises_json` preference and the backup files).
 * A custom exercise's picture is a file in the app's `custom_exercise_images` folder; its
 * [ExerciseDefinition.imagePath] is that file's name behind [IMAGE_PREFIX].
 */
object CustomExerciseCodec {
    const val IMAGE_PREFIX = "custom/"
    const val IMAGE_DIR = "custom_exercise_images"

    /** File name of a custom exercise's picture, or null when it has none. */
    fun imageFileName(def: ExerciseDefinition): String? =
        def.imagePath.takeIf { it.startsWith(IMAGE_PREFIX) }?.removePrefix(IMAGE_PREFIX)?.takeIf { it.isNotBlank() }

    fun encode(exercises: List<ExerciseDefinition>): String = JSONArray().apply {
        exercises.forEach { e ->
            put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("primaryMuscle", e.primaryMuscle.name)
                    .put("secondaryMuscles", JSONArray(e.secondaryMuscles.map { it.name }))
                    .put("equipment", e.equipment.name)
                    .put("targetMuscle", e.targetMuscle)
                    .put("bodyPart", e.bodyPart)
                    .put("image", imageFileName(e).orEmpty())
                    .put("summary", e.summaryNotes)
                    .put("instructions", JSONArray(e.instructions))
                    .put("cues", JSONArray(e.coachingCues))
                    .put("aliasOf", e.aliasOfId),
            )
        }
    }.toString()

    fun decode(json: String?): List<ExerciseDefinition> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val title = o.optString("title").ifBlank { return@mapNotNull null }
            // Only a bare file name is accepted, so a crafted backup can't point outside the image folder.
            val image = o.optString("image").substringAfterLast('/').substringAfterLast('\\')
            ExerciseDefinition(
                id = id,
                title = title,
                primaryMuscle = MuscleGroup.entries.find { it.name == o.optString("primaryMuscle") } ?: ExerciseMatching.guessMuscle(title),
                secondaryMuscles = strings(o.optJSONArray("secondaryMuscles")).mapNotNull { n -> MuscleGroup.entries.find { it.name == n } },
                equipment = EquipmentType.entries.find { it.name == o.optString("equipment") } ?: WorkoutMechanics.mapEquipmentString(title),
                targetMuscle = o.optString("targetMuscle"),
                bodyPart = o.optString("bodyPart"),
                imagePath = if (image.isBlank()) "" else IMAGE_PREFIX + image,
                summaryNotes = o.optString("summary"),
                instructions = strings(o.optJSONArray("instructions")),
                coachingCues = strings(o.optJSONArray("cues")),
                isCustom = true,
                aliasOfId = o.optString("aliasOf").takeIf { it != id }.orEmpty(),
            )
        }.distinctBy { it.id }
    }

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
}
