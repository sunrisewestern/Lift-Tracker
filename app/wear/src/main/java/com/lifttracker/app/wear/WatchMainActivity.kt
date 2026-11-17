package com.lifttracker.app.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.VignettePosition
import com.lifttracker.shared.WearProtocol
import com.lifttracker.shared.WearRoutineSummary
import com.lifttracker.shared.WearSetEdit
import com.lifttracker.shared.WearWorkoutState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Accent = Color(0xFF00D2FF)
private val Green = Color(0xFF27AE60)
private val Amber = Color(0xFFF2994A)
private val Red = Color(0xFFEB5757)
private val Muted = Color(0xFF9BA5B7)

class WatchMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchApp() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { WatchRepository.loadSyncedState(this@WatchMainActivity) }
            // Ask the phone to republish in case the synced DataItems are stale.
            WatchRepository.sendToPhone(this@WatchMainActivity, WearProtocol.MSG_REQUEST_STATE)
        }
    }
}

private fun formatClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun WatchApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by WatchRepository.workoutState.collectAsState()
    val routines by WatchRepository.routines.collectAsState()
    val heartRate by WatchRepository.heartRateBpm.collectAsState()
    val phoneReachable by WatchRepository.phoneReachable.collectAsState()

    val send: (String, String) -> Unit = { path, payload ->
        scope.launch { WatchRepository.sendToPhone(context, path, payload) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        HeartRateService.startIfPermitted(context)
        WatchWorkoutNotification.update(context, WatchRepository.workoutState.value)
    }

    // Heart rate tracking follows the phone's workout while the app is visible.
    LaunchedEffect(state.active) {
        if (state.active) {
            HeartRateService.startIfPermitted(context)
            val wanted = mutableListOf<String>()
            if (!HeartRateService.hasPermission(context)) wanted += HeartRateService.heartRatePermission()
            // Also needed for the ongoing workout on the watch face / Now Bar.
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                wanted += Manifest.permission.POST_NOTIFICATIONS
            }
            if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
        } else {
            HeartRateService.stop(context)
        }
    }

    // Ongoing Activity (watch face chip, recents, Galaxy Watch Now Bar) follows the phone's workout.
    LaunchedEffect(state) { WatchWorkoutNotification.update(context, state) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) {
        while (state.active) {
            now = System.currentTimeMillis()
            delay(500L)
        }
    }

    MaterialTheme {
        val listState = rememberScalingLazyListState()
        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { if (!state.active) PositionIndicator(scalingLazyListState = listState) }
        ) {
            if (state.active) {
                ActiveWorkoutPager(state, now, heartRate, send)
            } else {
                IdleScreen(routines, phoneReachable, listState, send)
            }
        }
    }
}

