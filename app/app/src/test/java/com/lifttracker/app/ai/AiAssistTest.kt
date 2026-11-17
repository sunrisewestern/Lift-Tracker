package com.lifttracker.app.ai

import com.lifttracker.app.domain.CustomExerciseCodec
import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.domain.ExerciseMatching
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.RoutineTemplateExercise
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutSetEntry
import com.lifttracker.app.export.ResolvedExercise
import com.lifttracker.app.export.RoutineCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistTest {
    private val resolver = { _: String, title: String -> ResolvedExercise(ExerciseMatching.customId(title), ExerciseMeasure.infer(title)) }

    @Test
    fun exerciseReplyIsReadThroughFencesAndChatter() {
        val reply = """
            Sure! Here it is:
            ```json
            {"title": "Landmine Press", "primaryMuscle": "shoulders", "secondaryMuscles": ["CHEST", "arms", "wings"],
             "equipment": "barbell", "measure": "weight_reps", "targetMuscle": "delts",
             "summary": "A pressing variation.", "instructions": ["Kneel.", " ", "Press."], "cues": ["Brace."]}
            ```
        """.trimIndent()
        val s = AssistPrompts.parseExercise(reply)
        assertEquals("Landmine Press", s.title)
        assertEquals(MuscleGroup.SHOULDERS, s.primaryMuscle)
        assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.ARMS), s.secondaryMuscles)
        assertEquals(EquipmentType.BARBELL, s.equipment)
        assertEquals(ExerciseMeasure.WEIGHT_REPS, s.measure)
        assertEquals(listOf("Kneel.", "Press."), s.instructions)
    }

    @Test
    fun unknownValuesAreLeftForTheFormToKeep() {
        val s = AssistPrompts.parseExercise("""{"title": "X", "primaryMuscle": "tail", "equipment": "trampoline", "measure": "vibes"}""")
        assertNull(s.primaryMuscle)
        assertNull(s.equipment)
        assertNull(s.measure)
        assertTrue(s.instructions.isEmpty())
    }

    @Test(expected = LlmException::class)
    fun proseInsteadOfJsonIsAnError() {
        AssistPrompts.parseExercise("I can't help with that.")
    }

    @Test
    fun optimizeReplyBecomesARoutine() {
        val reply = """{"summary": "More back work.", "changes": ["Added rows: no pulling before"],
            "routine": {"title": "Push A", "notes": "", "exercises": [
              {"title": "Barbell Bench Press", "measure": "weight_reps", "restSeconds": 150,
               "sets": [{"type": "warmup", "weightLbs": 135, "reps": 8}, {"type": "normal", "weightLbs": 225, "reps": 5}]},
              {"title": "Plank", "measure": "duration", "restSeconds": 60, "sets": [{"type": "normal", "durationSeconds": 45}]}]}}"""
        val parsed = AssistPrompts.parseOptimize(reply)
        assertEquals("More back work.", parsed.summary)
        assertEquals(1, parsed.changes.size)
        val routine = RoutineCodec.parseRoutines(parsed.routineJson, resolver).single()
        assertEquals(2, routine.exercises.size)
        val bench = routine.exercises[0]
        assertEquals(SetType.WARMUP, bench.sets[0].setType)
        assertEquals(102.06, bench.sets[1].weightKg, 0.01) // 225 lbs
        assertEquals(150, bench.restSeconds)
        assertEquals(45, routine.exercises[1].sets[0].durationSeconds)
        assertEquals(ExerciseMeasure.DURATION, routine.exercises[1].measure)
    }

    @Test
    fun setupReplyBecomesRoutinesInOneFolder() {
        val reply = """{"summary": "Upper/lower.", "routines": [
            {"title": "Upper", "folder": "AI Plan", "notes": "n", "exercises": [{"title": "Pull Up", "measure": "bodyweight_reps", "sets": [{"reps": 8}, {"reps": 8}]}]},
            {"title": "Lower", "folder": "AI Plan", "exercises": [{"title": "Barbell Full Squat", "sets": [{"weightKg": 0, "reps": 5}]}]}]}"""
        val parsed = AssistPrompts.parseSetup(reply)
        val routines = RoutineCodec.parseRoutines(parsed.routinesJson, resolver, defaultRestSeconds = 90)
        assertEquals(listOf("Upper", "Lower"), routines.map { it.title })
        assertTrue(routines.all { it.folderName == "AI Plan" })
        assertEquals(2, routines[0].exercises[0].sets.size)
        assertEquals(90, routines[1].exercises[0].restSeconds)
    }

    @Test
    fun promptsUseTheAthletesWeightUnit() {
        val routine = RoutineTemplate(
            id = "r1", title = "Push", exercises = listOf(
                RoutineTemplateExercise(
                    "ex_bench_press", "Barbell Bench Press", 1, 5, 100.0, 120,
                    sets = listOf(WorkoutSetEntry("s1", 1, weightKg = 100.0, reps = 5)),
                ),
            ),
        )
        val lbs = AssistPrompts.optimizeRoutine(routine, "stronger", WeightUnit.LBS, "p", "perf", "weekly", "", "Chest: Barbell Bench Press")
        assertTrue(lbs, lbs.contains("\"weightLbs\": 220.5"))
        assertFalse(lbs.contains("weightKg"))
        val kg = AssistPrompts.optimizeRoutine(routine, "", WeightUnit.KG, "p", "perf", "weekly", "", "lib")
        assertTrue(kg.contains("\"weightKg\": 100"))
        val setup = AssistPrompts.setupRoutines(SetupAnswers(useHistory = false), WeightUnit.KG, "p", "", "lib")
        assertTrue(setup.contains("Experience: Beginner"))
        assertFalse(setup.contains("training data below"))
    }

    @Test
    fun customExercisesSurviveARoundTrip() {
        val def = ExerciseDefinition(
            id = "custom_landmine_press", title = "Landmine Press", primaryMuscle = MuscleGroup.SHOULDERS,
            secondaryMuscles = listOf(MuscleGroup.CHEST), equipment = EquipmentType.BARBELL, targetMuscle = "delts",
            imagePath = "custom/custom_landmine_press_1.jpg", summaryNotes = "s", instructions = listOf("a", "b"),
            coachingCues = listOf("c"), isCustom = true,
        )
        val back = CustomExerciseCodec.decode(CustomExerciseCodec.encode(listOf(def, ExerciseMatching.customDefinition("Hex Press"))))
        assertEquals(def, back[0])
        assertTrue(back[1].isCustom)
        assertEquals("", back[1].imagePath)
    }

    @Test
    fun aPictureNameFromABackupCannotLeaveTheImageFolder() {
        val json = """[{"id": "custom_x", "title": "X", "image": "../../shared_prefs/secrets.xml"}]"""
        assertEquals("custom/secrets.xml", CustomExerciseCodec.decode(json).single().imagePath)
    }

    @Test
    fun performancesListTheLatestSetsPerExercise() {
        val text = TrainingContext.performancesOf(
            listOf("ex_bench_press" to "Barbell Bench Press", "ex_new" to "New Thing"),
            emptyList(), WeightUnit.KG,
        )
        assertTrue(text.contains("Barbell Bench Press: never logged"))
        assertTrue(text.contains("New Thing: never logged"))
    }
}
