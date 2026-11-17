package com.lifttracker.app.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lifttracker.app.ai.AssistPrompts
import com.lifttracker.app.ai.ExerciseFields
import com.lifttracker.app.ai.SetupAnswers
import com.lifttracker.app.ai.TrainingContext
import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.domain.ExerciseMatching
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.export.RoutineCodec
import com.lifttracker.app.ui.DatasetMediaLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** The exercise form (new or edited custom exercise). Lists are edited as text, one entry per line. */
data class ExerciseEditorState(
    /** Id of the custom exercise being edited; null for a new one. */
    val editingId: String? = null,
    val title: String = "",
    val primaryMuscle: MuscleGroup = MuscleGroup.CHEST,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val equipment: EquipmentType = EquipmentType.BARBELL,
    val measure: ExerciseMeasure = ExerciseMeasure.WEIGHT_REPS,
    val targetMuscle: String = "",
    val summary: String = "",
    val instructionsText: String = "",
    val cuesText: String = "",
    /** Picture already saved for this exercise ("custom/<file>"), if any. */
    val savedImagePath: String = "",
    /** Picture generated in this session and not saved yet (JPEG). */
    val newImage: ByteArray? = null,
    val removeImage: Boolean = false,
    /** What the user asked the AI for. */
    val aiRequest: String = "",
    /** Extra wishes for the picture. */
    val imageNotes: String = "",
    /** False: draw from the description only. */
    val useTemplate: Boolean = true,
    /** Exercise whose picture is the style template; null picks the most similar one. */
    val templateExerciseId: String? = null,
    /** The exercise this one is another name for; "" when it is an exercise of its own. */
    val aliasOfId: String = "",
) {
    val instructions: List<String> get() = lines(instructionsText)
    val cues: List<String> get() = lines(cuesText)

    fun fields() = ExerciseFields(
        title.trim(), primaryMuscle, (secondaryMuscles - primaryMuscle).toList(), equipment, measure,
        targetMuscle.trim(), summary.trim(), instructions, cues,
    )

    private companion object {
        val BULLET = Regex("""^\s*(?:[-•*]|\d+[.)])\s*""")
        fun lines(text: String) = text.lines().map { it.replace(BULLET, "").trim() }.filter { it.isNotEmpty() }
    }
}

/** The AI's improved version of a routine, waiting for the user to accept or discard it. */
class RoutineProposal internal constructor(
    val proposed: RoutineTemplate,
    val summary: String,
    val changes: List<String>,
    /** Exercises the AI named that are not in the library; they are added to it on accept. */
    val newExerciseTitles: List<String>,
    internal val resolver: LiftTrackerViewModel.ImportResolver,
)

/** A program the AI set up: routines waiting for the user to pick which ones to add. */
class SetupProposal internal constructor(
    val routines: List<RoutineTemplate>,
    val summary: String,
    val newExerciseTitles: List<String>,
    internal val resolver: LiftTrackerViewModel.ImportResolver,
)

/**
 * The AI helpers that write into the app. Nothing the AI returns is saved on its own: the exercise
 * helper fills the form, and routine proposals are shown first and saved only when accepted.
 */
class AiAssistController(private val vm: LiftTrackerViewModel, private val scope: CoroutineScope) {
    private val ai get() = vm.ai

    private fun progressText(what: String, chars: Int): String =
        if (chars <= 0) "$what…" else "$what… ${if (chars < 1000) "$chars" else String.format(java.util.Locale.US, "%.1fk", chars / 1000.0)} characters"

    private fun errorText(e: Throwable): String = e.message ?: e.javaClass.simpleName

    // ================================================================ exercise editor

    var exerciseEditor by mutableStateOf<ExerciseEditorState?>(null)
        private set
    /** What the AI is doing for the exercise form right now; null when idle. */
    var exerciseBusy by mutableStateOf<String?>(null)
        private set
    var exerciseError by mutableStateOf<String?>(null)
        private set
    /** A remark that is not an error, e.g. that the picture model ignored the template. */
    var exerciseNote by mutableStateOf<String?>(null)
        private set
    private var exerciseJob: Job? = null

    /** Opens the form for a new exercise; [copyFrom] pre-fills it with a library exercise. */
    fun openNewExercise(title: String = "", copyFrom: ExerciseDefinition? = null) {
        resetExerciseStatus()
        exerciseEditor = copyFrom?.let { stateFrom(it).copy(editingId = null, savedImagePath = "", aliasOfId = "", templateExerciseId = it.id.takeIf { _ -> it.imagePath.isNotBlank() }) }
            ?: ExerciseEditorState(title = title, aiRequest = title)
    }

