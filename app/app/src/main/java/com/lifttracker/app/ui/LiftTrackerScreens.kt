package com.lifttracker.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.lifttracker.app.domain.CustomExerciseCodec
import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RepType
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import com.lifttracker.app.backup.BackupKind
import com.lifttracker.app.export.ExportFormat
import com.lifttracker.app.export.ExportProvider
import com.lifttracker.app.export.WorkoutExporter
import com.lifttracker.app.notify.RestTimerAlerts
import com.lifttracker.app.notify.WorkoutSessionNotification
import com.lifttracker.app.ui.theme.GarminBlue
import com.lifttracker.app.ui.theme.LiftTrackerTheme
import com.lifttracker.app.ui.theme.SamsungGreen
import com.lifttracker.app.ui.theme.ThemeConfig
import com.lifttracker.app.ui.theme.ThemeMode
import com.lifttracker.app.ui.theme.ThemePreset
import com.lifttracker.app.domain.ExerciseChartMetric
import com.lifttracker.app.viewmodel.ExerciseSubTab
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.MainTab
import com.lifttracker.app.viewmodel.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val BgDark: Color @Composable get() = MaterialTheme.colorScheme.background
private val CardDark: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val CardElevated: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val CardOutline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
private val AccentBlue: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AccentCyan: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val AccentTertiary: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val AccentGreen = Color(0xFF27AE60)
private val AccentAmber = Color(0xFFF2994A)
private val AccentOrangeDeep = Color(0xFFFF6B35)
private val AccentRed = Color(0xFFEB5757)
private val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun rpeColor(rpe: Double?): Color {
    if (rpe == null) return TextSecondary
    return when {
        rpe <= 5.5 -> AccentCyan
        rpe <= 7.0 -> AccentGreen
        rpe <= 8.5 -> AccentAmber
        rpe <= 9.5 -> AccentOrangeDeep
        else -> AccentRed
    }
}

@Composable
private fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotColor: Color? = null,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dotColor?.let {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(it),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SegmentedSwitcher(
    selectedIndex: Int,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricHighlightCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, CardOutline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 1.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Keeps an input field and its caret scrolled cleanly above the soft keyboard
 * whenever focused or as the IME expands.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Modifier.keepCaretVisibleAboveIme(): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeVisible = WindowInsets.isImeVisible
    var isFocused by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isFocused, isImeVisible, imeBottom, fieldSize) {
        if (isFocused && imeBottom > 0 && fieldSize.height > 0) {
            withFrameNanos { }
            val topPad = with(density) { 8.dp.toPx() }
            val bottomPad = with(density) { 28.dp.toPx() }
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = -topPad,
                    right = fieldSize.width.toFloat(),
                    bottom = fieldSize.height.toFloat() + bottomPad
                )
            )
        }
    }

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onSizeChanged { fieldSize = it }
        .onFocusChanged { isFocused = it.isFocused }
}

@Composable
private fun SearchClearButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Clear search",
            tint = TextSecondary
        )
    }
}

