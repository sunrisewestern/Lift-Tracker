package com.lifttracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifttracker.app.ai.AiChatMessage
import com.lifttracker.app.ai.AiContextItem
import com.lifttracker.app.ai.AiProvider
import com.lifttracker.app.ai.CoachPrompts
import com.lifttracker.app.ai.PROVIDER_PRESETS
import com.lifttracker.app.ai.Protocol
import com.lifttracker.app.ai.ProviderDraft
import com.lifttracker.app.ai.Thinking
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.SettingsSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Bg: Color @Composable get() = MaterialTheme.colorScheme.background
internal val CardBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
internal val CardHigh: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
internal val Outline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
internal val Accent: Color @Composable get() = MaterialTheme.colorScheme.tertiary
internal val OnSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
internal val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val ErrorRed = Color(0xFFEB5757)

// ============================================================================
// Shared pieces
// ============================================================================

@Composable
internal fun Chip(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else CardHigh,
        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = 0.6f) else Outline),
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Muted.copy(alpha = 0.5f)
                selected -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> OnSurface
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Inline Markdown: **bold**, *italic* and `code`. */
private fun inlineMarkdown(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1 && !text[end - 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

private val NUMBERED = Regex("""^(\d+)[.)]\s+(.*)""")

/** A small Markdown renderer: headings, lists, quotes, code fences and tables shown as monospace rows. */
@Composable
internal fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = OnSurface) {
    val codeBg = CardHigh
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var inCode = false
        val code = StringBuilder()

        @Composable
        fun CodeBlock(content: String) {
            Text(
                text = content,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(codeBg, RoundedCornerShape(6.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            )
        }

        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    CodeBlock(code.toString().trimEnd('\n'))
                    code.clear()
                }
                inCode = !inCode
                return@forEach
            }
            if (inCode) {
                code.appendLine(line)
                return@forEach
            }
            when {
                trimmed.isEmpty() -> Spacer(Modifier.height(2.dp))
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    Text(
                        text = inlineMarkdown(trimmed.drop(level).trim(), codeBg),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = when (level) {
                            1 -> 18.sp
                            2 -> 16.sp
                            else -> 14.sp
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                trimmed.startsWith("|") -> Text(
                    text = trimmed,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
                trimmed == "---" || trimmed == "***" -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Outline),
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val indent = ((line.length - trimmed.length) / 2).coerceAtMost(3)
                    Row(Modifier.padding(start = (indent * 12).dp)) {
                        Text("•  ", color = color, fontSize = 14.sp)
                        Text(inlineMarkdown(trimmed.drop(2), codeBg), color = color, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                NUMBERED.matches(trimmed) -> {
                    val m = NUMBERED.find(trimmed)!!
                    Row {
                        Text("${m.groupValues[1]}.  ", color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(inlineMarkdown(m.groupValues[2], codeBg), color = color, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                trimmed.startsWith(">") -> Text(
                    text = inlineMarkdown(trimmed.drop(1).trim(), codeBg),
                    color = color.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
                else -> Text(inlineMarkdown(trimmed, codeBg), color = color, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        // A fence that is still open while the answer streams in.
        if (inCode && code.isNotEmpty()) CodeBlock(code.toString().trimEnd('\n'))
    }
}

@Composable
internal fun SetUpAiCard(viewModel: LiftTrackerViewModel, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcons.Sparkles(modifier = Modifier.size(20.dp), tint = Accent)
                Text("Set up the AI coach", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(message, color = Muted, fontSize = 13.sp)
            Button(
                onClick = {
                    viewModel.closeWorkoutSessionDetailLayer()
                    viewModel.openSettingsLayer(SettingsSection.AI)
                },
            ) {
                Text("Open AI settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// Settings → AI Coach
// ============================================================================

@Composable
internal fun AiSettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState,
    onBack: () -> Unit,
) {
    val ai = viewModel.ai
    var draft by remember { mutableStateOf<ProviderDraft?>(null) }
    var initialDraft by remember { mutableStateOf<ProviderDraft?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var presetMenuOpen by remember { mutableStateOf(false) }

    fun closeForm() {
        draft = null
        initialDraft = null
        formError = null
        ai.resetModelList()
    }

    fun requestCloseForm() {
        val current = draft
        if (current != null && initialDraft != null && current != initialDraft) {
            viewModel.requestConfirmation(
                title = "Discard unsaved changes?",
                message = "Your edits to this AI provider have not been saved. Quit without saving?",
                confirmLabel = "Quit without saving",
            ) { closeForm() }
        } else {
            closeForm()
        }
    }

    // Back leaves the form first, then the screen.
    BackHandler(enabled = draft != null) { requestCloseForm() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "AI Coach",
                subtitle = "Connect the AI provider of your choice for workout analysis, coach chat, and the exercise and routine helpers.",
                onBack = { if (draft != null) requestCloseForm() else onBack() },
            )
        }

        val current = draft
        if (current != null) {
            item {
                ProviderForm(
                    viewModel = viewModel,
                    draft = current,
                    error = formError,
                    onChange = { draft = it; formError = null },
                    onSave = {
                        val error = ai.saveProvider(current)
                        if (error == null) closeForm() else formError = error
                    },
                    onCancel = { requestCloseForm() },
                    onDelete = current.id?.let { id ->
                        {
                            viewModel.requestConfirmation(
                                title = "Delete provider?",
                                message = "\"${current.name}\" and its saved API key will be removed.",
                                confirmLabel = "Delete",
                            ) {
                                ai.deleteProvider(id)
                                closeForm()
                            }
                        }
                    },
                )
            }
        } else {
            if (ai.providers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, Outline),
                    ) {
                        Text(
                            text = "No provider yet. Add one with your own API key, or point the app at a model " +
                                "running on your network (Ollama, LM Studio).",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            items(ai.providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    active = provider.id == ai.activeProvider?.id,
                    onSelect = { ai.selectProvider(provider.id) },
                    onEdit = {
                        val loaded = ai.draftFrom(provider)
                        initialDraft = loaded
                        draft = loaded
                    },
                )
            }
            item {
                Box {
                    Button(onClick = { presetMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Add provider", fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                        PROVIDER_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(preset.name, fontWeight = FontWeight.SemiBold)
                                        Text(preset.protocol.label, fontSize = 11.sp, color = Muted)
                                    }
                                },
                                onClick = {
                                    presetMenuOpen = false
                                    val fresh = ai.draftFromPreset(preset)
                                    initialDraft = fresh
                                    draft = fresh
                                },
                            )
                        }
                    }
                }
            }
            item { ImageModelSettingsCard(viewModel) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, Outline),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Privacy", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = "Nothing is sent until you ask for an analysis, send a chat message, or use an AI " +
                                "helper (exercise details and pictures, routine optimizing and set-up). The request " +
                                "then goes straight from this phone to the provider you chose and includes the workout " +
                                "data in the prompt (sets, loads, notes, body weight). API keys are encrypted with " +
                                "the Android Keystore and stay on this device. Anything sent to an http:// address " +
                                "is not encrypted in transit.",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        if (ai.chats.isNotEmpty() || ai.analyses.isNotEmpty()) {
                            Text(
                                text = "${ai.chats.size} chats and ${ai.analyses.size} workout analyses are saved on this phone.",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(provider: AiProvider, active: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (active) Accent.copy(alpha = 0.7f) else Outline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = provider.name,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Text("IN USE", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(
                    text = provider.model.ifBlank { "No model chosen" },
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${provider.protocol.label} · ${provider.baseUrl}",
                    color = Muted.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Chip("Edit", onClick = onEdit)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderForm(
    viewModel: LiftTrackerViewModel,
    draft: ProviderDraft,
    error: String?,
    onChange: (ProviderDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val ai = viewModel.ai
    var modelMenuOpen by remember { mutableStateOf(false) }
    val plainText = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (draft.id == null) "New provider" else "Edit provider",
                color = OnSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("API type", color = Muted, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Protocol.entries.forEach { p ->
                        Chip(p.label, selected = draft.protocol == p) {
                            onChange(draft.copy(protocol = p))
                            ai.resetModelList()
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.baseUrl,
                onValueChange = { onChange(draft.copy(baseUrl = it)) },
                label = { Text("Base URL") },
                singleLine = true,
                keyboardOptions = plainText.copy(keyboardType = KeyboardType.Uri),
                supportingText = {
                    Text(
                        when (draft.protocol) {
                            Protocol.OPENAI -> "Usually ends with /v1"
                            Protocol.ANTHROPIC -> "https://api.anthropic.com, or an Anthropic-compatible endpoint"
                            Protocol.GEMINI -> "https://generativelanguage.googleapis.com"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { onChange(draft.copy(apiKey = it)) },
                label = { Text(if (draft.needsKey) "API key" else "API key (optional)") },
                placeholder = { if (draft.hasSavedKey) Text("Saved — leave empty to keep it") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = plainText.copy(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { onChange(draft.copy(model = it)) },
                    label = { Text("Model") },
                    singleLine = true,
                    keyboardOptions = plainText,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        label = if (ai.isLoadingModels) "Connecting…" else "Fetch models",
                        enabled = !ai.isLoadingModels,
                    ) { ai.fetchModels(draft) }
                    if (ai.availableModels.isNotEmpty()) {
                        Box {
                            Chip("Choose model ▾") { modelMenuOpen = true }
                            DropdownMenu(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false },
                                modifier = Modifier.heightIn(max = 360.dp),
                            ) {
                                ai.availableModels.forEach { id ->
                                    DropdownMenuItem(
                                        text = { Text(id, fontSize = 13.sp) },
                                        onClick = {
                                            modelMenuOpen = false
                                            onChange(draft.copy(model = id))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                ai.modelsMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (msg.startsWith("Connected")) Accent else ErrorRed,
                        fontSize = 12.sp,
                    )
                }
            }

            val levels = Thinking.levelsFor(draft.protocol, draft.model.trim())
            if (levels.size > 1) {
                val selected = Thinking.resolve(draft.protocol, draft.model.trim(), draft.thinkingLevel)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Thinking effort", color = Muted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        levels.forEach { level ->
                            Chip(level.label, selected = level == selected) { onChange(draft.copy(thinkingLevel = level)) }
                        }
                    }
                    Text(selected.description, color = Muted.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            }

            OutlinedTextField(
                value = draft.maxTokens,
                onValueChange = { v -> onChange(draft.copy(maxTokens = v.filter { it.isDigit() }.take(6))) },
                label = { Text("Max output tokens") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.extraSystem,
                onValueChange = { onChange(draft.copy(extraSystem = it)) },
                label = { Text("Coach instructions (optional)") },
                placeholder = { Text("e.g. I train 4 days a week for powerlifting; my left shoulder is sensitive.") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Delete provider", color = ErrorRed)
                }
            }
        }
    }
}

// ============================================================================
// Coach tab: chat panel
// ============================================================================

private fun chatDate(epochMs: Long): String = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(epochMs))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiCoachPanel(viewModel: LiftTrackerViewModel) {
    val ai = viewModel.ai
    val chat = ai.currentChat
    val messages = chat?.messages.orEmpty()
    val streaming = ai.streamingChatId != null && ai.streamingChatId == chat?.id
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var providerMenuOpen by remember { mutableStateOf(false) }

    // Follow the conversation as messages arrive and the reply grows.
    LaunchedEffect(chat?.id, messages.size, ai.chatStreamingText.length) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.scrollToItem(count - 1, Int.MAX_VALUE / 2)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        Surface(color = CardBg) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcons.Sparkles(modifier = Modifier.size(18.dp), tint = Accent)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = chat?.title ?: "New chat",
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        Text(
                            text = (ai.activeProvider?.label ?: "No provider") + if (ai.providers.size > 1) " ▾" else "",
                            color = Accent,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = ai.providers.size > 1) { providerMenuOpen = true },
                        )
                        DropdownMenu(expanded = providerMenuOpen, onDismissRequest = { providerMenuOpen = false }) {
                            ai.providers.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.label, fontSize = 13.sp) },
                                    onClick = {
                                        providerMenuOpen = false
                                        ai.selectProvider(p.id)
                                    },
                                )
                            }
                        }
                    }
                }
                Chip("History", enabled = ai.chats.isNotEmpty()) { ai.isHistoryOpen = true }
                Chip("+ New", enabled = chat != null) { ai.startNewChat() }
            }
        }

        if (!ai.isConfigured) {
            Box(Modifier.padding(16.dp)) {
                SetUpAiCard(
                    viewModel,
                    "Add an AI provider with your own API key (Claude, an OpenAI-compatible service, Gemini) or a " +
                        "local model. The coach can then read your training log and answer questions about it.",
                )
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && !streaming) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Ask about your training",
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Choose below what the coach can see: your whole training log, or specific " +
                                "routines, exercises and workouts to focus on.",
                            color = Muted,
                            fontSize = 13.sp,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CoachPrompts.QUICK_PROMPTS.forEach { (label, prompt) ->
                                Chip(label) { ai.sendChatMessage(prompt, title = label) }
                            }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { m ->
                ChatBubble(
                    message = m,
                    onRetry = if (m.id == messages.last().id && m.error != null && !ai.isChatStreaming) {
                        { ai.retryLastReply() }
                    } else {
                        null
                    },
                )
            }
            if (streaming) {
                item(key = "streaming") {
                    AssistantBubble {
                        if (ai.chatStreamingText.isEmpty()) {
                            Text("Thinking…", color = Muted, fontSize = 14.sp)
                        } else {
                            MarkdownText(ai.chatStreamingText)
                        }
                    }
                }
            }
        }

        Surface(color = CardBg) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Context", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Chip(
                        label = if (ai.includeTrainingInChat) "✓ Training log" else "Training log",
                        selected = ai.includeTrainingInChat,
                    ) { ai.setIncludeTrainingInChat(!ai.includeTrainingInChat) }
                    ai.chatContext.forEach { item ->
                        Chip(label = "${item.kindLabel}: ${item.title.take(28)}  ✕", selected = true) {
                            ai.toggleContextItem(item)
                        }
                    }
                    Chip("+ Add") { ai.isContextPickerOpen = true }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Message your coach") },
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                    if (ai.isChatStreaming) {
                        Button(
                            onClick = { ai.stopChatReply() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        ) {
                            Text("Stop", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            enabled = input.isNotBlank(),
                            onClick = {
                                ai.sendChatMessage(input)
                                input = ""
                            },
                        ) {
                            Text("Send", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Header shared by the coach's full-screen layers. */
@Composable
private fun CoachLayerHeader(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
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
            Text(
                text = title,
                color = OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            action()
        }
    }
}

// ============================================================================
// Coach → chat history (full-screen layer)
// ============================================================================

@Composable
internal fun AiChatHistoryLayerScreen(viewModel: LiftTrackerViewModel) {
    val ai = viewModel.ai
    val close = { ai.isHistoryOpen = false }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = true) { close() }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            CoachLayerHeader("Chat history", close) {
                Chip("+ New chat") {
                    ai.startNewChat()
                    close()
                }
            }
            val q = query.trim()
            val shown = ai.chats.filter { c ->
                q.isEmpty() || c.title.contains(q, ignoreCase = true) || c.messages.any { it.text.contains(q, ignoreCase = true) }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search chats") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            text = if (ai.chats.isEmpty()) "No chats yet." else "No chats match \"$q\".",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { c ->
                    val isCurrent = c.id == ai.currentChatId
                    Card(
                        onClick = {
                            ai.openChat(c.id)
                            close()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, if (isCurrent) Accent.copy(alpha = 0.6f) else Outline),
                    ) {
                        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = c.title,
                                    color = OnSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                c.messages.lastOrNull { it.text.isNotBlank() }?.let { last ->
                                    Text(
                                        text = last.text.replace('\n', ' ').take(140),
                                        color = Muted,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = chatDate(c.updatedEpochMs) + " · ${c.messages.size} messages" +
                                        if (c.contextItems.isEmpty()) "" else " · " + c.contextItems.joinToString(", ") { it.title },
                                    color = if (isCurrent) Accent else Muted.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.requestConfirmation(
                                        title = "Delete chat?",
                                        message = "\"${c.title}\" will be removed from this phone.",
                                        confirmLabel = "Delete",
                                    ) { ai.deleteChat(c.id) }
                                },
                            ) {
                                Text("Delete", color = ErrorRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Coach → add context (full-screen layer)
// ============================================================================

@Composable
internal fun AiContextPickerLayerScreen(viewModel: LiftTrackerViewModel) {
    val ai = viewModel.ai
    val close = { ai.isContextPickerOpen = false }
    var kind by rememberSaveable { mutableStateOf(AiContextItem.ROUTINE) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = true) { close() }

    val selected = ai.chatContext
    val q = query.trim()
    // (item, subtitle) rows of the chosen kind.
    val rows: List<Pair<AiContextItem, String>> = when (kind) {
        AiContextItem.ROUTINE -> viewModel.routines.map { r ->
            AiContextItem(AiContextItem.ROUTINE, r.id, r.title) to
                (if (r.folderName.isBlank()) "" else "${r.folderName} · ") + "${r.exercises.size} exercises"
        }
        AiContextItem.EXERCISE -> {
            // Exercises you have logged come first, most-trained on top.
            val counts = remember(viewModel.workoutHistory.size) {
                val m = HashMap<String, Int>()
                viewModel.workoutHistory.forEach { s -> s.exercises.map { it.exerciseId }.distinct().forEach { m.merge(it, 1, Int::plus) } }
                m
            }
            viewModel.exercises.sortedByDescending { counts[it.id] ?: 0 }.map { e ->
                val n = counts[e.id] ?: 0
                AiContextItem(AiContextItem.EXERCISE, e.id, e.title) to
                    e.primaryMuscle.displayName + if (n > 0) " · logged in $n workouts" else ""
            }
        }
        else -> viewModel.workoutHistory.sortedByDescending { it.endTimeEpochSeconds }.map { s ->
            AiContextItem(AiContextItem.WORKOUT, s.id, s.title) to
                SimpleDateFormat("EEE, MMM d yyyy", Locale.US).format(Date(s.endTimeEpochSeconds * 1000)) +
                " · ${s.exercises.size} exercises"
        }
    }.filter { q.isEmpty() || it.first.title.contains(q, ignoreCase = true) || it.second.contains(q, ignoreCase = true) }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            CoachLayerHeader("Add context", close) {
                Button(onClick = close) {
                    Text(if (selected.isEmpty()) "Done" else "Done (${selected.size})", fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "The coach gets the full details and your logged history of whatever you select here, in every " +
                        "message of this chat.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AiContextItem.ROUTINE to "Routines",
                        AiContextItem.EXERCISE to "Exercises",
                        AiContextItem.WORKOUT to "Workouts",
                    ).forEach { (k, label) ->
                        val n = selected.count { it.kind == k }
                        Chip(label = if (n > 0) "$label ($n)" else label, selected = kind == k) {
                            kind = k
                            query = ""
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (rows.isEmpty()) {
                    item { Text("Nothing here.", color = Muted, fontSize = 13.sp) }
                }
                items(rows, key = { it.first.kind + it.first.id }) { (item, subtitle) ->
                    val isOn = selected.any { it.kind == item.kind && it.id == item.id }
                    Surface(
                        onClick = { ai.toggleContextItem(item) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOn) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else CardBg,
                        border = BorderStroke(1.dp, if (isOn) Accent.copy(alpha = 0.6f) else Outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (subtitle.isNotBlank()) {
                                    Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(if (isOn) "✓" else "+", color = if (isOn) Accent else Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp),
            color = CardBg,
            border = BorderStroke(1.dp, Outline),
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    }
}

@Composable
private fun ChatBubble(message: AiChatMessage, onRetry: (() -> Unit)?) {
    if (message.isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        }
        return
    }
    val clipboard = LocalClipboardManager.current
    AssistantBubble {
        if (message.text.isNotBlank()) {
            SelectionContainer { MarkdownText(message.text) }
        }
        message.error?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            message.model?.let {
                Text(
                    text = it,
                    color = Muted.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } ?: Spacer(Modifier.weight(1f))
            if (message.text.isNotBlank()) {
                Text(
                    text = "Copy",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { clipboard.setText(AnnotatedString(message.text)) },
                )
            }
            if (onRetry != null) {
                Text(
                    text = "Retry",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onRetry() },
                )
            }
        }
    }
}

// ============================================================================
// Workout detail → AI analysis card
// ============================================================================

@Composable
internal fun WorkoutAiAnalysisCard(viewModel: LiftTrackerViewModel, session: WorkoutSession) {
    val ai = viewModel.ai
    if (!ai.isConfigured) {
        SetUpAiCard(viewModel, "Add an AI provider to get a coach's analysis of this workout.")
        return
    }
    val saved = ai.analyses[session.id]
    val running = ai.analyzingSessionId == session.id
    val busyElsewhere = ai.analyzingSessionId != null && !running

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcons.Sparkles(modifier = Modifier.size(18.dp), tint = Accent)
                Text("AI Coach Analysis", color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            when {
                running -> {
                    if (ai.analysisStreamingText.isEmpty()) {
                        Text("Reading your workout…", color = Muted, fontSize = 13.sp)
                    } else {
                        MarkdownText(ai.analysisStreamingText)
                    }
                    OutlinedButton(onClick = { ai.stopAnalysis() }) {
                        Text("Stop", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                saved != null -> {
                    if (saved.output.isNotBlank()) {
                        SelectionContainer { MarkdownText(saved.output) }
                    }
                    saved.error?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                    Text(
                        text = saved.model + " · " +
                            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(saved.createdEpochMs)),
                        color = Muted.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busyElsewhere,
                            onClick = { ai.analyzeWorkout(session) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("↻ Analyze again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (saved.output.isNotBlank()) {
                            OutlinedButton(
                                enabled = !ai.isChatStreaming,
                                onClick = { if (ai.discussAnalysis(session)) viewModel.openCoachTab() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Discuss in chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Compares this session with your previous performances of the same exercises and " +
                            "suggests what to change next time.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Button(enabled = !busyElsewhere, onClick = { ai.analyzeWorkout(session) }) {
                        Text("Analyze this workout", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