    fun openEditExercise(def: ExerciseDefinition) {
        if (!def.isCustom) return
        resetExerciseStatus()
        exerciseEditor = stateFrom(def)
    }

    private fun stateFrom(def: ExerciseDefinition) = ExerciseEditorState(
        editingId = def.id,
        title = def.title,
        primaryMuscle = def.primaryMuscle,
        secondaryMuscles = def.secondaryMuscles.toSet(),
        equipment = def.equipment,
        measure = vm.measureFor(def.id, def.title),
        targetMuscle = def.targetMuscle,
        summary = def.summaryNotes,
        instructionsText = def.instructions.joinToString("\n"),
        cuesText = def.coachingCues.joinToString("\n"),
        savedImagePath = def.imagePath,
        aliasOfId = def.aliasOfId,
    )

    private fun resetExerciseStatus() {
        exerciseJob?.cancel()
        exerciseBusy = null
        exerciseError = null
        exerciseNote = null
    }

    fun closeExerciseEditor() {
        resetExerciseStatus()
        exerciseEditor = null
    }

    fun updateExercise(transform: (ExerciseEditorState) -> ExerciseEditorState) {
        exerciseEditor = exerciseEditor?.let(transform)
    }

    fun stopExerciseAi() {
        exerciseJob?.cancel()
    }

    /** Asks the AI to fill the form from the name / request the user typed. */
    fun fillExerciseWithAi() {
        val state = exerciseEditor ?: return
        if (exerciseBusy != null) return
        val request = state.aiRequest.trim().ifBlank { state.title.trim() }
        if (request.isBlank()) {
            exerciseError = "Type the exercise's name, or describe it, first."
            return
        }
        val isFresh = state.editingId == null && state.summary.isBlank() && state.instructionsText.isBlank()
        val prompt = AssistPrompts.exercise(request, if (isFresh) null else state.fields())
        exerciseError = null
        exerciseNote = null
        exerciseBusy = progressText("Writing the exercise", 0)
        exerciseJob = scope.launch {
            try {
                val reply = ai.complete(prompt) { exerciseBusy = progressText("Writing the exercise", it) }
                val s = AssistPrompts.parseExercise(reply)
                updateExercise { cur ->
                    cur.copy(
                        title = s.title ?: cur.title,
                        primaryMuscle = s.primaryMuscle ?: cur.primaryMuscle,
                        secondaryMuscles = if (s.primaryMuscle != null) s.secondaryMuscles.toSet() else cur.secondaryMuscles,
                        equipment = s.equipment ?: cur.equipment,
                        measure = s.measure ?: cur.measure,
                        targetMuscle = s.targetMuscle ?: cur.targetMuscle,
                        summary = s.summary ?: cur.summary,
                        instructionsText = if (s.instructions.isEmpty()) cur.instructionsText else s.instructions.joinToString("\n"),
                        cuesText = if (s.cues.isEmpty()) cur.cuesText else s.cues.joinToString("\n"),
                    )
                }
            } catch (e: CancellationException) {
                // Stopped by the user: the form stays as it was.
            } catch (e: Exception) {
                exerciseError = errorText(e)
            } finally {
                exerciseBusy = null
            }
        }
    }

    /**
     * Fills the form from the exercise this one is an alias of: muscles, equipment, how it is logged,
     * the texts and a copy of its picture. The name stays as typed. Nothing is saved until Save.
     */
    fun fillFromAliasTarget() {
        val state = exerciseEditor ?: return
        if (exerciseBusy != null) return
        val target = vm.exercises.find { it.id == state.aliasOfId }
        if (target == null) {
            exerciseError = "Choose the exercise this is an alias of first."
            return
        }
        exerciseError = null
        exerciseNote = null
        updateExercise { cur ->
            cur.copy(
                primaryMuscle = target.primaryMuscle,
                secondaryMuscles = (target.secondaryMuscles - target.primaryMuscle).toSet(),
                equipment = target.equipment,
                measure = vm.measureFor(target.id, target.title),
                targetMuscle = target.targetMuscle,
                summary = target.summaryNotes,
                instructionsText = target.instructions.joinToString("\n"),
                cuesText = target.coachingCues.joinToString("\n"),
            )
        }
        if (target.imagePath.isBlank()) {
            exerciseNote = "\"${target.title}\" has no picture to copy; the details were filled in."
            return
        }
        val ctx = vm.appContext
        if (ctx == null) {
            exerciseNote = "The picture of \"${target.title}\" couldn't be loaded; the details were filled in."
            return
        }
        exerciseBusy = "Copying the picture…"
        val editor = state.editingId
        val job = scope.launch {
            try {
                val bytes = DatasetMediaLoader.loadBytes(ctx, target.imagePath)
                val jpeg = bytes?.let { withContext(Dispatchers.Default) { toStoredJpeg(it) } }
                // Only apply it to the same form, still pointing at the same exercise.
                val cur = exerciseEditor
                if (cur == null || cur.editingId != editor || cur.aliasOfId != target.id) return@launch
                if (jpeg == null) {
                    exerciseNote = "The picture of \"${target.title}\" couldn't be loaded; the details were filled in."
                } else {
                    updateExercise { it.copy(newImage = jpeg, removeImage = false) }
                }
            } catch (e: CancellationException) {
                // Stopped: the details stay filled in, the picture is unchanged.
            } catch (e: Exception) {
                exerciseError = errorText(e)
            } finally {
                if (exerciseJob === coroutineContext[Job]) exerciseBusy = null
            }
        }
        exerciseJob = job
    }

