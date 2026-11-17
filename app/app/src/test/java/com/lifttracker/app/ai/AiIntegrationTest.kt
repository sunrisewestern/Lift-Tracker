package com.lifttracker.app.ai

import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AiIntegrationTest {
    private val day = 86_400L
    private val now = 1_763_208_000L // 2025-11-15T12:00:00Z

    private fun bench(id: String, endsAt: Long, weight: Double, reps: Int, rpe: Double? = null) = WorkoutSession(
        id = id,
        title = "Push Day",
        startTimeEpochSeconds = endsAt - 3600,
        endTimeEpochSeconds = endsAt,
        exercises = listOf(
            WorkoutExerciseEntry(
                id = "${id}_e1",
                exerciseId = "ex_bench_press",
                exerciseTitle = "Barbell Bench Press",
                sets = listOf(
                    WorkoutSetEntry("${id}_s1", 1, SetType.WARMUP, weightKg = 60.0, reps = 10, completed = true),
                    WorkoutSetEntry("${id}_s2", 2, SetType.NORMAL, weightKg = weight, reps = reps, rpe = rpe, completed = true),
                    WorkoutSetEntry("${id}_s3", 3, SetType.NORMAL, weightKg = weight, reps = reps, completed = false),
                ),
            ),
        ),
    )

    private val older = bench("w1", now - 10 * day, 100.0, 5)
    private val latest = bench("w2", now - 1 * day, 105.0, 5, rpe = 8.5)

    @Test
    fun sessionDetailListsEverySetAndMarksUnfinishedOnes() {
        val text = TrainingContext.sessionDetail(latest, WeightUnit.KG, ZoneOffset.UTC)
        assertTrue(text.contains("Workout: Push Day"))
        assertTrue(text.contains("1. warmup 60×10"))
        assertTrue(text.contains("2. 105×5 @8.5"))
        assertTrue(text.contains("3. 105×5 (not completed)"))
        // Volume counts the one completed working set.
        assertTrue(text.contains("Working volume: 525 kg"))
    }

    @Test
    fun weightsFollowTheChosenUnit() {
        val text = TrainingContext.sessionDetail(older, WeightUnit.LBS, ZoneOffset.UTC)
        assertTrue(text, text.contains("220.5×5"))
    }

    @Test
    fun recentWorkoutsKeepsOnlyTheWindowNewestFirst() {
        val text = TrainingContext.recentWorkouts(listOf(older, latest), 7, now, WeightUnit.KG, ZoneOffset.UTC)
        assertTrue(text.contains("2025-11-14"))
        assertFalse(text.contains("2025-11-05"))
        assertTrue(text.contains("Barbell Bench Press: warmup 60×10, 105×5 @8.5"))
        assertEquals(
            "No workouts logged in the last 7 days.",
            TrainingContext.recentWorkouts(emptyList(), 7, now, WeightUnit.KG, ZoneOffset.UTC),
        )
    }

    @Test
    fun weeklySummaryCountsHardSetsPerMuscle() {
        val text = TrainingContext.weeklySummary(listOf(older, latest), 2, now, WeightUnit.KG) { _, _ -> MuscleGroup.CHEST }
        val lines = text.lines()
        assertEquals(2, lines.size)
        assertEquals("Last 7 days: 1 workouts, volume 525; hard sets — Chest 1", lines[0])
        assertEquals("8-14 days ago: 1 workouts, volume 500; hard sets — Chest 1", lines[1])
    }

    @Test
    fun exerciseProgressShowsBestAndTrend() {
        val text = TrainingContext.exerciseProgress(listOf(latest, older), WeightUnit.KG, ZoneOffset.UTC)
        // Epley: 105 × (1 + 5/30) = 122.5, 100 × (1 + 5/30) = 116.7
        assertTrue(text, text.startsWith("Barbell Bench Press: 2 sessions, best e1RM 122.5"))
        assertTrue(text, text.contains("first 116.7 → latest 122.5"))
    }

    @Test
    fun previousPerformancesOnlyLooksBack() {
        val forLatest = TrainingContext.previousPerformances(latest, listOf(older, latest), WeightUnit.KG, ZoneOffset.UTC)
        assertTrue(forLatest.contains("2025-11-05: warmup 60×10, 100×5"))
        val forOlder = TrainingContext.previousPerformances(older, listOf(older, latest), WeightUnit.KG, ZoneOffset.UTC)
        assertEquals("Barbell Bench Press: first time logged", forOlder)
    }

    @Test
    fun turnsAlternateAndEndWithTheUser() {
        fun m(role: String, text: String, error: String? = null) = AiChatMessage("id$text", role, text, 0L, error)
        val turns = AiController.toTurns(
            listOf(
                m(ChatTurn.USER, "a"),
                m(ChatTurn.ASSISTANT, "", error = "HTTP 500"), // failed reply: dropped
                m(ChatTurn.USER, "b"),
                m(ChatTurn.ASSISTANT, "c"),
                m(ChatTurn.USER, "d"),
                m(ChatTurn.ASSISTANT, "partial", error = "Stopped."),
            ),
        )
        assertEquals(listOf(ChatTurn.USER to "a\n\nb", ChatTurn.ASSISTANT to "c", ChatTurn.USER to "d"), turns.map { it.role to it.text })
    }

    @Test
    fun providersChatsAndAnalysesSurviveJson() {
        val providers = listOf(
            AiProvider("p1", "Anthropic", Protocol.ANTHROPIC, "https://api.anthropic.com", "enc==", "claude-opus-5-5",
                maxTokens = 8000, extraSystem = "Be brief", thinkingLevel = ThinkingLevel.HIGH),
        )
        assertEquals(providers, AiJson.decodeProviders(AiJson.encodeProviders(providers)))

        val chats = listOf(
            AiChat("c1", "Review", 1L, 2L, includeTraining = false, pinnedContext = "ctx", messages = listOf(
                AiChatMessage("m1", ChatTurn.USER, "hi", 1L),
                AiChatMessage("m2", ChatTurn.ASSISTANT, "", 2L, error = "HTTP 401", model = "Anthropic · claude-opus-5-5"),
            )),
        )
        val decoded = AiJson.decodeChats(AiJson.encodeChats(chats))
        assertEquals(chats, decoded)
        assertNull(decoded[0].messages[0].error)

        val analyses = listOf(AiAnalysis("w1", 5L, "m", "## Good", null), AiAnalysis("w2", 6L, "m", "", "HTTP 429"))
        assertEquals(analyses, AiJson.decodeAnalyses(AiJson.encodeAnalyses(analyses)))

        assertTrue(AiJson.decodeProviders("not json").isEmpty())
        assertTrue(AiJson.decodeChats(null).isEmpty())
    }

    @Test
    fun thinkingLevelIsClampedToWhatTheModelAccepts() {
        // Opus 5.5 can't switch thinking off: Off becomes the lightest effort.
        assertEquals(ThinkingLevel.LOW, Thinking.resolve(Protocol.ANTHROPIC, "claude-opus-5-5", ThinkingLevel.OFF))
        val t = Thinking.anthropic("claude-opus-5-5", ThinkingLevel.HIGH, 16000)
        assertEquals(mapOf("type" to "adaptive"), t.thinking)
        assertEquals("high", t.effort)
        // Default leaves the request untouched.
        assertEquals(AnthropicThinking(), Thinking.anthropic("claude-opus-5-5", ThinkingLevel.DEFAULT, 16000))
        assertNull(Thinking.openAiEffort(Protocol.OPENAI, "gpt-4o", ThinkingLevel.HIGH))
    }

    @Test
    fun modelListsFromRelaysAreParsed() {
        assertEquals(listOf("a", "b"), parseModelIds("""{"data":[{"id":"b"},{"id":"a"}]}"""))
        assertEquals(listOf("x"), parseModelIds("""["x"]"""))
    }
}
