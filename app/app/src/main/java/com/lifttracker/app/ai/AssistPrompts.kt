package com.lifttracker.app.ai

import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutMechanics
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** What the AI filled in for an exercise. Null / empty fields were not given and keep the form's value. */
data class ExerciseSuggestion(
    val title: String?,
    val primaryMuscle: MuscleGroup?,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: EquipmentType?,
    val measure: ExerciseMeasure?,
    val targetMuscle: String?,
    val summary: String?,
    val instructions: List<String>,
    val cues: List<String>,
)

/** The fields of the exercise form, as sent to the AI when it edits an existing exercise. */
data class ExerciseFields(
    val title: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: EquipmentType,
    val measure: ExerciseMeasure,
    val targetMuscle: String,
    val summary: String,
    val instructions: List<String>,
    val cues: List<String>,
)

/** Answers of the routine set-up questionnaire. */
data class SetupAnswers(
    /** True: plan from the logged workouts instead of the experience question. */
    val useHistory: Boolean = false,
    val level: String = LEVELS[0],
    val goal: String = GOALS[0],
    val daysPerWeek: Int = 3,
    val sessionMinutes: Int = 60,
    val equipment: Set<String> = setOf(EQUIPMENT[0]),
    val notes: String = "",
) {
    companion object {
        val LEVELS = listOf("Beginner (under 6 months)", "Intermediate (6 months – 2 years)", "Advanced (2+ years)")
        val GOALS = listOf("Build muscle", "Get stronger", "Lose fat", "General fitness")
        val EQUIPMENT = listOf("Full gym", "Dumbbells only", "Home gym (barbell & rack)", "Machines & cables", "Bodyweight only")
        val MINUTES = listOf(30, 45, 60, 75, 90)
    }
}

/** Prompts and reply parsing of the AI assistants (exercise editor, routine optimizer, routine set-up). Pure. */
object AssistPrompts {
    const val SYSTEM =
        "You are the assistant inside Lift Tracker, a workout logging app. You fill in the app's forms: you reply " +
            "with exactly one JSON object in the shape the request describes, with no Markdown fences and no text " +
            "before or after it. Write free-text values in the language the user writes in. You are a careful strength " +
            "coach: suggest only safe, standard training practice, and never invent exercises that do not exist."