    /**
     * The library exercise whose picture is used as the style template for [state]: the chosen one,
     * or the most similar exercise that has a picture (same words in the name, equipment, muscle).
     */
    fun templateFor(state: ExerciseEditorState): ExerciseDefinition? {
        if (!state.useTemplate) return null
        state.templateExerciseId?.let { id -> vm.exercises.find { it.id == id && it.imagePath.isNotBlank() }?.let { return it } }
        val key = ExerciseMatching.nameKey(state.title)
        return vm.exercises
            .filter { it.imagePath.isNotBlank() && it.id != state.editingId }
            .maxByOrNull { ex ->
                ExerciseMatching.nameKey(ex.title).count { it in key } * 3 +
                    (if (ex.equipment == state.equipment) 2 else 0) +
                    (if (ex.primaryMuscle == state.primaryMuscle) 2 else 0) +
                    (if (!ex.isCustom) 1 else 0)
            }
    }

    /** Draws a picture for the exercise in the form, in the style of the template picture. */
    fun generateExercisePicture() {
        val state = exerciseEditor ?: return
        if (exerciseBusy != null) return
        if (state.title.isBlank()) {
            exerciseError = "Give the exercise a name first."
            return
        }
        if (ai.imageModel == null) {
            exerciseError = "No picture model is set up. Add one in Settings → AI Coach."
            return
        }
        val ctx = vm.appContext ?: return
        val template = templateFor(state)
        exerciseError = null
        exerciseNote = null
        exerciseBusy = "Drawing the picture… this can take a minute"
        exerciseJob = scope.launch {
            try {
                val templateBytes = template?.let { DatasetMediaLoader.loadBytes(ctx, it.imagePath) }
                val mime = if (template?.imagePath?.endsWith(".png", ignoreCase = true) == true) "image/png" else "image/jpeg"
                val prompt = AssistPrompts.exercisePicture(state.fields(), state.imageNotes, hasTemplate = templateBytes != null)
                val result = ai.generateImage(prompt, templateBytes, mime)
                val jpeg = withContext(Dispatchers.Default) { toStoredJpeg(result.bytes) }
                    ?: throw IllegalStateException("The picture model returned data that isn't a picture.")
                updateExercise { it.copy(newImage = jpeg, removeImage = false) }
                exerciseNote = when {
                    template != null && templateBytes == null -> "The template picture of \"${template.title}\" couldn't be loaded, so this was drawn from the description only."
                    templateBytes != null && !result.usedTemplate -> "This picture model doesn't take a template picture, so this was drawn from the description only."
                    else -> null
                }
            } catch (e: CancellationException) {
                // Stopped by the user.
            } catch (e: Exception) {
                exerciseError = errorText(e)
            } finally {
                exerciseBusy = null
            }
        }
    }

    /** Scales a generated picture down to at most 768 px and stores it as JPEG on white, like the library's pictures. */
    private fun toStoredJpeg(bytes: ByteArray): ByteArray? {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = (768f / maxOf(src.width, src.height)).coerceAtMost(1f)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
        return ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    }

