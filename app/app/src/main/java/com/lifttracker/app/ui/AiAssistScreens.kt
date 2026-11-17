package com.lifttracker.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifttracker.app.ai.ImageModel
import com.lifttracker.app.ai.ImageModelDraft
import com.lifttracker.app.ai.SetupAnswers
import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.RoutineTemplateExercise
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSetEntry
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.SettingsSection

// ============================================================================
// Shared pieces of the AI assist layers
// ============================================================================

/** Full-screen layer with a back bar and a scrolling body. */
@Composable
private fun AssistLayer(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Surface(color = CardBg, tonalElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Chip("← Back", onClick = onBack)
                    Column(Modifier.weight(1f)) {
                        Text(title, color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, color = Accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AssistCard(
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (highlighted) Accent.copy(alpha = 0.45f) else Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun CardTitle(text: String, sparkle: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sparkle) AppIcons.Sparkles(modifier = Modifier.size(18.dp), tint = Accent)
        Text(text, color = if (sparkle) Accent else OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp)
}

/** Progress line with a Stop button, shown while a request is running. */
@Composable
private fun BusyRow(text: String, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onStop) { Text("Stop", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

/** Closes every AI assist layer and opens Settings → AI Coach. */
private fun openAiSettings(viewModel: LiftTrackerViewModel) {
    viewModel.assist.closeExerciseEditor()
    viewModel.assist.closeOptimize()
    viewModel.assist.closeSetup()
    viewModel.closeExerciseDetailLayer()
    viewModel.openSettingsLayer(SettingsSection.AI)
}

@Composable
private fun NeedsProviderCard(viewModel: LiftTrackerViewModel, what: String) {
    AssistCard(highlighted = true) {
        CardTitle("Set up an AI provider first", sparkle = true)
        Text("$what needs an AI provider with a model. Add one with your own API key in Settings → AI Coach.", color = Muted, fontSize = 13.sp)
        Button(onClick = { openAiSettings(viewModel) }) { Text("Open AI settings", fontWeight = FontWeight.Bold) }
    }
}

private fun setLine(set: WorkoutSetEntry, measure: ExerciseMeasure, unit: WeightUnit): String =
    if (measure == ExerciseMeasure.WEIGHT_REPS && set.weightKg <= 0.0) "${set.reps} reps"
    else WorkoutMechanics.formatSetSummary(set, measure, unit)

/** "W 40 kg × 10 · 3× 60 kg × 8": the sets of a routine exercise, equal neighbours folded together. */
private fun setsLine(ex: RoutineTemplateExercise, unit: WeightUnit): String {
    val groups = mutableListOf<Pair<String, Int>>()
    ex.sets.forEach { s ->
        val tag = if (s.setType == SetType.NORMAL) "" else "${s.setType.code} "
        val text = tag + setLine(s, ex.measure, unit)
        val last = groups.lastOrNull()
        if (last != null && last.first == text) groups[groups.lastIndex] = text to last.second + 1 else groups.add(text to 1)
    }
    return groups.joinToString(" · ") { (text, n) -> if (n > 1) "$n× $text" else text } +
        " · rest ${WorkoutMechanics.formatRest(ex.restSeconds)}"
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** One exercise of a proposed routine: picture, name, sets, and what it was before when it changed. */
@Composable
private fun ProposedExerciseRow(
    viewModel: LiftTrackerViewModel,
    ex: RoutineTemplateExercise,
    badge: String? = null,
    before: String? = null,
) {
    val def = viewModel.findExercise(ex.exerciseId, ex.exerciseTitle)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DatasetExerciseImage(imagePath = def?.imagePath.orEmpty(), contentDescription = ex.exerciseTitle, modifier = Modifier.size(38.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    ex.exerciseTitle, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) Badge(badge, if (badge == "NEW") Color(0xFF27AE60) else Accent)
                if (def == null) Badge("NOT IN LIBRARY", Color(0xFFF2994A))
            }
            Text(setsLine(ex, viewModel.weightUnit), color = Muted, fontSize = 12.sp)
            if (before != null) {
                Text("was: $before", color = Muted.copy(alpha = 0.7f), fontSize = 11.sp, textDecoration = TextDecoration.LineThrough)
            }
        }
    }
}

@Composable
private fun NewExercisesNote(titles: List<String>) {
    if (titles.isEmpty()) return
    Text(
        text = "Not in your library yet: ${titles.joinToString(", ")}. Accepting adds ${if (titles.size == 1) "it as a custom exercise" else "them as custom exercises"}, " +
            "which you can fill in and illustrate in Settings → Exercise Library.",
        color = Color(0xFFF2994A),
        fontSize = 12.sp,
    )
}

// ============================================================================
// Exercise editor (create / edit a custom exercise, with AI text and picture)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseEditorLayerScreen(viewModel: LiftTrackerViewModel) {
    val assist = viewModel.assist
    val state = assist.exerciseEditor ?: return
    val ai = viewModel.ai
    val busy = assist.exerciseBusy
    val initial = remember { state }
    var templatePickerOpen by remember { mutableStateOf(false) }
    var aliasPickerOpen by remember { mutableStateOf(false) }

    val close: () -> Unit = {
        if (state != initial) {
            viewModel.requestConfirmation(
                title = "Discard changes?",
                message = "What you entered or generated for this exercise will be lost.",
                confirmLabel = "Discard",
            ) { assist.closeExerciseEditor() }
        } else {
            assist.closeExerciseEditor()
        }
    }
    BackHandler(enabled = true) { close() }

    if (templatePickerOpen) {
        ExercisePickerDialog(
            title = "Picture to use as the style template",
            exercises = viewModel.exercises.filter { it.imagePath.isNotBlank() && it.id != state.editingId },
            onDismiss = { templatePickerOpen = false },
            onSelect = { def ->
                assist.updateExercise { it.copy(useTemplate = true, templateExerciseId = def.id) }
                templatePickerOpen = false
            },
        )
    }

    if (aliasPickerOpen) {
        ExercisePickerDialog(
            title = "This exercise is another name for…",
            exercises = viewModel.aliasCandidates(state.editingId),
            onDismiss = { aliasPickerOpen = false },
            onSelect = { def ->
                // An alias is logged the same way as the exercise it stands for.
                assist.updateExercise { it.copy(aliasOfId = def.id, measure = viewModel.measureFor(def.id, def.title)) }
                aliasPickerOpen = false
            },
        )
    }

    AssistLayer(
        title = if (state.editingId == null) "New exercise" else "Edit exercise",
        subtitle = "Your own exercise, saved to the library",
        onBack = close,
    ) {
        // ---- AI: write the details
        item(key = "ai_text") {
            AssistCard(highlighted = true) {
                CardTitle("Fill in with AI", sparkle = true)
                if (!ai.isConfigured) {
                    Text("Add an AI provider in Settings → AI Coach to have the details written for you. You can still fill in everything by hand below.", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = { openAiSettings(viewModel) }) { Text("Open AI settings", fontSize = 12.sp) }
                } else {
                    OutlinedTextField(
                        value = state.aiRequest,
                        onValueChange = { v -> assist.updateExercise { it.copy(aiRequest = v) } },
                        label = { Text(if (state.editingId == null) "Exercise name or description" else "What should change? (optional)") },
                        placeholder = { Text("e.g. Landmine press, half-kneeling, one arm") },
                        minLines = 1,
                        maxLines = 4,
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy != null && !busy.startsWith("Drawing") && !busy.startsWith("Copying")) {
                        BusyRow(busy) { assist.stopExerciseAi() }
                    } else {
                        Button(onClick = { assist.fillExerciseWithAi() }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                            Text("Write the details", fontWeight = FontWeight.Bold)
                        }
                        Text("Fills the fields below with ${ai.activeProvider?.label}. Nothing is saved until you tap Save.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        // ---- Picture
        item(key = "picture") {
            AssistCard {
                CardTitle("Picture")
                val template = remember(state.useTemplate, state.templateExerciseId, state.title, state.equipment, state.primaryMuscle) {
                    assist.templateFor(state)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(132.dp)) {
                        val generated = remember(state.newImage) {
                            state.newImage?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
                        }
                        if (generated != null) {
                            Image(
                                bitmap = generated,
                                contentDescription = state.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White),
                            )
                        } else {
                            DatasetExerciseImage(
                                imagePath = if (state.removeImage) "" else state.savedImagePath,
                                contentDescription = state.title,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Style template")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (template != null) {
                                DatasetExerciseImage(imagePath = template.imagePath, contentDescription = template.title, modifier = Modifier.size(40.dp))
                            }
                            Text(
                                text = when {
                                    template == null -> "None — drawn from the description"
                                    state.templateExerciseId == null -> "Auto: ${template.title}"
                                    else -> template.title
                                },
                                color = OnSurface, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Choose…", enabled = busy == null) { templatePickerOpen = true }
                            Chip("Auto", selected = state.useTemplate && state.templateExerciseId == null, enabled = busy == null) {
                                assist.updateExercise { it.copy(useTemplate = true, templateExerciseId = null) }
                            }
                            Chip("None", selected = !state.useTemplate, enabled = busy == null) {
                                assist.updateExercise { it.copy(useTemplate = false) }
                            }
                        }
                    }
                }
                Text(
                    "The template is an existing exercise picture. It is sent to the picture model so the new picture matches the library's style.",
                    color = Muted, fontSize = 11.sp,
                )
                if (ai.imageModel == null) {
                    Text("No picture model is set up yet. Add one in Settings → AI Coach → Picture model.", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = { openAiSettings(viewModel) }) { Text("Open AI settings", fontSize = 12.sp) }
                } else {
                    OutlinedTextField(
                        value = state.imageNotes,
                        onValueChange = { v -> assist.updateExercise { it.copy(imageNotes = v) } },
                        label = { Text("Picture wishes (optional)") },
                        placeholder = { Text("e.g. side view, show start and end position") },
                        maxLines = 3,
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy != null && busy.startsWith("Drawing")) {
                        BusyRow(busy) { assist.stopExerciseAi() }
                    } else {
                        val hasPicture = state.newImage != null || (state.savedImagePath.isNotBlank() && !state.removeImage)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { assist.generateExercisePicture() }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                                Text(if (hasPicture) "Draw again" else "Generate picture", fontWeight = FontWeight.Bold)
                            }
                            if (hasPicture) {
                                OutlinedButton(
                                    onClick = { assist.updateExercise { it.copy(newImage = null, removeImage = true) } },
                                    enabled = busy == null,
                                ) { Text("Remove", color = ErrorRed, fontSize = 12.sp) }
                            }
                        }
                        Text("Uses ${ai.imageModel?.label}. Picture models charge per picture.", color = Muted, fontSize = 11.sp)
                    }
                }
                assist.exerciseNote?.let { Text(it, color = Color(0xFFF2994A), fontSize = 12.sp) }
            }
        }

        assist.exerciseError?.let { msg ->
            item(key = "error") { Text(msg, color = ErrorRed, fontSize = 13.sp) }
        }

        // ---- Fields
        item(key = "fields") {
            AssistCard {
                CardTitle("Details")
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { v -> assist.updateExercise { it.copy(title = v) } },
                    label = { Text("Name (required)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldLabel("Main muscle group")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleGroup.entries.forEach { m ->
                        Chip(m.displayName, selected = state.primaryMuscle == m) {
                            assist.updateExercise { it.copy(primaryMuscle = m, secondaryMuscles = it.secondaryMuscles - m) }
                        }
                    }
                }
                FieldLabel("Also works")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleGroup.entries.filter { it != state.primaryMuscle }.forEach { m ->
                        Chip(m.displayName, selected = m in state.secondaryMuscles) {
                            assist.updateExercise { it.copy(secondaryMuscles = if (m in it.secondaryMuscles) it.secondaryMuscles - m else it.secondaryMuscles + m) }
                        }
                    }
                }
                FieldLabel("Equipment")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EquipmentType.entries.forEach { e ->
                        Chip(e.displayName, selected = state.equipment == e) { assist.updateExercise { it.copy(equipment = e) } }
                    }
                }
                FieldLabel("Logged as")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseMeasure.entries.forEach { m ->
                        Chip(m.shortLabel, selected = state.measure == m) { assist.updateExercise { it.copy(measure = m) } }
                    }
                }
                OutlinedTextField(
                    value = state.targetMuscle,
                    onValueChange = { v -> assist.updateExercise { it.copy(targetMuscle = v) } },
                    label = { Text("Target muscle") },
                    placeholder = { Text("e.g. biceps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.summary,
                    onValueChange = { v -> assist.updateExercise { it.copy(summary = v) } },
                    label = { Text("Summary") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.instructionsText,
                    onValueChange = { v -> assist.updateExercise { it.copy(instructionsText = v) } },
                    label = { Text("How to do it — one step per line") },
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.cuesText,
                    onValueChange = { v -> assist.updateExercise { it.copy(cuesText = v) } },
                    label = { Text("Coaching cues — one per line") },
                    minLines = 3,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Alias of an existing exercise
        item(key = "alias") {
            AssistCard {
                CardTitle("Alias of an existing exercise")
                val target = viewModel.exercises.find { it.id == state.aliasOfId }
                if (target == null) {
                    Text(
                        "If this is your own name for an exercise that is already in the library, pick that exercise. Both then share one history, one set of personal records and the same \"previous\" values.",
                        color = Muted, fontSize = 12.sp,
                    )
                    OutlinedButton(onClick = { aliasPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose exercise", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DatasetExerciseImage(imagePath = target.imagePath, contentDescription = target.title, modifier = Modifier.size(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(target.title, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${target.sourceLabel} · ${target.primaryMuscle.displayName} · ${target.equipment.displayName}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("Workouts logged under either name count as the same exercise in history and personal records.", color = Muted, fontSize = 12.sp)
                    if (busy != null && busy.startsWith("Copying")) {
                        BusyRow(busy) { assist.stopExerciseAi() }
                    } else {
                        Button(
                            onClick = {
                                val hasOwnContent = state.summary.isNotBlank() || state.instructionsText.isNotBlank() || state.cuesText.isNotBlank() ||
                                    state.targetMuscle.isNotBlank() || state.newImage != null || (state.savedImagePath.isNotBlank() && !state.removeImage)
                                if (hasOwnContent) {
                                    viewModel.requestConfirmation(
                                        title = "Replace details and picture?",
                                        message = "The details and picture in this form will be replaced with those of \"${target.title}\". The name stays. Nothing is saved until you tap Save.",
                                        confirmLabel = "Replace",
                                    ) { assist.fillFromAliasTarget() }
                                } else {
                                    assist.fillFromAliasTarget()
                                }
                            },
                            enabled = busy == null,
                            modifier = Modifier.fillMaxWidth().testTag("alias_fill_button"),
                        ) { Text("Copy details & picture from it", fontWeight = FontWeight.Bold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { aliasPickerOpen = true }, enabled = busy == null, modifier = Modifier.weight(1f)) { Text("Change") }
                        OutlinedButton(onClick = { assist.updateExercise { it.copy(aliasOfId = "") } }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                            Text("Remove alias", color = ErrorRed)
                        }
                    }
                }
            }
        }

        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val fromSettings = viewModel.isSettingsLayerOpen
                            if (assist.saveExercise() && !fromSettings) viewModel.openExerciseDetailLayer(viewModel.selectedExerciseId)
                        },
                        enabled = busy == null && state.title.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Save exercise", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = close, modifier = Modifier.weight(1f)) { Text("Cancel") }
                }
                state.editingId?.let { id ->
                    TextButton(
                        onClick = {
                            viewModel.requestConfirmation(
                                title = "Delete exercise?",
                                message = "\"${state.title}\" and its picture will be removed from your library. Logged workouts and routines keep their entries of it.",
                                confirmLabel = "Delete",
                            ) {
                                assist.closeExerciseEditor()
                                viewModel.deleteCustomExercise(id)
                            }
                        },
                    ) { Text("Delete exercise", color = ErrorRed) }
                }
            }
        }
    }
}

// ============================================================================
// Routine optimizer (proposal → accept, save as copy, or discard)
// ============================================================================

private val OPTIMIZE_GOALS = listOf(
    "More muscle-building volume",
    "Fit it in 45 minutes",
    "Break my plateau",
    "Easier on my joints",
    "Update the loads from my last sessions",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoutineOptimizeLayerScreen(viewModel: LiftTrackerViewModel) {
    val assist = viewModel.assist
    val routine = assist.optimizeTarget ?: return
    val proposal = assist.optimizeProposal
    val unit = viewModel.weightUnit

    BackHandler(enabled = true) { if (proposal != null) assist.discardOptimizeProposal() else assist.closeOptimize() }

    AssistLayer(
        title = "Optimize routine",
        subtitle = routine.title,
        onBack = { assist.closeOptimize() },
    ) {
        if (!viewModel.ai.isConfigured) {
            item { NeedsProviderCard(viewModel, "Optimizing a routine") }
            return@AssistLayer
        }
        if (proposal == null) {
            item(key = "ask") {
                AssistCard(highlighted = true) {
                    CardTitle("What should the AI improve?", sparkle = true)
                    OutlinedTextField(
                        value = assist.optimizeGoal,
                        onValueChange = { assist.optimizeGoal = it },
                        label = { Text("Your goal for this routine (optional)") },
                        placeholder = { Text("Leave empty for a general review") },
                        minLines = 2,
                        maxLines = 5,
                        enabled = assist.optimizeBusy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OPTIMIZE_GOALS.forEach { g ->
                            Chip(g, selected = assist.optimizeGoal == g, enabled = assist.optimizeBusy == null) {
                                assist.optimizeGoal = if (assist.optimizeGoal == g) "" else g
                            }
                        }
                    }
                    val busy = assist.optimizeBusy
                    if (busy != null) {
                        BusyRow(busy) { assist.stopOptimize() }
                    } else {
                        Button(onClick = { assist.runOptimize() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Optimize with AI", fontWeight = FontWeight.Bold)
                        }
                    }
                    assist.optimizeError?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
                    Text(
                        "Sends this routine, your recent sets of its exercises, your weekly totals and your other routines' " +
                            "outlines to ${viewModel.ai.activeProvider?.label}. You see the result before anything changes.",
                        color = Muted, fontSize = 11.sp,
                    )
                }
            }
            item(key = "current") {
                AssistCard {
                    CardTitle("Current routine")
                    if (routine.exercises.isEmpty()) Text("No exercises yet.", color = Muted, fontSize = 12.sp)
                    routine.exercises.forEach { ex -> ProposedExerciseRow(viewModel, ex) }
                }
            }
        } else {
            val originalById = routine.exercises.associateBy { it.exerciseId }
            val proposedIds = proposal.proposed.exercises.map { it.exerciseId }.toSet()
            val removed = routine.exercises.filter { it.exerciseId !in proposedIds }
            val unchanged = removed.isEmpty() &&
                proposal.proposed.exercises.map { it.exerciseId to setsLine(it, unit) } == routine.exercises.map { it.exerciseId to setsLine(it, unit) }

            item(key = "summary") {
                AssistCard(highlighted = true) {
                    CardTitle("What the AI suggests", sparkle = true)
                    if (proposal.summary.isNotBlank()) MarkdownText(proposal.summary)
                    if (unchanged) Text("The exercises and sets are the same as in your routine.", color = Muted, fontSize = 12.sp)
                    proposal.changes.forEach { change ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(change, color = OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item(key = "proposed") {
                AssistCard {
                    CardTitle("Proposed routine")
                    Text(proposal.proposed.title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (proposal.proposed.notes.isNotBlank()) Text(proposal.proposed.notes, color = Muted, fontSize = 12.sp)
                    HorizontalDivider(color = CardHigh)
                    proposal.proposed.exercises.forEach { ex ->
                        val was = originalById[ex.exerciseId]
                        val line = setsLine(ex, unit)
                        val wasLine = was?.let { setsLine(it, unit) }
                        ProposedExerciseRow(
                            viewModel = viewModel,
                            ex = ex,
                            badge = when {
                                was == null -> "NEW"
                                wasLine != line -> "CHANGED"
                                else -> null
                            },
                            before = wasLine?.takeIf { it != line },
                        )
                    }
                    if (removed.isNotEmpty()) {
                        HorizontalDivider(color = CardHigh)
                        FieldLabel("Removed")
                        removed.forEach { ex ->
                            Text("${ex.exerciseTitle} — ${setsLine(ex, unit)}", color = Muted, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough)
                        }
                    }
                    NewExercisesNote(proposal.newExerciseTitles)
                }
            }
            item(key = "decide") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { assist.acceptOptimize(asCopy = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Accept — replace my routine", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { assist.acceptOptimize(asCopy = true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Keep both — save as a copy")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { assist.discardOptimizeProposal() }, modifier = Modifier.weight(1f)) { Text("Ask again") }
                        OutlinedButton(onClick = { assist.closeOptimize() }, modifier = Modifier.weight(1f)) { Text("Discard", color = ErrorRed) }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Routine set-up (questions or workout history → program → pick routines to add)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoutineSetupLayerScreen(viewModel: LiftTrackerViewModel) {
    val assist = viewModel.assist
    val proposal = assist.setupProposal
    val answers = assist.setupAnswers
    val busy = assist.setupBusy

    BackHandler(enabled = true) { if (proposal != null) assist.discardSetupProposal() else assist.closeSetup() }

    AssistLayer(
        title = "AI routine set-up",
        subtitle = "A program built for you, as routines",
        onBack = { assist.closeSetup() },
    ) {
        if (!viewModel.ai.isConfigured) {
            item { NeedsProviderCard(viewModel, "Setting up routines") }
            return@AssistLayer
        }
        if (proposal == null) {
            item(key = "source") {
                AssistCard(highlighted = true) {
                    CardTitle("What should the program be based on?", sparkle = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("My answers", selected = !answers.useHistory, enabled = busy == null) {
                            assist.setupAnswers = answers.copy(useHistory = false)
                        }
                        Chip("My workout history", selected = answers.useHistory, enabled = busy == null && assist.canSetupFromHistory) {
                            assist.setupAnswers = answers.copy(useHistory = true)
                        }
                    }
                    Text(
                        text = when {
                            !assist.canSetupFromHistory -> "Log at least 3 workouts to build a program from your history."
                            answers.useHistory -> "Your level, exercises and loads are read from ${viewModel.workoutHistory.size} logged workouts (last 8 weeks in detail)."
                            else -> "Tell the AI your level and it starts from there. Loads are left for you to set."
                        },
                        color = Muted, fontSize = 12.sp,
                    )
                }
            }
            item(key = "questions") {
                AssistCard {
                    if (!answers.useHistory) {
                        FieldLabel("How long have you been lifting?")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SetupAnswers.LEVELS.forEach { v ->
                                Chip(v, selected = answers.level == v, enabled = busy == null) { assist.setupAnswers = answers.copy(level = v) }
                            }
                        }
                    }
                    FieldLabel("Main goal")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SetupAnswers.GOALS.forEach { v ->
                            Chip(v, selected = answers.goal == v, enabled = busy == null) { assist.setupAnswers = answers.copy(goal = v) }
                        }
                    }
                    FieldLabel("Training days per week")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (2..6).forEach { v ->
                            Chip("$v", selected = answers.daysPerWeek == v, enabled = busy == null) { assist.setupAnswers = answers.copy(daysPerWeek = v) }
                        }
                    }
                    FieldLabel("Time per session")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SetupAnswers.MINUTES.forEach { v ->
                            Chip("$v min", selected = answers.sessionMinutes == v, enabled = busy == null) { assist.setupAnswers = answers.copy(sessionMinutes = v) }
                        }
                    }
                    FieldLabel("Equipment you can use")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SetupAnswers.EQUIPMENT.forEach { v ->
                            Chip(v, selected = v in answers.equipment, enabled = busy == null) {
                                val next = if (v in answers.equipment) answers.equipment - v else answers.equipment + v
                                assist.setupAnswers = answers.copy(equipment = next)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = answers.notes,
                        onValueChange = { assist.setupAnswers = answers.copy(notes = it) },
                        label = { Text("Anything else? (optional)") },
                        placeholder = { Text("e.g. sore lower back, want bigger shoulders, no deadlifts") },
                        minLines = 2,
                        maxLines = 5,
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy != null) {
                        BusyRow(busy) { assist.stopSetup() }
                    } else {
                        Button(onClick = { assist.runSetup() }, enabled = answers.equipment.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Build my program", fontWeight = FontWeight.Bold)
                        }
                    }
                    assist.setupError?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
                    Text(
                        text = "Sends your answers" + (if (answers.useHistory) ", your logged workouts, body weight and saved routines" else "") +
                            " to ${viewModel.ai.activeProvider?.label}. You choose which routines to keep.",
                        color = Muted, fontSize = 11.sp,
                    )
                }
            }
        } else {
            item(key = "summary") {
                AssistCard(highlighted = true) {
                    CardTitle("Your program", sparkle = true)
                    if (proposal.summary.isNotBlank()) MarkdownText(proposal.summary)
                    NewExercisesNote(proposal.newExerciseTitles)
                }
            }
            proposal.routines.forEachIndexed { index, r ->
                item(key = "routine_$index") { SetupRoutineCard(viewModel, r, index in assist.setupSelected) { checked ->
                    assist.setupSelected = if (checked) assist.setupSelected + index else assist.setupSelected - index
                } }
            }
            item(key = "decide") {
                val count = assist.setupSelected.size
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { assist.acceptSetup() }, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
                        Text(if (count == 1) "Add 1 routine" else "Add $count routines", fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { assist.discardSetupProposal() }, modifier = Modifier.weight(1f)) { Text("Change answers") }
                        OutlinedButton(onClick = { assist.closeSetup() }, modifier = Modifier.weight(1f)) { Text("Discard", color = ErrorRed) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRoutineCard(viewModel: LiftTrackerViewModel, routine: RoutineTemplate, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (checked) Accent.copy(alpha = 0.45f) else Outline),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCheckedChange(!checked) },
            ) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Column(Modifier.weight(1f)) {
                    Text(routine.title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${routine.exercises.size} exercises · ${routine.exercises.sumOf { it.sets.size }} sets" +
                            if (routine.folderName.isBlank()) "" else " · ${routine.folderName}",
                        color = Muted, fontSize = 11.sp,
                    )
                }
            }
            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (routine.notes.isNotBlank()) Text(routine.notes, color = Muted, fontSize = 12.sp)
                routine.exercises.forEach { ex -> ProposedExerciseRow(viewModel, ex) }
            }
        }
    }
}

// ============================================================================
// Settings → AI Coach: picture model
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImageModelSettingsCard(viewModel: LiftTrackerViewModel) {
    val ai = viewModel.ai
    var draft by remember { mutableStateOf<ImageModelDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val saved = ai.imageModel
    val plainText = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    AssistCard(highlighted = draft != null) {
        CardTitle("Picture model")
        Text("Draws the pictures of exercises you create. It is separate from the chat provider, because most chat models can't draw.", color = Muted, fontSize = 12.sp)
        val d = draft
        if (d == null) {
            if (saved == null) {
                Text("Not set up.", color = OnSurface, fontSize = 13.sp)
            } else {
                Text(saved.model, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${saved.protocol.label} · ${saved.baseUrl}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = { draft = ai.imageDraft(); error = null }) {
                Text(if (saved == null) "Set up picture model" else "Edit", fontWeight = FontWeight.Bold)
            }
        } else {
            FieldLabel("API type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ImageModel.PROTOCOLS.forEach { p ->
                    Chip(if (p == com.lifttracker.app.ai.Protocol.OPENAI) "OpenAI Images API" else "Google Gemini", selected = d.protocol == p) {
                        if (d.protocol != p) {
                            draft = d.copy(
                                protocol = p, baseUrl = ImageModel.defaultBaseUrl(p), model = ImageModel.defaultModel(p),
                                keyFromProviderId = null, hasSavedKey = false,
                            )
                        }
                    }
                }
            }
            val reusable = ai.imageCapableProviders.filter { it.protocol == d.protocol }
            if (reusable.isNotEmpty()) {
                FieldLabel("Use the address and key of a chat provider")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reusable.forEach { p ->
                        Chip(p.name, selected = d.keyFromProviderId == p.id) {
                            draft = d.copy(baseUrl = p.baseUrl, keyFromProviderId = p.id, apiKey = "")
                        }
                    }
                }
            }
            OutlinedTextField(
                value = d.baseUrl,
                onValueChange = { draft = d.copy(baseUrl = it) },
                label = { Text("Base URL") },
                singleLine = true,
                keyboardOptions = plainText.copy(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = d.apiKey,
                onValueChange = { draft = d.copy(apiKey = it) },
                label = { Text("API key") },
                placeholder = {
                    when {
                        d.keyFromProviderId != null -> Text("Using the chat provider's key")
                        d.hasSavedKey -> Text("Saved — leave empty to keep it")
                    }
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = plainText.copy(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = d.model,
                onValueChange = { draft = d.copy(model = it) },
                label = { Text("Model") },
                supportingText = {
                    Text(
                        if (d.protocol == com.lifttracker.app.ai.Protocol.GEMINI) "A Gemini model with image output, e.g. gemini-2.5-flash-image"
                        else "A model of the Images API, e.g. gpt-image-1. Template pictures need its /images/edits endpoint.",
                    )
                },
                singleLine = true,
                keyboardOptions = plainText,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        error = ai.saveImageModel(d)
                        if (error == null) draft = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { draft = null; error = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
            if (saved != null) {
                TextButton(onClick = { ai.removeImageModel(); draft = null }) { Text("Remove picture model", color = ErrorRed) }
            }
        }
    }
}

// ============================================================================
// Settings → Exercise Library: the user's own exercises
// ============================================================================

@Composable
internal fun UserExercisesSettingsCard(viewModel: LiftTrackerViewModel) {
    val mine = viewModel.exercises.filter { it.isCustom }
    val expanded = viewModel.isUserExercisesExpanded
    AssistCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = if (expanded) "Fold" else "Unfold") { viewModel.toggleUserExercisesExpanded() },
        ) {
            Box(Modifier.weight(1f)) { CardTitle("Your exercises (${mine.size})") }
            if (expanded) {
                AppIcons.ChevronDown(modifier = Modifier.size(18.dp), tint = Accent)
            } else {
                AppIcons.ChevronRight(modifier = Modifier.size(18.dp), tint = Muted)
            }
        }
        if (!expanded) return@AssistCard
        Text(
            "Exercises you created or that came in with an import. They are included in both backups (Settings → Backup & Restore), pictures too.",
            color = Muted, fontSize = 12.sp,
        )
        if (mine.isEmpty()) {
            Text("None yet.", color = OnSurface, fontSize = 13.sp)
        }
        mine.forEach { ex ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { viewModel.assist.openEditExercise(ex) },
            ) {
                DatasetExerciseImage(imagePath = ex.imagePath, contentDescription = ex.title, modifier = Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex.title, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = (viewModel.aliasTargetOf(ex)?.let { "Alias of ${it.title} · " } ?: "") +
                            "${ex.primaryMuscle.displayName} · ${ex.equipment.displayName}" +
                            (if (ex.instructions.isEmpty()) " · no how-to yet" else "") +
                            (if (ex.imagePath.isBlank()) " · no picture" else ""),
                        color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Chip("Edit") { viewModel.assist.openEditExercise(ex) }
                Chip("Delete") {
                    viewModel.requestConfirmation(
                        title = "Delete exercise?",
                        message = "\"${ex.title}\" and its picture will be removed from your library. Logged workouts and routines keep their entries of it.",
                        confirmLabel = "Delete",
                    ) { viewModel.deleteCustomExercise(ex.id) }
                }
            }
        }
        Button(onClick = { viewModel.assist.openNewExercise() }, modifier = Modifier.fillMaxWidth()) {
            Text("+ New exercise", fontWeight = FontWeight.Bold)
        }
    }
}