    fun system(extra: String): String = listOf(SYSTEM, extra.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")

    private val MUSCLES = MuscleGroup.entries.joinToString(" | ") { it.name }
    private val EQUIPMENT = EquipmentType.entries.joinToString(" | ") { it.name }
    private val MEASURES = ExerciseMeasure.entries.joinToString(" | ") { it.code }

    // ---------------------------------------------------------------- JSON reply

    /** The JSON object in a model reply, tolerating code fences and stray text around it. */
    fun extractJson(reply: String): JSONObject {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw LlmException(notJsonMessage(reply))
        return runCatching { JSONObject(reply.substring(start, end + 1)) }.getOrElse { throw LlmException(notJsonMessage(reply)) }
    }

    private fun notJsonMessage(reply: String): String {
        val said = reply.trim().replace('\n', ' ').take(160)
        return if (said.isEmpty()) "The model returned an empty answer."
        else "The model didn't answer in the expected format. Try again, or raise max output tokens for this provider. It said: \"$said\""
    }

    private fun JSONObject.text(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotEmpty() } else null

    private fun JSONObject.texts(name: String): List<String> = optJSONArray(name)?.let { a ->
        (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }
    } ?: emptyList()

    private fun muscle(name: String?): MuscleGroup? {
        val raw = name?.trim()?.uppercase(Locale.US) ?: return null
        return MuscleGroup.entries.find { it.name == raw.replace(' ', '_') || it.displayName.uppercase(Locale.US) == raw }
    }

    // ---------------------------------------------------------------- exercise

    /** [request] is what the user typed; [current] is the form as it stands (null for a new exercise). */
    fun exercise(request: String, current: ExerciseFields?): String = buildString {
        if (current == null) {
            appendLine("Describe this exercise for my exercise library: ${request.trim()}")
        } else {
            appendLine("Update this exercise in my library.")
            if (request.isNotBlank()) appendLine("What I want: ${request.trim()}")
            appendLine("Fill in whatever is empty, and keep what is already good.")
            appendLine()
            appendLine("Current values:")
            appendLine(
                JSONObject()
                    .put("title", current.title)
                    .put("primaryMuscle", current.primaryMuscle.name)
                    .put("secondaryMuscles", JSONArray(current.secondaryMuscles.map { it.name }))
                    .put("equipment", current.equipment.name)
                    .put("measure", current.measure.code)
                    .put("targetMuscle", current.targetMuscle)
                    .put("summary", current.summary)
                    .put("instructions", JSONArray(current.instructions))
                    .put("cues", JSONArray(current.cues))
                    .toString(2),
            )
        }
        appendLine()
        appendLine("Reply with this JSON object:")
        appendLine("{")
        appendLine("  \"title\": \"standard name of the exercise, equipment first, e.g. Dumbbell Incline Curl\",")
        appendLine("  \"primaryMuscle\": \"$MUSCLES\",")
        appendLine("  \"secondaryMuscles\": [\"0-3 of the same values\"],")
        appendLine("  \"equipment\": \"$EQUIPMENT\",")
        appendLine("  \"measure\": \"$MEASURES\",")
        appendLine("  \"targetMuscle\": \"the specific muscle in lowercase, e.g. biceps, lats, glutes\",")
        appendLine("  \"summary\": \"one or two sentences: what it is and what it is good for\",")
        appendLine("  \"instructions\": [\"4-7 short step-by-step instructions\"],")
        appendLine("  \"cues\": [\"3-5 coaching and safety cues\"]")
        appendLine("}")
        append(
            "measure is how a set is logged: weight_reps (loaded exercises), bodyweight_reps (pull-ups, push-ups; " +
                "added weight optional), duration (planks, hangs), weight_distance (carries, sled).",
        )
    }

    fun parseExercise(reply: String): ExerciseSuggestion {
        val o = extractJson(reply)
        return ExerciseSuggestion(
            title = o.text("title"),
            primaryMuscle = muscle(o.text("primaryMuscle")),
            secondaryMuscles = o.texts("secondaryMuscles").mapNotNull { muscle(it) }.distinct(),
            equipment = o.text("equipment")?.let { v -> EquipmentType.entries.find { it.name.equals(v, ignoreCase = true) } },
            measure = ExerciseMeasure.fromCode(o.text("measure")),
            targetMuscle = o.text("targetMuscle"),
            summary = o.text("summary"),
            instructions = o.texts("instructions"),
            cues = o.texts("cues"),
        )
    }

    /** Prompt for the picture model. [hasTemplate]: a reference picture is sent along. */
    fun exercisePicture(fields: ExerciseFields, notes: String, hasTemplate: Boolean): String = buildString {
        append("Draw an instructional illustration of the strength exercise \"${fields.title}\"")
        append(" (equipment: ${fields.equipment.displayName.lowercase(Locale.US)}")
        if (fields.targetMuscle.isNotBlank()) append("; works the ${fields.targetMuscle}")
        append("). ")
        append("Show one athlete, whole body in frame, performing the movement with correct form")
        if (fields.instructions.isNotEmpty()) {
            append(". How it is done: ")
            append(fields.instructions.take(4).joinToString(" "))
        } else {
            append(". ")
        }
        append(" Plain white background, square picture, no text, no labels, no logos, no watermark. ")
        if (hasTemplate) {
            append(
                "The attached picture is the style template: match its drawing style, colours, line work, body " +
                    "proportions, camera angle and framing exactly, so the new picture looks like part of the same " +
                    "set — but show \"${fields.title}\", not the exercise in the template. ",
            )
        } else {
            append("Style: clean flat anatomical fitness illustration with the working muscles highlighted in red. ")
        }
        if (notes.isNotBlank()) append("Also: ${notes.trim()}")
    }.trim()

    // ---------------------------------------------------------------- routines

    private fun weightKey(unit: WeightUnit) = if (unit == WeightUnit.LBS) "weightLbs" else "weightKg"

    /** A routine as compact JSON for a prompt, with loads in the athlete's unit. */
    fun routineJson(routine: RoutineTemplate, unit: WeightUnit): JSONObject = JSONObject()
        .put("title", routine.title)
        .put("notes", routine.notes)
        .put("exercises", JSONArray().apply {
            routine.exercises.forEach { ex ->
                put(
                    JSONObject()
                        .put("title", ex.exerciseTitle)
                        .put("measure", ex.measure.code)
                        .put("restSeconds", ex.restSeconds)
                        .put("sets", JSONArray().apply {
                            ex.sets.forEach { s ->
                                put(
                                    JSONObject().apply {
                                        put("type", s.setType.name.lowercase(Locale.US))
                                        if (ex.measure.usesWeight) put(weightKey(unit), Math.round(WorkoutMechanics.convertWeightForDisplay(s.weightKg, unit) * 10.0) / 10.0)
                                        if (ex.measure.usesReps) put("reps", s.reps)
                                        if (s.durationSeconds > 0) put("durationSeconds", s.durationSeconds)
                                        if (s.distanceMeters > 0.0) put("distanceMeters", s.distanceMeters)
                                    },
                                )
                            }
                        }),
                )
            }
        })

    private fun routineShape(unit: WeightUnit, withFolder: Boolean): String = buildString {
        appendLine("{")
        appendLine("  \"title\": \"routine name\",")
        if (withFolder) appendLine("  \"folder\": \"name of the program, the same for all its routines\",")
        appendLine("  \"notes\": \"one or two sentences on how to run it\",")
        appendLine("  \"exercises\": [ {")
        appendLine("    \"title\": \"exercise name\",")
        appendLine("    \"measure\": \"$MEASURES\",")
        appendLine("    \"restSeconds\": 120,")
        appendLine("    \"sets\": [ { \"type\": \"warmup | normal | failure | dropset\", \"${weightKey(unit)}\": 60, \"reps\": 8 } ]")
        appendLine("  } ]")
        append("}")
    }

    private fun routineRules(unit: WeightUnit): String =
        "Rules: list every set separately. Loads go in \"${weightKey(unit)}\" (${unit.label}); use 0 when I should pick " +
            "the load myself or the exercise needs none. Sets of duration exercises use \"durationSeconds\" instead " +
            "of reps; weight_distance sets use \"distanceMeters\". Take exercise names letter for letter from the " +
            "exercise library below whenever a suitable one is there; only name an exercise that is not in the " +
            "library when nothing in it fits."

    /** Exercise names of the library, grouped by muscle group. */
    fun libraryBlock(library: List<ExerciseDefinition>): String =
        MuscleGroup.entries.mapNotNull { group ->
            val names = library.filter { it.primaryMuscle == group }.map { it.title }.distinct()
            if (names.isEmpty()) null else "${group.displayName}: " + names.joinToString("; ")
        }.joinToString("\n")

    fun optimizeRoutine(
        routine: RoutineTemplate,
        goal: String,
        unit: WeightUnit,
        profile: String,
        performances: String,
        weekly: String,
        otherRoutines: String,
        library: String,
    ): String = buildString {
        appendLine("Improve this routine of mine. Keep what works and change only what has a reason.")
        if (goal.isNotBlank()) appendLine("What I want from it: ${goal.trim()}")
        appendLine(
            "Look at exercise selection and order, weekly volume per muscle group next to my other routines, sets, " +
                "rep ranges, rest times, and set the loads from my recent performances (progress them sensibly).",
        )
        appendLine()
        appendLine(profile)
        appendLine()
        appendLine("# The routine")
        appendLine(routineJson(routine, unit).toString(2))
        appendLine()
        appendLine("# My recent performances of its exercises")
        appendLine(performances.ifBlank { "None logged yet." })
        appendLine()
        appendLine("# Weekly totals (last 4 weeks)")
        appendLine(weekly)
        if (otherRoutines.isNotBlank()) {
            appendLine()
            appendLine("# My other routines")
            appendLine(otherRoutines)
        }
        appendLine()
        appendLine("# Reply with this JSON object")
        appendLine("{")
        appendLine("  \"summary\": \"two or three sentences: what you changed overall and why\",")
        appendLine("  \"changes\": [\"one short line per change, with its reason\"],")
        appendLine("  \"routine\": " + routineShape(unit, withFolder = false).prependIndent("  ").trimStart())
        appendLine("}")
        appendLine("\"routine\" is the complete improved routine, not only the changed parts. If it is already good, return it unchanged and say so.")
        appendLine(routineRules(unit))
        appendLine()
        appendLine("# Exercise library")
        append(library)
    }

    fun setupRoutines(
        answers: SetupAnswers,
        unit: WeightUnit,
        profile: String,
        training: String,
        library: String,
    ): String = buildString {
        appendLine("Set up a training program for me as a set of routines (one routine per training day).")
        appendLine()
        appendLine("# About me")
        if (answers.useHistory) {
            appendLine("- Experience: work it out from my training data below.")
        } else {
            appendLine("- Experience: ${answers.level}")
        }
        appendLine("- Goal: ${answers.goal}")
        appendLine("- Training days per week: ${answers.daysPerWeek}")
        appendLine("- Time per session: about ${answers.sessionMinutes} minutes, warm-up sets included")
        appendLine("- Equipment: ${answers.equipment.joinToString(", ").ifBlank { "Full gym" }}")
        if (answers.notes.isNotBlank()) appendLine("- Also: ${answers.notes.trim()}")
        appendLine(profile)
        if (answers.useHistory) {
            appendLine()
            appendLine(training)
            appendLine()
            appendLine(
                "Build on this: keep the exercises I do regularly and progress on where they fit, fix what is missing " +
                    "or unbalanced, and set the loads from my recent performances.",
            )
        } else {
            appendLine()
            appendLine("I have no training data to go by, so set the loads to 0 and say in each routine's notes how to find the starting load.")
        }
        appendLine()
        appendLine("# Reply with this JSON object")
        appendLine("{")
        appendLine("  \"summary\": \"three or four sentences: the split, why it fits me, and how to progress week to week\",")
        appendLine("  \"routines\": [ " + routineShape(unit, withFolder = true).prependIndent("  ").trimStart() + " ]")
        appendLine("}")
        appendLine("Give ${answers.daysPerWeek} routines unless a repeating split needs fewer (say so in the summary). Only use equipment I have.")
        appendLine(routineRules(unit))
        appendLine()
        appendLine("# Exercise library")
        append(library)
    }

    /** Summary, change list and the routine object of an optimizer reply. */
    class OptimizeReply(val summary: String, val changes: List<String>, val routineJson: String)

    fun parseOptimize(reply: String): OptimizeReply {
        val o = extractJson(reply)
        // A model may answer with the bare routine.
        val routine = o.optJSONObject("routine") ?: o.takeIf { it.has("exercises") }
            ?: throw LlmException("The model's answer had no routine in it. Try again.")
        return OptimizeReply(o.text("summary").orEmpty(), o.texts("changes"), routine.toString())
    }

    /** Summary and the routines array (as routine-file JSON) of a set-up reply. */
    class SetupReply(val summary: String, val routinesJson: String)

    fun parseSetup(reply: String): SetupReply {
        val o = extractJson(reply)
        val routines = o.optJSONArray("routines")?.takeIf { it.length() > 0 }
            ?: o.takeIf { it.has("exercises") }?.let { JSONArray().put(it) }
            ?: throw LlmException("The model's answer had no routines in it. Try again.")
        return SetupReply(o.text("summary").orEmpty(), JSONObject().put("routines", routines).toString())
    }
}