    /** Saves the form to the library. Returns false (with [exerciseError] set) when it can't be saved. */
    fun saveExercise(): Boolean {
        val state = exerciseEditor ?: return false
        val title = state.title.trim()
        if (title.isEmpty()) {
            exerciseError = "Give the exercise a name."
            return false
        }
        if (vm.exercises.any { it.id != state.editingId && it.title.equals(title, ignoreCase = true) }) {
            exerciseError = "The library already has an exercise called \"$title\". Pick another name."
            return false
        }
        val f = state.fields()
        val def = ExerciseDefinition(
            id = state.editingId ?: vm.newCustomExerciseId(title),
            title = title,
            primaryMuscle = f.primaryMuscle,
            secondaryMuscles = f.secondaryMuscles,
            equipment = f.equipment,
            targetMuscle = f.targetMuscle,
            bodyPart = "",
            summaryNotes = f.summary,
            instructions = f.instructions,
            coachingCues = f.cues,
            isCustom = true,
            aliasOfId = state.aliasOfId.takeIf { target -> vm.aliasCandidates(state.editingId).any { it.id == target } }.orEmpty(),
        )
        val saved = vm.saveCustomExercise(def, state.measure, state.newImage, state.removeImage)
        vm.selectedExerciseId = saved.id
        closeExerciseEditor()
        return true
    }

    // ================================================================ routine optimizer

    /** The routine being optimized; the optimizer layer is open while this is set. */
    var optimizeTarget by mutableStateOf<RoutineTemplate?>(null)
        private set
    var optimizeGoal by mutableStateOf("")
    var optimizeProposal by mutableStateOf<RoutineProposal?>(null)
        private set
    var optimizeBusy by mutableStateOf<String?>(null)
        private set
    var optimizeError by mutableStateOf<String?>(null)
        private set
    private var optimizeJob: Job? = null

    fun openOptimize(routine: RoutineTemplate) {
        optimizeJob?.cancel()
        optimizeTarget = routine
        optimizeGoal = ""
        optimizeProposal = null
        optimizeBusy = null
        optimizeError = null
    }

    fun closeOptimize() {
        optimizeJob?.cancel()
        optimizeTarget = null
        optimizeProposal = null
        optimizeBusy = null
        optimizeError = null
    }

    fun stopOptimize() {
        optimizeJob?.cancel()
    }

    /** Back from the proposal to the question, keeping the routine open. */
    fun discardOptimizeProposal() {
        optimizeProposal = null
    }

    private fun libraryBlock(): String = AssistPrompts.libraryBlock(vm.exercises.toList())

    fun runOptimize() {
        val routine = optimizeTarget ?: return
        if (optimizeBusy != null) return
        if (routine.exercises.isEmpty()) {
            optimizeError = "This routine has no exercises yet. Add some, or use AI routine set-up."
            return
        }
        val unit = vm.weightUnit
        val history = vm.workoutHistory.toList()
        val now = System.currentTimeMillis() / 1000
        val prompt = AssistPrompts.optimizeRoutine(
            routine = routine,
            goal = optimizeGoal,
            unit = unit,
            profile = TrainingContext.profile(unit, vm.latestWeightKg, vm.latestBodyFatPercent),
            performances = TrainingContext.performancesOf(routine.exercises.map { it.exerciseId to it.exerciseTitle }, history, unit),
            weekly = TrainingContext.weeklySummary(history, 4, now, unit) { id, title -> vm.findExercise(id, title)?.primaryMuscle },
            otherRoutines = vm.routines.filter { it.id != routine.id }.takeIf { it.isNotEmpty() }
                ?.let { TrainingContext.routines(it, unit) }.orEmpty(),
            library = libraryBlock(),
        )
        val resolver = vm.newImportResolver()
        val rest = vm.defaultRestTimerSeconds
        optimizeError = null
        optimizeProposal = null
        optimizeBusy = progressText("Reviewing the routine", 0)
        optimizeJob = scope.launch {
            try {
                val reply = ai.complete(prompt) { optimizeBusy = progressText("Reviewing the routine", it) }
                val parsed = AssistPrompts.parseOptimize(reply)
                val proposed = RoutineCodec.parseRoutines(parsed.routineJson, resolver, rest).first()
                if (proposed.exercises.isEmpty()) throw IllegalStateException("The model returned a routine without exercises. Try again.")
                optimizeProposal = RoutineProposal(
                    // The routine keeps its place: same id and folder whatever the model wrote.
                    proposed = proposed.copy(id = routine.id, folderName = routine.folderName),
                    summary = parsed.summary,
                    changes = parsed.changes,
                    newExerciseTitles = resolver.newCustomTitles.values.toList(),
                    resolver = resolver,
                )
            } catch (e: CancellationException) {
                // Stopped by the user.
            } catch (e: Exception) {
                optimizeError = errorText(e)
            } finally {
                optimizeBusy = null
            }
        }
    }