@Composable
private fun NumericSetInputField(
    displayValue: String,
    isDecimal: Boolean,
    onValueCommit: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var field by remember { mutableStateOf(TextFieldValue(displayValue)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(displayValue, isFocused) {
        if (!isFocused) {
            field = TextFieldValue(displayValue)
        }
    }
    // Select the whole value when the field is tapped, so typing replaces it instead of appending.
    // Runs after the tap has placed its cursor, which would otherwise undo the selection.
    LaunchedEffect(isFocused) {
        if (isFocused) {
            field = field.copy(selection = TextRange(0, field.text.length))
        }
    }

    BasicTextField(
        value = field,
        onValueChange = { raw ->
            val filtered = buildString {
                var dotSeen = false
                for (ch in raw.text) {
                    val normalized = if (isDecimal && ch == ',') '.' else ch
                    if (normalized.isDigit()) {
                        append(normalized)
                    } else if (isDecimal && normalized == '.' && !dotSeen) {
                        dotSeen = true
                        append(normalized)
                    }
                }
            }
            if (filtered == raw.text) {
                field = raw
            } else if (raw.text == field.text) {
                // Only the cursor moved in a value with letters in it ("BW"): keep it as it is.
                field = raw
            } else {
                field = TextFieldValue(filtered, TextRange(filtered.length))
            }
            if (raw.text != displayValue) {
                filtered.toDoubleOrNull()?.let { parsed -> onValueCommit(parsed) }
            }
        },
        singleLine = true,
        textStyle = TextStyle(
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(AccentCyan),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        ),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CardElevated)
            .keepCaretVisibleAboveIme()
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                if (wasFocused && !focusState.isFocused) {
                    val parsed = field.text.toDoubleOrNull()
                    if (parsed != null && field.text != displayValue) {
                        onValueCommit(parsed)
                    } else {
                        field = TextFieldValue(displayValue)
                    }
                }
            }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

/** Parses a rest time typed as seconds ("90") or minutes:seconds ("1:30"). */
private fun parseRestInput(text: String): Int? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    if (clean.equals("off", ignoreCase = true)) return 0
    val parts = clean.split(':', '.', ' ').filter { it.isNotEmpty() }
    return when (parts.size) {
        1 -> parts[0].toIntOrNull()
        2 -> parts[0].toIntOrNull()?.let { m -> parts[1].toIntOrNull()?.let { sec -> m * 60 + sec } }
        else -> null
    }?.coerceIn(0, 3600)
}

/**
 * Rest time editor: a field that takes typed seconds or m:ss, between −15s and +15s buttons.
 * 0 shows as "Off".
 */
@Composable
private fun RestStepper(
    seconds: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val display = WorkoutMechanics.formatRest(seconds)
    var field by remember { mutableStateOf(TextFieldValue(display)) }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(display, isFocused) {
        if (!isFocused) field = TextFieldValue(display)
    }
    LaunchedEffect(isFocused) {
        if (isFocused) field = field.copy(selection = TextRange(0, field.text.length))
    }
    fun commit() {
        parseRestInput(field.text)?.let { if (it != seconds) onChange(it) }
        field = TextFieldValue(WorkoutMechanics.formatRest(parseRestInput(field.text) ?: seconds))
    }
    // −/+ work from what is typed in the field right now, not from the last saved value.
    fun stepBy(delta: Int) {
        val base = if (isFocused) parseRestInput(field.text) ?: seconds else seconds
        val next = (base + delta).coerceIn(0, 3600)
        field = TextFieldValue(WorkoutMechanics.formatRest(next))
        isFocused = false // so losing focus below does not commit the old text over the new value
        focusManager.clearFocus()
        onChange(next)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { stepBy(-15) },
            enabled = seconds > 0,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("−15s", color = if (seconds > 0) AccentCyan else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        BasicTextField(
            value = field,
            onValueChange = { raw ->
                field = raw.copy(text = raw.text.filter { it.isDigit() || it == ':' || it == '.' }.take(5))
            },
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(AccentCyan),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(CardElevated)
                .keepCaretVisibleAboveIme()
                .onFocusChanged { focusState ->
                    val wasFocused = isFocused
                    isFocused = focusState.isFocused
                    if (wasFocused && !focusState.isFocused) commit()
                }
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )
        OutlinedButton(
            onClick = { stepBy(15) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("+15s", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Replace / Remove menu for an exercise in a workout or a routine, opened by a long press. */
@Composable
private fun ExerciseActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = CardElevated) {
        DropdownMenuItem(
            text = { Text("⇄ Replace Exercise", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
            onClick = {
                onDismiss()
                onReplace()
            }
        )
        DropdownMenuItem(
            text = { Text("🗑 Remove Exercise", color = AccentRed, fontWeight = FontWeight.SemiBold) },
            onClick = {
                onDismiss()
                onRemove()
            }
        )
    }
}

/** Exercise search in a dialog, used to pick the replacement for an exercise. */
@Composable
internal fun ExercisePickerDialog(
    title: String,
    exercises: List<ExerciseDefinition>,
    onDismiss: () -> Unit,
    onSelect: (ExerciseDefinition) -> Unit
) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = BgDark, shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
                }
                Spacer(modifier = Modifier.height(6.dp))
                DatasetExercisePickerCard(
                    exercises = exercises,
                    query = query,
                    onQueryChange = { query = it },
                    onSelectExercise = onSelect
                )
            }
        }
    }
}

@Composable
private fun ReorderButton(
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CardElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "≡",
            color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Compact folded-exercise layer for reordering exercises in a workout session or routine.
 * Every exercise collapses into a single-line card that glides smoothly while dragged.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderExercisesLayer(
    exercises: List<WorkoutExerciseEntry>,
    highlightedExerciseId: String?,
    findExercise: (String, String) -> ExerciseDefinition?,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentExercises by rememberUpdatedState(exercises)
    val currentOnMove by rememberUpdatedState(onMove)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(highlightedExerciseId) {
        val targetIdx = exercises.indexOfFirst { it.id == highlightedExerciseId }
        if (targetIdx >= 0) {
            listState.scrollToItem(targetIdx)
        }
    }

    // Auto-scroll while dragging near the top or bottom edge of the folded list.
    LaunchedEffect(draggingId) {
        val activeId = draggingId ?: return@LaunchedEffect
        val edgeThresholdPx = 110f
        while (isActive && draggingId == activeId) {
            val layoutInfo = listState.layoutInfo
            val draggedInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeId }
            if (draggedInfo != null) {
                val visualTop = draggedInfo.offset + draggingOffsetY
                val visualBottom = visualTop + draggedInfo.size
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset
                val scrollBy = when {
                    visualTop < viewportStart + edgeThresholdPx && listState.canScrollBackward -> -14f
                    visualBottom > viewportEnd - edgeThresholdPx && listState.canScrollForward -> 14f
                    else -> 0f
                }
                if (scrollBy != 0f) {
                    listState.scrollBy(scrollBy)
                }
            }
            withFrameNanos { }
        }
    }

    Surface(
        color = BgDark.copy(alpha = 0.98f),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = CardDark, tonalElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "REORDER EXERCISES",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Drag any folded exercise up or down",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("✓ Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Anchors index 0 so swapping the top two exercises never jumps the scroll offset.
                item(key = "reorder_top_anchor") {
                    Spacer(modifier = Modifier.height(1.dp))
                }
                itemsIndexed(
                    items = exercises,
                    key = { _, item -> item.id }
                ) { idx, entry ->
                    val isDragging = draggingId == entry.id
                    val isHighlighted = entry.id == highlightedExerciseId
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.03f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "reorderItemScale"
                    )
                    val exDef = findExercise(entry.exerciseId, entry.exerciseTitle)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging || isHighlighted) CardElevated else CardDark
                        ),
                        border = BorderStroke(
                            width = if (isDragging || isHighlighted) 1.5.dp else 1.dp,
                            color = when {
                                isDragging -> AccentCyan
                                isHighlighted -> AccentBlue.copy(alpha = 0.75f)
                                else -> CardOutline
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 10f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = draggingOffsetY
                                }
                                scaleX = scale
                                scaleY = scale
                            }
                            .pointerInput(entry.id) {
                                var activeDragY = 0f
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        activeDragY = 0f
                                        draggingId = entry.id
                                        draggingOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        val startY = activeDragY
                                        scope.launch {
                                            Animatable(startY).animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            ) {
                                                if (draggingId == entry.id) draggingOffsetY = value
                                            }
                                            if (draggingId == entry.id) {
                                                draggingId = null
                                                draggingOffsetY = 0f
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        val startY = activeDragY
                                        scope.launch {
                                            Animatable(startY).animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            ) {
                                                if (draggingId == entry.id) draggingOffsetY = value
                                            }
                                            if (draggingId == entry.id) {
                                                draggingId = null
                                                draggingOffsetY = 0f
                                            }
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    activeDragY += dragAmount

                                    val visible = listState.layoutInfo.visibleItemsInfo
                                    val draggedInfo = visible.firstOrNull { it.key == entry.id }
                                    val currentListIdx = currentExercises.indexOfFirst { it.id == entry.id }
                                    if (draggedInfo != null && currentListIdx >= 0) {
                                        val draggedCenterY = draggedInfo.offset + activeDragY + draggedInfo.size / 2f
                                        val prevInfo = visible.firstOrNull {
                                            it.index == draggedInfo.index - 1 && it.key != "reorder_top_anchor"
                                        }
                                        val nextInfo = visible.firstOrNull { it.index == draggedInfo.index + 1 }

                                        if (prevInfo != null && currentListIdx > 0 &&
                                            draggedCenterY < prevInfo.offset + prevInfo.size / 2f
                                        ) {
                                            currentOnMove(currentListIdx, currentListIdx - 1)
                                            activeDragY += (draggedInfo.offset - prevInfo.offset).toFloat()
                                        } else if (nextInfo != null && currentListIdx < currentExercises.lastIndex &&
                                            draggedCenterY > nextInfo.offset + nextInfo.size / 2f
                                        ) {
                                            currentOnMove(currentListIdx, currentListIdx + 1)
                                            activeDragY -= (nextInfo.offset - draggedInfo.offset).toFloat()
                                        }
                                    }
                                    draggingOffsetY = activeDragY
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDragging) AccentCyan.copy(alpha = 0.2f) else CardElevated)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "≡",
                                    color = if (isDragging) AccentCyan else TextSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DatasetExerciseImage(
                                imagePath = exDef?.imagePath ?: "",
                                contentDescription = entry.exerciseTitle,
                                modifier = Modifier.size(38.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${idx + 1}. ${entry.exerciseTitle}",
                                    color = if (isDragging || isHighlighted) AccentCyan else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${entry.sets.size} set${if (entry.sets.size == 1) "" else "s"} · Rest ${WorkoutMechanics.formatRest(entry.restTimerSeconds)}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            if (idx > 0) {
                                SmallActionChip("▲") { currentOnMove(idx, idx - 1) }
                            }
                            if (idx < exercises.lastIndex) {
                                SmallActionChip("▼") { currentOnMove(idx, idx + 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragReorderHandle(
    itemKey: Any,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragOffsetChange: (Float) -> Unit
) {
    val stepPx = with(LocalDensity.current) { 52.dp.toPx() }
    val latestCanUp by rememberUpdatedState(canMoveUp)
    val latestCanDown by rememberUpdatedState(canMoveDown)
    val latestMoveUp by rememberUpdatedState(onMoveUp)
    val latestMoveDown by rememberUpdatedState(onMoveDown)
    var accumY by remember(itemKey) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CardElevated)
            .pointerInput(itemKey) {
                detectVerticalDragGestures(
                    onDragStart = {
                        accumY = 0f
                        onDragOffsetChange(0f)
                    },
                    onDragEnd = {
                        accumY = 0f
                        onDragOffsetChange(0f)
                    },
                    onDragCancel = {
                        accumY = 0f
                        onDragOffsetChange(0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    accumY += dragAmount
                    if (accumY < -stepPx && latestCanUp) {
                        latestMoveUp()
                        accumY += stepPx
                    } else if (accumY > stepPx && latestCanDown) {
                        latestMoveDown()
                        accumY -= stepPx
                    }
                    onDragOffsetChange(accumY)
                }
            }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "≡",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

internal object DatasetMediaLoader {
    private val bitmapCache = LruCache<String, ImageBitmap>(120)

    suspend fun loadBitmap(context: Context, relativePath: String): ImageBitmap? {
        if (relativePath.isBlank()) return null
        bitmapCache.get(relativePath)?.let { return it }
        val bytes = loadBytes(context, relativePath) ?: return null
        val bmp = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        if (bmp != null) {
            bitmapCache.put(relativePath, bmp)
        }
        return bmp
    }

    suspend fun loadAnimatedDrawable(context: Context, relativePath: String): Drawable? {
        if (relativePath.isBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val bytes = loadBytes(context, relativePath) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ImageDecoder.decodeDrawable(source)
            }.getOrNull()
        }
    }

    /** Bytes of a library picture: a custom exercise's file, a bundled asset, or the dataset download (cached). */
    suspend fun loadBytes(context: Context, relativePath: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            if (relativePath.startsWith(CustomExerciseCodec.IMAGE_PREFIX)) {
                val file = File(
                    File(context.filesDir, CustomExerciseCodec.IMAGE_DIR),
                    relativePath.removePrefix(CustomExerciseCodec.IMAGE_PREFIX).substringAfterLast('/')
                )
                return@withContext runCatching { file.readBytes() }.getOrNull()
            }
            runCatching {
                context.assets.open(relativePath).use { it.readBytes() }
            }.getOrNull()?.let { return@withContext it }

            val cacheFile = File(File(context.cacheDir, "dataset_media"), relativePath)
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                runCatching { cacheFile.readBytes() }.getOrNull()?.let { return@withContext it }
            }

            runCatching {
                val url = URL("${WorkoutMechanics.DATASET_RAW_BASE_URL}$relativePath")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode in 200..299) {
                    val data = conn.inputStream.use { it.readBytes() }
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeBytes(data)
                    data
                } else {
                    null
                }
            }.getOrNull()
        }
    }
}

@Composable
internal fun DatasetExerciseImage(
    imagePath: String,
    gifPath: String = "",
    animateGif: Boolean = false,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val animatedDrawable by produceState<Drawable?>(
        initialValue = null,
        key1 = gifPath,
        key2 = animateGif
    ) {
        value = if (animateGif && gifPath.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DatasetMediaLoader.loadAnimatedDrawable(context, gifPath)
        } else {
            null
        }
    }

    val staticBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = imagePath
    ) {
        value = if (imagePath.isNotBlank()) {
            DatasetMediaLoader.loadBitmap(context, imagePath)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .border(1.dp, CardElevated, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val drawable = animatedDrawable
        if (drawable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        this.contentDescription = contentDescription
                    }
                },
                update = { imageView ->
                    imageView.setImageDrawable(drawable)
                    if (drawable is AnimatedImageDrawable) {
                        drawable.start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (staticBitmap != null) {
            Image(
                bitmap = staticBitmap!!,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "🏋",
                fontSize = 20.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiftTrackerRoot(viewModel: LiftTrackerViewModel) {
    // Hoisted so opening/closing ExerciseDetailLayer keeps exact scroll position
    val exerciseListState = rememberLazyListState()
    val isKeyboardOpen = WindowInsets.isImeVisible

    // 1-second ticker for active workout timer and rest timer
    LaunchedEffect(viewModel.isWorkoutActive, viewModel.isTimerPaused) {
        while (true) {
            delay(1000L)
            if (viewModel.isWorkoutActive && !viewModel.isTimerPaused) {
                viewModel.onTimerTick()
            }
        }
    }

    // Rest timer alerts are notifications: ask for permission when a workout starts (Android 13+).
    val rootContext = LocalContext.current
    val askNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.notificationStateVersion++ }
    LaunchedEffect(viewModel.isWorkoutActive) {
        if (viewModel.isWorkoutActive && viewModel.restAlertEnabled && Build.VERSION.SDK_INT >= 33 &&
            !RestTimerAlerts.hasNotificationPermission(rootContext)
        ) {
            askNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // On every return to the foreground: re-check Health Connect grants, pull new weight /
    // body-fat records, and re-check the watch connection.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealthConnectState()
                viewModel.refreshWatchStatus()
                viewModel.notificationStateVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLayerAboveTabs = viewModel.isExerciseDetailLayerOpen ||
        viewModel.profileLayer != null ||
        viewModel.isWorkoutSessionDetailLayerOpen ||
        viewModel.isRoutineEditorLayerOpen ||
        viewModel.assist.optimizeTarget != null ||
        viewModel.assist.isSetupOpen ||
        viewModel.assist.exerciseEditor != null ||
        viewModel.ai.isHistoryOpen ||
        viewModel.ai.isContextPickerOpen
    val isAnyOverlayOpen = viewModel.isSettingsLayerOpen ||
        viewModel.isWorkoutLayerOpen ||
        isLayerAboveTabs ||
        viewModel.pendingConfirmation != null

    var doubleBackArmed by remember { mutableStateOf(false) }
    var exitHintVisible by remember { mutableStateOf(false) }
    var backHintNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(isAnyOverlayOpen) {
        if (isAnyOverlayOpen) {
            doubleBackArmed = false
            exitHintVisible = false
        }
    }
    LaunchedEffect(backHintNonce) {
        if (backHintNonce > 0) {
            delay(1300L)
            exitHintVisible = false
            delay(700L)
            doubleBackArmed = false
        }
    }

    LiftTrackerTheme(themeConfig = viewModel.themeConfig) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Double back on root tabs to leave the app; first back shows a fading notification.
            BackHandler(enabled = !isAnyOverlayOpen && !doubleBackArmed) {
                doubleBackArmed = true
                exitHintVisible = true
                backHintNonce++
            }

            Scaffold(
                containerColor = BgDark,
                topBar = {
                    if (!viewModel.isSettingsLayerOpen) {
                        TopHeaderBar(viewModel)
                    }
                },
                bottomBar = {
                    if (!isKeyboardOpen) {
                        Column {
                            if (viewModel.isWorkoutActive && !viewModel.isWorkoutLayerOpen) {
                                MinimizedWorkoutBar(viewModel)
                            }
                            BottomNavigationBar(viewModel)
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding()
                ) {
                    if (viewModel.isSettingsLayerOpen) {
                        BackHandler(enabled = true) {
                            if (viewModel.activeSettingsSubScreen != null) {
                                viewModel.openSettingsSubScreen(null)
                            } else {
                                viewModel.closeSettingsLayer()
                            }
                        }
                        SettingsLayerScreen(viewModel)
                    } else {
                        when (viewModel.selectedTab) {
                            MainTab.WORKOUT -> WorkoutOverviewScreen(viewModel)
                            MainTab.EXERCISES -> ExercisesScreen(viewModel, exerciseListState)
                            MainTab.ROUTINES -> RoutinesScreen(viewModel)
                            MainTab.COACH -> AiCoachPanel(viewModel)
                            MainTab.PROFILE -> ProfileScreen(viewModel)
                        }
                    }
                }
            }

            // Layers above the tabs. While a workout is running and minimized, its bar stays
            // docked under every one of them, so the session is one tap away on any page.
            val showDockedWorkoutBar = isLayerAboveTabs && !isKeyboardOpen &&
                viewModel.isWorkoutActive && !viewModel.isWorkoutLayerOpen

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            // The docked bar covers the navigation bar inset for the layers.
                            if (showDockedWorkoutBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                            else Modifier
                        )
                ) {
                    // Profile pages; a workout opened from their calendars shows above them.
                    ProfileLayerHost(viewModel)

                    if (viewModel.isWorkoutSessionDetailLayerOpen) {
                        BackHandler(enabled = true) {
                            viewModel.closeWorkoutSessionDetailLayer()
                        }
                        WorkoutSessionDetailLayerScreen(viewModel)
                    }

                    if (viewModel.isRoutineEditorLayerOpen) {
                        BackHandler(enabled = true) {
                            viewModel.requestCloseRoutineEditorLayer()
                        }
                        RoutineEditorLayerScreen(viewModel)
                    }

                    // Above the workout detail, which can open it; Back returns to that page.
                    if (viewModel.isExerciseDetailLayerOpen) {
                        BackHandler(enabled = true) {
                            viewModel.closeExerciseDetailLayer()
                        }
                        ExerciseDetailLayerScreen(viewModel)
                    }

                    // AI assist layers; each handles Back itself.
                    if (viewModel.assist.optimizeTarget != null) RoutineOptimizeLayerScreen(viewModel)
                    if (viewModel.assist.isSetupOpen) RoutineSetupLayerScreen(viewModel)
                    if (viewModel.assist.exerciseEditor != null) ExerciseEditorLayerScreen(viewModel)

                    // Coach layers; each handles Back itself.
                    if (viewModel.ai.isHistoryOpen) AiChatHistoryLayerScreen(viewModel)
                    if (viewModel.ai.isContextPickerOpen) AiContextPickerLayerScreen(viewModel)
                }
                if (showDockedWorkoutBar) {
                    MinimizedWorkoutBar(viewModel, coversNavigationBar = true)
                }
            }

            // The running workout is a drawer: it slides up over whatever page is showing and
            // slides back down to that same page when minimized.
            BackHandler(enabled = viewModel.isWorkoutLayerOpen) {
                viewModel.minimizeWorkoutLayer()
            }
            AnimatedVisibility(
                visible = viewModel.isWorkoutLayerOpen,
                enter = slideInVertically(animationSpec = tween(220)) { it },
                exit = slideOutVertically(animationSpec = tween(200)) { it }
            ) {
                ActiveWorkoutSessionLayer(viewModel)
            }

            AnimatedVisibility(
                visible = exitHintVisible,
                enter = fadeIn(animationSpec = tween(150)) + slideInVertically(animationSpec = tween(150)) { it / 2 },
                exit = fadeOut(animationSpec = tween(700)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (viewModel.isWorkoutActive) 136.dp else 92.dp, start = 24.dp, end = 24.dp)
                    .zIndex(50f)
            ) {
                Surface(
                    color = CardElevated.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = "Press back again to exit",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }

            viewModel.pendingConfirmation?.let { request ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissPendingConfirmation() },
                    containerColor = CardDark,
                    titleContentColor = TextPrimary,
                    textContentColor = TextSecondary,
                    title = { Text(request.title, fontWeight = FontWeight.Bold) },
                    text = { Text(request.message, fontSize = 13.sp) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmPendingAction() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                        ) {
                            Text(request.confirmLabel, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissPendingConfirmation() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MinimizedWorkoutBar(viewModel: LiftTrackerViewModel, coversNavigationBar: Boolean = false) {
    val openThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val flickThresholdPx = with(LocalDensity.current) { 10.dp.toPx() }
    var dragPreviewY by remember { mutableFloatStateOf(0f) }

    Surface(
        color = CardElevated,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.45f)),
        modifier = Modifier.graphicsLayer { translationY = dragPreviewY }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.openActiveWorkoutLayer() }
                // Pull up anywhere on the minimized bar to open the workout drawer.
                .pointerInput(Unit) {
                    var dragged = 0f
                    var triggered = false
                    val velocityTracker = VelocityTracker()
                    fun maybeOpenOnRelease() {
                        val velocityY = velocityTracker.calculateVelocity().y
                        if (!triggered && (dragged <= -flickThresholdPx || velocityY < -350f)) {
                            triggered = true
                            viewModel.openActiveWorkoutLayer()
                        }
                        dragged = 0f
                        dragPreviewY = 0f
                    }
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragged = 0f
                            triggered = false
                            dragPreviewY = 0f
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = { maybeOpenOnRelease() },
                        onDragCancel = { maybeOpenOnRelease() }
                    ) { change, amount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        dragged += amount
                        dragPreviewY = dragged.coerceIn(-48f, 0f)
                        if (!triggered && dragged <= -openThresholdPx) {
                            triggered = true
                            dragPreviewY = 0f
                            viewModel.openActiveWorkoutLayer()
                        }
                    }
                }
                .then(if (coversNavigationBar) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WORKOUT · ${WorkoutMechanics.formatDuration(viewModel.workoutElapsedSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    text = "${viewModel.activeWorkoutTitle} (${viewModel.activeCompletedSets}/${viewModel.activeTotalSets} sets)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Rest timer controls; tapping anywhere else on the bar opens the workout.
            val restActive = viewModel.restTimerRemainingSeconds > 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SmallActionChip("−15") { viewModel.adjustRestTimer(-15) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("REST", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = WorkoutMechanics.formatClock(viewModel.restTimerRemainingSeconds),
                        color = if (restActive) AccentCyan else TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
                SmallActionChip("+15") { viewModel.adjustRestTimer(15) }
                SmallActionChip("Skip") { viewModel.skipRestTimer() }
            }
        }
    }
}

@Composable
private fun TopHeaderBar(viewModel: LiftTrackerViewModel) {
    Surface(color = BgDark) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcons.Barbell(
                        modifier = Modifier.size(22.dp),
                        tint = AccentBlue
                    )
                }
                Column {
                    Text(
                        text = "Lift Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${viewModel.selectedTab.title} · Strength Intelligence",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardElevated,
                    border = BorderStroke(1.dp, CardOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppIcons.HeartPulse(
                            modifier = Modifier.size(14.dp),
                            tint = AccentRed
                        )
                        Text(
                            text = if (viewModel.liveHeartRateBpm > 0) "${viewModel.liveHeartRateBpm}" else "--",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (viewModel.selectedTab == MainTab.PROFILE) {
                    Surface(
                        onClick = { viewModel.openSettingsLayer() },
                        shape = RoundedCornerShape(10.dp),
                        color = CardElevated,
                        border = BorderStroke(1.dp, CardOutline),
                        modifier = Modifier.semantics { contentDescription = "Settings" }
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                            AppIcons.Tune(modifier = Modifier.size(18.dp), tint = TextPrimary)
                        }
                    }
                }

                val watchReachable = viewModel.wearWatchStatus.isWatchWithAppReachable
                Surface(
                    onClick = { viewModel.openSettingsLayer(SettingsSection.WATCH) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (watchReachable) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else CardElevated,
                    border = BorderStroke(
                        1.dp,
                        if (watchReachable) AccentBlue.copy(alpha = 0.5f) else CardOutline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppIcons.Watch(
                            modifier = Modifier.size(14.dp),
                            tint = if (watchReachable) AccentBlue else TextSecondary
                        )
                        Text(
                            text = if (watchReachable) "Ready" else "Watch",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (watchReachable) AccentBlue else TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(viewModel: LiftTrackerViewModel) {
    NavigationBar(
        containerColor = CardDark,
        tonalElevation = 6.dp
    ) {
        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        )

        MainTab.entries.forEach { tab ->
            val isSelected = viewModel.selectedTab == tab
            val iconTint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    viewModel.closeSettingsLayer()
                    viewModel.selectedTab = tab
                },
                icon = {
                    when (tab) {
                        MainTab.WORKOUT -> AppIcons.Barbell(modifier = Modifier.size(24.dp), tint = iconTint)
                        MainTab.EXERCISES -> AppIcons.Analytics(modifier = Modifier.size(24.dp), tint = iconTint)
                        MainTab.ROUTINES -> AppIcons.Clipboard(modifier = Modifier.size(24.dp), tint = iconTint)
                        MainTab.COACH -> AppIcons.Sparkles(modifier = Modifier.size(24.dp), tint = iconTint)
                        MainTab.PROFILE -> AppIcons.Person(modifier = Modifier.size(24.dp), tint = iconTint)
                    }
                },
                label = {
                    Text(
                        text = tab.title,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = navColors
            )
        }
    }
}

// ============================================================================
// 1. WORKOUT OVERVIEW SCREEN (Start Routine OR Start Empty Session)
// ============================================================================

@Composable
private fun WorkoutOverviewScreen(viewModel: LiftTrackerViewModel) {
    val totalVolumeKg = remember(viewModel.workoutHistory.size, viewModel.workoutHistory.toList()) {
        viewModel.workoutHistory.sumOf { it.totalVolumeKg }
    }
    val totalDurationSec = remember(viewModel.workoutHistory.size, viewModel.workoutHistory.toList()) {
        viewModel.workoutHistory.sumOf { it.durationSeconds }
    }
    val totalHours = totalDurationSec / 3600L
    val totalMins = (totalDurationSec % 3600L) / 60L
    val timeLabel = if (totalHours > 0L) "${totalHours}h ${totalMins}m" else "${totalMins}m"
    val volumeDisplay = remember(totalVolumeKg, viewModel.weightUnit) {
        val converted = totalVolumeKg * viewModel.weightUnit.factorFromKg
        if (converted >= 1000.0) {
            String.format(Locale.US, "%.1fk", converted / 1000.0)
        } else {
            WorkoutMechanics.formatWeightValue(totalVolumeKg, viewModel.weightUnit)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        viewModel.workoutBannerMessage?.let { msg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.16f)),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, color = AccentGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            text = "Dismiss",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clickable { viewModel.workoutBannerMessage = null }
                                .padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        // Quick Metric Highlights (matching HealthAnalyzer's 3-column summary row)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricHighlightCard(
                    title = "Workouts",
                    value = "${viewModel.workoutHistory.size}",
                    unit = "sessions",
                    modifier = Modifier.weight(1f)
                )
                MetricHighlightCard(
                    title = "Total Vol",
                    value = volumeDisplay,
                    unit = viewModel.weightUnit.label,
                    modifier = Modifier.weight(1f)
                )
                MetricHighlightCard(
                    title = "Total Time",
                    value = timeLabel,
                    unit = "",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Start Workout Hero Card -> Choose Empty Session OR Open a Routine
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            text = if (viewModel.isWorkoutActive) "Session In Progress" else "Ready to Train",
                            containerColor = if (viewModel.isWorkoutActive) AccentGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            contentColor = if (viewModel.isWorkoutActive) AccentGreen else AccentBlue,
                            dotColor = if (viewModel.isWorkoutActive) AccentGreen else AccentBlue
                        )
                        if (viewModel.isWorkoutActive) {
                            Text(
                                text = WorkoutMechanics.formatDuration(viewModel.workoutElapsedSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (viewModel.isWorkoutActive) {
                            viewModel.activeWorkoutTitle
                        } else {
                            "Start an empty workout or pick a routine"
                        },
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (viewModel.isWorkoutActive) {
                            "${viewModel.activeCompletedSets}/${viewModel.activeTotalSets} sets completed · Tap below to return to active logging"
                        } else {
                            "Full-screen workout layer with rest timer, set types, optional RPE, and form guides."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (viewModel.isWorkoutActive) {
                        Button(
                            onClick = { viewModel.openActiveWorkoutLayer() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("▲ Resume Active Workout", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startEmptyWorkoutSession() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+ Empty Session", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { viewModel.selectedTab = MainTab.ROUTINES },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardOutline),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open Routines", color = AccentBlue, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Open a Routine directly into the Active Workout Layer
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Start from a Routine",
                            color = AccentBlue,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage →",
                            color = AccentBlue,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { viewModel.selectedTab = MainTab.ROUTINES }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (viewModel.routines.isEmpty()) {
                        Text(
                            text = "No routines yet. Create one in the Routines tab.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.routines.forEach { routine ->
                                Surface(
                                    onClick = { viewModel.startRoutineAsWorkout(routine) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = CardElevated,
                                    border = BorderStroke(1.dp, CardOutline),
                                    modifier = Modifier.width(196.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (routine.folderName.isNotBlank()) {
                                                AppIcons.Folder(modifier = Modifier.size(12.dp), tint = AccentCyan)
                                            } else {
                                                AppIcons.Play(modifier = Modifier.size(12.dp), tint = AccentBlue)
                                            }
                                            Text(
                                                text = routine.folderName.ifBlank { "Routine" },
                                                color = if (routine.folderName.isNotBlank()) AccentCyan else AccentBlue,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = routine.title,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${routine.exercises.size} exercises",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Past Workouts stored in BigStorage/workouts.sqlite (Long-press for Delete / Archive / Save as Routine)
        val activeLogs = viewModel.workoutHistory.filter { !it.archived }
        val archivedLogs = viewModel.workoutHistory.filter { it.archived }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Workout Logs",
                    color = AccentBlue,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Long-press for options",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (activeLogs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (archivedLogs.isEmpty()) {
                            "No workouts logged yet. Start an empty session or a routine above to log sets."
                        } else {
                            "No active workout logs. Check Archived Workouts below."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(activeLogs, key = { "wk_active_${it.id}" }) { session ->
                WorkoutSessionLogCard(
                    session = session,
                    viewModel = viewModel
                )
            }
        }

        if (archivedLogs.isNotEmpty()) {
            item(key = "archived_drawer_header") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.isArchivedWorkoutsExpanded = !viewModel.isArchivedWorkoutsExpanded }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (viewModel.isArchivedWorkoutsExpanded) {
                                AppIcons.ChevronDown(modifier = Modifier.size(16.dp), tint = AccentBlue)
                            } else {
                                AppIcons.ChevronRight(modifier = Modifier.size(16.dp), tint = AccentBlue)
                            }
                            Text(
                                text = "Archived Workouts",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        StatusBadge(
                            text = "${archivedLogs.size} archived",
                            containerColor = CardElevated,
                            contentColor = TextSecondary
                        )
                    }
                }
            }

            if (viewModel.isArchivedWorkoutsExpanded) {
                items(archivedLogs, key = { "wk_arch_${it.id}" }) { session ->
                    WorkoutSessionLogCard(
                        session = session,
                        viewModel = viewModel
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutSessionLogCard(
    session: WorkoutSession,
    viewModel: LiftTrackerViewModel
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    var lastPressOffsetPx by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(session.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastPressOffsetPx = down.position
                }
            }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, CardOutline),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = { viewModel.openWorkoutSessionDetailLayer(session.id) },
                    onLongClick = { showOptionsMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusBadge(
                            text = if (session.syncedToHealthConnect) "Health Connect" else "On device",
                            containerColor = if (session.syncedToHealthConnect) AccentGreen.copy(alpha = 0.16f) else CardElevated,
                            contentColor = if (session.syncedToHealthConnect) AccentGreen else TextSecondary,
                            dotColor = if (session.syncedToHealthConnect) AccentGreen else null
                        )
                        SmallActionChip("⋮") {
                            showOptionsMenu = true
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${WorkoutMechanics.formatDuration(session.durationSeconds)} · ${WorkoutMechanics.formatWeight(session.totalVolumeKg, viewModel.weightUnit)} · ${session.completedSetsCount} sets" +
                        (if (session.averageHeartRateBpm > 0) " · ${session.averageHeartRateBpm} bpm" else "") +
                        " · ${session.caloriesBurnedKcal.toInt()} kcal",
                    color = AccentBlue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                session.exercises.take(3).forEach { exEntry ->
                    val exDef = viewModel.findExercise(exEntry.exerciseId, exEntry.exerciseTitle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DatasetExerciseImage(
                            imagePath = exDef?.imagePath ?: "",
                            contentDescription = exEntry.exerciseTitle,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "${exEntry.sets.size}× ${exEntry.exerciseTitle}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (session.exercises.size > 3) {
                    Text(
                        text = "+${session.exercises.size - 3} more exercises • Tap for full log ›",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (session.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.notes,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        PressAnchoredMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false },
            pressOffsetPx = lastPressOffsetPx
        ) {
            DropdownMenuItem(
                text = { Text("👁 View Workout Details", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    viewModel.openWorkoutSessionDetailLayer(session.id)
                }
            )
            DropdownMenuItem(
                text = { Text("📋 Save as Routine", color = AccentCyan, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    viewModel.convertWorkoutSessionToRoutine(session)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (session.archived) "📤 Unarchive Workout" else "📦 Archive Workout",
                        color = AccentAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                onClick = {
                    showOptionsMenu = false
                    viewModel.toggleArchiveWorkoutSession(session.id)
                }
            )
            DropdownMenuItem(
                text = { Text("🗑 Delete Workout", color = AccentRed, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    viewModel.requestConfirmation(
                        title = "Delete workout?",
                        message = "\"${session.title}\" will be permanently removed from your history. This cannot be undone.",
                        confirmLabel = "Delete"
                    ) { viewModel.deleteWorkoutSession(session.id) }
                }
            )
        }
    }
}

@Composable
private fun WorkoutSessionDetailLayerScreen(viewModel: LiftTrackerViewModel) {
    val session = viewModel.workoutHistory.find { it.id == viewModel.selectedWorkoutSessionId }
    if (session == null) {
        viewModel.closeWorkoutSessionDetailLayer()
        return
    }
    val dateLabel = remember(session.endTimeEpochSeconds) {
        SimpleDateFormat("EEE, MMM dd, yyyy • HH:mm", Locale.US)
            .format(Date(session.endTimeEpochSeconds * 1000L))
    }

    Surface(
        color = BgDark,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = CardDark,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallActionChip("← Back") {
                        viewModel.closeWorkoutSessionDetailLayer()
                    }
                    Text(
                        text = "WORKOUT DETAIL",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    SmallActionChip("📋 Save Routine") {
                        viewModel.convertWorkoutSessionToRoutine(session)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = session.title,
                                color = TextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dateLabel,
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (session.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = session.notes,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricPill("DURATION", WorkoutMechanics.formatDuration(session.durationSeconds), AccentCyan)
                                MetricPill("VOLUME", WorkoutMechanics.formatWeight(session.totalVolumeKg, viewModel.weightUnit), AccentBlue)
                                MetricPill("SETS", "${session.completedSetsCount}", AccentGreen)
                                MetricPill("CALORIES", "${session.caloriesBurnedKcal.toInt()} kcal", AccentAmber)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.toggleArchiveWorkoutSession(session.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (session.archived) "📤 Unarchive" else "📦 Archive",
                                        color = AccentAmber,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.requestConfirmation(
                                            title = "Delete workout?",
                                            message = "\"${session.title}\" will be permanently removed from your history. This cannot be undone.",
                                            confirmLabel = "Delete"
                                        ) { viewModel.deleteWorkoutSession(session.id) }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🗑 Delete", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val hcSyncing = session.id in viewModel.healthSyncingSessionIds
                                OutlinedButton(
                                    enabled = !hcSyncing && viewModel.canWriteWorkoutsToHealthConnect,
                                    onClick = { viewModel.syncWorkoutToHealthConnect(session.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = when {
                                            hcSyncing -> "Syncing…"
                                            session.syncedToHealthConnect -> "↻ Health Connect"
                                            else -> "❤ Sync to Health"
                                        },
                                        color = AccentGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item { WorkoutAiAnalysisCard(viewModel, session) }

                items(session.exercises, key = { "detail_${session.id}_${it.id}" }) { exEntry ->
                    val exDef = viewModel.findExercise(exEntry.exerciseId, exEntry.exerciseTitle)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DatasetExerciseImage(
                                    imagePath = exDef?.imagePath ?: "",
                                    contentDescription = exEntry.exerciseTitle,
                                    modifier = Modifier.size(46.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exEntry.exerciseTitle,
                                        color = AccentCyan,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            exDef?.let {
                                                viewModel.openExerciseDetailLayer(it.id)
                                            }
                                        }
                                    )
                                    if (exEntry.notes.isNotBlank()) {
                                        Text(exEntry.notes, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SET", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                                Text("WEIGHT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(amountHeader(exEntry.measure), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(84.dp))
                                Text("RPE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
                                Text("✓", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                            }
                            exEntry.sets.forEach { setItem ->
                                val sColor = when (setItem.setType) {
                                    SetType.WARMUP -> AccentAmber
                                    SetType.NORMAL -> AccentBlue
                                    SetType.FAILURE -> AccentRed
                                    SetType.DROPSET -> AccentCyan
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${setItem.setNumber}${setItem.setType.code}",
                                        color = sColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(56.dp)
                                    )
                                    Text(
                                        text = when {
                                            !exEntry.measure.usesWeight -> "—"
                                            exEntry.measure.weightOptional && setItem.weightKg <= 0.0 -> "Bodyweight"
                                            else -> (if (exEntry.measure.weightOptional) "+" else "") +
                                                WorkoutMechanics.formatWeight(setItem.weightKg, viewModel.weightUnit)
                                        },
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = when (exEntry.measure) {
                                            ExerciseMeasure.DURATION -> WorkoutMechanics.formatClock(setItem.durationSeconds)
                                            ExerciseMeasure.WEIGHT_DISTANCE -> WorkoutMechanics.formatDistance(setItem.distanceMeters)
                                            else -> "${setItem.reps} ${setItem.repType.unitLabel}"
                                        },
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(84.dp)
                                    )
                                    Text(
                                        text = setItem.rpe?.let {
                                            if (it % 1.0 == 0.0) "${it.toInt()}" else String.format(Locale.US, "%.1f", it)
                                        } ?: "—",
                                        color = rpeColor(setItem.rpe),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(46.dp),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = if (setItem.completed) "✓" else "—",
                                        color = if (setItem.completed) AccentGreen else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

// ============================================================================
// 1B. FULL-SCREEN ACTIVE WORKOUT SESSION LAYER
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveWorkoutSessionLayer(viewModel: LiftTrackerViewModel) {
    val rootView = LocalView.current
    DisposableEffect(viewModel.keepScreenAwake) {
        rootView.keepScreenOn = viewModel.keepScreenAwake
        onDispose {
            rootView.keepScreenOn = false
        }
    }

    var showAddExercisePicker by remember { mutableStateOf(false) }
    var pickerQuery by remember { mutableStateOf("") }
    var showFinishConfirmDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var reorderHighlightId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drawerOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val velocityMinY = 650f
    var isBannerDragging by remember { mutableStateOf(false) }

    fun finishDrawerDrag(velocityY: Float) {
        if (drawerOffsetY.value > dismissThresholdPx || (drawerOffsetY.value > 12f && velocityY > velocityMinY)) {
            viewModel.minimizeWorkoutLayer()
        } else {
            scope.launch { drawerOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && drawerOffsetY.value > 0f && !isBannerDragging) {
            finishDrawerDrag(0f)
        }
    }

    val drawerNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (drawerOffsetY.value > 0f && available.y < 0f) {
                    val next = (drawerOffsetY.value + available.y).coerceAtLeast(0f)
                    val consumed = next - drawerOffsetY.value
                    scope.launch { drawerOffsetY.snapTo(next) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f && !listState.canScrollBackward) {
                    val next = (drawerOffsetY.value + available.y).coerceAtLeast(0f)
                    scope.launch { drawerOffsetY.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawerOffsetY.value > 0f) {
                    finishDrawerDrag(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    if (showFinishConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFinishConfirmDialog = false },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Finish Workout Session?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Save \"${viewModel.activeWorkoutTitle}\" (${viewModel.activeCompletedSets}/${viewModel.activeTotalSets} sets completed, ${WorkoutMechanics.formatDuration(viewModel.workoutElapsedSeconds)}) to workout history?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishConfirmDialog = false
                        viewModel.finishActiveWorkout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("✓ Finish & Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Discard Workout Session?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will discard the active workout and all unsaved sets. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        viewModel.discardActiveWorkout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Discard Workout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("Keep Training", color = AccentCyan)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = drawerOffsetY.value }
    ) {
        Surface(
            color = BgDark,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(drawerNestedScroll)
            ) {
                // Top banner: session timer and Finish; pull down anywhere on it to minimize.
                Surface(
                    color = CardDark,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                val velocityTracker = VelocityTracker()
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        isBannerDragging = true
                                        velocityTracker.resetTracking()
                                    },
                                    onDragEnd = {
                                        isBannerDragging = false
                                        finishDrawerDrag(velocityTracker.calculateVelocity().y)
                                    },
                                    onDragCancel = {
                                        isBannerDragging = false
                                        finishDrawerDrag(velocityTracker.calculateVelocity().y)
                                    }
                                ) { change, amount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val next = (drawerOffsetY.value + amount).coerceAtLeast(0f)
                                    scope.launch { drawerOffsetY.snapTo(next) }
                                }
                            }
                            .statusBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(TextSecondary.copy(alpha = 0.45f))
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallActionChip("▼ Minimize") {
                                viewModel.minimizeWorkoutLayer()
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "ACTIVE WORKOUT LAYER",
                                    color = AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = WorkoutMechanics.formatDuration(viewModel.workoutElapsedSeconds),
                                    color = if (viewModel.isTimerPaused) AccentAmber else TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Button(
                                onClick = { showFinishConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Text("✓ Finish", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                viewModel.workoutBannerMessage?.let { msg ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(msg, color = AccentGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(
                                    text = "Dismiss",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable { viewModel.workoutBannerMessage = null }
                                )
                            }
                        }
                    }
                }

                // Live Active Session & Rest Timer Banner
                item {
                    ActiveWorkoutBannerCard(viewModel)
                }

                if (viewModel.activeExercises.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Empty Workout Ready",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap '+ Add Exercise' below to pick from ${viewModel.exercises.size} exercises in exercises-dataset.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Active Exercise Set Logging Cards (tap ≡ to open folded reorder layer)
                itemsIndexed(
                    items = viewModel.activeExercises,
                    key = { _, item -> item.id }
                ) { exIdx, exerciseEntry ->
                    WorkoutExerciseCard(
                        exerciseIndex = exIdx,
                        totalExercises = viewModel.activeExercises.size,
                        entry = exerciseEntry,
                        viewModel = viewModel,
                        onOpenReorder = { reorderHighlightId = exerciseEntry.id },
                        modifier = Modifier.animateItem()
                    )
                }

                // Action row: + Add Exercise / Save as Routine / Discard Workout
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showAddExercisePicker = !showAddExercisePicker },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (showAddExercisePicker) "Close Picker" else "+ Add Exercise",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.saveCurrentWorkoutAsRoutine() },
                                enabled = viewModel.activeExercises.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📋 Save as Routine", color = AccentCyan, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = { showDiscardConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✕ Discard Workout", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showAddExercisePicker) {
                    item {
                        DatasetExercisePickerCard(
                            exercises = viewModel.exercises,
                            query = pickerQuery,
                            onQueryChange = { pickerQuery = it },
                            onSelectExercise = { def ->
                                viewModel.addExerciseToActiveWorkout(def)
                                showAddExercisePicker = false
                                pickerQuery = ""
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                // Rest timer, docked at the bottom where the thumb is; it stays above the keyboard.
                RestTimerBottomBar(viewModel)
            }
        }

        AnimatedVisibility(
            visible = reorderHighlightId != null && viewModel.activeExercises.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(200)) { it / 6 },
            exit = fadeOut(animationSpec = tween(140)) + slideOutVertically(animationSpec = tween(160)) { it / 6 }
        ) {
            ReorderExercisesLayer(
                exercises = viewModel.activeExercises,
                highlightedExerciseId = reorderHighlightId,
                findExercise = viewModel::findExercise,
                onMove = { from, to -> viewModel.moveActiveWorkoutExercise(from, to) },
                onDismiss = { reorderHighlightId = null }
            )
        }
    }
}

@Composable
private fun RestTimerBottomBar(viewModel: LiftTrackerViewModel) {
    val restActive = viewModel.restTimerRemainingSeconds > 0
    Surface(
        color = CardDark,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, if (restActive) AccentCyan.copy(alpha = 0.5f) else CardOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REST",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = WorkoutMechanics.formatClock(viewModel.restTimerRemainingSeconds),
                color = if (restActive) AccentCyan else TextSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.weight(1f))
            SmallActionChip("−15s") { viewModel.adjustRestTimer(-15) }
            SmallActionChip("+15s") { viewModel.adjustRestTimer(15) }
            SmallActionChip("Skip") { viewModel.skipRestTimer() }
            val soundOn = viewModel.restAlertEnabled && viewModel.restTimerVolumeFloat > 0.02f
            SmallActionChip(if (soundOn) "🔔" else "🔕") {
                viewModel.updateRestTimerVolume(if (viewModel.restTimerVolumeFloat > 0.02f) "off" else "normal")
            }
        }
    }
}

@Composable
private fun DatasetExercisePickerCard(
    exercises: List<ExerciseDefinition>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectExercise: (ExerciseDefinition) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val pickerMatches = exercises.filter { def ->
        query.isBlank() ||
            def.title.contains(query, ignoreCase = true) ||
            def.targetMuscle.contains(query, ignoreCase = true) ||
            def.bodyPart.contains(query, ignoreCase = true)
    }.take(30)

    Card(
        colors = CardDefaults.cardColors(containerColor = CardElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Add Exercise from exercises-dataset (${exercises.size})",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search name, muscle, or body part...") },
                trailingIcon = if (query.isNotEmpty()) {
                    { SearchClearButton { onQueryChange("") } }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .keepCaretVisibleAboveIme(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            pickerMatches.forEach { def ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectExercise(def) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DatasetExerciseImage(
                        imagePath = def.imagePath,
                        contentDescription = def.title,
                        modifier = Modifier.size(44.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(def.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${def.bodyPart.ifBlank { def.primaryMuscle.displayName }} • ${def.targetMuscle.ifBlank { def.equipment.displayName }}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Text("+ Add", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = CardDark)
            }
        }
    }
}

@Composable
private fun ActiveWorkoutBannerCard(viewModel: LiftTrackerViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = viewModel.activeWorkoutTitle,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (viewModel.activeWorkoutNotes.isNotBlank()) {
                        Text(
                            text = viewModel.activeWorkoutNotes,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.toggleWorkoutPaused() }
                ) {
                    Text(
                        text = if (viewModel.isTimerPaused) "▶ Resume" else "⏸ Pause",
                        color = if (viewModel.isTimerPaused) AccentAmber else AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    "VOLUME",
                    WorkoutMechanics.formatWeight(viewModel.activeTotalVolumeKg, viewModel.weightUnit),
                    AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    "SETS",
                    "${viewModel.activeCompletedSets}/${viewModel.activeTotalSets}",
                    AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    "EST. KCAL",
                    "${viewModel.activeEstimatedCalories.toInt()} kcal",
                    AccentAmber,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardElevated)
            .border(1.dp, CardOutline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SmallActionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardElevated)
            .border(1.dp, CardOutline, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One-tap rest times shown under the rest input; 0 turns the rest timer off for that exercise. */
private val REST_QUICK_PICKS_SECONDS: List<Int> = listOf(0, 60, 90, 120, 150, 180)

/**
 * Per-exercise options, used by both the active workout and the routine editor:
 * the rest timer for this exercise and how the exercise is tracked.
 */
@Composable
private fun ExerciseOptionsRow(
    restSeconds: Int,
    measure: ExerciseMeasure,
    onRestChange: (Int) -> Unit,
    onMeasureChange: (ExerciseMeasure) -> Unit
) {
    var showRestDialog by remember { mutableStateOf(false) }
    var showMeasureMenu by remember { mutableStateOf(false) }
    if (showRestDialog) {
        AlertDialog(
            onDismissRequest = { showRestDialog = false },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            title = { Text("Rest Timer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rest after each set of this exercise. Type seconds or m:ss.", color = TextSecondary, fontSize = 12.sp)
                    RestStepper(seconds = restSeconds, onChange = onRestChange, modifier = Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        REST_QUICK_PICKS_SECONDS.forEach { seconds ->
                            FilterPill(
                                label = WorkoutMechanics.formatRest(seconds),
                                selected = seconds == restSeconds,
                                onClick = { onRestChange(seconds) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRestDialog = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallActionChip("⏱ Rest ${WorkoutMechanics.formatRest(restSeconds)} ▾") { showRestDialog = true }
        Box {
            SmallActionChip("${measure.shortLabel} ▾") { showMeasureMenu = true }
            DropdownMenu(
                expanded = showMeasureMenu,
                onDismissRequest = { showMeasureMenu = false },
                containerColor = CardElevated
            ) {
                ExerciseMeasure.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = if (option == measure) AccentCyan else TextPrimary,
                                fontWeight = if (option == measure) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onMeasureChange(option)
                            showMeasureMenu = false
                        }
                    )
                }
            }
        }
    }
}

/** Column heading for the second value of a set: reps, seconds or metres. */
private fun amountHeader(measure: ExerciseMeasure): String = when (measure) {
    ExerciseMeasure.DURATION -> "SEC"
    ExerciseMeasure.WEIGHT_DISTANCE -> "METERS"
    else -> "REPS"
}

/** Weight shown in a set field; bodyweight exercises show "BW" until weight is added. */
private fun weightFieldText(setItem: WorkoutSetEntry, measure: ExerciseMeasure, unit: WeightUnit): String =
    if (measure.weightOptional && setItem.weightKg <= 0.0) "BW" else WorkoutMechanics.formatWeightValue(setItem.weightKg, unit)

/** The −/value/+ input for a set's second value: reps, duration (seconds) or distance (metres). */
@Composable
private fun SetAmountInput(
    setItem: WorkoutSetEntry,
    measure: ExerciseMeasure,
    onRepsDelta: (Int) -> Unit,
    onRepsInput: (Int) -> Unit,
    onSetChange: ((WorkoutSetEntry) -> WorkoutSetEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    fun step(direction: Int) = when (measure) {
        ExerciseMeasure.DURATION -> onSetChange { it.copy(durationSeconds = (it.durationSeconds + 5 * direction).coerceAtLeast(0)) }
        ExerciseMeasure.WEIGHT_DISTANCE -> onSetChange { it.copy(distanceMeters = (it.distanceMeters + 5.0 * direction).coerceAtLeast(0.0)) }
        else -> onRepsDelta(direction)
    }
    val display = when (measure) {
        ExerciseMeasure.DURATION -> "${setItem.durationSeconds}"
        ExerciseMeasure.WEIGHT_DISTANCE ->
            if (setItem.distanceMeters % 1.0 == 0.0) "${setItem.distanceMeters.toLong()}" else String.format(Locale.US, "%.1f", setItem.distanceMeters)
        else -> "${setItem.reps}"
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(
            text = "−",
            color = AccentCyan,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { step(-1) }
                .padding(horizontal = 3.dp)
        )
        NumericSetInputField(
            displayValue = display,
            isDecimal = measure == ExerciseMeasure.WEIGHT_DISTANCE,
            onValueCommit = { value ->
                when (measure) {
                    ExerciseMeasure.DURATION -> onSetChange { it.copy(durationSeconds = value.toInt().coerceIn(0, 86400)) }
                    ExerciseMeasure.WEIGHT_DISTANCE -> onSetChange { it.copy(distanceMeters = value.coerceIn(0.0, 100000.0)) }
                    else -> onRepsInput(value.toInt().coerceAtLeast(1))
                }
            },
            modifier = Modifier.width(if (measure.usesReps) 34.dp else 40.dp)
        )
        Text(
            text = "+",
            color = AccentCyan,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { step(1) }
                .padding(horizontal = 3.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutExerciseCard(
    exerciseIndex: Int,
    totalExercises: Int,
    entry: WorkoutExerciseEntry,
    viewModel: LiftTrackerViewModel,
    onOpenReorder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exDef = viewModel.findExercise(entry.exerciseId, entry.exerciseTitle)
    var showGuide by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var showReplacePicker by remember { mutableStateOf(false) }

    if (showReplacePicker) {
        ExercisePickerDialog(
            title = "Replace ${entry.exerciseTitle}",
            exercises = viewModel.exercises,
            onDismiss = { showReplacePicker = false },
            onSelect = { def ->
                viewModel.replaceActiveExercise(exerciseIndex, def)
                showReplacePicker = false
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Long-press the exercise header to replace or remove the exercise.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { showActionsMenu = true }),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExerciseActionsMenu(
                    expanded = showActionsMenu,
                    onDismiss = { showActionsMenu = false },
                    onReplace = { showReplacePicker = true },
                    onRemove = {
                        viewModel.requestConfirmation(
                            title = "Remove exercise?",
                            message = "\"${entry.exerciseTitle}\" and its ${entry.sets.size} set(s) will be removed from this workout.",
                            confirmLabel = "Remove"
                        ) { viewModel.removeActiveExercise(exerciseIndex) }
                    }
                )
                ReorderButton(
                    enabled = totalExercises > 1,
                    onClick = onOpenReorder
                )
                DatasetExerciseImage(
                    imagePath = exDef?.imagePath ?: "",
                    gifPath = exDef?.gifPath ?: "",
                    animateGif = false,
                    contentDescription = entry.exerciseTitle,
                    modifier = Modifier.size(48.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.exerciseTitle,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                exDef?.let {
                                    viewModel.openExerciseDetailLayer(it.id)
                                }
                            },
                            onLongClick = { showActionsMenu = true }
                        )
                    )
                    if (exDef != null && exDef.targetMuscle.isNotBlank()) {
                        Text("Target: ${exDef.targetMuscle}", color = TextSecondary, fontSize = 11.sp)
                    }
                    if (entry.notes.isNotBlank()) {
                        Text(entry.notes, color = TextSecondary, fontSize = 11.sp)
                    }
                }

                SmallActionChip(if (showGuide) "Hide Guide" else "ⓘ How To") {
                    showGuide = !showGuide
                }
            }

            if (showGuide && exDef != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardElevated)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DatasetExerciseImage(
                            imagePath = exDef.imagePath,
                            gifPath = exDef.gifPath,
                            animateGif = viewModel.preferAnimatedExerciseGif,
                            contentDescription = exDef.title,
                            modifier = Modifier.size(96.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (exDef.isCustom) "CUSTOM • ${exDef.primaryMuscle.displayName.uppercase(Locale.US)}" else "DATASET #${exDef.datasetId} • ${exDef.bodyPart.uppercase(Locale.US)}",
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Instructions (exercises-dataset):",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            exDef.instructions.take(3).forEachIndexed { idx, step ->
                                Text(
                                    text = "${idx + 1}. $step",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            ExerciseOptionsRow(
                restSeconds = entry.restTimerSeconds,
                measure = entry.measure,
                onRestChange = { viewModel.setActiveExerciseRest(exerciseIndex, it) },
                onMeasureChange = { viewModel.setActiveExerciseMeasure(exerciseIndex, it) }
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Set table header (No 1RM; optional RPE column)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SET", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                Text("PREVIOUS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (entry.measure.usesWeight) {
                    Text(
                        (if (entry.measure.weightOptional) "+" else "") + viewModel.weightUnit.label.uppercase(Locale.US),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(92.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Text(amountHeader(entry.measure), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
                if (viewModel.showRpeColumn) {
                    Text("RPE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
                }
                Text("✓", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
            }

            entry.sets.forEachIndexed { setIdx, setItem ->
                key(setItem.id) {
                    SetRowItem(
                        setItem = setItem,
                        measure = entry.measure,
                        canRemoveSet = entry.sets.size > 1,
                        onSetChange = { transform -> viewModel.updateActiveSet(exerciseIndex, setIdx, transform) },
                        weightUnit = viewModel.weightUnit,
                        weightIncrementDeltaKg = viewModel.activeWeightStepDeltaKg,
                        showRpeColumn = viewModel.showRpeColumn,
                        onSelectType = { newType -> viewModel.updateSetType(exerciseIndex, setIdx, newType) },
                        onRemoveSet = { viewModel.removeSetFromActiveExercise(exerciseIndex, setIdx) },
                        onWeightDelta = { delta -> viewModel.adjustSetWeight(exerciseIndex, setIdx, delta) },
                        onWeightInput = { inputVal -> viewModel.setSetWeightInCurrentUnit(exerciseIndex, setIdx, inputVal) },
                        onRepsDelta = { delta -> viewModel.adjustSetReps(exerciseIndex, setIdx, delta) },
                        onRepsInput = { inputReps -> viewModel.setSetReps(exerciseIndex, setIdx, inputReps) },
                        onSelectRpe = { newRpe -> viewModel.updateSetRpe(exerciseIndex, setIdx, newRpe) },
                        onToggleComplete = { viewModel.toggleSetCompleted(exerciseIndex, setIdx) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.addSetToActiveExercise(exerciseIndex) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add Set", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SetTypeDropdownCell(
    setItem: WorkoutSetEntry,
    canRemoveSet: Boolean,
    onSelectType: (SetType) -> Unit,
    onRemoveSet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeColor = when (setItem.setType) {
        SetType.WARMUP -> AccentAmber
        SetType.NORMAL -> AccentBlue
        SetType.FAILURE -> AccentRed
        SetType.DROPSET -> AccentCyan
    }
    var showTypeMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.width(44.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(typeColor.copy(alpha = 0.22f))
                .clickable { showTypeMenu = true }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${setItem.setNumber}${setItem.setType.code}▾",
                color = typeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(
            expanded = showTypeMenu,
            onDismissRequest = { showTypeMenu = false },
            containerColor = CardElevated
        ) {
            SetType.entries.forEach { st ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${st.code} — ${st.label}",
                            color = if (st == setItem.setType) AccentCyan else TextPrimary,
                            fontWeight = if (st == setItem.setType) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelectType(st)
                        showTypeMenu = false
                    }
                )
            }
            HorizontalDivider(color = CardDark)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "✕ Remove Set",
                        color = if (canRemoveSet) AccentRed else TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                enabled = canRemoveSet,
                onClick = {
                    showTypeMenu = false
                    onRemoveSet()
                }
            )
        }
    }
}

@Composable
private fun SetRowItem(
    setItem: WorkoutSetEntry,
    measure: ExerciseMeasure,
    canRemoveSet: Boolean,
    onSetChange: ((WorkoutSetEntry) -> WorkoutSetEntry) -> Unit,
    weightUnit: WeightUnit,
    weightIncrementDeltaKg: Double,
    showRpeColumn: Boolean,
    onSelectType: (SetType) -> Unit,
    onRemoveSet: () -> Unit,
    onWeightDelta: (Double) -> Unit,
    onWeightInput: (Double) -> Unit,
    onRepsDelta: (Int) -> Unit,
    onRepsInput: (Int) -> Unit,
    onSelectRpe: (Double?) -> Unit,
    onToggleComplete: () -> Unit
) {
    val rowBg = if (setItem.completed) AccentGreen.copy(alpha = 0.14f) else Color.Transparent
    var showRpeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set Type & Remove Set Dropdown Selector
        SetTypeDropdownCell(
            setItem = setItem,
            canRemoveSet = canRemoveSet,
            onSelectType = onSelectType,
            onRemoveSet = onRemoveSet
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = setItem.previousText,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Weight numeric input + steppers (hidden for time-only exercises)
        if (measure.usesWeight) Row(
            modifier = Modifier.width(92.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "−",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onWeightDelta(-weightIncrementDeltaKg) }
                    .padding(horizontal = 3.dp)
            )
            NumericSetInputField(
                displayValue = weightFieldText(setItem, measure, weightUnit),
                isDecimal = true,
                onValueCommit = { onWeightInput(it) },
                modifier = Modifier.width(46.dp)
            )
            Text(
                text = "+",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onWeightDelta(weightIncrementDeltaKg) }
                    .padding(horizontal = 3.dp)
            )
        }

        // Reps, duration or distance, depending on how the exercise is tracked
        SetAmountInput(
            setItem = setItem,
            measure = measure,
            onRepsDelta = onRepsDelta,
            onRepsInput = onRepsInput,
            onSetChange = onSetChange,
            modifier = Modifier.width(76.dp)
        )

        // Optional, Skippable & Color-Coded RPE (1–10 scale) Dropdown
        if (showRpeColumn) {
            val activeRpeColor = rpeColor(setItem.rpe)
            Box(
                modifier = Modifier.width(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (setItem.rpe != null) activeRpeColor.copy(alpha = 0.22f) else CardElevated)
                        .clickable { showRpeMenu = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val rpeLabel = setItem.rpe?.let {
                        if (it % 1.0 == 0.0) "${it.toInt()}" else String.format(Locale.US, "%.1f", it)
                    } ?: "—"
                    Text(
                        text = rpeLabel,
                        color = activeRpeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded = showRpeMenu,
                    onDismissRequest = { showRpeMenu = false },
                    containerColor = CardElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("— Skip RPE", color = TextSecondary) },
                        onClick = {
                            onSelectRpe(null)
                            showRpeMenu = false
                        }
                    )
                    val rpeOptions = listOf(10.0, 9.5, 9.0, 8.5, 8.0, 7.5, 7.0, 6.5, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0)
                    rpeOptions.forEach { rpeVal ->
                        val textVal = if (rpeVal % 1.0 == 0.0) "${rpeVal.toInt()}" else "$rpeVal"
                        val desc = when {
                            rpeVal >= 10.0 -> "Max effort (0 reps left)"
                            rpeVal >= 9.0 -> "1 rep in reserve"
                            rpeVal >= 8.0 -> "2 reps in reserve"
                            rpeVal >= 7.0 -> "3 reps in reserve"
                            else -> "Light / Warmup"
                        }
                        val itemColor = rpeColor(rpeVal)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "RPE $textVal • $desc",
                                    color = itemColor,
                                    fontWeight = if (setItem.rpe == rpeVal) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            onClick = {
                                onSelectRpe(rpeVal)
                                showRpeMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Completed Checkbox
        Box(
            modifier = Modifier.width(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = setItem.completed,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentGreen,
                    uncheckedColor = TextSecondary
                )
            )
        }
    }
}

// ============================================================================
// 2. EXERCISES SCREEN (Drawer Design + Keep Scroll Position + Detail Layer)
// ============================================================================

@Composable
private fun ExercisesScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState
) {
    val filteredExercises = viewModel.exercises.filter { ex ->
        val matchesSearch = viewModel.exerciseSearchQuery.isBlank() ||
            ex.title.contains(viewModel.exerciseSearchQuery, ignoreCase = true) ||
            ex.targetMuscle.contains(viewModel.exerciseSearchQuery, ignoreCase = true) ||
            ex.bodyPart.contains(viewModel.exerciseSearchQuery, ignoreCase = true) ||
            ex.equipment.displayName.contains(viewModel.exerciseSearchQuery, ignoreCase = true) ||
            ex.datasetId.contains(viewModel.exerciseSearchQuery, ignoreCase = true)
        val matchesMuscle = viewModel.selectedMuscleFilter == null || ex.primaryMuscle == viewModel.selectedMuscleFilter
        val matchesEquip = viewModel.selectedEquipmentFilter == null || ex.equipment == viewModel.selectedEquipmentFilter
        matchesSearch && matchesMuscle && matchesEquip
    }

    val isSearchingOrFiltering = viewModel.exerciseSearchQuery.isNotBlank() ||
        viewModel.selectedMuscleFilter != null ||
        viewModel.selectedEquipmentFilter != null

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search & Filter Bar
        item(key = "search_filter_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exercise Library",
                            color = AccentBlue,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        StatusBadge(
                            text = "${filteredExercises.size} / ${viewModel.exercises.size}",
                            containerColor = CardElevated,
                            contentColor = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val focusManager = LocalFocusManager.current
                    val keyboardController = LocalSoftwareKeyboardController.current
                    OutlinedTextField(
                        value = viewModel.exerciseSearchQuery,
                        onValueChange = { viewModel.exerciseSearchQuery = it },
                        label = { Text("Search name, target muscle, body part…") },
                        trailingIcon = if (viewModel.exerciseSearchQuery.isNotEmpty()) {
                            { SearchClearButton { viewModel.exerciseSearchQuery = "" } }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .keepCaretVisibleAboveIme(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterPill(
                            label = "All Muscles",
                            selected = viewModel.selectedMuscleFilter == null,
                            onClick = { viewModel.selectedMuscleFilter = null }
                        )
                        MuscleGroup.entries.forEach { mg ->
                            FilterPill(
                                label = mg.displayName,
                                selected = viewModel.selectedMuscleFilter == mg,
                                onClick = {
                                    viewModel.selectedMuscleFilter =
                                        if (viewModel.selectedMuscleFilter == mg) null else mg
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.assist.openNewExercise(viewModel.exerciseSearchQuery.trim()) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (viewModel.exerciseSearchQuery.isNotBlank() && filteredExercises.isEmpty()) {
                                "+ Create \"${viewModel.exerciseSearchQuery.trim()}\" (AI can fill it in)"
                            } else {
                                "+ New Exercise (AI can fill it in)"
                            },
                            color = AccentBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (isSearchingOrFiltering) {
            items(filteredExercises.take(120), key = { "flat_${it.id}" }) { ex ->
                ExerciseDrawerRowItem(
                    exercise = ex,
                    onClick = { viewModel.openExerciseDetailLayer(ex.id) }
                )
            }
        } else {
            // Muscle Group Drawer Accordions
            val grouped = filteredExercises.groupBy { it.primaryMuscle }
            MuscleGroup.entries.forEach { group ->
                val groupExercises = grouped[group].orEmpty()
                if (groupExercises.isNotEmpty()) {
                    val isExpanded = viewModel.expandedMuscleDrawers.contains(group)
                    item(key = "drawer_header_${group.name}") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isExpanded) AccentBlue.copy(alpha = 0.5f) else CardOutline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.toggleMuscleDrawer(group) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppIcons.Barbell(modifier = Modifier.size(18.dp), tint = AccentBlue)
                                    }
                                    Column {
                                        Text(
                                            text = group.displayName,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${groupExercises.size} exercises",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (isExpanded) {
                                    AppIcons.ChevronDown(modifier = Modifier.size(18.dp), tint = AccentBlue)
                                } else {
                                    AppIcons.ChevronRight(modifier = Modifier.size(18.dp), tint = TextSecondary)
                                }
                            }
                        }
                    }

                    if (isExpanded) {
                        items(groupExercises, key = { "drawer_${group.name}_${it.id}" }) { ex ->
                            ExerciseDrawerRowItem(
                                exercise = ex,
                                onClick = { viewModel.openExerciseDetailLayer(ex.id) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "footer_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ExerciseDrawerRowItem(
    exercise: ExerciseDefinition,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DatasetExerciseImage(
                imagePath = exercise.imagePath,
                contentDescription = exercise.title,
                modifier = Modifier.size(46.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${exercise.sourceLabel} · ${exercise.targetMuscle.ifBlank { exercise.primaryMuscle.displayName }} · ${exercise.equipment.displayName}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AppIcons.ChevronRight(modifier = Modifier.size(16.dp), tint = TextSecondary)
        }
    }
}

// ============================================================================
// 2B. FULL-SCREEN EXERCISE DETAIL LAYER (Summary / History / How To)
// ============================================================================

@Composable
private fun ExerciseDetailLayerScreen(viewModel: LiftTrackerViewModel) {
    val currentExercise = viewModel.exercises.find { it.id == viewModel.selectedExerciseId }
        ?: viewModel.exercises.first()

    Surface(
        color = BgDark,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = CardDark,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallActionChip("← Back") {
                        viewModel.closeExerciseDetailLayer()
                    }
                    Text(
                        text = currentExercise.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )
                    if (currentExercise.isCustom) {
                        SmallActionChip("✏ Edit") { viewModel.assist.openEditExercise(currentExercise) }
                    } else {
                        SmallActionChip("⧉ Copy & Edit") {
                            viewModel.assist.openNewExercise(copyFrom = currentExercise.copy(title = "${currentExercise.title} (my version)"))
                        }
                    }
                    if (viewModel.isWorkoutActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        SmallActionChip("+ Workout") {
                            viewModel.addExerciseToActiveWorkout(currentExercise)
                            viewModel.closeExerciseDetailLayer()
                            viewModel.openActiveWorkoutLayer()
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // Exercise Header + Summary/History/How-To Sub-Tabs
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DatasetExerciseImage(
                                    imagePath = currentExercise.imagePath,
                                    gifPath = currentExercise.gifPath,
                                    animateGif = viewModel.preferAnimatedExerciseGif,
                                    contentDescription = currentExercise.title,
                                    modifier = Modifier.size(72.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentExercise.title,
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "${currentExercise.sourceLabel} • Target: ${currentExercise.targetMuscle.ifBlank { currentExercise.primaryMuscle.displayName }} • Body Part: ${currentExercise.bodyPart.ifBlank { currentExercise.primaryMuscle.displayName }}",
                                        color = AccentCyan,
                                        fontSize = 11.sp
                                    )
                                    viewModel.aliasTargetOf(currentExercise)?.let { target ->
                                        Text(
                                            text = "Alias of ${target.title} ›",
                                            color = AccentBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clickable { viewModel.openExerciseDetailLayer(target.id) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    SmallActionChip("+ Start / Add to Workout") {
                                        if (!viewModel.isWorkoutActive) {
                                            viewModel.startEmptyWorkoutSession(currentExercise.title)
                                        }
                                        viewModel.addExerciseToActiveWorkout(currentExercise)
                                        viewModel.closeExerciseDetailLayer()
                                        viewModel.openActiveWorkoutLayer()
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            SegmentedSwitcher(
                                selectedIndex = ExerciseSubTab.entries.indexOf(viewModel.selectedExerciseSubTab),
                                options = ExerciseSubTab.entries.map { it.title },
                                onSelect = { idx -> viewModel.selectedExerciseSubTab = ExerciseSubTab.entries[idx] }
                            )
                        }
                    }
                }

                when (viewModel.selectedExerciseSubTab) {
                    ExerciseSubTab.SUMMARY -> item { ExerciseSummaryView(currentExercise, viewModel) }
                    ExerciseSubTab.HISTORY -> item { ExerciseHistoryView(currentExercise, viewModel) }
                    ExerciseSubTab.HOW_TO -> item { ExerciseHowToView(currentExercise, viewModel.preferAnimatedExerciseGif) }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else CardElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) AccentBlue else CardOutline
        )
    ) {
        Text(
            text = label,
            color = if (selected) AccentBlue else TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RecordTile(label: String, value: String, detail: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardElevated)
            .border(1.dp, CardOutline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Value of a progression chart point as text, e.g. "102.5 kg", "24 reps", "1:30". */
private fun formatChartValue(metric: ExerciseChartMetric, value: Double, unit: WeightUnit): String = when {
    metric.isWeight -> WorkoutMechanics.formatWeight(value, unit)
    metric == ExerciseChartMetric.DURATION -> WorkoutMechanics.formatClock(value.toInt())
    metric == ExerciseChartMetric.DISTANCE -> WorkoutMechanics.formatDistance(value)
    else -> "${value.toInt()} reps"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSummaryView(
    exercise: ExerciseDefinition,
    viewModel: LiftTrackerViewModel
) {
    val stats = viewModel.exerciseStats(exercise.id)
    val unit = viewModel.weightUnit
    val measure = stats.measure
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    fun date(epochSeconds: Long): String = dateFmt.format(Date(epochSeconds * 1000L))
    fun setText(set: WorkoutSetEntry): String = WorkoutMechanics.formatSetSummary(set, measure, unit)
    val otherNames = viewModel.otherNamesOf(exercise.id)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (exercise.summaryNotes.isNotBlank() || otherNames.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ABOUT", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (exercise.summaryNotes.isNotBlank()) {
                        Text(exercise.summaryNotes, color = TextPrimary, fontSize = 13.sp)
                    }
                    if (otherNames.isNotEmpty()) {
                        Text(
                            text = "Counted together with: ${otherNames.joinToString(", ") { it.title }}. History and records below cover all of these names.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (stats.sessions == 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PERSONAL RECORDS", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("No completed sets logged for ${exercise.title} yet.", color = TextPrimary, fontSize = 13.sp)
                    Text(
                        "Records, totals and the progression chart appear after the first workout that includes it.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            return@Column
        }

        // ---- Personal records
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PERSONAL RECORDS", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                val tiles = buildList<Triple<String, String, String>> {
                    if (measure == ExerciseMeasure.WEIGHT_REPS) {
                        stats.best1Rm?.let { add(Triple("EST. 1RM", WorkoutMechanics.formatWeight(it.value, unit), "${setText(it.set)} · ${date(it.epochSeconds)}")) }
                    }
                    stats.heaviest?.let {
                        add(Triple(if (measure == ExerciseMeasure.BODYWEIGHT_REPS) "HEAVIEST ADDED WEIGHT" else "HEAVIEST WEIGHT", WorkoutMechanics.formatWeight(it.value, unit), "${setText(it.set)} · ${date(it.epochSeconds)}"))
                    }
                    if (measure == ExerciseMeasure.WEIGHT_REPS) {
                        stats.bestSetVolume?.let { add(Triple("BEST SET VOLUME", WorkoutMechanics.formatWeight(it.value, unit), "${setText(it.set)} · ${date(it.epochSeconds)}")) }
                    }
                    stats.mostReps?.let { add(Triple("MOST REPS IN A SET", "${it.set.reps} reps", "${setText(it.set)} · ${date(it.epochSeconds)}")) }
                    stats.longestHold?.let { add(Triple("LONGEST", WorkoutMechanics.formatClock(it.set.durationSeconds), date(it.epochSeconds))) }
                    stats.longestDistance?.let { add(Triple("LONGEST DISTANCE", WorkoutMechanics.formatDistance(it.set.distanceMeters), "${setText(it.set)} · ${date(it.epochSeconds)}")) }
                    stats.bestSession?.let { best ->
                        val value = when (measure) {
                            ExerciseMeasure.WEIGHT_REPS -> WorkoutMechanics.formatWeight(best.volumeKg, unit)
                            ExerciseMeasure.BODYWEIGHT_REPS -> "${best.totalReps} reps"
                            ExerciseMeasure.DURATION -> WorkoutMechanics.formatClock(best.totalSeconds)
                            ExerciseMeasure.WEIGHT_DISTANCE -> WorkoutMechanics.formatDistance(best.totalMeters)
                        }
                        add(Triple(if (measure == ExerciseMeasure.WEIGHT_REPS) "BEST SESSION VOLUME" else "BEST SESSION TOTAL", value, "${best.sets.size} sets · ${date(best.epochSeconds)}"))
                    }
                }
                val tileColors = listOf(AccentCyan, AccentGreen, AccentAmber, AccentBlue)
                tiles.chunked(2).forEachIndexed { rowIdx, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { i, (label, value, detail) ->
                            RecordTile(label, value, detail, tileColors[(rowIdx * 2 + i) % tileColors.size], Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (stats.repRecords.size > 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("BEST WEIGHT BY REPS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    stats.repRecords.forEach { record ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (record.set.reps == 1) "1 rep" else "${record.set.reps} reps",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(64.dp)
                            )
                            Text(
                                text = WorkoutMechanics.formatWeight(record.value, unit),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(date(record.epochSeconds), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ---- Totals
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EXERCISE SUMMARY", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill("WORKOUTS", "${stats.sessions}", TextPrimary, Modifier.weight(1f))
                    MetricPill("SETS", "${stats.totalSets}", TextPrimary, Modifier.weight(1f))
                    when (measure) {
                        ExerciseMeasure.WEIGHT_REPS ->
                            MetricPill("VOLUME", formatStatVolume(stats.totalVolumeKg, unit), TextPrimary, Modifier.weight(1f))
                        ExerciseMeasure.BODYWEIGHT_REPS ->
                            MetricPill("REPS", "${stats.totalReps}", TextPrimary, Modifier.weight(1f))
                        ExerciseMeasure.DURATION ->
                            MetricPill("TIME", formatStatDuration(stats.totalSeconds.toLong()), TextPrimary, Modifier.weight(1f))
                        ExerciseMeasure.WEIGHT_DISTANCE ->
                            MetricPill("DISTANCE", WorkoutMechanics.formatDistance(stats.totalMeters), TextPrimary, Modifier.weight(1f))
                    }
                }
                val first = stats.firstEpochSeconds
                val last = stats.lastEpochSeconds
                if (first != null && last != null) {
                    val daysAgo = ((System.currentTimeMillis() / 1000L - last) / 86_400L).coerceAtLeast(0L)
                    Text(
                        text = "Last done ${date(last)} (" + (when (daysAgo) { 0L -> "today"; 1L -> "yesterday"; else -> "$daysAgo days ago" }) + ")" +
                            (if (stats.sessions > 1) " · first logged ${date(first)}" else "") +
                            (if (measure == ExerciseMeasure.WEIGHT_REPS) " · ${stats.totalReps} reps in total" else ""),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ---- Progression
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val metrics = ExerciseChartMetric.forMeasure(measure)
                var metric by remember(exercise.id, measure) { mutableStateOf(metrics.first()) }
                Text("PROGRESSION", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (metrics.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.forEach { m -> FilterPill(label = m.label, selected = metric == m, onClick = { metric = m }) }
                    }
                }
                val values = stats.points.map { metric.value(it) }
                val latest = values.last()
                val change = latest - values.first()
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatChartValue(metric, latest, unit), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    if (values.size > 1 && change != 0.0) {
                        Text(
                            text = (if (change > 0) "↑ " else "↓ ") + formatChartValue(metric, kotlin.math.abs(change), unit) + " since first workout",
                            color = if (change > 0) AccentGreen else TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                if (metric == ExerciseChartMetric.ONE_REP_MAX) {
                    Text("Estimated with the Epley formula: weight × (1 + reps / 30).", color = TextSecondary, fontSize = 11.sp)
                }

                if (values.size < 2) {
                    Text("One workout so far. The chart starts with the second one.", color = TextSecondary, fontSize = 12.sp)
                } else {
                    val minVal = values.min()
                    val maxVal = values.max()
                    val pointColor = AccentCyan
                    val lineColor = AccentBlue
                    val gridColor = CardOutline
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("High ${formatChartValue(metric, maxVal, unit)}", color = TextSecondary, fontSize = 10.sp)
                        Text("Low ${formatChartValue(metric, minVal, unit)}", color = TextSecondary, fontSize = 10.sp)
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardElevated)
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
                        val stepX = size.width / (values.size - 1)
                        fun pointAt(idx: Int): Offset {
                            val norm = if (maxVal == minVal) 0.5f else ((values[idx] - minVal) / range).toFloat()
                            return Offset(idx * stepX, size.height - norm * size.height)
                        }
                        listOf(0f, 0.5f, 1f).forEach { f ->
                            drawLine(gridColor, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1.dp.toPx())
                        }
                        val path = Path()
                        values.indices.forEach { idx -> pointAt(idx).let { if (idx == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                        drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        if (values.size <= 40) {
                            values.indices.forEach { idx -> drawCircle(color = pointColor, radius = 4.dp.toPx(), center = pointAt(idx)) }
                        } else {
                            drawCircle(color = pointColor, radius = 4.dp.toPx(), center = pointAt(values.lastIndex))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(date(stats.points.first().epochSeconds), color = TextSecondary, fontSize = 10.sp)
                        Text("${values.size} workouts", color = TextSecondary, fontSize = 10.sp)
                        Text(date(stats.points.last().epochSeconds), color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseHistoryView(
    exercise: ExerciseDefinition,
    viewModel: LiftTrackerViewModel
) {
    val history = viewModel.getExerciseHistory(exercise.id)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (history.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "No completed sessions logged yet for ${exercise.title}.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Complete sets in the Active Workout Layer and tap 'Finish' to populate exercise history.",
                        color = AccentCyan,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            history.forEach { point ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = point.workoutTitle,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = point.dateLabel,
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (point.loggedAs.isNotBlank() && !point.loggedAs.equals(exercise.title, ignoreCase = true)) {
                            Text(
                                text = "Logged as ${point.loggedAs}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Best Weight: ${WorkoutMechanics.formatWeight(point.bestWeightKg, viewModel.weightUnit)} • Est. 1RM: ${WorkoutMechanics.formatWeight(point.best1RmKg, viewModel.weightUnit)} • Volume: ${WorkoutMechanics.formatWeight(point.sessionVolumeKg, viewModel.weightUnit)}",
                            color = AccentGreen,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        point.setsSummary.forEachIndexed { idx, sLine ->
                            val rpeAt = sLine.indexOf(" @RPE")
                            Text(
                                text = buildAnnotatedString {
                                    append("Set ${idx + 1}: ")
                                    append(if (rpeAt >= 0) sLine.substring(0, rpeAt) else sLine)
                                    if (rpeAt >= 0) {
                                        withStyle(SpanStyle(fontSize = 9.sp, color = TextSecondary.copy(alpha = 0.75f))) {
                                            append("  " + sLine.substring(rpeAt + 2))
                                        }
                                    }
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseHowToView(
    exercise: ExerciseDefinition,
    animateGif: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (exercise.isCustom) "PICTURE & FORM GUIDE (your exercise)" else "EXERCISE ANIMATION & FORM GUIDE (hasaneyldrm/exercises-dataset)",
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardElevated)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DatasetExerciseImage(
                        imagePath = exercise.imagePath,
                        gifPath = exercise.gifPath,
                        animateGif = animateGif,
                        contentDescription = exercise.title,
                        modifier = Modifier.size(180.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${exercise.title} (${exercise.sourceLabel})",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Target: ${exercise.targetMuscle} • Equipment: ${exercise.equipment.displayName}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "STEP-BY-STEP EXECUTION INSTRUCTIONS",
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            exercise.instructions.forEachIndexed { idx, step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${idx + 1}.",
                        color = AccentBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = step,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CardElevated)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "KEY COACHING & SAFETY CUES",
                color = AccentAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            exercise.coachingCues.forEach { cue ->
                Text(
                    text = "• $cue",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// ============================================================================
// 3. ROUTINES SCREEN (Horizontal + New Routine, No-Folder Default, Folder Drawers)
// ============================================================================

@Composable
private fun RoutinesScreen(viewModel: LiftTrackerViewModel) {
    val distinctFolders = viewModel.routines
        .mapNotNull { it.folderName.trim().takeIf { f -> f.isNotEmpty() } }
        .distinct()
    val noFolderCount = viewModel.routines.count { it.folderName.isBlank() }

    val displayedRoutines = viewModel.routines.filter { rt ->
        when (val filter = viewModel.selectedRoutineFolderFilter) {
            null -> true
            "__NO_FOLDER__" -> rt.folderName.isBlank()
            else -> rt.folderName == filter
        }
    }
    val displayedIds = displayedRoutines.map { it.id }

    // Import / export / share routines as JSON. "" in pendingExportId means "all routines".
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var pendingExportId by rememberSaveable { mutableStateOf("") }
    var showExportAllMenu by remember { mutableStateOf(false) }
    val saveRoutinesFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val id = pendingExportId.ifEmpty { null }
            viewModel.writeTextToUri(
                uri,
                viewModel.buildRoutinesJson(id),
                if (id == null) "Saved ${viewModel.routines.size} routines to a file." else "Saved the routine to a file.",
                forRoutines = true
            )
        }
    }
    val openRoutinesFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importRoutinesFromUri(uri)
    }
    val exportToFile: (String?) -> Unit = { id ->
        pendingExportId = id.orEmpty()
        saveRoutinesFile.launch(viewModel.routinesFileName(id))
    }
    val copyJson: (String?) -> Unit = { id ->
        clipboard.setText(AnnotatedString(viewModel.buildRoutinesJson(id)))
        viewModel.routinesStatusMessage =
            if (id == null) "Copied ${viewModel.routines.size} routines to the clipboard as JSON." else "Copied the routine to the clipboard as JSON."
    }
    val shareJson: (String?) -> Unit = { id ->
        val uri = ExportProvider.routinesUri(context, id)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(viewModel.routinesFileName(id), uri)
        runCatching { context.startActivity(Intent.createChooser(send, "Share routine")) }
    }
    val pasteJson: () -> Unit = {
        val text = clipboard.getText()?.text
        if (text.isNullOrBlank()) {
            viewModel.routinesStatusMessage = "The clipboard is empty. Copy routine JSON first."
        } else {
            viewModel.importRoutinesFromText(text)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Card: Horizontal "+ New Routine" Button + Scrollable Folder Filter Pills
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Routines & Folders",
                            color = AccentBlue,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        StatusBadge(
                            text = "${displayedRoutines.size} shown",
                            containerColor = CardElevated,
                            contentColor = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap arrow to expand, tap routine for details, or drag ≡ to reorder",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal folder scroll bar (matches Exercises screen)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterPill(
                            label = "All (${viewModel.routines.size})",
                            selected = viewModel.selectedRoutineFolderFilter == null,
                            onClick = { viewModel.selectedRoutineFolderFilter = null }
                        )
                        FilterPill(
                            label = "No Folder ($noFolderCount)",
                            selected = viewModel.selectedRoutineFolderFilter == "__NO_FOLDER__",
                            onClick = {
                                viewModel.selectedRoutineFolderFilter =
                                    if (viewModel.selectedRoutineFolderFilter == "__NO_FOLDER__") null else "__NO_FOLDER__"
                            }
                        )
                        distinctFolders.forEach { folder ->
                            val count = viewModel.routines.count { it.folderName == folder }
                            FilterPill(
                                label = "$folder ($count)",
                                selected = viewModel.selectedRoutineFolderFilter == folder,
                                onClick = {
                                    viewModel.selectedRoutineFolderFilter =
                                        if (viewModel.selectedRoutineFolderFilter == folder) null else folder
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.openNewRoutineEditor() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "+ New Routine",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.assist.openSetup() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AccentTertiary.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcons.Sparkles(modifier = Modifier.size(16.dp), tint = AccentTertiary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Set-up",
                                color = AccentTertiary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallActionChip("⬇ Import File") {
                            openRoutinesFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                        SmallActionChip("📋 Paste JSON") { pasteJson() }
                        Box {
                            SmallActionChip("⬆ Export All ▾") { showExportAllMenu = true }
                            DropdownMenu(
                                expanded = showExportAllMenu,
                                onDismissRequest = { showExportAllMenu = false },
                                containerColor = CardElevated
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Save to File", color = TextPrimary) },
                                    onClick = {
                                        showExportAllMenu = false
                                        exportToFile(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy JSON", color = TextPrimary) },
                                    onClick = {
                                        showExportAllMenu = false
                                        copyJson(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share…", color = TextPrimary) },
                                    onClick = {
                                        showExportAllMenu = false
                                        shareJson(null)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        viewModel.routinesStatusMessage?.let { msg ->
            item { StatusMessageCard(msg) { viewModel.routinesStatusMessage = null } }
        }

        if (displayedRoutines.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No routines in this folder. Tap '+ New Routine' above to create one, or 'AI Set-up' to have a program built for you.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = displayedRoutines,
                key = { _, rt -> "rt_drawer_${rt.id}" }
            ) { idx, routine ->
                RoutineCard(
                    routine = routine,
                    canMoveUp = idx > 0,
                    canMoveDown = idx < displayedRoutines.lastIndex,
                    onMoveUp = { viewModel.moveRoutineWithinList(routine.id, displayedIds, -1) },
                    onMoveDown = { viewModel.moveRoutineWithinList(routine.id, displayedIds, 1) },
                    viewModel = viewModel,
                    onStartRoutine = { viewModel.startRoutineAsWorkout(routine) },
                    onExportToFile = { exportToFile(routine.id) },
                    onCopyJson = { copyJson(routine.id) },
                    onShare = { shareJson(routine.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineCard(
    routine: RoutineTemplate,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    viewModel: LiftTrackerViewModel,
    onStartRoutine: () -> Unit,
    onExportToFile: () -> Unit,
    onCopyJson: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = viewModel.expandedRoutineDrawerIds.contains(routine.id)
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderInput by remember(routine.id, routine.folderName) { mutableStateOf(routine.folderName) }
    var lastPressOffsetPx by remember { mutableStateOf(Offset.Zero) }
    var dragOffsetY by remember(routine.id) { mutableFloatStateOf(0f) }


    if (showFolderDialog) {
        val existingFolders = viewModel.routines.mapNotNull {
            it.folderName.trim().takeIf { f -> f.isNotEmpty() }
        }.distinct()

        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Change Routine Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Move \"${routine.title}\" to a folder or leave blank for No Folder.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        label = { Text("Folder Name (blank = No Folder)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterPill(
                            label = "No Folder",
                            selected = folderInput.isBlank(),
                            onClick = { folderInput = "" }
                        )
                        existingFolders.forEach { folder ->
                            FilterPill(
                                label = "📁 $folder",
                                selected = folderInput == folder,
                                onClick = { folderInput = folder }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changeRoutineFolder(routine.id, folderInput)
                        showFolderDialog = false
                    }
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (dragOffsetY != 0f) 10f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .pointerInput(routine.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastPressOffsetPx = down.position
                }
            }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isExpanded) AccentBlue.copy(alpha = 0.5f) else CardOutline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = { viewModel.openEditRoutineEditor(routine) },
                    onLongClick = { showOptionsMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Folded drawer header: chevron arrow (unfolds) + routine name (opens details) (+ drag, menu, Start)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DragReorderHandle(
                        itemKey = routine.id,
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onDragOffsetChange = { dragOffsetY = it }
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardElevated)
                            .clickable { viewModel.toggleRoutineDrawer(routine.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isExpanded) {
                            AppIcons.ChevronDown(modifier = Modifier.size(16.dp), tint = AccentBlue)
                        } else {
                            AppIcons.ChevronRight(modifier = Modifier.size(16.dp), tint = AccentBlue)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = routine.title,
                            color = TextPrimary,
                            fontSize = if (isExpanded) 15.sp else 14.sp,
                            lineHeight = if (isExpanded) 19.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (routine.notes.isNotBlank()) {
                            Text(
                                text = routine.notes,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    SmallActionChip("⋮") {
                        showOptionsMenu = true
                    }

                    Button(
                        onClick = onStartRoutine,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                // Unfolded drawer content: folder tag, notes, and all exercises
                if (isExpanded) {
                    if (routine.folderName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusBadge(
                            text = routine.folderName,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = AccentCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CardElevated)
                    Spacer(modifier = Modifier.height(8.dp))

                    routine.exercises.forEach { rex ->
                        val exDef = viewModel.findExercise(rex.exerciseId, rex.exerciseTitle)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DatasetExerciseImage(
                                imagePath = exDef?.imagePath ?: "",
                                contentDescription = rex.exerciseTitle,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = rex.exerciseTitle,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = run {
                                    val refSet = rex.sets.firstOrNull { it.setType != SetType.WARMUP } ?: rex.sets.firstOrNull()
                                    val target = if (rex.measure == ExerciseMeasure.WEIGHT_REPS || refSet == null) {
                                        "${rex.targetSets}×${rex.targetReps} ${rex.repType.unitLabel} @ ${WorkoutMechanics.formatWeight(rex.targetWeightKg, viewModel.weightUnit)}"
                                    } else {
                                        "${rex.targetSets}× ${WorkoutMechanics.formatSetSummary(refSet, rex.measure, viewModel.weightUnit)}"
                                    }
                                    "$target · ⏱ ${WorkoutMechanics.formatRest(rex.restSeconds)}"
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        PressAnchoredMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false },
            pressOffsetPx = lastPressOffsetPx
        ) {
            DropdownMenuItem(
                text = { Text("✏ Edit Routine", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    viewModel.openEditRoutineEditor(routine)
                }
            )
            DropdownMenuItem(
                text = { Text("✨ Optimize with AI", color = AccentTertiary, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    viewModel.assist.openOptimize(routine)
                }
            )
            DropdownMenuItem(
                text = { Text("📁 Change Folder", color = AccentCyan, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    folderInput = routine.folderName
                    showFolderDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("⬆ Export to File", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    onExportToFile()
                }
            )
            DropdownMenuItem(
                text = { Text("📋 Copy JSON", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    onCopyJson()
                }
            )
            DropdownMenuItem(
                text = { Text("↗ Share…", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text("🗑 Delete Routine", color = AccentRed, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    showOptionsMenu = false
                    viewModel.requestConfirmation(
                        title = "Delete routine?",
                        message = "\"${routine.title}\" will be permanently deleted. Logged workouts are not affected.",
                        confirmLabel = "Delete"
                    ) { viewModel.deleteRoutine(routine.id) }
                }
            )
        }
    }
}

// ============================================================================
// 3B. FULL-SCREEN ROUTINE EDITOR LAYER (Name + Add Exercises Like a Workout)
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineEditorLayerScreen(viewModel: LiftTrackerViewModel) {
    var showAddExercisePicker by remember { mutableStateOf(false) }
    var pickerQuery by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var reorderHighlightId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = BgDark,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = CardDark,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallActionChip("✕ Cancel") {
                            viewModel.requestCloseRoutineEditorLayer()
                        }
                        Text(
                            text = if (viewModel.editingRoutineId != null) "EDIT ROUTINE" else "NEW ROUTINE",
                            color = AccentCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Button(
                            onClick = {
                                if (!viewModel.saveRoutineDraft()) {
                                    nameError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Text("✓ Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "ROUTINE DETAILS",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val focusManager = LocalFocusManager.current
                            val keyboardController = LocalSoftwareKeyboardController.current
                            OutlinedTextField(
                                value = viewModel.routineEditorTitle,
                                onValueChange = {
                                    viewModel.routineEditorTitle = it
                                    if (it.isNotBlank()) nameError = false
                                },
                                label = { Text("Routine Name (required)") },
                                isError = nameError,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .keepCaretVisibleAboveIme(),
                                singleLine = true
                            )
                            if (nameError) {
                                Text(
                                    text = "Please enter a routine name.",
                                    color = AccentRed,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = viewModel.routineEditorNotes,
                                onValueChange = { viewModel.routineEditorNotes = it },
                                label = { Text("Notes (optional)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .keepCaretVisibleAboveIme(),
                                minLines = 2,
                                maxLines = 5
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var showFolderDropdown by remember { mutableStateOf(false) }
                            val existingFolders = viewModel.routines.mapNotNull {
                                it.folderName.trim().takeIf { f -> f.isNotEmpty() }
                            }.distinct()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = viewModel.routineEditorFolder,
                                    onValueChange = { viewModel.routineEditorFolder = it },
                                    label = { Text("Folder (blank = No Folder)") },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .keepCaretVisibleAboveIme(),
                                    singleLine = true
                                )
                                Box {
                                    OutlinedButton(onClick = { showFolderDropdown = true }) {
                                        Text("📁 Folders ▾", color = AccentCyan, fontSize = 12.sp, maxLines = 1)
                                    }
                                    DropdownMenu(
                                        expanded = showFolderDropdown,
                                        onDismissRequest = { showFolderDropdown = false },
                                        containerColor = CardElevated
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "No Folder (Default)",
                                                    color = if (viewModel.routineEditorFolder.isBlank()) AccentCyan else TextPrimary
                                                )
                                            },
                                            onClick = {
                                                viewModel.routineEditorFolder = ""
                                                showFolderDropdown = false
                                            }
                                        )
                                        existingFolders.forEach { folder ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "📁 $folder",
                                                        color = if (viewModel.routineEditorFolder == folder) AccentCyan else TextPrimary
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.routineEditorFolder = folder
                                                    showFolderDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (viewModel.routineEditorExercises.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No exercises added yet. Tap '+ Add Exercise' below to build this routine.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = viewModel.routineEditorExercises,
                    key = { _, item -> item.id }
                ) { exIdx, entry ->
                    val exDef = viewModel.findExercise(entry.exerciseId, entry.exerciseTitle)
                    var showActionsMenu by remember(entry.id) { mutableStateOf(false) }
                    var showReplacePicker by remember(entry.id) { mutableStateOf(false) }
                    if (showReplacePicker) {
                        ExercisePickerDialog(
                            title = "Replace ${entry.exerciseTitle}",
                            exercises = viewModel.exercises,
                            onDismiss = { showReplacePicker = false },
                            onSelect = { def ->
                                viewModel.replaceRoutineDraftExercise(exIdx, def)
                                showReplacePicker = false
                            }
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Long-press the exercise header (or tap ⋮) to replace or remove it.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = {}, onLongClick = { showActionsMenu = true }),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ReorderButton(
                                    enabled = viewModel.routineEditorExercises.size > 1,
                                    onClick = { reorderHighlightId = entry.id }
                                )
                                DatasetExerciseImage(
                                    imagePath = exDef?.imagePath ?: "",
                                    contentDescription = entry.exerciseTitle,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = entry.exerciseTitle,
                                    color = AccentCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    SmallActionChip("⋮") { showActionsMenu = true }
                                    ExerciseActionsMenu(
                                        expanded = showActionsMenu,
                                        onDismiss = { showActionsMenu = false },
                                        onReplace = { showReplacePicker = true },
                                        onRemove = { viewModel.removeExerciseFromRoutineDraft(exIdx) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ExerciseOptionsRow(
                                restSeconds = entry.restTimerSeconds,
                                measure = entry.measure,
                                onRestChange = { viewModel.setRoutineDraftExerciseRest(exIdx, it) },
                                onMeasureChange = { viewModel.setRoutineDraftExerciseMeasure(exIdx, it) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            entry.sets.forEachIndexed { setIdx, setItem ->
                                key(setItem.id) {
                                    var showRepTypeMenu by remember { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Set Type & Remove Set Dropdown
                                        SetTypeDropdownCell(
                                            setItem = setItem,
                                            canRemoveSet = entry.sets.size > 1,
                                            onSelectType = { st -> viewModel.updateRoutineDraftSetType(exIdx, setIdx, st) },
                                            onRemoveSet = { viewModel.removeSetFromRoutineDraftExercise(exIdx, setIdx) }
                                        )

                                        // Weight numeric input + steppers (hidden for time-only exercises)
                                        if (!entry.measure.usesWeight) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        } else Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "−",
                                                color = AccentCyan,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { viewModel.adjustRoutineDraftWeight(exIdx, setIdx, -viewModel.activeWeightStepDeltaKg) }
                                                    .padding(horizontal = 4.dp)
                                            )
                                            NumericSetInputField(
                                                displayValue = weightFieldText(setItem, entry.measure, viewModel.weightUnit),
                                                isDecimal = true,
                                                onValueCommit = { viewModel.setRoutineDraftWeightInCurrentUnit(exIdx, setIdx, it) },
                                                modifier = Modifier.width(48.dp)
                                            )
                                            Text(
                                                text = viewModel.weightUnit.label,
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(start = 2.dp)
                                            )
                                            Text(
                                                text = "+",
                                                color = AccentCyan,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { viewModel.adjustRoutineDraftWeight(exIdx, setIdx, viewModel.activeWeightStepDeltaKg) }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }

                                        // Reps, duration or distance, depending on how the exercise is tracked
                                        SetAmountInput(
                                            setItem = setItem,
                                            measure = entry.measure,
                                            onRepsDelta = { viewModel.adjustRoutineDraftReps(exIdx, setIdx, it) },
                                            onRepsInput = { viewModel.setRoutineDraftReps(exIdx, setIdx, it) },
                                            onSetChange = { transform -> viewModel.updateRoutineDraftSet(exIdx, setIdx, transform) },
                                            modifier = Modifier.width(74.dp)
                                        )

                                        // Rep Type Dropdown (reps-based exercises) or the unit of the value
                                        if (!entry.measure.usesReps) {
                                            Text(
                                                text = if (entry.measure == ExerciseMeasure.DURATION) "sec" else "m",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.width(64.dp)
                                            )
                                        } else Box(modifier = Modifier.width(64.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(CardElevated)
                                                    .clickable { showRepTypeMenu = true }
                                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${setItem.repType.code}▾",
                                                    color = AccentCyan,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showRepTypeMenu,
                                                onDismissRequest = { showRepTypeMenu = false },
                                                containerColor = CardElevated
                                            ) {
                                                RepType.entries.forEach { rt ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = rt.label,
                                                                color = if (rt == setItem.repType) AccentCyan else TextPrimary
                                                            )
                                                        },
                                                        onClick = {
                                                            viewModel.updateRoutineDraftRepType(exIdx, setIdx, rt)
                                                            showRepTypeMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.addSetToRoutineDraftExercise(exIdx) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Add Set", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { showAddExercisePicker = !showAddExercisePicker },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showAddExercisePicker) "Close Exercise Picker" else "+ Add Exercise",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showAddExercisePicker) {
                    item {
                        DatasetExercisePickerCard(
                            exercises = viewModel.exercises,
                            query = pickerQuery,
                            onQueryChange = { pickerQuery = it },
                            onSelectExercise = { def ->
                                viewModel.addExerciseToRoutineDraft(def)
                                showAddExercisePicker = false
                                pickerQuery = ""
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        AnimatedVisibility(
            visible = reorderHighlightId != null && viewModel.routineEditorExercises.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(200)) { it / 6 },
            exit = fadeOut(animationSpec = tween(140)) + slideOutVertically(animationSpec = tween(160)) { it / 6 }
        ) {
            ReorderExercisesLayer(
                exercises = viewModel.routineEditorExercises,
                highlightedExerciseId = reorderHighlightId,
                findExercise = viewModel::findExercise,
                onMove = { from, to -> viewModel.moveRoutineDraftExercise(from, to) },
                onDismiss = { reorderHighlightId = null }
            )
        }
    }
}

// ============================================================================
// 4. SETTINGS LAYER (Multi-Layer Hub + Sub-Screens inspired by HealthAnalyzer)
// ============================================================================

@Composable
internal fun StatusMessageCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, color = TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                text = "Dismiss",
                color = AccentBlue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = AccentBlue,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun OverviewMetricTile(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = CardElevated,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: @Composable () -> Unit,
    iconBackground: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardOutline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            AppIcons.ChevronRight(
                modifier = Modifier.size(18.dp),
                tint = TextSecondary,
            )
        }
    }
}

@Composable
internal fun SubScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(10.dp),
                    color = CardDark,
                    border = BorderStroke(1.dp, CardOutline),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcons.ArrowLeft(modifier = Modifier.size(20.dp), tint = TextPrimary)
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailingAction?.invoke()
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}

@Composable
private fun SettingsLayerScreen(viewModel: LiftTrackerViewModel) {
    val hubListState = rememberLazyListState()
    val workoutListState = rememberLazyListState()
    val appearanceListState = rememberLazyListState()
    val exercisesListState = rememberLazyListState()
    val healthListState = rememberLazyListState()
    val watchListState = rememberLazyListState()
    val aiListState = rememberLazyListState()
    val dataListState = rememberLazyListState()

    Surface(
        color = BgDark,
        modifier = Modifier.fillMaxSize()
    ) {
        when (viewModel.activeSettingsSubScreen) {
            null -> SettingsHubScreen(
                viewModel = viewModel,
                listState = hubListState,
                onNavigate = { viewModel.openSettingsSubScreen(it) }
            )
            SettingsSection.WORKOUT -> WorkoutSettingsSubScreen(
                viewModel = viewModel,
                listState = workoutListState,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.NOTIFICATIONS -> NotificationSettingsSubScreen(
                viewModel = viewModel,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.APPEARANCE -> AppearanceSubScreen(
                themeConfig = viewModel.themeConfig,
                listState = appearanceListState,
                onUpdateTheme = viewModel::updateThemeConfig,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.EXERCISES -> ExerciseLibrarySettingsSubScreen(
                viewModel = viewModel,
                listState = exercisesListState,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.HEALTH_CONNECT -> HealthConnectSettingsSubScreen(
                viewModel = viewModel,
                listState = healthListState,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.WATCH -> WatchSettingsSubScreen(
                viewModel = viewModel,
                listState = watchListState,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.AI -> AiSettingsSubScreen(
                viewModel = viewModel,
                listState = aiListState,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.BACKUP -> BackupSettingsSubScreen(
                viewModel = viewModel,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
            SettingsSection.DATA -> DataAndStorageSettingsSubScreen(
                viewModel = viewModel,
                listState = dataListState,
                onBack = { viewModel.openSettingsSubScreen(null) }
            )
        }
    }
}

@Composable
private fun SettingsHubScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState,
    onNavigate: (SettingsSection) -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }
    val watchStatus = viewModel.wearWatchStatus
    val grantedCount = viewModel.healthPermissions.count { it.granted }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Settings & Preferences",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Personalize training defaults, theme & appearance, Health Connect sync, Wear OS watch, backups, and data",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        viewModel.settingsStatusMessage?.let { msg ->
            item { StatusMessageCard(msg) { viewModel.settingsStatusMessage = null } }
        }

        // Live System Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "System Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue,
                        )
                        StatusBadge(
                            text = when {
                                watchStatus.isWatchWithAppReachable -> "Watch Ready"
                                viewModel.isHealthConnectAvailable -> "Health Ready"
                                else -> "Ready"
                            },
                            containerColor = if (watchStatus.isWatchWithAppReachable) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (watchStatus.isWatchWithAppReachable) AccentBlue else TextSecondary,
                            dotColor = if (watchStatus.isWatchWithAppReachable) AccentGreen else AccentBlue,
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OverviewMetricTile(
                            label = "Training Defaults",
                            value = "${viewModel.weightUnit.label.uppercase(Locale.US)} · ${viewModel.defaultRestTimerSeconds}s rest",
                            subtitle = "Step ${viewModel.activeWeightStepDisplayValue} ${viewModel.weightUnit.label} · ${if (viewModel.showRpeColumn) "RPE on" else "RPE off"}",
                            modifier = Modifier.weight(1f),
                        )
                        OverviewMetricTile(
                            label = "Appearance",
                            value = viewModel.themeConfig.preset.title,
                            subtitle = "${viewModel.themeConfig.mode.label}${if (viewModel.themeConfig.amoledBlack && viewModel.themeConfig.mode != ThemeMode.LIGHT) " · AMOLED" else ""}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OverviewMetricTile(
                            label = "Health & Watch",
                            value = if (viewModel.isHealthConnectAvailable) "$grantedCount/${viewModel.healthPermissions.size} HC perms" else "HC unavailable",
                            subtitle = if (watchStatus.isWatchWithAppReachable) watchStatus.watchModelName else "Watch offline",
                            modifier = Modifier.weight(1f),
                        )
                        OverviewMetricTile(
                            label = "Workout Library",
                            value = "${viewModel.workoutHistory.size} workouts",
                            subtitle = "${viewModel.routines.size} routines · ${viewModel.exercises.size} exercises",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Section: Training & Personalization
        item { SectionHeader("Training & Personalization") }
        item {
            CategoryTile(
                title = "Workout & Rest Timer",
                subtitle = "Weight units, step increments, default rest countdown, RPE & wake lock",
                badgeText = "${viewModel.weightUnit.label.uppercase(Locale.US)} · ${WorkoutMechanics.formatRest(viewModel.defaultRestTimerSeconds)} rest",
                icon = { AppIcons.Barbell(modifier = Modifier.size(22.dp), tint = AccentBlue) },
                iconBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                onClick = { onNavigate(SettingsSection.WORKOUT) },
            )
        }
        item {
            CategoryTile(
                title = "Notifications & Timer Sound",
                subtitle = "Rest timer alert and sound, watch vibration, workout-in-progress notification",
                badgeText = if (viewModel.restAlertEnabled) viewModel.restAlertSoundTitle else "Alerts off",
                icon = { AppIcons.Tune(modifier = Modifier.size(22.dp), tint = AccentBlue) },
                iconBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                onClick = { onNavigate(SettingsSection.NOTIFICATIONS) },
            )
        }
        item {
            CategoryTile(
                title = "Theme & Appearance",
                subtitle = "Dark/Light display mode, AMOLED black, curated athletic presets & custom palette",
                badgeText = "${viewModel.themeConfig.preset.title} · ${viewModel.themeConfig.mode.label}",
                icon = { AppIcons.Palette(modifier = Modifier.size(22.dp), tint = AccentBlue) },
                iconBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                onClick = { onNavigate(SettingsSection.APPEARANCE) },
            )
        }
        item {
            CategoryTile(
                title = "Exercise Library & Media",
                subtitle = "Animated exercise GIFs, dataset reload, and cached demonstration media",
                badgeText = "${viewModel.exercises.size} exercises · ${if (viewModel.preferAnimatedExerciseGif) "GIFs on" else "Static images"}",
                icon = { AppIcons.Analytics(modifier = Modifier.size(22.dp), tint = AccentTertiary) },
                iconBackground = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                onClick = { onNavigate(SettingsSection.EXERCISES) },
            )
        }

        // Section: Integrations & Devices
        item { SectionHeader("Integrations & Devices") }
        item {
            CategoryTile(
                title = "Health Connect & Biometrics",
                subtitle = "Workout sync, calories, heart rate, weight & body fat measurements",
                badgeText = if (viewModel.isHealthConnectAvailable) "$grantedCount of ${viewModel.healthPermissions.size} permissions" else "Setup required",
                icon = { AppIcons.HeartPulse(modifier = Modifier.size(22.dp), tint = AccentRed) },
                iconBackground = AccentRed.copy(alpha = 0.16f),
                onClick = { onNavigate(SettingsSection.HEALTH_CONNECT) },
            )
        }
        item {
            CategoryTile(
                title = "Wear OS Watch Companion",
                subtitle = "Live workout mirroring, wrist heart rate stream & watch app launcher",
                badgeText = when {
                    watchStatus.isWatchWithAppReachable -> "${watchStatus.watchModelName} · Connected"
                    watchStatus.hasConnectedWatch -> "${watchStatus.watchModelName} · App missing"
                    else -> "No watch connected"
                },
                icon = { AppIcons.Watch(modifier = Modifier.size(22.dp), tint = GarminBlue) },
                iconBackground = GarminBlue.copy(alpha = 0.16f),
                onClick = { onNavigate(SettingsSection.WATCH) },
            )
        }

        item {
            val aiProvider = viewModel.ai.activeProvider
            CategoryTile(
                title = "AI Coach",
                subtitle = "Bring your own model for workout analysis and coach chat: Claude, OpenAI-compatible, Gemini or a local model",
                badgeText = aiProvider?.label ?: "Not set up",
                icon = { AppIcons.Sparkles(modifier = Modifier.size(22.dp), tint = AccentTertiary) },
                iconBackground = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                onClick = { onNavigate(SettingsSection.AI) },
            )
        }

        // Section: Data & Storage
        item { SectionHeader("Data & Storage") }
        item {
            CategoryTile(
                title = "Backup & Restore",
                subtitle = "Encrypted file backups, restore, WebDAV cloud sync & automatic schedule",
                badgeText = "Files & WebDAV",
                icon = { AppIcons.Cloud(modifier = Modifier.size(22.dp), tint = AccentBlue) },
                iconBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                onClick = { onNavigate(SettingsSection.BACKUP) },
            )
        }
        item {
            CategoryTile(
                title = "Data, Export & Storage",
                subtitle = "Workout CSV import/export, portable JSON/CSV sharing, storage counts & reset",
                badgeText = "${viewModel.workoutHistory.size} workouts · ${viewModel.routines.size} routines",
                icon = { AppIcons.Database(modifier = Modifier.size(22.dp), tint = SamsungGreen) },
                iconBackground = SamsungGreen.copy(alpha = 0.16f),
                onClick = { onNavigate(SettingsSection.DATA) },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Lift Tracker v$versionName",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Local-first & private · Workout logs and preferences stay on your devices.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WorkoutSettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState,
    onBack: () -> Unit,
) {
    val weightStepOptions = if (viewModel.weightUnit == WeightUnit.KG) {
        listOf(1.0, 2.5, 5.0)
    } else {
        listOf(2.5, 5.0, 10.0)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Workout & Rest Timer",
                subtitle = "Configure weight display units, increment steps, rest countdown, and training toggles.",
                onBack = onBack,
            )
        }

        item { SectionHeader("Units & Step Increment") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Weight Unit", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    SegmentedSwitcher(
                        selectedIndex = WeightUnit.entries.indexOf(viewModel.weightUnit),
                        options = WeightUnit.entries.map { it.label.uppercase(Locale.US) },
                        onSelect = { idx -> viewModel.updateWeightUnit(WeightUnit.entries[idx]) }
                    )

                    HorizontalDivider(color = CardElevated)

                    Text(
                        text = "Weight Step Increment (${viewModel.weightUnit.label.uppercase(Locale.US)})",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SegmentedSwitcher(
                        selectedIndex = weightStepOptions.indexOf(viewModel.activeWeightStepDisplayValue).coerceAtLeast(0),
                        options = weightStepOptions.map { "$it ${viewModel.weightUnit.label}" },
                        onSelect = { idx -> viewModel.updateWeightStepInCurrentUnit(weightStepOptions[idx]) }
                    )
                }
            }
        }

        item { SectionHeader("Rest Timer") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Default Rest Duration", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    RestStepper(
                        seconds = viewModel.defaultRestTimerSeconds,
                        onChange = { viewModel.updateDefaultRestTimer(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Used for exercises you add. Each exercise can have its own rest time; Off disables the timer.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    HorizontalDivider(color = CardElevated)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openSettingsSubScreen(SettingsSection.NOTIFICATIONS) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rest Timer Alert & Sound", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (viewModel.restAlertEnabled) viewModel.restAlertSoundTitle else "Alerts off",
                                color = AccentCyan,
                                fontSize = 12.sp
                            )
                        }
                        Text("Notifications ›", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item { SectionHeader("Session Behavior") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggleRow(
                        title = "Enable RPE (1–10 Scale)",
                        subtitle = "Optional Rate of Perceived Exertion column on workout sets (skippable)",
                        checked = viewModel.showRpeColumn,
                        onCheckedChange = { viewModel.updateShowRpeColumn(it) }
                    )
                    HorizontalDivider(color = CardElevated)
                    SettingToggleRow(
                        title = "Auto-Start Rest Timer",
                        subtitle = "Start countdown automatically when a set is checked",
                        checked = viewModel.autoStartRestTimer,
                        onCheckedChange = { viewModel.updateAutoStartRestTimer(it) }
                    )
                    HorizontalDivider(color = CardElevated)
                    SettingToggleRow(
                        title = "Keep Screen Awake",
                        subtitle = "Prevent screen sleep while the Active Workout Layer is open",
                        checked = viewModel.keepScreenAwake,
                        onCheckedChange = { viewModel.updateKeepScreenAwake(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Re-read the system state whenever the app resumes or a permission dialog closes.
    val stateVersion = viewModel.notificationStateVersion
    val canNotify = remember(stateVersion) { RestTimerAlerts.canNotify(context) }
    val exactAlarms = remember(stateVersion) { RestTimerAlerts.canScheduleExact(context) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.notificationStateVersion++
        if (!granted) {
            // Android stops showing the dialog after it was declined; send the user to system settings.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.updateRestAlertSound(picked?.toString() ?: "")
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Notifications & Timer Sound",
                subtitle = "Get an alert when your rest is over, even with the screen off or the app in the background.",
                onBack = onBack,
            )
        }
        viewModel.settingsStatusMessage?.let { msg ->
            item { StatusMessageCard(msg) { viewModel.settingsStatusMessage = null } }
        }

        if (!canNotify) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, AccentAmber),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notifications are off for Lift Tracker", color = AccentAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Rest timer alerts cannot sound or show until notifications are allowed.", color = TextSecondary, fontSize = 12.sp)
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33 && !RestTimerAlerts.hasNotificationPermission(context)) {
                                    askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Allow Notifications", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Rest Timer Alert") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggleRow(
                        title = "Alert When Rest Is Over",
                        subtitle = "Notification with sound at the end of each rest countdown",
                        checked = viewModel.restAlertEnabled,
                        onCheckedChange = { viewModel.updateRestAlertEnabled(it) }
                    )
                    HorizontalDivider(color = CardElevated)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val current = viewModel.restAlertSoundUri
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                        RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE
                                    )
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Rest timer sound")
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    .putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                    )
                                    .putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        when {
                                            current == null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                            current.isEmpty() -> null
                                            else -> Uri.parse(current)
                                        }
                                    )
                                runCatching { pickSound.launch(intent) }
                                    .onFailure { viewModel.settingsStatusMessage = "No sound picker is available on this device." }
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alert Sound", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(viewModel.restAlertSoundTitle, color = AccentCyan, fontSize = 12.sp)
                        }
                        Text("Choose ›", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = CardElevated)
                    SettingToggleRow(
                        title = "Vibrate",
                        subtitle = "Vibrate with the alert",
                        checked = viewModel.restAlertVibrate,
                        onCheckedChange = { viewModel.updateRestAlertVibrate(it) }
                    )
                    HorizontalDivider(color = CardElevated)
                    SettingToggleRow(
                        title = "Sound On",
                        subtitle = "Loudness follows the phone's notification volume; Do Not Disturb can silence it",
                        checked = viewModel.restTimerVolumeFloat > 0.02f,
                        onCheckedChange = { viewModel.updateRestTimerVolume(if (it) "normal" else "off") }
                    )
                    if (!exactAlarms) {
                        Text(
                            "Exact alarms are not allowed for this app, so the alert can arrive a little late when the phone is idle.",
                            color = AccentAmber,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallActionChip("🔔 Test Alert") { viewModel.testRestAlert() }
                        SmallActionChip("System notification settings") {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        }
                    }
                }
            }
        }
        item { NotificationExtrasCards(viewModel) }
    }
}

@Composable
private fun NotificationExtrasCards(viewModel: LiftTrackerViewModel) {
    SectionHeader("Watch")
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardOutline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingToggleRow(
                title = "Vibrate on Watch",
                subtitle = "Buzz the watch when the rest is over",
                checked = viewModel.watchAlertVibrate,
                onCheckedChange = { viewModel.updateWatchAlert(vibrate = it) }
            )
            HorizontalDivider(color = CardElevated)
            SettingToggleRow(
                title = "Sound on Watch",
                subtitle = "Play the watch's notification sound (watches with a speaker)",
                checked = viewModel.watchAlertSound,
                onCheckedChange = { viewModel.updateWatchAlert(sound = it) }
            )
            Text(
                "Needs the Lift Tracker watch app, and \"Alert When Rest Is Over\" switched on above.",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    SectionHeader("Workout In Progress")
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardOutline),
    ) {
        Column(Modifier.padding(14.dp)) {
            SettingToggleRow(
                title = "Keep a Workout Notification",
                subtitle = "Stays up during a workout with the next set, the rest countdown, and buttons: Complete set, Skip rest, −15s, +15s",
                checked = viewModel.workoutNotificationEnabled,
                onCheckedChange = { viewModel.updateWorkoutNotificationEnabled(it) }
            )
            if (WorkoutSessionNotification.liveUpdatesSupported && viewModel.workoutNotificationEnabled) {
                LiveUpdatesRow()
            }
        }
    }
}

/** Android 16+: whether the workout notification may show as a Live Update (status bar chip, Samsung Now Bar). */
@Composable
private fun LiveUpdatesRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(WorkoutSessionNotification.canShowLiveUpdates(context)) }
    DisposableEffect(lifecycleOwner) {
        // Re-checked when coming back from the system settings page.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = WorkoutSessionNotification.canShowLiveUpdates(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingToggleRow(
        title = "Live Updates & Now Bar",
        subtitle = if (allowed) {
            "The workout shows in the status bar chip, on the lock screen and in the Samsung Now Bar"
        } else {
            "Off in system settings — turn on to show the workout in the status bar chip and the Samsung Now Bar"
        },
        checked = allowed,
        onCheckedChange = {
            runCatching { context.startActivity(WorkoutSessionNotification.liveUpdatesSettingsIntent(context)) }
        }
    )
}

@Composable
private fun BackupSettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    onBack: () -> Unit,
) {
    var asking by remember { mutableStateOf<BackupKind?>(null) }
    // Kept across the system file-save screen.
    var pendingKind by remember { mutableStateOf<BackupKind?>(null) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val kind = pendingKind
        if (uri != null && kind != null) viewModel.backupToUri(kind, pendingPassword, uri)
        pendingKind = null
        pendingPassword = null
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickRestoreFile(uri)
    }
    val ready = !viewModel.backupBusy

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Backup & Restore",
                subtitle = "Save everything to a file or a WebDAV folder, by hand or on a schedule, and bring it back on this or another phone.",
                onBack = onBack,
            )
        }
        viewModel.backupProgress?.let { step ->
            item { Text(step, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        viewModel.backupMessage?.let { msg ->
            item { StatusMessageCard(msg) { viewModel.backupMessage = null } }
        }
        item { SectionHeader("Local File Backup & Restore") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackupRow(
                        title = BackupKind.CONFIG.label,
                        subtitle = "Units, rest timer, theme and notification choices, your AI providers and picture model with their keys, and the exercises you created (with pictures). Always password-protected.",
                        enabled = ready,
                        onBackup = { asking = BackupKind.CONFIG }
                    )
                    HorizontalDivider(color = CardElevated)
                    BackupRow(
                        title = BackupKind.DATA.label,
                        subtitle = "${viewModel.workoutHistory.size} logged workouts, ${viewModel.routines.size} routines, your exercises with their pictures, body measurements, AI chats and analyses. Password optional.",
                        enabled = ready,
                        onBackup = { asking = BackupKind.DATA }
                    )
                    HorizontalDivider(color = CardElevated)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Restore from a backup", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Settings or data — the file says which. You'll see what's in it before anything is replaced.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { openFile.launch(arrayOf("*/*")) },
                            enabled = ready,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { SectionHeader("WebDAV") }
        item { WebDavBackupCard(viewModel) }
        item { SectionHeader("Automatic Backup") }
        item { AutoBackupCard(viewModel) }
    }

    asking?.let { kind ->
        BackupPasswordDialog(
            title = "Back up ${kind.phrase}",
            message = if (kind == BackupKind.CONFIG) {
                "This backup holds your API keys, so it is encrypted. You need this password to restore it — it cannot be recovered."
            } else {
                "Add a password to encrypt the file, or leave both fields empty for a plain zip."
            },
            passwordRequired = kind == BackupKind.CONFIG,
            confirmTwice = true,
            confirmLabel = "Choose File",
            onDismiss = { asking = null },
            onConfirm = { password ->
                asking = null
                pendingKind = kind
                pendingPassword = password
                saveFile.launch(viewModel.backupFileName(kind, password != null))
            }
        )
    }
    if (viewModel.restoreNeedsPassword) {
        BackupPasswordDialog(
            title = "Encrypted backup",
            message = viewModel.backupMessage ?: "Enter the password this backup was made with.",
            passwordRequired = true,
            confirmTwice = false,
            confirmLabel = "Open",
            onDismiss = { viewModel.cancelRestore() },
            onConfirm = { password -> password?.let { viewModel.submitRestorePassword(it) } }
        )
    }
    viewModel.pendingRestore?.let { opened ->
        val m = opened.manifest
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Restore ${m.kind.phrase}?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Made ${m.createdAt.replace('T', ' ')} on ${m.device}" + if (m.appVersion.isNotBlank()) " (app ${m.appVersion})" else "", fontSize = 12.sp)
                    Text("Contains: ${m.summary()}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (m.kind == BackupKind.CONFIG) {
                            "Your current settings, AI providers and picture model on this phone will be replaced. The backup's custom exercises are added to yours. Workouts and routines are not touched."
                        } else {
                            "ALL workouts, routines, custom exercises, body measurements and AI history on this phone will be replaced by the backup's. This cannot be undone."
                        },
                        color = AccentAmber,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmRestore() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) { Text("Replace & Restore", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun BackupRow(title: String, subtitle: String, enabled: Boolean, onBackup: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onBackup, enabled = enabled, shape = RoundedCornerShape(10.dp)) {
            Text("Back up", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Asks for a backup password. Returns null from [onConfirm] when left empty and not required. */
@Composable
internal fun BackupPasswordDialog(
    title: String,
    message: String,
    passwordRequired: Boolean,
    confirmTwice: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (CharArray?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val passwordKeys = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
    val mismatch = confirmTwice && password != repeat
    val tooShort = password.isNotEmpty() && confirmTwice && password.length < 6
    val canConfirm = !mismatch && !tooShort && (password.isNotEmpty() || !passwordRequired)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, fontSize = 12.sp)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (passwordRequired) "Password" else "Password (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = passwordKeys,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmTwice) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text("Repeat password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = passwordKeys,
                        isError = mismatch && repeat.isNotEmpty(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                when {
                    tooShort -> Text("Use at least 6 characters.", color = AccentAmber, fontSize = 11.sp)
                    mismatch && repeat.isNotEmpty() -> Text("The passwords don't match.", color = AccentAmber, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(if (password.isEmpty()) null else password.toCharArray()) }, enabled = canConfirm) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

private val QUICK_SWATCHES = listOf(
    Color(0xFF00D2FF), // Glass Cyan
    Color(0xFFE040FB), // Neon Magenta
    Color(0xFFFF8C3B), // Ember Amber
    Color(0xFF2F80ED), // Cobalt Lift
    Color(0xFF00E5BE), // Runner Teal
    Color(0xFF2DB84C), // Samsung Green
    Color(0xFFFF5252), // Pulse Coral
    Color(0xFF9D65FF), // AI Purple
    Color(0xFF3B82F6), // Cobalt Blue
    Color(0xFFF59E0B), // Podium Gold
    Color(0xFFEC4899), // Neon Pink
    Color(0xFF10B981), // Emerald
)

@Composable
private fun AppearanceSubScreen(
    themeConfig: ThemeConfig,
    listState: LazyListState,
    onUpdateTheme: (ThemeConfig) -> Unit,
    onBack: () -> Unit,
) {
    var editingSlot by rememberSaveable { mutableIntStateOf(0) } // 0 = Primary, 1 = Secondary, 2 = Tertiary
    val currentSlotColor = when (editingSlot) {
        0 -> themeConfig.activePrimary
        1 -> themeConfig.activeSecondary
        else -> themeConfig.activeTertiary
    }
    var hexInput by remember(editingSlot, themeConfig.preset, currentSlotColor) {
        mutableStateOf(ThemeConfig.toHex(currentSlotColor))
    }

    fun applySlotColor(newColor: Color) {
        val argb = newColor.toArgb()
        val baseP = themeConfig.activePrimary.toArgb()
        val baseS = themeConfig.activeSecondary.toArgb()
        val baseT = themeConfig.activeTertiary.toArgb()
        onUpdateTheme(
            themeConfig.copy(
                preset = ThemePreset.CUSTOM,
                customPrimaryArgb = if (editingSlot == 0) argb else baseP,
                customSecondaryArgb = if (editingSlot == 1) argb else baseS,
                customTertiaryArgb = if (editingSlot == 2) argb else baseT,
            ),
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Theme & Appearance",
                subtitle = "Customize display mode, select a curated color preset, or craft your own palette.",
                onBack = onBack,
                trailingAction = {
                    if (themeConfig != ThemeConfig()) {
                        TextButton(onClick = { onUpdateTheme(ThemeConfig()) }) {
                            Text("Reset")
                        }
                    }
                },
            )
        }

        // Live Preview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Live Theme Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue,
                        )
                        StatusBadge(
                            text = themeConfig.preset.title,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            dotColor = AccentBlue,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(
                            text = "Bench · 105 kg",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(
                            text = "HR · 148 bpm",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(
                            text = "PR Streak ★",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Display Mode
        item { SectionHeader("Display Mode") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SegmentedSwitcher(
                        selectedIndex = ThemeMode.entries.indexOf(themeConfig.mode),
                        options = ThemeMode.entries.map { it.label },
                        onSelect = { idx -> onUpdateTheme(themeConfig.copy(mode = ThemeMode.entries[idx])) },
                    )
                    if (themeConfig.mode != ThemeMode.LIGHT) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("AMOLED Pure Black", color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Uses true #000000 pitch-black background in dark mode for OLED screens.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = themeConfig.amoledBlack,
                                onCheckedChange = { onUpdateTheme(themeConfig.copy(amoledBlack = it)) },
                            )
                        }
                    }
                }
            }
        }

        // Color Presets
        item { SectionHeader("Curated Color Presets") }
        items(ThemePreset.selectablePresets.chunked(2), key = { it.first().name }) { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPresets.forEach { preset ->
                    val isSelected = themeConfig.preset == preset
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onUpdateTheme(
                                    themeConfig.copy(
                                        preset = preset,
                                        customPrimaryArgb = preset.primary.toArgb(),
                                        customSecondaryArgb = preset.secondary.toArgb(),
                                        customTertiaryArgb = preset.tertiary.toArgb(),
                                    ),
                                )
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                CardDark
                            },
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AccentBlue else CardOutline,
                        ),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ColorDot(preset.primary, size = 18.dp)
                                    ColorDot(preset.secondary, size = 18.dp)
                                    ColorDot(preset.tertiary, size = 18.dp)
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentBlue,
                                    )
                                }
                            }
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (rowPresets.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Custom Palette Editor
        item { SectionHeader("Custom Accent Palette") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Fine-tune individual accent roles with swatches, hex codes, or RGB sliders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    SegmentedSwitcher(
                        selectedIndex = editingSlot,
                        options = listOf("Primary", "Secondary", "Tertiary"),
                        onSelect = { editingSlot = it },
                    )

                    // Quick Swatches (2 rows of 6)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_SWATCHES.chunked(6).forEach { swatchRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                swatchRow.forEach { swatch ->
                                    val selected = swatch.toArgb() == currentSlotColor.toArgb()
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) TextPrimary else Color.White.copy(alpha = 0.25f),
                                                shape = CircleShape,
                                            )
                                            .clickable {
                                                hexInput = ThemeConfig.toHex(swatch)
                                                applySlotColor(swatch)
                                            },
                                    )
                                }
                            }
                        }
                    }

                    // Hex input & current color box
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentSlotColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                        )
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { input ->
                                hexInput = input.uppercase().take(7)
                                ThemeConfig.parseHex(hexInput)?.let { parsed ->
                                    applySlotColor(parsed)
                                }
                            },
                            label = { Text("Hex Color (#RRGGBB)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // RGB Sliders
                    val r = (currentSlotColor.red * 255).roundToInt()
                    val g = (currentSlotColor.green * 255).roundToInt()
                    val b = (currentSlotColor.blue * 255).roundToInt()

                    RgbSliderRow("R", r, Color(0xFFFF5252)) { newR ->
                        val c = Color(newR, g, b)
                        hexInput = ThemeConfig.toHex(c)
                        applySlotColor(c)
                    }
                    RgbSliderRow("G", g, Color(0xFF2DB84C)) { newG ->
                        val c = Color(r, newG, b)
                        hexInput = ThemeConfig.toHex(c)
                        applySlotColor(c)
                    }
                    RgbSliderRow("B", b, Color(0xFF3B82F6)) { newB ->
                        val c = Color(r, g, newB)
                        hexInput = ThemeConfig.toHex(c)
                        applySlotColor(c)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
    )
}

@Composable
private fun RgbSliderRow(
    channel: String,
    value: Int,
    trackColor: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = channel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = trackColor,
            modifier = Modifier.width(18.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(30.dp),
        )
    }
}

@Composable
private fun ExerciseLibrarySettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState,
    onBack: () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Exercise Library",
                subtitle = "Your own exercises, the built-in dataset, animated GIFs and the media cache.",
                onBack = onBack,
            )
        }

        viewModel.settingsStatusMessage?.let { msg ->
            item { StatusMessageCard(msg) { viewModel.settingsStatusMessage = null } }
        }

        item { UserExercisesSettingsCard(viewModel) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Loaded Exercises: ${viewModel.exercises.size}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = WorkoutMechanics.DATASET_REPO_URL,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = CardElevated)
                    SettingToggleRow(
                        title = "Animate Exercise GIFs",
                        subtitle = "Play animated demonstration GIFs in How-To & workout guides",
                        checked = viewModel.preferAnimatedExerciseGif,
                        onCheckedChange = { viewModel.updatePreferAnimatedGif(it) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.reloadDatasetFromAssets() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardOutline),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reload Dataset", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearMediaCache() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardOutline),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Image Cache", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthConnectSettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState,
    onBack: () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Health Connect",
                subtitle = "Sync workouts, watch heart rate, body weight, and body fat percentage.",
                onBack = onBack,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp)) {
                    HealthConnectSettingsContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun WatchSettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState,
    onBack: () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Wear OS Watch",
                subtitle = "Monitor companion watch connectivity, live heart rate, and remote launch.",
                onBack = onBack,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp)) {
                    WatchSettingsContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun DataAndStorageSettingsSubScreen(
    viewModel: LiftTrackerViewModel,
    listState: LazyListState,
    onBack: () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubScreenHeader(
                title = "Data, Export & Storage",
                subtitle = "Import or export CSV and JSON data, review SQLite storage counts, or reset app data.",
                onBack = onBack,
            )
        }

        viewModel.settingsStatusMessage?.let { msg ->
            item { StatusMessageCard(msg) { viewModel.settingsStatusMessage = null } }
        }

        item { SectionHeader("Local Storage & Reset") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OverviewMetricTile(
                            label = "Logged Workouts",
                            value = "${viewModel.workoutHistory.size}",
                            subtitle = "Stored in SQLite",
                            modifier = Modifier.weight(1f),
                        )
                        OverviewMetricTile(
                            label = "Routines",
                            value = "${viewModel.routines.size}",
                            subtitle = "Active templates",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.requestConfirmation(
                                    title = "Clear workout history?",
                                    message = "All ${viewModel.workoutHistory.size} logged workouts will be permanently deleted from this device. Routines and settings are kept. This cannot be undone.",
                                    confirmLabel = "Clear history"
                                ) { viewModel.clearWorkoutHistory() }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear History", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                viewModel.requestConfirmation(
                                    title = "Reset everything?",
                                    message = "Your workout history, routines and preferences will be replaced with the app defaults, and any workout in progress is discarded. This cannot be undone.",
                                    confirmLabel = "Reset"
                                ) { viewModel.resetAppDefaults() }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Defaults", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Import & Export") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, CardOutline),
            ) {
                Column(Modifier.padding(14.dp)) {
                    DataExportContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun HealthConnectSettingsContent(viewModel: LiftTrackerViewModel) {
    var weightInput by remember { mutableStateOf("") }
    var bodyFatInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> viewModel.onHealthPermissionsResult(granted) }

    val available = viewModel.isHealthConnectAvailable
    val grantedCount = viewModel.healthPermissions.count { it.granted }
    val unsyncedCount = viewModel.workoutHistory.count { !it.syncedToHealthConnect }

    viewModel.healthStatusMessage?.let { msg ->
        StatusMessageCard(msg) { viewModel.healthStatusMessage = null }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Text(
        text = when (viewModel.healthConnectSdkStatus) {
            WorkoutMechanics.HEALTH_CONNECT_SDK_AVAILABLE -> "Available"
            WorkoutMechanics.HEALTH_CONNECT_SDK_UPDATE_REQUIRED -> "Needs install or update"
            else -> "Not available on this device"
        },
        color = if (available) AccentGreen else AccentAmber,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    SettingToggleRow(
        title = "Auto-Sync Finished Workouts",
        subtitle = "Write each workout, its calories and watch heart rate to Health Connect",
        checked = viewModel.autoSyncWorkoutsToHealthConnect,
        onCheckedChange = { viewModel.updateAutoSyncHealthConnect(it) }
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (available) {
            if (grantedCount < viewModel.healthPermissions.size) {
                SmallActionChip("Grant permissions") {
                    permissionLauncher.launch(viewModel.healthPermissionsToRequest)
                }
            }
            SmallActionChip("Manage in Health Connect") {
                viewModel.healthConnectManageIntent()?.let { intent ->
                    runCatching { context.startActivity(intent) }
                }
            }
            if (viewModel.canWriteWorkoutsToHealthConnect && unsyncedCount > 0) {
                SmallActionChip("Sync $unsyncedCount workout(s)") {
                    viewModel.syncAllUnsyncedWorkoutsToHealthConnect()
                }
            }
        } else if (viewModel.healthConnectSdkStatus == WorkoutMechanics.HEALTH_CONNECT_SDK_UPDATE_REQUIRED) {
            SmallActionChip("Install Health Connect") {
                viewModel.healthConnectInstallIntent()?.let { intent ->
                    runCatching { context.startActivity(intent) }
                }
            }
        }
        SmallActionChip("↻ Refresh") { viewModel.refreshHealthConnectState() }
    }

    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = CardElevated)
    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Weight & Body Fat", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        SmallActionChip(if (viewModel.isMeasurementSyncInProgress) "Syncing…" else "↻ Sync now") {
            viewModel.syncMeasurementsFromHealthConnect()
        }
    }
    Text(
        text = "Latest: " +
            (viewModel.latestWeightKg?.let { WorkoutMechanics.formatWeight(it, viewModel.weightUnit) } ?: "no weight") +
            " • " + (viewModel.latestBodyFatPercent?.let { "$it % body fat" } ?: "no body fat") +
            (viewModel.lastMeasurementSyncLabel?.let { "\nLast synced $it" } ?: ""),
        color = AccentCyan,
        fontSize = 12.sp
    )
    SettingToggleRow(
        title = "Sync From Health Connect Automatically",
        subtitle = "Pull new, edited and deleted weight and body fat records when the app opens",
        checked = viewModel.autoSyncMeasurementsFromHealthConnect,
        onCheckedChange = { viewModel.updateAutoSyncMeasurements(it) }
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = weightInput,
            onValueChange = { weightInput = it },
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Button(
            enabled = available && weightInput.toDoubleOrNull() != null,
            onClick = { weightInput.toDoubleOrNull()?.let { viewModel.writeBodyMeasurement("write_weight", it) } },
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = bodyFatInput,
            onValueChange = { bodyFatInput = it },
            label = { Text("Body fat (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Button(
            enabled = available && bodyFatInput.toDoubleOrNull() != null,
            onClick = { bodyFatInput.toDoubleOrNull()?.let { viewModel.writeBodyMeasurement("write_bodyFat", it) } },
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (viewModel.bodyMeasurements.isEmpty()) {
        Text("No weight or body fat records synced yet.", color = TextSecondary, fontSize = 12.sp)
    }
    viewModel.bodyMeasurements.take(6).forEach { bm ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${if (bm.unit == "kg") "Weight" else "Body fat"}: ${bm.value} ${bm.unit} (${bm.dateLabel})",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = bm.source,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = CardElevated)
    Spacer(modifier = Modifier.height(10.dp))

    Text("Permissions", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    viewModel.healthPermissions.forEach { perm ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = perm.description,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(
                text = if (perm.granted) "✓ Granted" else "Not granted",
                color = if (perm.granted) AccentGreen else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (viewModel.healthExportLogs.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = CardElevated)
        Spacer(modifier = Modifier.height(10.dp))
        Text("Recent Sync Activity", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        viewModel.healthExportLogs.take(5).forEach { log ->
            Spacer(modifier = Modifier.height(6.dp))
            Text("${log.timestampLabel} • ${log.summary}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(log.details, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WatchSettingsContent(viewModel: LiftTrackerViewModel) {
    val status = viewModel.wearWatchStatus
    LaunchedEffect(Unit) { viewModel.refreshWatchStatus() }

    viewModel.wearStatusMessage?.let { msg ->
        StatusMessageCard(msg) { viewModel.wearStatusMessage = null }
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text(
        text = status.watchModelName,
        color = TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = when {
            status.isWatchWithAppReachable -> "Watch app is connected. Workouts mirror to your wrist, and you can log sets from the watch."
            status.hasWatchAppInstalled -> "Watch app is installed but the watch is out of reach."
            status.hasConnectedWatch -> "Install Lift Tracker on the watch to control workouts from your wrist."
            else -> "Pair a Wear OS watch with this phone to use the companion app."
        },
        color = TextSecondary,
        fontSize = 12.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            MetricPill(
                "WATCH",
                if (status.hasConnectedWatch) "Connected" else "None",
                if (status.hasConnectedWatch) AccentGreen else AccentRed
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            MetricPill(
                "WATCH APP",
                if (status.hasWatchAppInstalled) "Installed" else "Missing",
                if (status.hasWatchAppInstalled) AccentGreen else AccentAmber
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            MetricPill(
                "REACHABLE",
                if (status.isWatchWithAppReachable) "Yes" else "No",
                if (status.isWatchWithAppReachable) AccentGreen else AccentAmber
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallActionChip("↻ Refresh") { viewModel.refreshWatchStatus() }
        if (status.hasConnectedWatch) {
            SmallActionChip("Open app on watch") { viewModel.launchAppOnWear() }
        }
    }
}

/**
 * Long-press context menu anchored at the press position. When there is not enough room below or
 * beside the press point, the menu is shifted just far enough to fit on screen instead of jumping
 * to the top of the anchor.
 */
@Composable
private fun PressAnchoredMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    pressOffsetPx: Offset,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val marginPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val insets = WindowInsets.systemBars
    val density = LocalDensity.current
    val topInsetPx = insets.getTop(density)
    val bottomInsetPx = insets.getBottom(density)
    val positionProvider = remember(pressOffsetPx, marginPx, topInsetPx, bottomInsetPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val wantedX = anchorBounds.left + pressOffsetPx.x.toInt()
                val wantedY = anchorBounds.top + pressOffsetPx.y.toInt()
                val minX = marginPx
                val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(minX)
                val minY = topInsetPx + marginPx
                val maxY = (windowSize.height - bottomInsetPx - popupContentSize.height - marginPx).coerceAtLeast(minY)
                return IntOffset(wantedX.coerceIn(minX, maxX), wantedY.coerceIn(minY, maxY))
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            color = CardElevated,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DataExportContent(viewModel: LiftTrackerViewModel) {
    val context = LocalContext.current
    val saveJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType)
    ) { uri -> if (uri != null) viewModel.writeExportToUri(uri, ExportFormat.JSON) }
    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)
    ) { uri -> if (uri != null) viewModel.writeExportToUri(uri, ExportFormat.CSV) }

    val saveHistoryCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)
    ) { uri ->
        if (uri != null) {
            viewModel.writeTextToUri(uri, viewModel.buildWorkoutHistoryCsv(), "Saved ${viewModel.workoutHistory.size} workouts as a history CSV.")
        }
    }
    val openHistoryCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importWorkoutHistoryFromUri(uri)
    }

    fun shareUri(uri: Uri, mimeType: String, name: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        runCatching { context.startActivity(Intent.createChooser(send, "Share workout export")) }
    }

    fun share(format: ExportFormat) =
        shareUri(ExportProvider.uriFor(context, format), format.mimeType, WorkoutExporter.suggestedFileName(format))

    Text("WORKOUT HISTORY (CSV)", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "One row per set (title, start_time, exercise_title, set_type, weight, reps, distance_km, duration_seconds, rpe) — " +
            "the layout other strength trackers export. Importing the same file twice does not create duplicates. " +
            "Exported weights use your current unit (${viewModel.weightUnit.label}).",
        color = TextSecondary,
        fontSize = 12.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { openHistoryCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
            enabled = !viewModel.isImportInProgress,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(if (viewModel.isImportInProgress) "Importing…" else "Import CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { saveHistoryCsv.launch("lift-tracker-workouts.csv") },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CardOutline),
            modifier = Modifier.weight(1f)
        ) {
            Text("Export CSV", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    SmallActionChip("Share history CSV") {
        shareUri(ExportProvider.historyCsvUri(context), ExportFormat.CSV.mimeType, "lift-tracker-workouts.csv")
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Routines are imported, exported and shared as JSON from the Routines tab.",
        color = TextSecondary,
        fontSize = 12.sp
    )

    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider(color = CardElevated)
    Spacer(modifier = Modifier.height(14.dp))
    Text("PORTABLE DATA EXPORT (JSON / CSV)", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "JSON holds every workout and routine. The detailed CSV has one row per set with ids and estimated 1RM. Weights are always in kg.",
        color = TextSecondary,
        fontSize = 12.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { saveJson.launch(WorkoutExporter.suggestedFileName(ExportFormat.JSON)) },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CardOutline),
            modifier = Modifier.weight(1f)
        ) {
            Text("Save JSON", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { saveCsv.launch(WorkoutExporter.suggestedFileName(ExportFormat.CSV)) },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CardOutline),
            modifier = Modifier.weight(1f)
        ) {
            Text("Save detailed CSV", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallActionChip("Share workouts JSON") { share(ExportFormat.JSON) }
        SmallActionChip("Share detailed CSV") { share(ExportFormat.CSV) }
    }
}