@Composable
private fun IdleScreen(
    routines: List<WearRoutineSummary>,
    phoneReachable: Boolean?,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    send: (String, String) -> Unit
) {
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        item {
            Text("Lift Tracker", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (phoneReachable == false) {
            item {
                Text(
                    "Phone not reachable. Open Lift Tracker on your phone.",
                    color = Amber,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
        item {
            Chip(
                onClick = { send(WearProtocol.MSG_WORKOUT_STARTED, "") },
                label = { Text("Start empty workout") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (routines.isNotEmpty()) {
            item { Text("Routines", color = Muted, fontSize = 12.sp) }
        }
        items(routines, key = { it.id }) { routine ->
            Chip(
                onClick = { send(WearProtocol.MSG_START_ROUTINE, routine.id) },
                label = { Text(routine.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text("${routine.exerciseCount} exercises") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

/**
 * Active workout: page 0 is the current set (weight / reps / complete), page 1 (swipe left) lists
 * the exercises of the session so another one can be made current.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveWorkoutPager(
    state: WearWorkoutState,
    now: Long,
    heartRate: Int,
    send: (String, String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val pageIndicatorState = remember(pagerState) {
        object : PageIndicatorState {
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
            override val pageCount: Int get() = 2
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                CurrentSetPage(state, now, heartRate, send)
            } else {
                ExerciseListPage(state, send) { index ->
                    send(WearProtocol.MSG_SELECT_EXERCISE, index.toString())
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
            }
        }
        HorizontalPageIndicator(pageIndicatorState = pageIndicatorState)
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onMinus,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
        ) { Text("−", fontSize = 18.sp) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = Muted, fontSize = 10.sp)
        }
        Button(
            onClick = onPlus,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
        ) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
private fun CurrentSetPage(
    state: WearWorkoutState,
    now: Long,
    heartRate: Int,
    send: (String, String) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val elapsed = state.elapsedSecondsAt(now)
    val rest = state.restRemainingSecondsAt(now)
    val allDone = state.setIndex < 0

    // Local edits are shown immediately and sent to the phone once tapping pauses.
    val setKey = state.exerciseIndex to state.setIndex
    var editWeight by remember(setKey) { mutableStateOf<Double?>(null) }
    var editReps by remember(setKey) { mutableStateOf<Int?>(null) }
    val weight = editWeight ?: state.weight
    val reps = editReps ?: state.reps
    val hasEdit = editWeight != null || editReps != null
    val editPayload = { WearSetEdit(state.exerciseIndex, state.setIndex, weight, reps).toJson() }

    LaunchedEffect(setKey, editWeight, editReps) {
        if (!hasEdit) return@LaunchedEffect
        delay(600L)
        send(WearProtocol.MSG_EDIT_SET, editPayload())
        // Fall back to the phone's values if it never confirms the edit.
        delay(4000L)
        editWeight = null
        editReps = null
    }
    LaunchedEffect(state.weight, state.reps) {
        if (editWeight == state.weight) editWeight = null
        if (editReps == state.reps) editReps = null
    }

    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        item {
            Text(
                text = (if (heartRate > 0) "♥ $heartRate  " else "") + formatClock(elapsed) +
                    if (state.paused) "  ⏸" else "",
                color = if (state.paused) Amber else Red,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        item {
            Text(
                text = if (allDone) "All sets done" else state.exerciseTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
        if (rest > 0) {
            item {
                Text("Rest ${formatClock(rest.toLong())}", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { send(WearProtocol.MSG_ADJUST_REST, "-15") },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) { Text("-15") }
                    Button(
                        onClick = { send(WearProtocol.MSG_SKIP_REST, "") },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) { Text("Skip") }
                    Button(
                        onClick = { send(WearProtocol.MSG_ADJUST_REST, "15") },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) { Text("+15") }
                }
            }
        }
        if (!allDone) {
            item {
                Text(
                    text = state.setLabel.substringBefore(" · "),
                    color = Accent,
                    fontSize = 12.sp
                )
            }
            item {
                StepperRow(
                    label = state.weightUnit,
                    value = formatWeight(weight),
                    onMinus = { editWeight = (weight - state.weightStep).coerceAtLeast(0.0) },
                    onPlus = { editWeight = weight + state.weightStep }
                )
            }
            item {
                StepperRow(
                    label = "reps",
                    value = reps.toString(),
                    onMinus = { editReps = (reps - 1).coerceAtLeast(1) },
                    onPlus = { editReps = reps + 1 }
                )
            }
            item {
                Chip(
                    onClick = {
                        // Make sure a pending edit reaches the phone before the set is completed.
                        if (hasEdit) send(WearProtocol.MSG_EDIT_SET, editPayload())
                        send(WearProtocol.MSG_COMPLETE_SET, "")
                    },
                    label = { Text("✓ Complete set") },
                    secondaryLabel = if (state.nextSetLabel.isNotBlank()) {
                        { Text("Next: ${state.nextSetLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Green),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Text(
                "${state.completedSets}/${state.totalSets} sets · swipe to finish",
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Chip(
                onClick = { send(WearProtocol.MSG_TOGGLE_PAUSE, "") },
                label = { Text(if (state.paused) "Resume" else "Pause") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Full-screen yes / no step shown before finishing or discarding a workout. */
@Composable
private fun ConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(showDialog = show, onDismissRequest = onDismiss) {
        Alert(
            title = { Text(title, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
            negativeButton = {
                Button(onClick = onDismiss, colors = ButtonDefaults.secondaryButtonColors()) { Text("No") }
            },
            positiveButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(backgroundColor = confirmColor)
                ) { Text("Yes") }
            }
        ) {
            Text(message, textAlign = TextAlign.Center, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun ExerciseListPage(
    state: WearWorkoutState,
    send: (String, String) -> Unit,
    onSelect: (Int) -> Unit
) {
    val listState = rememberScalingLazyListState()
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val remaining = (state.totalSets - state.completedSets).coerceAtLeast(0)

    ConfirmDialog(
        show = confirmFinish,
        title = "Finish workout?",
        message = if (remaining > 0) "$remaining set(s) are not completed. The workout is saved as it is." else "The workout is saved to your history.",
        confirmColor = Green,
        onConfirm = {
            confirmFinish = false
            send(WearProtocol.MSG_FINISH_WORKOUT, "")
        },
        onDismiss = { confirmFinish = false }
    )
    ConfirmDialog(
        show = confirmDiscard,
        title = "Discard workout?",
        message = "Everything logged in this workout is lost. This cannot be undone.",
        confirmColor = Red,
        onConfirm = {
            confirmDiscard = false
            send(WearProtocol.MSG_DISCARD_WORKOUT, "")
        },
        onDismiss = { confirmDiscard = false }
    )
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        item { Text("Exercises", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        if (state.exercises.isEmpty()) {
            item {
                Text(
                    "No exercises yet. Add them on your phone.",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
        itemsIndexed(state.exercises) { index, exercise ->
            val isCurrent = index == state.exerciseIndex
            val done = exercise.totalSets > 0 && exercise.completedSets >= exercise.totalSets
            Chip(
                onClick = { onSelect(index) },
                label = { Text(exercise.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(
                        (if (isCurrent) "Current · " else if (done) "Done · " else "") +
                            "${exercise.completedSets}/${exercise.totalSets} sets"
                    )
                },
                colors = if (isCurrent) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = { confirmFinish = true },
                label = { Text("Finish workout") },
                colors = ChipDefaults.chipColors(backgroundColor = Green),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = { confirmDiscard = true },
                label = { Text("Discard workout") },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF5A1F1F)),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