    /** Applies the proposal: replaces the routine, or with [asCopy] adds it next to the original. */
    fun acceptOptimize(asCopy: Boolean) {
        val proposal = optimizeProposal ?: return
        val original = optimizeTarget ?: return
        if (asCopy) {
            val title = if (proposal.proposed.title.equals(original.title, ignoreCase = true)) "${original.title} (optimized)" else proposal.proposed.title
            vm.addParsedRoutines(listOf(proposal.proposed.copy(title = title)), proposal.resolver, idPrefix = "rt_ai")
            vm.routinesStatusMessage = "Added the optimized routine as \"$title\". The original is unchanged."
        } else {
            vm.replaceRoutine(proposal.proposed, proposal.resolver)
            vm.routinesStatusMessage = "Updated \"${proposal.proposed.title}\" with the AI's changes."
        }
        closeOptimize()
    }

    // ================================================================ routine set-up

    var isSetupOpen by mutableStateOf(false)
        private set
    var setupAnswers by mutableStateOf(SetupAnswers())
    var setupProposal by mutableStateOf<SetupProposal?>(null)
        private set
    /** Indexes into [SetupProposal.routines] the user ticked. */
    var setupSelected by mutableStateOf<Set<Int>>(emptySet())
    var setupBusy by mutableStateOf<String?>(null)
        private set
    var setupError by mutableStateOf<String?>(null)
        private set
    private var setupJob: Job? = null

    /** Enough logged workouts to plan from. */
    val canSetupFromHistory: Boolean
        get() = vm.workoutHistory.count { it.completedSetsCount > 0 } >= MIN_HISTORY_WORKOUTS

    fun openSetup() {
        setupJob?.cancel()
        setupProposal = null
        setupBusy = null
        setupError = null
        if (!canSetupFromHistory) setupAnswers = setupAnswers.copy(useHistory = false)
        isSetupOpen = true
    }

    fun closeSetup() {
        setupJob?.cancel()
        isSetupOpen = false
        setupProposal = null
        setupBusy = null
        setupError = null
    }

    fun stopSetup() {
        setupJob?.cancel()
    }

    fun discardSetupProposal() {
        setupProposal = null
    }

    fun runSetup() {
        if (setupBusy != null) return
        val answers = setupAnswers.copy(useHistory = setupAnswers.useHistory && canSetupFromHistory)
        val unit = vm.weightUnit
        val prompt = AssistPrompts.setupRoutines(
            answers = answers,
            unit = unit,
            profile = TrainingContext.profile(unit, vm.latestWeightKg, vm.latestBodyFatPercent),
            training = if (answers.useHistory) TrainingContext.trainingBlock(ai.dataSource, System.currentTimeMillis() / 1000) else "",
            library = libraryBlock(),
        )
        val resolver = vm.newImportResolver()
        val rest = vm.defaultRestTimerSeconds
        setupError = null
        setupProposal = null
        setupBusy = progressText("Building your program", 0)
        setupJob = scope.launch {
            try {
                val reply = ai.complete(prompt) { setupBusy = progressText("Building your program", it) }
                val parsed = AssistPrompts.parseSetup(reply)
                val routines = RoutineCodec.parseRoutines(parsed.routinesJson, resolver, rest).filter { it.exercises.isNotEmpty() }
                if (routines.isEmpty()) throw IllegalStateException("The model returned routines without exercises. Try again.")
                setupSelected = routines.indices.toSet()
                setupProposal = SetupProposal(routines, parsed.summary, resolver.newCustomTitles.values.toList(), resolver)
            } catch (e: CancellationException) {
                // Stopped by the user.
            } catch (e: Exception) {
                setupError = errorText(e)
            } finally {
                setupBusy = null
            }
        }
    }

    /** Adds the ticked routines to the routine list. */
    fun acceptSetup() {
        val proposal = setupProposal ?: return
        val chosen = proposal.routines.filterIndexed { i, _ -> i in setupSelected }
        if (chosen.isEmpty()) return
        // Only exercises of the chosen routines join the library.
        val usedIds = chosen.flatMap { r -> r.exercises.map { it.exerciseId } }.toHashSet()
        proposal.resolver.newCustomTitles.keys.retainAll(usedIds)
        val added = vm.addParsedRoutines(chosen, proposal.resolver, idPrefix = "rt_ai")
        vm.selectedRoutineFolderFilter = null
        vm.routinesStatusMessage = "Added ${added.size} routine(s) from the AI set-up" +
            (added.firstOrNull()?.folderName?.takeIf { it.isNotBlank() }?.let { " to the folder \"$it\"." } ?: ".")
        closeSetup()
    }

    private companion object {
        const val MIN_HISTORY_WORKOUTS = 3
    }
}
