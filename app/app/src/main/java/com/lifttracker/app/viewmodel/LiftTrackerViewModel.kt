package com.lifttracker.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifttracker.app.ai.AiController
import com.lifttracker.app.ai.AiDataSource
import com.lifttracker.app.ai.AiStore
import com.lifttracker.app.domain.BodyMeasurementRecord
import com.lifttracker.app.domain.BodyMuscle
import com.lifttracker.app.domain.ExerciseAliases
import com.lifttracker.app.domain.ExerciseFamily
import com.lifttracker.app.domain.ExerciseStats
import com.lifttracker.app.domain.StatMetric
import com.lifttracker.app.domain.StatPeriod
import com.lifttracker.app.domain.CustomExerciseCodec
import com.lifttracker.app.domain.EquipmentType
import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.domain.ProfileDashboard
import com.lifttracker.app.domain.ExerciseHistoryPoint
import com.lifttracker.app.domain.ExerciseMatching
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.HealthConnectExportLog
import com.lifttracker.app.domain.HealthConnectPermissionItem
import com.lifttracker.app.domain.HeartRateSamplePoint
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.RepType
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.RoutineTemplateExercise
import com.lifttracker.app.domain.SampleCatalog
import com.lifttracker.app.domain.SetType
import com.lifttracker.app.domain.WearMessageLog
import com.lifttracker.app.domain.WearWatchStatus
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutExerciseEntry
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutSetEntry
import com.lifttracker.app.export.ExerciseResolver
import com.lifttracker.app.export.ExportFormat
import com.lifttracker.app.export.ResolvedExercise
import com.lifttracker.app.export.RoutineCodec
import com.lifttracker.app.export.WorkoutCsv
import com.lifttracker.app.export.WorkoutExporter
import com.lifttracker.app.health.HealthConnectManager
import com.lifttracker.app.health.HealthMeasurement
import com.lifttracker.app.health.MeasurementType
import com.lifttracker.app.backup.AutoBackup
import com.lifttracker.app.backup.AutoBackupFrequency
import com.lifttracker.app.backup.BackupKind
import com.lifttracker.app.backup.RemoteBackupFile
import com.lifttracker.app.backup.RemoteBackupSettings
import com.lifttracker.app.backup.WebDav
import com.lifttracker.app.backup.WebDavConfig
import com.lifttracker.app.backup.BackupManager
import com.lifttracker.app.backup.WrongPasswordException
import com.lifttracker.app.notify.RestTimerAlerts
import com.lifttracker.app.notify.WorkoutSessionNotification
import com.lifttracker.app.storage.SQLiteWorkoutStorage
import com.lifttracker.app.ui.theme.ThemeConfig
import com.lifttracker.app.wear.WearConnector
import com.lifttracker.app.wear.WearEventBus
import com.lifttracker.app.wear.WearIncomingMessage
import com.lifttracker.shared.WearExerciseSummary
import com.lifttracker.shared.WearHeartRateSample
import com.lifttracker.shared.WearProtocol
import com.lifttracker.shared.WearRoutineSummary
import com.lifttracker.shared.WearSetEdit
import com.lifttracker.shared.WearWorkoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

enum class MainTab(val title: String) {
    WORKOUT("Workout"),
    EXERCISES("Exercises"),
    ROUTINES("Routines"),
    COACH("Coach"),
    PROFILE("Profile")
}

/** Pages opened from the Profile tab; they cover the tabs like the other layers. */
enum class ProfileLayer {
    STATISTICS,
    MONTHLY_REPORT,
    CALENDAR
}

/** A destructive action waiting for the user to confirm it. */
class ConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit
)

/** Categories of the Settings layer. */
enum class SettingsSection(val title: String) {
    WORKOUT("Workout"),
    NOTIFICATIONS("Notifications"),
    APPEARANCE("Theme & Appearance"),
    EXERCISES("Exercise Library"),
    HEALTH_CONNECT("Health Connect"),
    WATCH("Wear OS Watch"),
    AI("AI Coach"),
    BACKUP("Backup & Restore"),
    DATA("Data, Export & Storage")
}

enum class ExerciseSubTab(val title: String) {
    SUMMARY("Summary"),
    HISTORY("History"),
    HOW_TO("How To")
}

class LiftTrackerViewModel : ViewModel() {

    internal var appContext: Context? = null
        // Set by initializeWithContext; UI tests set it alone to load bundled pictures without storage.
        @androidx.annotation.VisibleForTesting internal set
    private var prefs: SharedPreferences? = null
    private var workoutStorage: SQLiteWorkoutStorage? = null
    private var hasInitializedContext = false
    private var healthConnect: HealthConnectManager? = null
    private var wearConnector: WearConnector? = null

    var selectedTab by mutableStateOf(MainTab.WORKOUT)

    /** AI coach: providers, chat and workout analyses. */
    val ai: AiController by lazy {
        AiController(
            viewModelScope,
            object : AiDataSource {
                override val history get() = workoutHistory.toList()
                override val routineTemplates get() = routines.toList()
                override val unit get() = weightUnit
                override val bodyWeightKg get() = latestWeightKg
                override val bodyFatPercent get() = latestBodyFatPercent
                override fun muscleOf(exerciseId: String, title: String) = findExercise(exerciseId, title)?.primaryMuscle
                override fun exerciseOf(exerciseId: String, title: String) = findExercise(exerciseId, title)
            }
        )
    }

    // --- Confirmation dialog for destructive actions ---
    var pendingConfirmation by mutableStateOf<ConfirmationRequest?>(null)
        private set

    fun requestConfirmation(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit) {
        pendingConfirmation = ConfirmationRequest(title, message, confirmLabel, onConfirm)
    }

    fun confirmPendingAction() {
        val request = pendingConfirmation ?: return
        pendingConfirmation = null
        request.onConfirm()
    }

    fun dismissPendingConfirmation() {
        pendingConfirmation = null
    }

    // --- Settings Layer ---
    var isSettingsLayerOpen by mutableStateOf(false)
    var activeSettingsSubScreen by mutableStateOf<SettingsSection?>(null)
    val expandedSettingsSections = mutableStateListOf<SettingsSection>()

    fun openSettingsLayer(section: SettingsSection? = null) {
        if (section != null && section !in expandedSettingsSections) expandedSettingsSections.add(section)
        activeSettingsSubScreen = section
        isSettingsLayerOpen = true
    }

    fun openSettingsSubScreen(section: SettingsSection?) {
        activeSettingsSubScreen = section
    }

    /** Shows the Coach tab, closing whatever layer is on top of the tabs. */
    fun openCoachTab() {
        closeSettingsLayer()
        isExerciseDetailLayerOpen = false
        isWorkoutSessionDetailLayerOpen = false
        selectedTab = MainTab.COACH
    }

    fun closeSettingsLayer() {
        activeSettingsSubScreen = null
        isSettingsLayerOpen = false
    }

    fun toggleSettingsSection(section: SettingsSection) {
        if (!expandedSettingsSections.remove(section)) expandedSettingsSections.add(section)
    }

    // --- Profile: dashboard, statistics, monthly report, calendar ---
    var profileLayer by mutableStateOf<ProfileLayer?>(null)
        private set
    var profilePeriod by mutableStateOf(StatPeriod.MONTH)
    var profileMetric by mutableStateOf(StatMetric.WORKOUTS)
    /** First day of the month the monthly report shows. */
    var reportMonth by mutableStateOf<LocalDate>(LocalDate.now().withDayOfMonth(1))
        private set

    fun openProfileLayer(layer: ProfileLayer) {
        profileLayer = layer
    }

    fun openMonthlyReport(month: LocalDate) {
        reportMonth = month.withDayOfMonth(1)
        profileLayer = ProfileLayer.MONTHLY_REPORT
    }

    /** Back from a profile page: the monthly report returns to Statistics, the others to the Profile tab. */
    fun closeProfileLayer() {
        profileLayer = if (profileLayer == ProfileLayer.MONTHLY_REPORT) ProfileLayer.STATISTICS else null
    }

    // The Profile tab's numbers are kept between visits and recomputed only when the history,
    // the exercise library, the period, the day or the calendar settings change. A snapshot list's
    // toList() returns the same immutable list instance until the list changes, which is what the
    // cache compares (and reading it here subscribes the Profile screen to changes).
    private var dashboardCache: ProfileDashboard? = null
    private var dashboardLibrary: List<ExerciseDefinition>? = null

    /** The Profile dashboard for the current history and [profilePeriod]; cached, see above. */
    fun profileDashboard(today: LocalDate, zone: ZoneId, firstDayOfWeek: DayOfWeek, bucketCount: Int): ProfileDashboard {
        val sessions = workoutHistory.toList()
        val library = exercises.toList()
        val period = profilePeriod
        dashboardCache?.let { c ->
            if (c.sessions === sessions && dashboardLibrary === library && c.period == period && c.today == today &&
                c.zone == zone && c.firstDayOfWeek == firstDayOfWeek && c.bucketCount == bucketCount
            ) return c
        }
        // Look exercises up by id / name once instead of scanning the library for every logged entry.
        val byId = HashMap<String, ExerciseDefinition>()
        val byTitle = HashMap<String, ExerciseDefinition>()
        library.forEach { byId.putIfAbsent(it.id, it); byTitle.putIfAbsent(it.title.lowercase(Locale.ROOT), it) }
        val loads = HashMap<Pair<String, String>, Map<BodyMuscle, Double>>()
        val loadOf: (WorkoutExerciseEntry) -> Map<BodyMuscle, Double> = { entry ->
            loads.getOrPut(entry.exerciseId to entry.exerciseTitle) {
                val def = byId[entry.exerciseId]
                    ?: entry.exerciseTitle.takeIf { it.isNotBlank() }?.let { byTitle[it.lowercase(Locale.ROOT)] }
                def?.let { BodyMuscle.loadPerSet(it) } ?: BodyMuscle.loadPerSet(entry.exerciseTitle)
            }
        }
        return ProfileDashboard(sessions, period, today, zone, firstDayOfWeek, bucketCount, loadOf).also {
            dashboardCache = it
            dashboardLibrary = library
        }
    }

    /** Muscles one set of a logged exercise counts for, on the heatmap and the muscle charts. */
    fun muscleLoadOf(entry: WorkoutExerciseEntry): Map<BodyMuscle, Double> =
        findExercise(entry.exerciseId, entry.exerciseTitle)?.let { BodyMuscle.loadPerSet(it) }
            ?: BodyMuscle.loadPerSet(entry.exerciseTitle)

    // --- Active Workout Layer & Rest Timer State ---
    var isWorkoutActive by mutableStateOf(false)
    var isWorkoutLayerOpen by mutableStateOf(false)
    var activeWorkoutTitle by mutableStateOf("Push Day A — Heavy Chest & Delts")
    var activeWorkoutNotes by mutableStateOf("Focusing on controlled eccentric and crisp lockout.")
    var workoutElapsedSeconds by mutableLongStateOf(0L)
    var isTimerPaused by mutableStateOf(false)
    var workoutBannerMessage by mutableStateOf<String?>(null)

    private var restRemainingState by mutableIntStateOf(0)
    /** When the running rest ends, on the SystemClock.elapsedRealtime() clock; 0 when no rest is running. */
    private var restEndsAtElapsedMs = 0L

    /**
     * Seconds of rest left. Setting it starts, changes or (with 0) stops the rest countdown and
     * re-schedules the "rest is over" alert to match.
     */
    var restTimerRemainingSeconds: Int
        get() = restRemainingState
        set(value) {
            restRemainingState = value.coerceAtLeast(0)
            syncRestAlert()
        }

    private fun syncRestAlert() {
        val remaining = restRemainingState
        restEndsAtElapsedMs = if (remaining > 0) android.os.SystemClock.elapsedRealtime() + remaining * 1000L else 0L
        val ctx = appContext ?: return
        if (remaining > 0 && isWorkoutActive && !isTimerPaused) {
            val next = currentWatchTarget()?.let { (exIdx, setIdx) ->
                "${activeExercises[exIdx].exerciseTitle} · ${describeSet(exIdx, setIdx)}"
            } ?: "Time for your next set."
            RestTimerAlerts.schedule(ctx, remaining, "Rest is over", next)
        } else {
            RestTimerAlerts.cancel(ctx)
        }
    }

    /** Called once a second while a workout is running and not paused. */
    fun onTimerTick() {
        workoutElapsedSeconds += 1L
        if (restEndsAtElapsedMs > 0L) {
            // Derived from the end time, so the countdown stays right after the app was in the background.
            val leftMs = restEndsAtElapsedMs - android.os.SystemClock.elapsedRealtime()
            restRemainingState = ((leftMs + 999L) / 1000L).toInt().coerceAtLeast(0)
            if (restRemainingState == 0) {
                restEndsAtElapsedMs = 0L
                publishWearState() // the workout notification switches from the countdown to the next set
            }
        }
    }

    // --- Notification settings ---
    var restAlertEnabled by mutableStateOf(true)
    /** null = system default notification sound, "" = silent, otherwise a ringtone URI. */
    var restAlertSoundUri by mutableStateOf<String?>(null)
    var restAlertVibrate by mutableStateOf(true)
    var restAlertSoundTitle by mutableStateOf("Default notification sound")
    /** Bumped to make the UI re-read the system's notification permission state. */
    var notificationStateVersion by mutableIntStateOf(0)

    fun updateRestAlertEnabled(enabled: Boolean) {
        restAlertEnabled = enabled
        saveSettingsToPrefs()
        syncRestAlert()
    }

    fun updateRestAlertSound(uri: String?) {
        restAlertSoundUri = uri
        restAlertSoundTitle = appContext?.let { RestTimerAlerts.soundTitle(it, uri) } ?: "Custom sound"
        saveSettingsToPrefs()
    }

    fun updateRestAlertVibrate(enabled: Boolean) {
        restAlertVibrate = enabled
        saveSettingsToPrefs()
    }

    /** Vibrate / play a sound on the watch when the rest is over. */
    var watchAlertVibrate by mutableStateOf(true)
    var watchAlertSound by mutableStateOf(true)
    /** Keep a notification with set and rest buttons up while a workout is running. */
    var workoutNotificationEnabled by mutableStateOf(true)

    fun updateWatchAlert(vibrate: Boolean = watchAlertVibrate, sound: Boolean = watchAlertSound) {
        watchAlertVibrate = vibrate
        watchAlertSound = sound
        saveSettingsToPrefs()
    }

    fun updateWorkoutNotificationEnabled(enabled: Boolean) {
        workoutNotificationEnabled = enabled
        saveSettingsToPrefs()
        publishWearState()
    }

    /** Plays the alert now so the user can check the sound; reports when notifications are blocked. */
    fun testRestAlert() {
        val ctx = appContext ?: return
        // Settings are written with apply(); the alert reads them back from the same in-memory prefs.
        settingsStatusMessage = if (RestTimerAlerts.show(ctx, "Rest is over", "Test alert from Lift Tracker.")) {
            "Test alert sent. If you heard nothing, check the phone's notification volume and Do Not Disturb."
        } else {
            "Notifications are turned off for Lift Tracker. Allow them to get rest timer alerts."
        }
    }
    var restTimerTargetSeconds by mutableIntStateOf(90)
    var restTimerVolumeSetting by mutableStateOf("normal") // "off", "low", "normal", "high"
    var restTimerVolumeFloat by mutableFloatStateOf(WorkoutMechanics.TIMER_VOLUME_NORMAL)

    // --- User Settings State ---
    var weightUnit by mutableStateOf(WeightUnit.KG)
    var weightIncrementKg by mutableDoubleStateOf(2.5)
    var weightIncrementLbs by mutableDoubleStateOf(5.0)
    var defaultRestTimerSeconds by mutableIntStateOf(90)
    var autoStartRestTimer by mutableStateOf(true)
    var showRpeColumn by mutableStateOf(true)
    var preferAnimatedExerciseGif by mutableStateOf(true)
    var keepScreenAwake by mutableStateOf(true)
    var themeConfig by mutableStateOf(ThemeConfig())
    var datasetTotalCount by mutableIntStateOf(SampleCatalog.defaultExercises.size)
    var isDatasetLoaded by mutableStateOf(false)
    var settingsStatusMessage by mutableStateOf<String?>(null)

    val activeWeightStepDisplayValue: Double
        get() = if (weightUnit == WeightUnit.KG) weightIncrementKg else weightIncrementLbs

    val activeWeightStepDeltaKg: Double
        get() = if (weightUnit == WeightUnit.KG) {
            weightIncrementKg
        } else {
            weightIncrementLbs / WeightUnit.LBS.factorFromKg
        }

    /** Latest heart rate streamed from the watch; 0 when none is being received. */
    var liveHeartRateBpm by mutableIntStateOf(0)
    private val activeHeartRateSamples = mutableListOf<HeartRateSamplePoint>()
    var activeExerciseIndex by mutableIntStateOf(0)
    var activeSetIndex by mutableIntStateOf(0)

    val activeExercises = mutableStateListOf<WorkoutExerciseEntry>()
    val workoutHistory = mutableStateListOf<WorkoutSession>()
    var isArchivedWorkoutsExpanded by mutableStateOf(false)
    var selectedWorkoutSessionId by mutableStateOf<String?>(null)
    var isWorkoutSessionDetailLayerOpen by mutableStateOf(false)

    // --- Exercise Catalog, Drawers & Detail Layer State ---
    val exercises = mutableStateListOf<ExerciseDefinition>()
    var selectedExerciseId by mutableStateOf("ex_bench_press")
    var isExerciseDetailLayerOpen by mutableStateOf(false)
    var selectedExerciseSubTab by mutableStateOf(ExerciseSubTab.SUMMARY)
    var exerciseSearchQuery by mutableStateOf("")
    var selectedMuscleFilter by mutableStateOf<MuscleGroup?>(null)
    var selectedEquipmentFilter by mutableStateOf<EquipmentType?>(null)
    /** Muscle group sections start folded. */
    val expandedMuscleDrawers = mutableStateListOf<MuscleGroup>()

    // --- Routines, Folder Drawers & Routine Editor Layer State ---
    val routines = mutableStateListOf<RoutineTemplate>()
    var selectedRoutineFolderFilter by mutableStateOf<String?>(null)
    val expandedRoutineFolders = mutableStateListOf<String>()
    val expandedRoutineDrawerIds = mutableStateListOf<String>()
    var isRoutineEditorLayerOpen by mutableStateOf(false)
    var editingRoutineId by mutableStateOf<String?>(null)
    var routineEditorTitle by mutableStateOf("")
    var routineEditorFolder by mutableStateOf("")
    var routineEditorNotes by mutableStateOf("")
    val routineEditorExercises = mutableStateListOf<WorkoutExerciseEntry>()
    var routinesStatusMessage by mutableStateOf<String?>(null)
    var isImportInProgress by mutableStateOf(false)

    /** The user's choice of how an exercise is tracked, by exercise id; wins over the name-based guess. */
    val exerciseMeasureOverrides = mutableStateMapOf<String, ExerciseMeasure>()
    /** The user's own exercises: created in the exercise editor, or by imports when nothing in the library matched. */
    private val customExercises = mutableListOf<ExerciseDefinition>()

    /** AI helpers that write into the app: exercise editor, routine optimizer and routine set-up. */
    val assist: AiAssistController by lazy { AiAssistController(this, viewModelScope) }

    /** How an exercise is tracked: weight & reps, bodyweight reps, duration, or weight & distance. */
    fun measureFor(exerciseId: String, title: String): ExerciseMeasure =
        exerciseMeasureOverrides[exerciseId]
            ?: ExerciseMeasure.infer(title, exercises.find { it.id == exerciseId }?.equipment)

    private fun defaultSetFor(measure: ExerciseMeasure, id: String, number: Int): WorkoutSetEntry = WorkoutSetEntry(
        id = id,
        setNumber = number,
        weightKg = when (measure) {
            ExerciseMeasure.WEIGHT_REPS -> 60.0
            ExerciseMeasure.WEIGHT_DISTANCE -> 20.0
            else -> 0.0
        },
        reps = if (measure.usesReps) 10 else 0,
        durationSeconds = if (measure == ExerciseMeasure.DURATION) 30 else 0,
        distanceMeters = if (measure == ExerciseMeasure.WEIGHT_DISTANCE) 20.0 else 0.0
    )

    /** Fills in the value a set needs when its exercise switches to another way of tracking. */
    private fun WorkoutSetEntry.fittedTo(measure: ExerciseMeasure): WorkoutSetEntry = copy(
        reps = if (measure.usesReps && reps <= 0) 10 else reps,
        durationSeconds = if (measure == ExerciseMeasure.DURATION && durationSeconds <= 0) 30 else durationSeconds,
        distanceMeters = if (measure == ExerciseMeasure.WEIGHT_DISTANCE && distanceMeters <= 0.0) 20.0 else distanceMeters
    )

    private fun setSummary(set: WorkoutSetEntry, measure: ExerciseMeasure): String =
        WorkoutMechanics.formatSetSummary(set, measure, weightUnit)

    // --- Exercise aliases ---
    /** alias id -> the exercise it stands for; kept current by [appendCustomExercises]. */
    private var exerciseAliases: Map<String, String> = emptyMap()

    /** The exercise [exerciseId] stands for (itself unless it is an alias). */
    fun canonicalExerciseId(exerciseId: String): String = ExerciseAliases.canonicalId(exerciseId, exerciseAliases)

    /** The library exercise an alias stands for, or null when [def] is not an alias. */
    fun aliasTargetOf(def: ExerciseDefinition): ExerciseDefinition? =
        canonicalExerciseId(def.id).takeIf { it != def.id }?.let { id -> exercises.find { it.id == id } }

    /** Other names of the same exercise: its aliases, and the exercise it is an alias of. */
    fun otherNamesOf(exerciseId: String): List<ExerciseDefinition> =
        ExerciseAliases.familyIds(exerciseId, exerciseAliases).filter { it != exerciseId }
            .mapNotNull { id -> exercises.find { it.id == id } }

    /** Exercises [editingId] (null for a new exercise) may become an alias of. */
    fun aliasCandidates(editingId: String?): List<ExerciseDefinition> =
        exercises.filter { ExerciseAliases.canAlias(editingId, it.id, exerciseAliases) }

    /** [exerciseId] with its aliases, counted as one exercise in history and records. */
    fun exerciseFamily(exerciseId: String, fallbackTitle: String = ""): ExerciseFamily {
        val ids = ExerciseAliases.familyIds(exerciseId, exerciseAliases)
        val titles = ids.mapNotNull { id -> exercises.find { it.id == id }?.title }
        return ExerciseFamily(ids, if (fallbackTitle.isBlank()) titles else titles + fallbackTitle)
    }

    /** Records, totals and progression of an exercise (aliases included) from the logged workouts. */
    fun exerciseStats(exerciseId: String): ExerciseStats {
        val canonical = canonicalExerciseId(exerciseId)
        val title = exercises.find { it.id == canonical }?.title ?: exercises.find { it.id == exerciseId }?.title.orEmpty()
        return ExerciseStats.compute(workoutHistory.toList(), exerciseFamily(exerciseId), measureFor(canonical, title))
    }

    /** The sets completed the last time this exercise (or an alias of it) was logged, or empty when it never was. */
    fun lastPerformedSets(exerciseId: String, exerciseTitle: String): List<WorkoutSetEntry> =
        WorkoutMechanics.lastPerformedSets(workoutHistory, exerciseFamily(exerciseId, exerciseTitle))

    /**
     * Starts each set from what was done last time (set by set), so a routine progresses with the
     * lifter instead of resetting to the numbers saved in it. Sets with no history stay as they are.
     */
    private fun List<WorkoutSetEntry>.startingFromLastTime(
        exerciseId: String,
        exerciseTitle: String,
        measure: ExerciseMeasure
    ): List<WorkoutSetEntry> {
        val last = lastPerformedSets(exerciseId, exerciseTitle)
        if (last.isEmpty()) return this
        return WorkoutMechanics.carryOverLastSets(this, last).map { (set, from) ->
            if (from == null) set else set.fittedTo(measure).copy(previousText = setSummary(from, measure))
        }
    }

    // --- Health Connect State ---
    var healthConnectSdkStatus by mutableIntStateOf(WorkoutMechanics.HEALTH_CONNECT_SDK_UNAVAILABLE)
    var autoSyncWorkoutsToHealthConnect by mutableStateOf(true)
    val healthPermissions = mutableStateListOf<HealthConnectPermissionItem>()
    val bodyMeasurements = mutableStateListOf<BodyMeasurementRecord>()
    val healthExportLogs = mutableStateListOf<HealthConnectExportLog>()
    var weightChangeToken by mutableStateOf<String?>(null)
    var bodyFatChangeToken by mutableStateOf<String?>(null)
    var healthStatusMessage by mutableStateOf<String?>(null)
    var autoSyncMeasurementsFromHealthConnect by mutableStateOf(true)
    var isMeasurementSyncInProgress by mutableStateOf(false)
    var lastMeasurementSyncLabel by mutableStateOf<String?>(null)

    val latestWeightKg: Double?
        get() = bodyMeasurements.firstOrNull { it.unit == "kg" }?.value

    val latestBodyFatPercent: Double?
        get() = bodyMeasurements.firstOrNull { it.unit == "%" }?.value

    val canReadMeasurementsFromHealthConnect: Boolean
        get() = isHealthConnectAvailable &&
            healthPermissions.any { (it.key == "read_weight" || it.key == "read_bodyFat") && it.granted }
    val healthSyncingSessionIds = mutableStateListOf<String>()

    val isHealthConnectAvailable: Boolean
        get() = healthConnectSdkStatus == WorkoutMechanics.HEALTH_CONNECT_SDK_AVAILABLE

    /** Permissions to pass to Health Connect's permission request contract. */
    val healthPermissionsToRequest: Set<String>
        get() = HealthConnectManager.ALL_PERMISSIONS

    val canWriteWorkoutsToHealthConnect: Boolean
        get() = isHealthConnectAvailable &&
            healthPermissions.any { it.key == "write_workout" && it.granted }

    // --- Wear OS Companion State ---
    var wearWatchStatus by mutableStateOf(
        WearWatchStatus(
            hasConnectedWatch = false,
            hasWatchAppInstalled = false,
            isWatchWithAppReachable = false
        )
    )
    val wearMessageLogs = mutableStateListOf<WearMessageLog>()
    var wearStatusMessage by mutableStateOf<String?>(null)

    init {
        exercises.addAll(SampleCatalog.defaultExercises)
        datasetTotalCount = exercises.size
        routines.addAll(SampleCatalog.defaultRoutines)
        workoutHistory.addAll(SampleCatalog.defaultWorkouts)
        // Grants are unknown until Health Connect is queried in initializeWithContext().
        healthPermissions.addAll(SampleCatalog.defaultHealthConnectPermissions.map { it.copy(granted = false) })

        seedDefaultActiveExercises()
    }

    private fun seedDefaultActiveExercises() {
        activeExercises.clear()
        activeExercises.addAll(
            listOf(
                WorkoutExerciseEntry(
                    id = "act_ex_1",
                    exerciseId = "ex_bench_press",
                    exerciseTitle = "Barbell Bench Press",
                    notes = "Competition pause on chest",
                    restTimerSeconds = 120,
                    sets = listOf(
                        WorkoutSetEntry("act_s1", 1, SetType.WARMUP, "60kg × 10", 60.0, 10, null, false),
                        WorkoutSetEntry("act_s2", 2, SetType.NORMAL, "100kg × 6", 102.5, 6, null, false),
                        WorkoutSetEntry("act_s3", 3, SetType.NORMAL, "102.5kg × 6", 105.0, 6, null, false),
                        WorkoutSetEntry("act_s4", 4, SetType.FAILURE, "102.5kg × 5", 105.0, 5, null, false)
                    )
                ),
                WorkoutExerciseEntry(
                    id = "act_ex_2",
                    exerciseId = "ex_overhead_press",
                    exerciseTitle = "Barbell Seated Overhead Press",
                    notes = "Strict seated press",
                    restTimerSeconds = 90,
                    sets = listOf(
                        WorkoutSetEntry("act_s5", 1, SetType.NORMAL, "60kg × 8", 62.5, 8, null, false),
                        WorkoutSetEntry("act_s6", 2, SetType.NORMAL, "62.5kg × 8", 62.5, 8, null, false),
                        WorkoutSetEntry("act_s7", 3, SetType.NORMAL, "62.5kg × 7", 62.5, 8, null, false)
                    )
                ),
                WorkoutExerciseEntry(
                    id = "act_ex_3",
                    exerciseId = "ex_incline_db_press",
                    exerciseTitle = "Dumbbell Incline Bench Press",
                    notes = "45° bench angle, deep stretch",
                    restTimerSeconds = 90,
                    sets = listOf(
                        WorkoutSetEntry("act_s8", 1, SetType.NORMAL, "34kg × 10", 36.0, 10, null, false),
                        WorkoutSetEntry("act_s9", 2, SetType.DROPSET, "34kg × 10", 30.0, 12, null, false)
                    )
                )
            )
        )
    }

    fun initializeWithContext(context: Context) {
        if (hasInitializedContext) return
        hasInitializedContext = true
        val appCtx = context.applicationContext
        appContext = appCtx
        val sp = appCtx.getSharedPreferences("lifttracker_settings", Context.MODE_PRIVATE)
        prefs = sp
        val storage = SQLiteWorkoutStorage(appCtx)
        workoutStorage = storage
        loadSettingsFromPrefs(sp)
        runCatching { ai.attach(AiStore(appCtx)) }
        runCatching { refreshRemoteBackupState() }
        loadExercisesDatasetAsync(appCtx)
        loadWorkoutHistoryFromSqliteAsync(storage, sp)

        healthConnect = runCatching { HealthConnectManager(appCtx) }.getOrNull()
        refreshHealthConnectState()

        wearConnector = WearConnector(appCtx)
        viewModelScope.launch {
            WearEventBus.messages.collect { handleWatchMessage(it) }
        }
        refreshWatchStatus()
        publishWearState()
        publishWearRoutines()
    }

    private fun updateWorkoutSession(sessionId: String, transform: (WorkoutSession) -> WorkoutSession): WorkoutSession? {
        val idx = workoutHistory.indexOfFirst { it.id == sessionId }
        if (idx < 0) return null
        val updated = transform(workoutHistory[idx])
        workoutHistory[idx] = updated
        persistWorkoutSessionToDb(updated)
        return updated
    }

    private companion object {
        const val MAX_STORED_MEASUREMENTS = 200
    }

    private fun timestampLabel(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private fun loadWorkoutHistoryFromSqliteAsync(storage: SQLiteWorkoutStorage, sp: SharedPreferences) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val existing = storage.loadWorkoutSessions()
                    if (existing.isEmpty() && !sp.getBoolean("seeded_default_workouts", false)) {
                        storage.saveWorkoutSessions(SampleCatalog.defaultWorkouts)
                        sp.edit().putBoolean("seeded_default_workouts", true).apply()
                        SampleCatalog.defaultWorkouts
                    } else {
                        if (existing.isNotEmpty() && !sp.getBoolean("seeded_default_workouts", false)) {
                            sp.edit().putBoolean("seeded_default_workouts", true).apply()
                        }
                        existing
                    }
                }.getOrNull()
            }
            if (loaded != null && sp.getBoolean("seeded_default_workouts", false)) {
                workoutHistory.clear()
                workoutHistory.addAll(loaded)
            }
        }
    }

    private fun persistWorkoutSessionToDb(session: WorkoutSession) {
        val storage = workoutStorage ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { storage.saveWorkoutSessions(listOf(session)) }
        }
    }

    private fun deleteWorkoutSessionFromDb(sessionId: String) {
        val storage = workoutStorage ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { storage.deleteWorkouts(listOf(sessionId)) }
        }
    }

    private fun clearWorkoutHistoryFromDb() {
        val storage = workoutStorage ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { storage.clearWorkouts() }
        }
    }

    private fun loadSettingsFromPrefs(sp: SharedPreferences) {
        val unitName = sp.getString("weight_unit", WeightUnit.KG.name)
        weightUnit = WeightUnit.entries.find { it.name == unitName } ?: WeightUnit.KG
        weightIncrementKg = sp.getFloat("weight_increment_kg", 2.5f).toDouble()
        weightIncrementLbs = sp.getFloat("weight_increment_lbs", 5.0f).toDouble()
        defaultRestTimerSeconds = sp.getInt("default_rest_timer_seconds", 90)
        autoStartRestTimer = sp.getBoolean("auto_start_rest_timer", true)
        showRpeColumn = sp.getBoolean("show_rpe_column", true)
        restTimerVolumeSetting = sp.getString("rest_timer_volume", "normal") ?: "normal"
        restAlertEnabled = sp.getBoolean(RestTimerAlerts.KEY_ENABLED, true)
        restAlertSoundUri = sp.getString(RestTimerAlerts.KEY_SOUND_URI, null)
        restAlertVibrate = sp.getBoolean(RestTimerAlerts.KEY_VIBRATE, true)
        watchAlertVibrate = sp.getBoolean(RestTimerAlerts.KEY_WATCH_VIBRATE, true)
        watchAlertSound = sp.getBoolean(RestTimerAlerts.KEY_WATCH_SOUND, true)
        workoutNotificationEnabled = sp.getBoolean(RestTimerAlerts.KEY_ONGOING, true)
        restAlertSoundTitle = appContext?.let { RestTimerAlerts.soundTitle(it, restAlertSoundUri) } ?: restAlertSoundTitle
        restTimerVolumeFloat = sp.getFloat(
            "rest_timer_volume_float",
            WorkoutMechanics.timerVolumeFromSetting(restTimerVolumeSetting)
        ).coerceIn(0f, 1f)
        preferAnimatedExerciseGif = sp.getBoolean("prefer_animated_gif", true)
        keepScreenAwake = sp.getBoolean("keep_screen_awake", true)
        isUserExercisesExpanded = sp.getBoolean("user_exercises_expanded", true)
        themeConfig = ThemeConfig.fromPrefs(sp)
        autoSyncWorkoutsToHealthConnect = sp.getBoolean("hc_auto_sync", true)
        autoSyncMeasurementsFromHealthConnect = sp.getBoolean("hc_auto_import_measurements", true)
        lastMeasurementSyncLabel = sp.getString("hc_last_measurement_sync", null)
        bodyMeasurements.clear()
        bodyMeasurements.addAll(parseStoredMeasurements(sp.getString("body_measurements", null)))

        exerciseMeasureOverrides.clear()
        runCatching {
            val obj = JSONObject(sp.getString("exercise_measures", null) ?: "{}")
            obj.keys().forEach { id -> ExerciseMeasure.fromCode(obj.optString(id))?.let { exerciseMeasureOverrides[id] = it } }
        }
        customExercises.clear()
        customExercises.addAll(CustomExerciseCodec.decode(sp.getString(BackupManager.CUSTOM_EXERCISES_KEY, null)))
        // Older versions (and older backups) only stored the titles of imported exercises.
        runCatching {
            val arr = JSONArray(sp.getString("custom_exercise_titles", null) ?: "[]")
            for (i in 0 until arr.length()) {
                val title = arr.optString(i).takeIf { it.isNotBlank() } ?: continue
                val id = ExerciseMatching.customId(title)
                if (customExercises.none { it.id == id }) customExercises.add(ExerciseMatching.customDefinition(title))
            }
        }
        appendCustomExercises()
        sp.getString("routines_json", null)?.let { stored ->
            val loaded = runCatching {
                if ((JSONObject(stored).optJSONArray("routines")?.length() ?: 0) == 0) {
                    emptyList()
                } else {
                    // Stored routines already carry this app's exercise ids and tracking types.
                    RoutineCodec.parseRoutines(stored, { id, title -> ResolvedExercise(id, ExerciseMeasure.infer(title)) })
                }
            }.getOrNull()
            if (loaded != null) {
                routines.clear()
                routines.addAll(loaded)
            }
        }
    }

    /** Makes the custom part of [exercises] match [customExercises]. */
    private fun appendCustomExercises() {
        exercises.removeAll { it.isCustom }
        val known = exercises.map { it.id }.toHashSet()
        exercises.addAll(customExercises.filter { known.add(it.id) })
        exerciseAliases = ExerciseAliases.aliasMap(customExercises)
    }

    var isUserExercisesExpanded by mutableStateOf(true)
        private set

    /** Folds or unfolds "Your exercises" in Settings → Exercise Library. */
    fun toggleUserExercisesExpanded() {
        isUserExercisesExpanded = !isUserExercisesExpanded
        prefs?.edit()?.putBoolean("user_exercises_expanded", isUserExercisesExpanded)?.apply()
    }

    private fun customImageDir(ctx: Context) = File(ctx.filesDir, CustomExerciseCodec.IMAGE_DIR)

    /**
     * Adds a custom exercise, or replaces the one with the same id. [newImage] (JPEG bytes) becomes
     * its picture; [removeImage] drops the current one. Returns the saved definition.
     */
    fun saveCustomExercise(
        def: ExerciseDefinition,
        measure: ExerciseMeasure,
        newImage: ByteArray? = null,
        removeImage: Boolean = false
    ): ExerciseDefinition {
        val previous = customExercises.find { it.id == def.id }
        var imagePath = previous?.imagePath.orEmpty()
        val ctx = appContext
        if (ctx != null && (newImage != null || removeImage)) {
            val dir = customImageDir(ctx)
            previous?.let { CustomExerciseCodec.imageFileName(it) }?.let { File(dir, it).delete() }
            imagePath = ""
            if (newImage != null) {
                // A new file name per picture, so cached bitmaps of the old one are never shown.
                val name = "${def.id}_${System.currentTimeMillis()}.jpg"
                val written = runCatching { dir.mkdirs(); File(dir, name).writeBytes(newImage) }.isSuccess
                if (written) imagePath = CustomExerciseCodec.IMAGE_PREFIX + name
            }
        }
        val saved = def.copy(imagePath = imagePath, gifPath = "", datasetId = "", isCustom = true)
        val idx = customExercises.indexOfFirst { it.id == saved.id }
        if (idx >= 0) customExercises[idx] = saved else customExercises.add(saved)
        exerciseMeasureOverrides[saved.id] = measure
        appendCustomExercises()
        persistExerciseCustomisation()
        // Routines and a running workout show the exercise's name; keep it current after a rename.
        if (previous != null && previous.title != saved.title) renameExerciseEverywhere(saved.id, saved.title)
        return saved
    }

    private fun renameExerciseEverywhere(exerciseId: String, title: String) {
        var routinesChanged = false
        routines.forEachIndexed { i, r ->
            if (r.exercises.any { it.exerciseId == exerciseId && it.exerciseTitle != title }) {
                routines[i] = r.copy(exercises = r.exercises.map { if (it.exerciseId == exerciseId) it.copy(exerciseTitle = title) else it })
                routinesChanged = true
            }
        }
        if (routinesChanged) onRoutinesChanged()
        activeExercises.forEachIndexed { i, e -> if (e.exerciseId == exerciseId) activeExercises[i] = e.copy(exerciseTitle = title) }
    }

    /** Removes a custom exercise from the library. Logged workouts and routines keep their entries of it. */
    fun deleteCustomExercise(exerciseId: String) {
        val def = customExercises.find { it.id == exerciseId } ?: return
        customExercises.remove(def)
        appContext?.let { ctx -> CustomExerciseCodec.imageFileName(def)?.let { File(customImageDir(ctx), it).delete() } }
        exerciseMeasureOverrides.remove(exerciseId)
        if (selectedExerciseId == exerciseId) {
            isExerciseDetailLayerOpen = false
            selectedExerciseId = exercises.firstOrNull { !it.isCustom }?.id ?: selectedExerciseId
        }
        appendCustomExercises()
        persistExerciseCustomisation()
    }

    /** An id for a new custom exercise called [title] that no exercise in the library has yet. */
    fun newCustomExerciseId(title: String): String {
        val base = ExerciseMatching.customId(title).ifBlank { "custom_exercise" }
        var id = base
        var n = 2
        while (exercises.any { it.id == id }) id = "${base}_${n++}"
        return id
    }

    private fun persistExerciseCustomisation() {
        val measures = JSONObject()
        exerciseMeasureOverrides.forEach { (id, measure) -> measures.put(id, measure.code) }
        prefs?.edit()
            ?.putString("exercise_measures", measures.toString())
            ?.putString("custom_exercise_titles", JSONArray(customExercises.map { it.title }).toString())
            ?.putString(BackupManager.CUSTOM_EXERCISES_KEY, CustomExerciseCodec.encode(customExercises))
            ?.apply()
    }

    /** Saves routines and tells the watch; call after every change to [routines]. */
    private fun onRoutinesChanged() {
        prefs?.edit()?.putString("routines_json", RoutineCodec.exportRoutines(routines.toList()))?.apply()
        publishWearRoutines()
    }

    private fun saveSettingsToPrefs() {
        prefs?.edit()?.apply {
            putString("weight_unit", weightUnit.name)
            putFloat("weight_increment_kg", weightIncrementKg.toFloat())
            putFloat("weight_increment_lbs", weightIncrementLbs.toFloat())
            putInt("default_rest_timer_seconds", defaultRestTimerSeconds)
            putBoolean("auto_start_rest_timer", autoStartRestTimer)
            putBoolean("show_rpe_column", showRpeColumn)
            putString("rest_timer_volume", restTimerVolumeSetting)
            putFloat("rest_timer_volume_float", restTimerVolumeFloat)
            putBoolean(RestTimerAlerts.KEY_ENABLED, restAlertEnabled)
            if (restAlertSoundUri == null) remove(RestTimerAlerts.KEY_SOUND_URI) else putString(RestTimerAlerts.KEY_SOUND_URI, restAlertSoundUri)
            putBoolean(RestTimerAlerts.KEY_VIBRATE, restAlertVibrate)
            putBoolean(RestTimerAlerts.KEY_WATCH_VIBRATE, watchAlertVibrate)
            putBoolean(RestTimerAlerts.KEY_WATCH_SOUND, watchAlertSound)
            putBoolean(RestTimerAlerts.KEY_ONGOING, workoutNotificationEnabled)
            putBoolean("prefer_animated_gif", preferAnimatedExerciseGif)
            putBoolean("keep_screen_awake", keepScreenAwake)
            ThemeConfig.saveToPrefs(this, themeConfig)
            putBoolean("hc_auto_sync", autoSyncWorkoutsToHealthConnect)
            putBoolean("hc_auto_import_measurements", autoSyncMeasurementsFromHealthConnect)
            apply()
        }
    }

    fun reloadDatasetFromAssets() {
        val ctx = appContext ?: return
        loadExercisesDatasetAsync(ctx)
        settingsStatusMessage = "Reloaded exercises from exercises-dataset."
    }

    fun clearMediaCache() {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = File(ctx.cacheDir, "dataset_media")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
            withContext(Dispatchers.Main) {
                settingsStatusMessage = "Cleared cached dataset images & GIFs."
            }
        }
    }

    private fun loadExercisesDatasetAsync(ctx: Context) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val jsonText = ctx.assets.open(WorkoutMechanics.DATASET_ASSET_JSON)
                        .bufferedReader()
                        .use { it.readText() }
                    parseExercisesDatasetJson(jsonText)
                }.getOrNull()
            }
            if (!loaded.isNullOrEmpty()) {
                exercises.clear()
                exercises.addAll(loaded)
                appendCustomExercises()
                datasetTotalCount = loaded.size
                isDatasetLoaded = true
                if (exercises.none { it.id == selectedExerciseId }) {
                    selectedExerciseId = exercises.first().id
                }
            }
        }
    }

    private fun parseExercisesDatasetJson(jsonText: String): List<ExerciseDefinition> {
        val arr = JSONArray(jsonText)
        val defaultsById = SampleCatalog.defaultExercises.associateBy { it.id }
        val canonicalByDatasetId = SampleCatalog.canonicalDatasetIds
        val coreList = mutableListOf<ExerciseDefinition>()
        val otherList = mutableListOf<ExerciseDefinition>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val dsId = obj.optString("id")
            val name = obj.optString("name")
            val bodyPart = obj.optString("body_part")
            val equipmentStr = obj.optString("equipment")
            val target = obj.optString("target")
            val muscleGroupStr = obj.optString("muscle_group")
            val imagePath = obj.optString("image")
            val gifPath = obj.optString("gif_url")

            val secArray = obj.optJSONArray("secondary_muscles")
            val secondaryList = mutableListOf<String>()
            val secondaryGroups = mutableSetOf<MuscleGroup>()
            if (secArray != null) {
                for (j in 0 until secArray.length()) {
                    val secName = secArray.optString(j)
                    if (secName.isNotBlank()) {
                        secondaryList.add(secName)
                        secondaryGroups.add(WorkoutMechanics.mapBodyPartToMuscleGroup("", secName))
                    }
                }
            }

            val instArray = obj.optJSONArray("instructions")
            val instructionsList = mutableListOf<String>()
            if (instArray != null) {
                for (j in 0 until instArray.length()) {
                    val step = instArray.optString(j)
                    if (step.isNotBlank()) instructionsList.add(step)
                }
            }

            val canonicalId = canonicalByDatasetId[dsId]
            val appId = canonicalId ?: "ds_$dsId"
            val curatedDefault = defaultsById[appId]
            val primaryMuscle = WorkoutMechanics.mapBodyPartToMuscleGroup(bodyPart, target)
            val equipment = WorkoutMechanics.mapEquipmentString(equipmentStr)

            val secSummary = if (secondaryList.isNotEmpty()) secondaryList.joinToString(", ") else muscleGroupStr
            val summary = "Dataset #$dsId • Body part: $bodyPart • Target: $target" +
                (if (secSummary.isNotBlank()) " • Secondary: $secSummary" else "") +
                " • Equipment: $equipmentStr."

            val cues = curatedDefault?.coachingCues ?: listOf(
                "Target muscle focus: $target ($bodyPart) using $equipmentStr.",
                "Perform controlled eccentric and full-range repetitions."
            )

            val def = ExerciseDefinition(
                id = appId,
                datasetId = dsId,
                title = name,
                primaryMuscle = primaryMuscle,
                secondaryMuscles = secondaryGroups.toList(),
                equipment = equipment,
                targetMuscle = target,
                bodyPart = bodyPart,
                imagePath = imagePath,
                gifPath = gifPath,
                summaryNotes = summary,
                instructions = instructionsList.ifEmpty { curatedDefault?.instructions ?: emptyList() },
                coachingCues = cues,
                secondaryTargets = secondaryList
            )

            if (canonicalId != null || curatedDefault != null) {
                coreList.add(def)
            } else {
                otherList.add(def)
            }
        }

        val coreOrder = SampleCatalog.defaultExercises.map { it.id }
        val sortedCore = coreList.sortedBy { def ->
            val idx = coreOrder.indexOf(def.id)
            if (idx >= 0) idx else Int.MAX_VALUE
        }
        return sortedCore + otherList
    }

    fun findExercise(exerciseId: String, fallbackTitle: String = ""): ExerciseDefinition? {
        return exercises.find { it.id == exerciseId }
            ?: if (fallbackTitle.isNotBlank()) {
                exercises.find { it.title.equals(fallbackTitle, ignoreCase = true) }
            } else {
                null
            }
    }

    // --- Settings Actions ---
    fun updateWeightUnit(unit: WeightUnit) {
        weightUnit = unit
        saveSettingsToPrefs()
    }

    fun updateWeightStepInCurrentUnit(stepValue: Double) {
        if (weightUnit == WeightUnit.KG) {
            weightIncrementKg = stepValue
        } else {
            weightIncrementLbs = stepValue
        }
        saveSettingsToPrefs()
    }

    fun updateWeightIncrementKg(incrementKg: Double) {
        weightIncrementKg = incrementKg
        saveSettingsToPrefs()
    }

    fun updateDefaultRestTimer(seconds: Int) {
        defaultRestTimerSeconds = seconds.coerceIn(0, 3600)
        saveSettingsToPrefs()
    }

    fun updateAutoStartRestTimer(enabled: Boolean) {
        autoStartRestTimer = enabled
        saveSettingsToPrefs()
    }

    fun updateShowRpeColumn(enabled: Boolean) {
        showRpeColumn = enabled
        saveSettingsToPrefs()
    }

    fun updateRestTimerVolume(setting: String) {
        restTimerVolumeSetting = setting
        restTimerVolumeFloat = WorkoutMechanics.timerVolumeFromSetting(setting)
        saveSettingsToPrefs()
    }

    fun updateRestTimerVolumeFloat(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        restTimerVolumeFloat = clamped
        restTimerVolumeSetting = when {
            clamped <= 0.02f -> "off"
            clamped <= 0.35f -> "low"
            clamped <= 0.80f -> "normal"
            else -> "high"
        }
        saveSettingsToPrefs()
    }

    fun updatePreferAnimatedGif(enabled: Boolean) {
        preferAnimatedExerciseGif = enabled
        saveSettingsToPrefs()
    }

    fun updateKeepScreenAwake(enabled: Boolean) {
        keepScreenAwake = enabled
        saveSettingsToPrefs()
    }

    fun updateThemeConfig(newConfig: ThemeConfig) {
        themeConfig = newConfig
        saveSettingsToPrefs()
    }

    fun updateAutoSyncHealthConnect(enabled: Boolean) {
        autoSyncWorkoutsToHealthConnect = enabled
        saveSettingsToPrefs()
    }

    fun updateAutoSyncMeasurements(enabled: Boolean) {
        autoSyncMeasurementsFromHealthConnect = enabled
        saveSettingsToPrefs()
        if (enabled) syncMeasurementsFromHealthConnect(announce = false)
    }

    fun clearWorkoutHistory() {
        workoutHistory.clear()
        clearWorkoutHistoryFromDb()
        ai.clearAnalyses()
        if (isWorkoutSessionDetailLayerOpen) {
            isWorkoutSessionDetailLayerOpen = false
        }
        settingsStatusMessage = "Cleared logged workout history."
    }

    fun resetAppDefaults() {
        routines.clear()
        routines.addAll(SampleCatalog.defaultRoutines)
        workoutHistory.clear()
        workoutHistory.addAll(SampleCatalog.defaultWorkouts)
        workoutStorage?.let { storage ->
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    storage.clearWorkouts()
                    storage.saveWorkoutSessions(SampleCatalog.defaultWorkouts)
                }
            }
        }
        onRoutinesChanged()
        seedDefaultActiveExercises()
        ai.clearAnalyses()
        healthExportLogs.clear()
        wearMessageLogs.clear()
        activeHeartRateSamples.clear()

        weightUnit = WeightUnit.KG
        weightIncrementKg = 2.5
        weightIncrementLbs = 5.0
        defaultRestTimerSeconds = 90
        autoStartRestTimer = true
        showRpeColumn = true
        restTimerVolumeSetting = "normal"
        restTimerVolumeFloat = WorkoutMechanics.TIMER_VOLUME_NORMAL
        preferAnimatedExerciseGif = true
        keepScreenAwake = true
        themeConfig = ThemeConfig()
        autoSyncWorkoutsToHealthConnect = true
        autoSyncMeasurementsFromHealthConnect = true
        isWorkoutActive = false
        isWorkoutLayerOpen = false
        isExerciseDetailLayerOpen = false
        isRoutineEditorLayerOpen = false
        routineEditorInitialSnapshot = null
        workoutElapsedSeconds = 0L
        restTimerRemainingSeconds = 0
        saveSettingsToPrefs()
        publishWearState()
        settingsStatusMessage = "Reset workouts, routines, and preferences to defaults."
    }

    // --- Exercise Drawers & Detail Layer ---
    fun toggleMuscleDrawer(group: MuscleGroup) {
        if (expandedMuscleDrawers.contains(group)) {
            expandedMuscleDrawers.remove(group)
        } else {
            expandedMuscleDrawers.add(group)
        }
    }

    /** True while the exercise detail was opened from the running workout, so closing it returns there. */
    private var returnToWorkoutAfterExerciseDetail = false

    fun openExerciseDetailLayer(exerciseId: String) {
        selectedExerciseId = exerciseId
        if (!isExerciseDetailLayerOpen) {
            returnToWorkoutAfterExerciseDetail = isWorkoutActive && isWorkoutLayerOpen
        }
        isWorkoutLayerOpen = false
        isExerciseDetailLayerOpen = true
    }

    /** Closes the exercise detail and goes back to the page it was opened from. */
    fun closeExerciseDetailLayer() {
        isExerciseDetailLayerOpen = false
        if (returnToWorkoutAfterExerciseDetail && isWorkoutActive) isWorkoutLayerOpen = true
        returnToWorkoutAfterExerciseDetail = false
    }

    // --- Workout Session Detail Layer ---
    fun openWorkoutSessionDetailLayer(sessionId: String) {
        selectedWorkoutSessionId = sessionId
        isWorkoutSessionDetailLayerOpen = true
    }

    fun closeWorkoutSessionDetailLayer() {
        isWorkoutSessionDetailLayerOpen = false
    }

    // --- Routine Folder Drawers, Routine Drawers & Routine Editor Layer ---
    fun toggleRoutineDrawer(routineId: String) {
        if (expandedRoutineDrawerIds.contains(routineId)) {
            expandedRoutineDrawerIds.remove(routineId)
        } else {
            expandedRoutineDrawerIds.add(routineId)
        }
    }

    fun toggleRoutineFolderDrawer(folderName: String) {
        if (expandedRoutineFolders.contains(folderName)) {
            expandedRoutineFolders.remove(folderName)
        } else {
            expandedRoutineFolders.add(folderName)
        }
    }

    private data class RoutineDraftSetSnapshot(
        val setType: SetType,
        val repType: RepType,
        val weightKg: Double,
        val reps: Int,
        val durationSeconds: Int,
        val distanceMeters: Double
    )

    private data class RoutineDraftExerciseSnapshot(
        val exerciseId: String,
        val exerciseTitle: String,
        val restTimerSeconds: Int,
        val measure: ExerciseMeasure,
        val sets: List<RoutineDraftSetSnapshot>
    )

    private data class RoutineDraftSnapshot(
        val title: String,
        val folder: String,
        val notes: String,
        val exercises: List<RoutineDraftExerciseSnapshot>
    )

    private var routineEditorInitialSnapshot: RoutineDraftSnapshot? = null

    private fun currentRoutineDraftSnapshot(): RoutineDraftSnapshot = RoutineDraftSnapshot(
        title = routineEditorTitle.trim(),
        folder = routineEditorFolder.trim(),
        notes = routineEditorNotes.trim(),
        exercises = routineEditorExercises.map { ex ->
            RoutineDraftExerciseSnapshot(
                exerciseId = ex.exerciseId,
                exerciseTitle = ex.exerciseTitle,
                restTimerSeconds = ex.restTimerSeconds,
                measure = ex.measure,
                sets = ex.sets.map { s ->
                    RoutineDraftSetSnapshot(
                        setType = s.setType,
                        repType = s.repType,
                        weightKg = s.weightKg,
                        reps = s.reps,
                        durationSeconds = s.durationSeconds,
                        distanceMeters = s.distanceMeters
                    )
                }
            )
        }
    )

    fun hasUnsavedRoutineDraftChanges(): Boolean =
        isRoutineEditorLayerOpen &&
            routineEditorInitialSnapshot != null &&
            currentRoutineDraftSnapshot() != routineEditorInitialSnapshot

    fun openNewRoutineEditor(initialName: String = "") {
        editingRoutineId = null
        routineEditorTitle = initialName
        routineEditorFolder = ""
        routineEditorNotes = ""
        routineEditorExercises.clear()
        routineEditorInitialSnapshot = currentRoutineDraftSnapshot()
        isRoutineEditorLayerOpen = true
    }

    fun openEditRoutineEditor(routine: RoutineTemplate) {
        editingRoutineId = routine.id
        routineEditorTitle = routine.title
        routineEditorFolder = routine.folderName
        routineEditorNotes = routine.notes
        routineEditorExercises.clear()
        routine.exercises.forEachIndexed { idx, rEx ->
            val sets = if (rEx.sets.isNotEmpty()) {
                rEx.sets.mapIndexed { sIdx, s ->
                    s.copy(
                        id = "rt_edit_s_${idx}_${sIdx + 1}",
                        setNumber = sIdx + 1,
                        completed = false
                    )
                }
            } else {
                (1..rEx.targetSets.coerceAtLeast(1)).map { sNum ->
                    WorkoutSetEntry(
                        id = "rt_edit_s_${idx}_$sNum",
                        setNumber = sNum,
                        setType = SetType.NORMAL,
                        repType = rEx.repType,
                        previousText = "—",
                        weightKg = rEx.targetWeightKg,
                        reps = rEx.targetReps,
                        rpe = null,
                        completed = false
                    )
                }
            }
            routineEditorExercises.add(
                WorkoutExerciseEntry(
                    id = "rt_edit_ex_${idx}_${System.currentTimeMillis()}",
                    exerciseId = rEx.exerciseId,
                    exerciseTitle = rEx.exerciseTitle,
                    notes = "",
                    restTimerSeconds = rEx.restSeconds,
                    sets = sets,
                    measure = rEx.measure
                )
            )
        }
        routineEditorInitialSnapshot = currentRoutineDraftSnapshot()
        isRoutineEditorLayerOpen = true
    }

    fun requestCloseRoutineEditorLayer() {
        if (hasUnsavedRoutineDraftChanges()) {
            requestConfirmation(
                title = "Discard unsaved changes?",
                message = "You have unsaved changes to this routine. Quit without saving?",
                confirmLabel = "Quit without saving"
            ) {
                closeRoutineEditorLayer()
            }
        } else {
            closeRoutineEditorLayer()
        }
    }

    fun closeRoutineEditorLayer() {
        editingRoutineId = null
        routineEditorInitialSnapshot = null
        isRoutineEditorLayerOpen = false
    }

    fun addExerciseToRoutineDraft(exerciseDef: ExerciseDefinition) {
        val idx = routineEditorExercises.size + 1
        val now = System.currentTimeMillis()
        val measure = measureFor(exerciseDef.id, exerciseDef.title)
        routineEditorExercises.add(
            WorkoutExerciseEntry(
                id = "rt_draft_ex_${now}_$idx",
                exerciseId = exerciseDef.id,
                exerciseTitle = exerciseDef.title,
                notes = "",
                restTimerSeconds = defaultRestTimerSeconds,
                sets = (1..3).map { defaultSetFor(measure, "rt_draft_s_${now}_$it", it) },
                measure = measure
            )
        )
    }

    fun removeExerciseFromRoutineDraft(exerciseIndex: Int) {
        if (exerciseIndex in routineEditorExercises.indices) {
            routineEditorExercises.removeAt(exerciseIndex)
        }
    }

    fun addSetToRoutineDraftExercise(exerciseIndex: Int) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        val nextNum = ex.sets.size + 1
        val id = "rt_draft_s_${System.currentTimeMillis()}_$nextNum"
        val newSet = ex.sets.lastOrNull()?.copy(id = id, setNumber = nextNum, setType = SetType.NORMAL, rpe = null, completed = false)
            ?: defaultSetFor(ex.measure, id, nextNum)
        routineEditorExercises[exerciseIndex] = ex.copy(sets = ex.sets + newSet)
    }

    /** Edits one set of a routine being edited (duration, distance, …). */
    fun updateRoutineDraftSet(exerciseIndex: Int, setIndex: Int, transform: (WorkoutSetEntry) -> WorkoutSetEntry) {
        val ex = routineEditorExercises.getOrNull(exerciseIndex) ?: return
        if (setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = transform(ex.sets[setIndex])
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    /** Rest after each set of this exercise; 0 turns the timer off for it. */
    fun setRoutineDraftExerciseRest(exerciseIndex: Int, seconds: Int) {
        val ex = routineEditorExercises.getOrNull(exerciseIndex) ?: return
        routineEditorExercises[exerciseIndex] = ex.copy(restTimerSeconds = seconds.coerceIn(0, 3600))
    }

    fun setRoutineDraftExerciseMeasure(exerciseIndex: Int, measure: ExerciseMeasure) {
        val ex = routineEditorExercises.getOrNull(exerciseIndex) ?: return
        routineEditorExercises[exerciseIndex] = ex.copy(measure = measure, sets = ex.sets.map { it.fittedTo(measure) })
        rememberMeasure(ex.exerciseId, measure)
    }

    private fun rememberMeasure(exerciseId: String, measure: ExerciseMeasure) {
        if (exerciseId.isBlank()) return
        exerciseMeasureOverrides[exerciseId] = measure
        persistExerciseCustomisation()
    }

    fun removeSetFromRoutineDraftExercise(exerciseIndex: Int, setIndex: Int) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        if (ex.sets.size <= 1 || setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
            .apply { removeAt(setIndex) }
            .mapIndexed { idx, set -> set.copy(setNumber = idx + 1) }
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun removeLastSetFromRoutineDraftExercise(exerciseIndex: Int) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        removeSetFromRoutineDraftExercise(exerciseIndex, routineEditorExercises[exerciseIndex].sets.lastIndex)
    }

    fun adjustRoutineDraftWeight(exerciseIndex: Int, setIndex: Int, deltaKg: Double) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val newWeight = (currentSet.weightKg + deltaKg).coerceAtLeast(0.0)
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = currentSet.copy(weightKg = newWeight)
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun adjustRoutineDraftReps(exerciseIndex: Int, setIndex: Int, deltaReps: Int) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val newReps = (currentSet.reps + deltaReps).coerceAtLeast(1)
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = currentSet.copy(reps = newReps)
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun setRoutineDraftWeightInCurrentUnit(exerciseIndex: Int, setIndex: Int, inputWeight: Double) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val weightKg = if (weightUnit == WeightUnit.KG) {
            inputWeight.coerceAtLeast(0.0)
        } else {
            (inputWeight / WeightUnit.LBS.factorFromKg).coerceAtLeast(0.0)
        }
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = ex.sets[setIndex].copy(weightKg = weightKg)
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun setRoutineDraftReps(exerciseIndex: Int, setIndex: Int, inputReps: Int) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = ex.sets[setIndex].copy(reps = inputReps.coerceAtLeast(1))
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun updateRoutineDraftSetType(exerciseIndex: Int, setIndex: Int, newType: SetType) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = ex.sets[setIndex].copy(setType = newType)
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun updateRoutineDraftRepType(exerciseIndex: Int, setIndex: Int, newRepType: RepType) {
        if (exerciseIndex !in routineEditorExercises.indices) return
        val ex = routineEditorExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = ex.sets[setIndex].copy(repType = newRepType)
        routineEditorExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun moveRoutineDraftExercise(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in routineEditorExercises.indices || toIndex !in routineEditorExercises.indices || fromIndex == toIndex) return
        val item = routineEditorExercises.removeAt(fromIndex)
        routineEditorExercises.add(toIndex, item)
    }

    fun moveActiveWorkoutExercise(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in activeExercises.indices || toIndex !in activeExercises.indices || fromIndex == toIndex) return
        val item = activeExercises.removeAt(fromIndex)
        activeExercises.add(toIndex, item)
        publishWearState()
    }

    fun saveRoutineDraft(): Boolean {
        val cleanTitle = routineEditorTitle.trim()
        if (cleanTitle.isEmpty()) return false
        val templateExercises = routineEditorExercises.map { ex ->
            val refSet = ex.sets.firstOrNull { it.setType != SetType.WARMUP } ?: ex.sets.firstOrNull()
            RoutineTemplateExercise(
                exerciseId = ex.exerciseId,
                exerciseTitle = ex.exerciseTitle,
                targetSets = ex.sets.size.coerceAtLeast(1),
                targetReps = refSet?.reps ?: 8,
                targetWeightKg = refSet?.weightKg ?: 60.0,
                restSeconds = ex.restTimerSeconds,
                repType = refSet?.repType ?: RepType.REPS,
                sets = ex.sets.mapIndexed { idx, s -> s.copy(setNumber = idx + 1) },
                measure = ex.measure
            )
        }
        val cleanFolder = routineEditorFolder.trim()
        val existingId = editingRoutineId
        if (existingId != null) {
            val idx = routines.indexOfFirst { it.id == existingId }
            val updated = RoutineTemplate(
                id = existingId,
                folderName = cleanFolder,
                title = cleanTitle,
                notes = routineEditorNotes.trim(),
                exercises = templateExercises
            )
            if (idx >= 0) {
                routines[idx] = updated
            } else {
                routines.add(0, updated)
            }
        } else {
            routines.add(
                0,
                RoutineTemplate(
                    id = "rt_${System.currentTimeMillis()}",
                    folderName = cleanFolder,
                    title = cleanTitle,
                    notes = routineEditorNotes.trim(),
                    exercises = templateExercises
                )
            )
        }
        if (cleanFolder.isNotBlank() && !expandedRoutineFolders.contains(cleanFolder)) {
            expandedRoutineFolders.add(cleanFolder)
        }
        editingRoutineId = null
        routineEditorInitialSnapshot = null
        isRoutineEditorLayerOpen = false
        onRoutinesChanged()
        return true
    }

    fun deleteRoutine(routineId: String) {
        routines.removeAll { it.id == routineId }
        onRoutinesChanged()
    }

    fun changeRoutineFolder(routineId: String, newFolderName: String) {
        val idx = routines.indexOfFirst { it.id == routineId }
        if (idx < 0) return
        val cleanFolder = newFolderName.trim()
        routines[idx] = routines[idx].copy(folderName = cleanFolder)
        if (cleanFolder.isNotBlank() && !expandedRoutineFolders.contains(cleanFolder)) {
            expandedRoutineFolders.add(cleanFolder)
        }
        onRoutinesChanged()
    }

    fun moveRoutine(routineId: String, direction: Int) {
        val currentIdx = routines.indexOfFirst { it.id == routineId }
        if (currentIdx < 0 || direction == 0) return
        val targetIdx = (currentIdx + direction).coerceIn(0, routines.lastIndex)
        if (targetIdx == currentIdx) return
        val item = routines.removeAt(currentIdx)
        routines.add(targetIdx, item)
        onRoutinesChanged()
    }

    fun moveRoutineWithinList(routineId: String, targetOrderIds: List<String>, direction: Int) {
        val pos = targetOrderIds.indexOf(routineId)
        if (pos < 0 || direction == 0) return
        val nextPos = pos + direction
        if (nextPos !in targetOrderIds.indices) return
        val swapRoutineId = targetOrderIds[nextPos]
        val idxA = routines.indexOfFirst { it.id == routineId }
        val idxB = routines.indexOfFirst { it.id == swapRoutineId }
        if (idxA >= 0 && idxB >= 0 && idxA != idxB) {
            val temp = routines[idxA]
            routines[idxA] = routines[idxB]
            routines[idxB] = temp
            onRoutinesChanged()
        }
    }

    // --- Computed Active Workout Metrics ---
    val activeTotalVolumeKg: Double
        get() = activeExercises.sumOf { ex ->
            ex.sets.sumOf { s -> WorkoutMechanics.calculateSetVolume(s.weightKg, s.reps, s.setType, s.completed) }
        }

    val activeCompletedSets: Int
        get() = activeExercises.sumOf { ex -> ex.sets.count { it.completed } }

    val activeTotalSets: Int
        get() = activeExercises.sumOf { it.sets.size }

    val activeEstimatedCalories: Double
        get() = WorkoutMechanics.estimateWorkoutCalories(workoutElapsedSeconds, activeCompletedSets)

    // --- Active Workout Layer Navigation & Session Actions ---
    fun startEmptyWorkoutSession(title: String = "Freestyle Workout") {
        activeExercises.clear()
        activeWorkoutTitle = title
        activeWorkoutNotes = ""
        workoutElapsedSeconds = 0L
        restTimerRemainingSeconds = 0
        isTimerPaused = false
        isWorkoutActive = true
        isWorkoutLayerOpen = true
        workoutBannerMessage = null
        onWorkoutStarted()
    }

    private fun onWorkoutStarted() {
        activeHeartRateSamples.clear()
        liveHeartRateBpm = 0
        publishWearState()
        sendWearMessage(WearProtocol.MSG_WORKOUT_STARTED, activeWorkoutTitle)
        // Bring the watch app forward so it can start heart rate tracking.
        if (wearWatchStatus.isWatchWithAppReachable) {
            wearConnector?.let { connector ->
                viewModelScope.launch(Dispatchers.IO) { runCatching { connector.launchAppOnWear() } }
            }
        }
    }

    fun toggleWorkoutPaused() {
        isTimerPaused = !isTimerPaused
        syncRestAlert() // paused: no alert; resumed: alert moves to the new end time
        publishWearState()
    }

    fun openActiveWorkoutLayer() {
        isWorkoutActive = true
        isWorkoutLayerOpen = true
    }

    fun minimizeWorkoutLayer() {
        isWorkoutLayerOpen = false
    }

    fun discardActiveWorkout() {
        isWorkoutActive = false
        isWorkoutLayerOpen = false
        isTimerPaused = false
        workoutElapsedSeconds = 0L
        restTimerRemainingSeconds = 0
        workoutBannerMessage = null
        activeExercises.clear()
        activeHeartRateSamples.clear()
        liveHeartRateBpm = 0
        publishWearState()
        sendWearMessage(WearProtocol.MSG_WORKOUT_FINISHED, "discarded")
    }

    fun saveCurrentWorkoutAsRoutine(customTitle: String? = null): RoutineTemplate? {
        if (activeExercises.isEmpty()) return null
        val routineTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${activeWorkoutTitle.ifBlank { "Custom Workout" }} (Routine)"
        val templateExercises = activeExercises.map { ex ->
            val refSet = ex.sets.firstOrNull { it.setType != SetType.WARMUP } ?: ex.sets.firstOrNull()
            RoutineTemplateExercise(
                exerciseId = ex.exerciseId,
                exerciseTitle = ex.exerciseTitle,
                targetSets = ex.sets.size.coerceAtLeast(1),
                targetReps = refSet?.reps ?: 8,
                targetWeightKg = refSet?.weightKg ?: 60.0,
                restSeconds = ex.restTimerSeconds,
                repType = refSet?.repType ?: RepType.REPS,
                sets = ex.sets.mapIndexed { idx, s -> s.copy(setNumber = idx + 1, completed = false) },
                measure = ex.measure
            )
        }
        val created = RoutineTemplate(
            id = "rt_from_active_${System.currentTimeMillis()}",
            folderName = "",
            title = routineTitle,
            notes = activeWorkoutNotes.ifBlank { "Saved from active workout session." },
            exercises = templateExercises
        )
        routines.add(0, created)
        onRoutinesChanged()
        workoutBannerMessage = "Saved \"${created.title}\" to Routines."
        return created
    }

    fun convertWorkoutSessionToRoutine(session: WorkoutSession): RoutineTemplate {
        val templateExercises = session.exercises.map { ex ->
            val refSet = ex.sets.firstOrNull { it.setType != SetType.WARMUP } ?: ex.sets.firstOrNull()
            RoutineTemplateExercise(
                exerciseId = ex.exerciseId,
                exerciseTitle = ex.exerciseTitle,
                targetSets = ex.sets.size.coerceAtLeast(1),
                targetReps = refSet?.reps ?: 8,
                targetWeightKg = refSet?.weightKg ?: 60.0,
                restSeconds = ex.restTimerSeconds,
                repType = refSet?.repType ?: RepType.REPS,
                sets = ex.sets.mapIndexed { idx, s -> s.copy(setNumber = idx + 1, completed = false) },
                measure = ex.measure
            )
        }
        val created = RoutineTemplate(
            id = "rt_from_wk_${System.currentTimeMillis()}",
            folderName = "",
            title = "${session.title} (Routine)",
            notes = session.notes.ifBlank { "Converted from logged workout." },
            exercises = templateExercises
        )
        routines.add(0, created)
        onRoutinesChanged()
        workoutBannerMessage = "Converted \"${session.title}\" into a new routine."
        return created
    }

    fun deleteWorkoutSession(sessionId: String) {
        val removed = workoutHistory.removeAll { it.id == sessionId }
        if (removed) {
            deleteWorkoutSessionFromDb(sessionId)
            ai.deleteAnalysis(sessionId)
            if (selectedWorkoutSessionId == sessionId) {
                isWorkoutSessionDetailLayerOpen = false
                selectedWorkoutSessionId = null
            }
            workoutBannerMessage = "Deleted workout session."
        }
    }

    fun toggleArchiveWorkoutSession(sessionId: String) {
        val idx = workoutHistory.indexOfFirst { it.id == sessionId }
        if (idx < 0) return
        val current = workoutHistory[idx]
        val nextArchived = !current.archived
        val updated = current.copy(archived = nextArchived)
        workoutHistory[idx] = updated
        persistWorkoutSessionToDb(updated)
        workoutBannerMessage = if (nextArchived) {
            "Archived \"${current.title}\"."
        } else {
            "Unarchived \"${current.title}\"."
        }
    }

    fun toggleSetCompleted(exerciseIndex: Int, setIndex: Int) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val updatedCompleted = !currentSet.completed
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = currentSet.copy(completed = updatedCompleted)
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)

        if (updatedCompleted) {
            activeExerciseIndex = exerciseIndex
            activeSetIndex = (setIndex + 1).coerceAtMost(updatedSets.size - 1)
            if (autoStartRestTimer && ex.restTimerSeconds > 0) {
                restTimerTargetSeconds = ex.restTimerSeconds
                restTimerRemainingSeconds = ex.restTimerSeconds
            }
        }
        publishWearState()
    }

    fun updateSetType(exerciseIndex: Int, setIndex: Int, newType: SetType) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = currentSet.copy(setType = newType)
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
        publishWearState()
    }

    fun updateSetRpe(exerciseIndex: Int, setIndex: Int, newRpe: Double?) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = currentSet.copy(rpe = newRpe?.coerceIn(1.0, 10.0))
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
    }

    fun cycleSetType(exerciseIndex: Int, setIndex: Int) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val types = SetType.entries
        val nextType = types[(types.indexOf(currentSet.setType) + 1) % types.size]
        updateSetType(exerciseIndex, setIndex, nextType)
    }

    fun adjustSetWeight(exerciseIndex: Int, setIndex: Int, deltaKg: Double) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val newWeight = (currentSet.weightKg + deltaKg).coerceAtLeast(0.0)
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = currentSet.copy(weightKg = newWeight)
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
        publishWearState()
    }

    fun adjustSetReps(exerciseIndex: Int, setIndex: Int, deltaReps: Int) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val currentSet = ex.sets[setIndex]
        val newReps = (currentSet.reps + deltaReps).coerceAtLeast(1)
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = currentSet.copy(reps = newReps)
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
        publishWearState()
    }

    fun setSetWeightInCurrentUnit(exerciseIndex: Int, setIndex: Int, inputWeight: Double) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val weightKg = if (weightUnit == WeightUnit.KG) {
            inputWeight.coerceAtLeast(0.0)
        } else {
            (inputWeight / WeightUnit.LBS.factorFromKg).coerceAtLeast(0.0)
        }
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = ex.sets[setIndex].copy(weightKg = weightKg)
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
        publishWearState()
    }

    fun setSetReps(exerciseIndex: Int, setIndex: Int, inputReps: Int) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = ex.sets[setIndex].copy(reps = inputReps.coerceAtLeast(1))
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
        publishWearState()
    }

    fun addSetToActiveExercise(exerciseIndex: Int) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        val lastSet = ex.sets.lastOrNull()
        val nextNum = ex.sets.size + 1
        val id = "set_${System.currentTimeMillis()}_$nextNum"
        val newSet = lastSet?.copy(
            id = id, setNumber = nextNum, setType = SetType.NORMAL,
            previousText = setSummary(lastSet, ex.measure), rpe = null, completed = false
        ) ?: defaultSetFor(ex.measure, id, nextNum)
        activeExercises[exerciseIndex] = ex.copy(sets = ex.sets + newSet)
        publishWearState()
    }

    fun removeSetFromActiveExercise(exerciseIndex: Int, setIndex: Int) {
        if (exerciseIndex !in activeExercises.indices) return
        val ex = activeExercises[exerciseIndex]
        if (ex.sets.size <= 1 || setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
            .apply { removeAt(setIndex) }
            .mapIndexed { idx, set -> set.copy(setNumber = idx + 1) }
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
        if (activeExerciseIndex == exerciseIndex) {
            activeSetIndex = activeSetIndex.coerceIn(0, updatedSets.lastIndex)
        }
        publishWearState()
    }

    fun addExerciseToActiveWorkout(exerciseDef: ExerciseDefinition) {
        val idx = activeExercises.size + 1
        val now = System.currentTimeMillis()
        val measure = measureFor(exerciseDef.id, exerciseDef.title)
        activeExercises.add(
            WorkoutExerciseEntry(
                id = "act_ex_${now}_$idx",
                exerciseId = exerciseDef.id,
                exerciseTitle = exerciseDef.title,
                notes = exerciseDef.targetMuscle.ifBlank { "" }.let { if (it.isNotBlank()) "Target: $it" else "" },
                restTimerSeconds = defaultRestTimerSeconds,
                sets = lastPerformedSets(exerciseDef.id, exerciseDef.title)
                    .let { last -> (1..last.size.coerceAtLeast(2)).map { defaultSetFor(measure, "act_s_${now}_$it", it) } }
                    .startingFromLastTime(exerciseDef.id, exerciseDef.title, measure),
                measure = measure
            )
        )
        publishWearState()
    }

    fun removeActiveExercise(exerciseIndex: Int) {
        if (exerciseIndex !in activeExercises.indices) return
        activeExercises.removeAt(exerciseIndex)
        activeExerciseIndex = activeExerciseIndex.coerceIn(0, (activeExercises.size - 1).coerceAtLeast(0))
        publishWearState()
    }

    /** Swaps the exercise but keeps its sets, rest timer and position. */
    private fun WorkoutExerciseEntry.replacedWith(def: ExerciseDefinition): WorkoutExerciseEntry {
        val newMeasure = measureFor(def.id, def.title)
        return copy(
            exerciseId = def.id,
            exerciseTitle = def.title,
            notes = "",
            measure = newMeasure,
            sets = sets.map { it.fittedTo(newMeasure).copy(previousText = "—") }
        )
    }

    fun replaceActiveExercise(exerciseIndex: Int, def: ExerciseDefinition) {
        val ex = activeExercises.getOrNull(exerciseIndex) ?: return
        val replaced = ex.replacedWith(def)
        activeExercises[exerciseIndex] = replaced.copy(
            sets = ex.sets.zip(replaced.sets) { old, new -> if (old.completed) new else null }.let { kept ->
                val fresh = replaced.sets.startingFromLastTime(def.id, def.title, replaced.measure)
                kept.mapIndexed { i, done -> done ?: fresh[i] }
            }
        )
        publishWearState()
    }

    fun replaceRoutineDraftExercise(exerciseIndex: Int, def: ExerciseDefinition) {
        val ex = routineEditorExercises.getOrNull(exerciseIndex) ?: return
        routineEditorExercises[exerciseIndex] = ex.replacedWith(def)
    }

    /** Edits one set of the active workout (duration, distance, …). */
    fun updateActiveSet(exerciseIndex: Int, setIndex: Int, transform: (WorkoutSetEntry) -> WorkoutSetEntry) {
        val ex = activeExercises.getOrNull(exerciseIndex) ?: return
        if (setIndex !in ex.sets.indices) return
        val updatedSets = ex.sets.toMutableList()
        updatedSets[setIndex] = transform(ex.sets[setIndex])
        activeExercises[exerciseIndex] = ex.copy(sets = updatedSets)
        publishWearState()
    }

    /** Rest after each set of this exercise in the current session; 0 turns the timer off for it. */
    fun setActiveExerciseRest(exerciseIndex: Int, seconds: Int) {
        val ex = activeExercises.getOrNull(exerciseIndex) ?: return
        activeExercises[exerciseIndex] = ex.copy(restTimerSeconds = seconds.coerceIn(0, 3600))
    }

    fun setActiveExerciseMeasure(exerciseIndex: Int, measure: ExerciseMeasure) {
        val ex = activeExercises.getOrNull(exerciseIndex) ?: return
        activeExercises[exerciseIndex] = ex.copy(measure = measure, sets = ex.sets.map { it.fittedTo(measure) })
        rememberMeasure(ex.exerciseId, measure)
        publishWearState()
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        restTimerRemainingSeconds = (restTimerRemainingSeconds + deltaSeconds).coerceAtLeast(0)
        publishWearState()
    }

    fun skipRestTimer() {
        restTimerRemainingSeconds = 0
        publishWearState()
    }

    fun cycleTimerVolumeSetting() {
        val order = listOf("off", "low", "normal", "high")
        val nextIdx = (order.indexOf(restTimerVolumeSetting) + 1) % order.size
        updateRestTimerVolume(order[nextIdx])
    }

    fun finishActiveWorkout() {
        val wasActive = isWorkoutActive
        var finishedSessionId: String? = null
        if (activeExercises.isNotEmpty()) {
            val nowSec = System.currentTimeMillis() / 1000L
            val startSec = nowSec - workoutElapsedSeconds.coerceAtLeast(60L)
            val samples = activeHeartRateSamples
                .filter { it.heartRateTimestampMs in (startSec * 1000L)..(nowSec * 1000L) }
            val completedSession = WorkoutSession(
                id = "wk_${System.currentTimeMillis()}",
                title = activeWorkoutTitle.ifBlank { "Strength Session" },
                startTimeEpochSeconds = startSec,
                endTimeEpochSeconds = nowSec,
                notes = activeWorkoutNotes,
                exercises = activeExercises.toList(),
                averageHeartRateBpm = if (samples.isEmpty()) 0 else samples.map { it.heartRate }.average().toInt(),
                caloriesBurnedKcal = activeEstimatedCalories,
                syncedToHealthConnect = false,
                heartRateSamples = samples
            )
            workoutHistory.add(0, completedSession)
            persistWorkoutSessionToDb(completedSession)
            finishedSessionId = completedSession.id
        }

        isWorkoutActive = false
        isWorkoutLayerOpen = false
        isTimerPaused = false
        workoutElapsedSeconds = 0L
        restTimerRemainingSeconds = 0
        workoutBannerMessage = null
        activeHeartRateSamples.clear()
        liveHeartRateBpm = 0
        selectedTab = MainTab.WORKOUT

        if (wasActive) {
            publishWearState()
            sendWearMessage(WearProtocol.MSG_WORKOUT_FINISHED, "finished")
        }
        finishedSessionId?.let { id ->
            if (autoSyncWorkoutsToHealthConnect && canWriteWorkoutsToHealthConnect) {
                syncWorkoutToHealthConnect(id)
            }
        }
    }

    fun startRoutineAsWorkout(routine: RoutineTemplate) {
        activeWorkoutTitle = routine.title
        activeWorkoutNotes = routine.notes
        workoutElapsedSeconds = 0L
        restTimerRemainingSeconds = 0
        isTimerPaused = false
        isWorkoutActive = true
        isWorkoutLayerOpen = true
        workoutBannerMessage = null
        activeExercises.clear()
        routine.exercises.forEachIndexed { idx, rEx ->
            val sets = if (rEx.sets.isNotEmpty()) {
                rEx.sets.mapIndexed { sIdx, s ->
                    s.copy(
                        id = "rt_s_${idx}_${sIdx + 1}",
                        setNumber = sIdx + 1,
                        previousText = setSummary(s, rEx.measure),
                        rpe = null,
                        completed = false
                    )
                }
            } else {
                (1..rEx.targetSets).map { sNum ->
                    WorkoutSetEntry(
                        id = "rt_s_${idx}_$sNum",
                        setNumber = sNum,
                        setType = SetType.NORMAL,
                        repType = rEx.repType,
                        previousText = "${WorkoutMechanics.formatWeightValue(rEx.targetWeightKg, weightUnit)}${weightUnit.label} × ${rEx.targetReps}",
                        weightKg = rEx.targetWeightKg,
                        reps = rEx.targetReps,
                        rpe = null,
                        completed = false
                    )
                }
            }
            activeExercises.add(
                WorkoutExerciseEntry(
                    id = "rt_we_$idx",
                    exerciseId = rEx.exerciseId,
                    exerciseTitle = rEx.exerciseTitle,
                    notes = if (rEx.measure == ExerciseMeasure.WEIGHT_REPS) {
                        "Target: ${rEx.targetSets}×${rEx.targetReps} @ ${WorkoutMechanics.formatWeight(rEx.targetWeightKg, weightUnit)}"
                    } else {
                        ""
                    },
                    restTimerSeconds = rEx.restSeconds,
                    sets = sets.startingFromLastTime(rEx.exerciseId, rEx.exerciseTitle, rEx.measure),
                    measure = rEx.measure
                )
            )
        }
        onWorkoutStarted()
    }

    fun createNewRoutine(title: String, folder: String, selectedExIds: List<String>) {
        if (title.isBlank() || selectedExIds.isEmpty()) return
        val templateExercises = selectedExIds.mapNotNull { id ->
            val def = exercises.find { it.id == id } ?: return@mapNotNull null
            RoutineTemplateExercise(
                exerciseId = def.id,
                exerciseTitle = def.title,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 60.0,
                restSeconds = defaultRestTimerSeconds,
                measure = measureFor(def.id, def.title)
            )
        }
        routines.add(
            0,
            RoutineTemplate(
                id = "rt_${System.currentTimeMillis()}",
                folderName = folder.trim(),
                title = title.trim(),
                notes = "Custom routine created in Lift Tracker.",
                exercises = templateExercises
            )
        )
        onRoutinesChanged()
    }

    // --- Exercise History & Personal Records Computation ---
    /** Logged sessions of an exercise, newest first. Aliases of the exercise count as the exercise. */
    fun getExerciseHistory(exerciseId: String): List<ExerciseHistoryPoint> {
        val points = mutableListOf<ExerciseHistoryPoint>()
        val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val family = exerciseFamily(exerciseId)
        for (session in workoutHistory) {
            val matchingBlocks = session.exercises.filter { family.matches(it) }
            if (matchingBlocks.isEmpty()) continue
            val completedSets = matchingBlocks.flatMap { it.sets }.filter { it.completed }
            if (completedSets.isEmpty()) continue
            val measure = matchingBlocks.first().measure

            val bestWeight = completedSets.maxOf { it.weightKg }
            val best1Rm = completedSets.maxOf { WorkoutMechanics.estimateOneRepMax(it.weightKg, it.reps) }
            val vol = completedSets.sumOf {
                WorkoutMechanics.calculateSetVolume(it.weightKg, it.reps, it.setType, it.completed)
            }
            val summaries = completedSets.map { s ->
                "${s.setType.code}: ${setSummary(s, measure)}" +
                    (s.rpe?.let { " @RPE $it" } ?: "")
            }
            points.add(
                ExerciseHistoryPoint(
                    workoutId = session.id,
                    workoutTitle = session.title,
                    dateLabel = fmt.format(Date(session.endTimeEpochSeconds * 1000L)),
                    timestampEpochSeconds = session.endTimeEpochSeconds,
                    bestWeightKg = bestWeight,
                    best1RmKg = best1Rm,
                    sessionVolumeKg = vol,
                    setsSummary = summaries,
                    loggedAs = matchingBlocks.map { it.exerciseTitle }.distinct().joinToString(" + ")
                )
            )
        }
        return points.sortedByDescending { it.timestampEpochSeconds }
    }

    // --- Health Connect Actions ---
    private fun addHealthLog(recordType: String, summary: String, details: String) {
        healthExportLogs.add(
            0,
            HealthConnectExportLog(
                id = "hcl_${System.currentTimeMillis()}_${healthExportLogs.size}",
                recordType = recordType,
                summary = summary,
                details = details,
                timestampLabel = timestampLabel()
            )
        )
    }

    /** Re-reads SDK availability, granted permissions, change tokens and recent measurements. */
    fun refreshHealthConnectState() {
        val hc = healthConnect ?: return
        healthConnectSdkStatus = runCatching { hc.sdkStatus }
            .getOrDefault(WorkoutMechanics.HEALTH_CONNECT_SDK_UNAVAILABLE)
        weightChangeToken = hc.storedChangeToken(MeasurementType.WEIGHT)
        bodyFatChangeToken = hc.storedChangeToken(MeasurementType.BODY_FAT)
        if (!isHealthConnectAvailable) {
            applyGrantedHealthPermissions(emptySet())
            return
        }
        viewModelScope.launch {
            runCatching {
                val granted = withContext(Dispatchers.IO) { hc.grantedPermissions() }
                applyGrantedHealthPermissions(granted)
                if (autoSyncMeasurementsFromHealthConnect) syncMeasurementsFromHealthConnect(announce = false)
            }.onFailure { healthStatusMessage = "Health Connect: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    private fun applyGrantedHealthPermissions(granted: Set<String>) {
        for (i in healthPermissions.indices) {
            val item = healthPermissions[i]
            val isGranted = item.androidPermission in granted
            if (item.granted != isGranted) healthPermissions[i] = item.copy(granted = isGranted)
        }
    }

    /** Result callback of Health Connect's permission request contract. */
    fun onHealthPermissionsResult(granted: Set<String>) {
        applyGrantedHealthPermissions(granted)
        healthStatusMessage = "${healthPermissions.count { it.granted }} of ${healthPermissions.size} Health Connect permissions granted."
        refreshHealthConnectState()
    }

    private fun mergeMeasurements(items: List<HealthMeasurement>, deletedIds: List<String> = emptyList()) {
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val ownPackage = appContext?.packageName
        val mapped = items.map { m ->
            BodyMeasurementRecord(
                id = m.recordId,
                recordTypeKey = m.type.readKey,
                value = m.value,
                unit = m.type.unit,
                dateLabel = dayFormat.format(Date(m.time.toEpochMilli())),
                epochSeconds = m.time.epochSecond,
                source = if (m.originPackage == ownPackage) "Lift Tracker" else m.originPackage
            )
        }
        val replaced = mapped.map { it.id }.toSet() + deletedIds
        val merged = (bodyMeasurements.filter { it.id !in replaced } + mapped)
            .sortedByDescending { it.epochSeconds }
            .take(MAX_STORED_MEASUREMENTS)
        bodyMeasurements.clear()
        bodyMeasurements.addAll(merged)
        prefs?.edit()?.putString("body_measurements", serializeMeasurements(merged))?.apply()
    }

    private fun serializeMeasurements(items: List<BodyMeasurementRecord>): String {
        val arr = JSONArray()
        items.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("type", m.recordTypeKey)
                    put("value", m.value)
                    put("unit", m.unit)
                    put("date", m.dateLabel)
                    put("epochSeconds", m.epochSeconds)
                    put("source", m.source)
                }
            )
        }
        return arr.toString()
    }

    private fun parseStoredMeasurements(json: String?): List<BodyMeasurementRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BodyMeasurementRecord(
                    id = o.optString("id"),
                    recordTypeKey = o.optString("type"),
                    value = o.optDouble("value", 0.0),
                    unit = o.optString("unit"),
                    dateLabel = o.optString("date"),
                    epochSeconds = o.optLong("epochSeconds", 0L),
                    source = o.optString("source")
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Pulls weight and body-fat records that changed in Health Connect since the last sync
     * (new, edited and deleted), using change tokens. Runs automatically on start and whenever the
     * app returns to the foreground while auto-sync is on; [announce] adds a status message and log.
     */
    fun syncMeasurementsFromHealthConnect(announce: Boolean = true) {
        val hc = healthConnect
        if (hc == null || !isHealthConnectAvailable) {
            if (announce) healthStatusMessage = "Health Connect is not available on this device."
            return
        }
        if (!canReadMeasurementsFromHealthConnect) {
            if (announce) healthStatusMessage = "Grant the weight and body fat read permissions first."
            return
        }
        if (isMeasurementSyncInProgress) return
        isMeasurementSyncInProgress = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { hc.fetchNewMeasurements() } }
                .onSuccess { changes ->
                    mergeMeasurements(changes.measurements, changes.deletedRecordIds)
                    weightChangeToken = hc.storedChangeToken(MeasurementType.WEIGHT)
                    bodyFatChangeToken = hc.storedChangeToken(MeasurementType.BODY_FAT)
                    lastMeasurementSyncLabel = timestampLabel()
                    prefs?.edit()?.putString("hc_last_measurement_sync", lastMeasurementSyncLabel)?.apply()
                    val changed = changes.measurements.size + changes.deletedRecordIds.size
                    if (announce || changed > 0) {
                        addHealthLog(
                            recordType = "Measurement sync",
                            summary = "${changes.measurements.size} new or updated, ${changes.deletedRecordIds.size} removed",
                            details = if (changes.tokenWasReset) "Initial import of the last 90 days" else "Changes since the last sync"
                        )
                    }
                    if (announce) {
                        healthStatusMessage = if (changed == 0) {
                            "Weight and body fat are up to date."
                        } else {
                            "Synced $changed weight / body fat change(s) from Health Connect."
                        }
                    }
                }
                .onFailure {
                    if (announce) healthStatusMessage = "Could not read measurements: ${it.message ?: it.javaClass.simpleName}"
                }
            isMeasurementSyncInProgress = false
        }
    }

    fun syncWorkoutToHealthConnect(sessionId: String) {
        val hc = healthConnect
        val session = workoutHistory.find { it.id == sessionId } ?: return
        if (hc == null || !isHealthConnectAvailable) {
            healthStatusMessage = "Health Connect is not available on this device."
            return
        }
        if (sessionId in healthSyncingSessionIds) return
        healthSyncingSessionIds.add(sessionId)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { hc.writeWorkout(session) } }
                .onSuccess { result ->
                    updateWorkoutSession(sessionId) { it.copy(syncedToHealthConnect = true) }
                    val parts = mutableListOf("ExerciseSessionRecord")
                    if (result.wroteCalories) parts += "TotalCaloriesBurnedRecord"
                    if (result.heartRateSamplesWritten > 0) parts += "HeartRateRecord"
                    addHealthLog(
                        recordType = parts.joinToString(" + "),
                        summary = "${session.title} (${WorkoutMechanics.formatDuration(session.durationSeconds)})",
                        details = buildString {
                            append("Strength training session written")
                            if (result.wroteCalories) append(" • ${session.caloriesBurnedKcal.toInt()} kcal")
                            if (result.heartRateSamplesWritten > 0) append(" • ${result.heartRateSamplesWritten} heart rate samples")
                        }
                    )
                    healthStatusMessage = "Synced \"${session.title}\" to Health Connect."
                }
                .onFailure {
                    healthStatusMessage = "Could not sync \"${session.title}\": ${it.message ?: it.javaClass.simpleName}"
                }
            healthSyncingSessionIds.remove(sessionId)
        }
    }

    fun syncAllUnsyncedWorkoutsToHealthConnect() {
        val pending = workoutHistory.filter { !it.syncedToHealthConnect }.map { it.id }
        if (pending.isEmpty()) {
            healthStatusMessage = "All workouts are already synced."
            return
        }
        pending.forEach { syncWorkoutToHealthConnect(it) }
    }

    fun writeBodyMeasurement(typeKey: String, value: Double) {
        val type = if (typeKey.contains("weight", ignoreCase = true)) MeasurementType.WEIGHT else MeasurementType.BODY_FAT
        val hc = healthConnect
        if (hc == null || !isHealthConnectAvailable) {
            healthStatusMessage = "Health Connect is not available on this device."
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { hc.writeMeasurement(type, value) }
            }.onSuccess {
                // The new record comes back through the change-token sync, with its Health Connect id.
                syncMeasurementsFromHealthConnect(announce = false)
                addHealthLog(
                    recordType = if (type == MeasurementType.WEIGHT) "WeightRecord" else "BodyFatRecord",
                    summary = "$value ${type.unit} written",
                    details = "Today's Lift Tracker entry was created or replaced"
                )
                healthStatusMessage = "Saved $value ${type.unit} to Health Connect."
            }.onFailure {
                healthStatusMessage = "Could not write measurement: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    fun healthConnectInstallIntent() = healthConnect?.providerInstallIntent()

    fun healthConnectManageIntent() = healthConnect?.manageDataIntent()

    // --- Wear OS Actions ---
    private fun addWearLog(direction: String, path: String, payload: String, nodeId: String) {
        wearMessageLogs.add(
            0,
            WearMessageLog(
                id = "wm_${System.currentTimeMillis()}_${wearMessageLogs.size}",
                direction = direction,
                path = path,
                payload = payload,
                sourceNodeId = nodeId,
                timestampLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            )
        )
        while (wearMessageLogs.size > 60) wearMessageLogs.removeAt(wearMessageLogs.lastIndex)
    }

    /** Queries the Wearable Data Layer for connected watches and whether they have the watch app. */
    fun refreshWatchStatus() {
        val connector = wearConnector ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { connector.queryConnection() } }
                .onSuccess { snapshot ->
                    val primary = snapshot.reachableNodesWithApp.firstOrNull()
                        ?: snapshot.nodesWithApp.firstOrNull()
                        ?: snapshot.connectedNodes.firstOrNull()
                    wearWatchStatus = WearWatchStatus(
                        hasConnectedWatch = snapshot.connectedNodes.isNotEmpty(),
                        hasWatchAppInstalled = snapshot.nodesWithApp.isNotEmpty(),
                        isWatchWithAppReachable = snapshot.reachableNodesWithApp.isNotEmpty(),
                        watchNodeId = primary?.id.orEmpty(),
                        watchModelName = primary?.displayName ?: "No watch connected"
                    )
                    wearStatusMessage = null
                }
                .onFailure {
                    wearWatchStatus = WearWatchStatus(false, false, false)
                    wearStatusMessage = "Wear OS is unavailable: ${it.message ?: it.javaClass.simpleName}"
                }
        }
    }

    /** Next set to perform, searching from the current exercise onwards. */
    private fun currentWatchTarget(): Pair<Int, Int>? {
        if (activeExercises.isEmpty()) return null
        val start = activeExerciseIndex.coerceIn(0, activeExercises.lastIndex)
        for (offset in activeExercises.indices) {
            val exIdx = (start + offset) % activeExercises.size
            val setIdx = activeExercises[exIdx].sets.indexOfFirst { !it.completed }
            if (setIdx >= 0) return exIdx to setIdx
        }
        return null
    }

    private fun describeSet(exerciseIndex: Int, setIndex: Int): String {
        val ex = activeExercises.getOrNull(exerciseIndex) ?: return ""
        val set = ex.sets.getOrNull(setIndex) ?: return ""
        val type = if (set.setType == SetType.NORMAL) "" else " ${set.setType.label}"
        return "Set ${setIndex + 1}/${ex.sets.size}$type · ${setSummary(set, ex.measure)}"
    }

    fun buildWearWorkoutState(nowEpochMs: Long = System.currentTimeMillis()): WearWorkoutState {
        if (!isWorkoutActive) return WearWorkoutState(active = false, sentAtEpochMs = nowEpochMs)
        val target = currentWatchTarget()
        val exercise = target?.let { activeExercises[it.first] }
        val nextLabel = target?.let { (exIdx, setIdx) ->
            val ex = activeExercises[exIdx]
            val nextSetIdx = (setIdx + 1 until ex.sets.size).firstOrNull { !ex.sets[it].completed }
            if (nextSetIdx != null) describeSet(exIdx, nextSetIdx) else ""
        }.orEmpty()
        val restRunning = restTimerRemainingSeconds > 0
        val targetSet = target?.let { activeExercises[it.first].sets[it.second] }
        return WearWorkoutState(
            setIndex = target?.second ?: -1,
            weight = targetSet?.let { WorkoutMechanics.convertWeightForDisplay(it.weightKg, weightUnit) } ?: 0.0,
            weightStep = activeWeightStepDisplayValue,
            weightUnit = weightUnit.label,
            reps = targetSet?.reps ?: 0,
            exercises = activeExercises.map { ex ->
                WearExerciseSummary(ex.exerciseTitle, ex.sets.count { it.completed }, ex.sets.size)
            },
            active = true,
            title = activeWorkoutTitle,
            paused = isTimerPaused,
            elapsedSeconds = workoutElapsedSeconds,
            sentAtEpochMs = nowEpochMs,
            exerciseTitle = exercise?.exerciseTitle.orEmpty(),
            exerciseIndex = target?.first ?: -1,
            exerciseCount = activeExercises.size,
            setLabel = target?.let { describeSet(it.first, it.second) }.orEmpty(),
            nextSetLabel = nextLabel,
            completedSets = activeCompletedSets,
            totalSets = activeTotalSets,
            restEndsAtEpochMs = if (restRunning && !isTimerPaused) nowEpochMs + restTimerRemainingSeconds * 1000L else 0L,
            restRemainingSeconds = restTimerRemainingSeconds,
            restTargetSeconds = restTimerTargetSeconds
        )
    }

    /** Pushes the workout's current state to the ongoing notification and to the watch. */
    private fun publishWearState() {
        val state = buildWearWorkoutState()
        appContext?.let { ctx -> runCatching { WorkoutSessionNotification.update(ctx, state) } }
        val connector = wearConnector ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { connector.publishWorkoutState(state) }
        }
    }

    private fun publishWearRoutines() {
        val connector = wearConnector ?: return
        val summaries = routines.map { WearRoutineSummary(it.id, it.title, it.exercises.size) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { connector.publishRoutines(summaries) }
        }
    }

    fun launchAppOnWear() {
        val connector = wearConnector ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { connector.launchAppOnWear() } }
                .onSuccess { count ->
                    wearStatusMessage = if (count > 0) "Asked $count watch(es) to open Lift Tracker." else "No watch is connected."
                    if (count > 0) addWearLog("PHONE -> WATCH", WearProtocol.WEAR_LAUNCH_URI, "RemoteActivityHelper", "phone")
                }
                .onFailure { wearStatusMessage = "Could not open the watch app: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun sendWearMessage(path: String, payload: String) {
        val connector = wearConnector ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { connector.sendMessage(path, payload) } }
                .onSuccess { nodes -> if (nodes.isNotEmpty()) addWearLog("PHONE -> WATCH", path, payload, "phone") }
        }
    }

    /** Applies a command or data message received from the watch. */
    fun handleWatchMessage(message: WearIncomingMessage) {
        if (message.path != WearProtocol.MSG_HEART_RATE) {
            addWearLog("WATCH -> PHONE", message.path, message.data, message.sourceNodeId)
        }
        when (message.path) {
            WearProtocol.MSG_WORKOUT_STARTED -> {
                if (!isWorkoutActive) startEmptyWorkoutSession("Watch Workout") else publishWearState()
            }
            WearProtocol.MSG_START_ROUTINE -> {
                val routine = routines.find { it.id == message.data }
                if (routine != null && !isWorkoutActive) startRoutineAsWorkout(routine) else publishWearState()
            }
            WearProtocol.MSG_COMPLETE_SET -> completeCurrentSetFromWatch()
            WearProtocol.MSG_SKIP_REST -> skipRestTimer()
            WearProtocol.MSG_ADJUST_REST -> message.data.trim().toIntOrNull()?.let { adjustRestTimer(it.coerceIn(-600, 600)) }
            WearProtocol.MSG_TOGGLE_PAUSE -> if (isWorkoutActive) toggleWorkoutPaused()
            WearProtocol.MSG_FINISH_WORKOUT -> if (isWorkoutActive) finishActiveWorkout()
            WearProtocol.MSG_DISCARD_WORKOUT -> if (isWorkoutActive) discardActiveWorkout()
            WearProtocol.MSG_HEART_RATE -> {
                val samples = WearHeartRateSample.listFromJson(message.data)
                samples.lastOrNull()?.let { liveHeartRateBpm = it.bpm }
                if (isWorkoutActive) {
                    samples.forEach { activeHeartRateSamples.add(HeartRateSamplePoint(it.epochMs, it.bpm.toDouble())) }
                }
            }
            WearProtocol.MSG_EDIT_SET -> {
                val edit = WearSetEdit.fromJson(message.data)
                // Only apply the edit to the set the watch was showing when it was made.
                if (edit != null && isWorkoutActive && currentWatchTarget() == (edit.exerciseIndex to edit.setIndex)) {
                    setSetWeightInCurrentUnit(edit.exerciseIndex, edit.setIndex, edit.weight.coerceIn(0.0, 2000.0))
                    setSetReps(edit.exerciseIndex, edit.setIndex, edit.reps.coerceIn(1, 999))
                } else {
                    publishWearState()
                }
            }
            WearProtocol.MSG_SELECT_EXERCISE -> {
                val index = message.data.trim().toIntOrNull()
                if (isWorkoutActive && index != null && index in activeExercises.indices) {
                    activeExerciseIndex = index
                }
                publishWearState()
            }
            WearProtocol.MSG_REQUEST_STATE -> {
                publishWearState()
                publishWearRoutines()
                refreshWatchStatus()
            }
        }
    }

    fun completeCurrentSetFromWatch() {
        if (!isWorkoutActive) return
        val (exIdx, setIdx) = currentWatchTarget() ?: return
        toggleSetCompleted(exIdx, setIdx)
    }

    // --- Data Import & Export ---
    fun buildExport(format: ExportFormat): String =
        WorkoutExporter.export(format, workoutHistory.toList(), routines.toList())

    /** Workout history in the common one-row-per-set CSV layout, weights in the current unit. */
    fun buildWorkoutHistoryCsv(): String = WorkoutCsv.exportWorkouts(workoutHistory.toList(), weightUnit)

    /** Routine JSON for sharing: one routine, or all of them when [routineId] is null. */
    fun buildRoutinesJson(routineId: String? = null): String =
        RoutineCodec.exportRoutines(routines.filter { routineId == null || it.id == routineId })

    fun routinesFileName(routineId: String? = null): String =
        RoutineCodec.suggestedFileName(routines.filter { routineId == null || it.id == routineId })

    /** Writes an export to a document the user picked with the system file picker. */
    fun writeExportToUri(uri: Uri, format: ExportFormat) =
        writeTextToUri(uri, buildExport(format), "Exported ${workoutHistory.size} workouts as ${format.name}.")

    fun writeTextToUri(uri: Uri, content: String, successMessage: String, forRoutines: Boolean = false) {
        val ctx = appContext ?: return
        fun report(message: String) {
            if (forRoutines) routinesStatusMessage = message else settingsStatusMessage = message
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open the selected file")
                }
            }.onSuccess { report(successMessage) }
                .onFailure { report("Export failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    /**
     * Maps exercise names from a file to this app's exercises: by id, then exact title, then a
     * word-order-independent match ("Bench Press (Barbell)" = "Barbell Bench Press"). Names with no
     * match are collected in [newCustomTitles] and become custom exercises.
     */
    internal class ImportResolver(
        library: List<ExerciseDefinition>,
        private val overrides: Map<String, ExerciseMeasure>
    ) : ExerciseResolver {
        private val byId = library.associateBy { it.id }
        private val byTitle = HashMap<String, ExerciseDefinition>()
        private val byKey = HashMap<Set<String>, ExerciseDefinition>()
        val newCustomTitles = LinkedHashMap<String, String>()

        init {
            library.forEach {
                byTitle.putIfAbsent(it.title.lowercase(Locale.US), it)
                byKey.putIfAbsent(ExerciseMatching.nameKey(it.title), it)
            }
        }

        override fun resolve(exerciseId: String, title: String): ResolvedExercise {
            val def = byId[exerciseId] ?: byTitle[title.lowercase(Locale.US)] ?: byKey[ExerciseMatching.nameKey(title)]
            val id = def?.id ?: ExerciseMatching.customId(title).also { newCustomTitles.putIfAbsent(it, title) }
            return ResolvedExercise(id, overrides[id] ?: ExerciseMeasure.infer(title, def?.equipment))
        }
    }

    internal fun newImportResolver() = ImportResolver(exercises.toList(), exerciseMeasureOverrides.toMap())

    private fun adoptCustomExercises(resolver: ImportResolver) {
        val existing = customExercises.map { it.id }.toHashSet()
        resolver.newCustomTitles.forEach { (id, title) ->
            if (existing.add(id)) customExercises.add(ExerciseMatching.customDefinition(title).copy(id = id))
        }
        appendCustomExercises()
    }

    /**
     * Adds [parsed] routines (from a file or the AI) to the top of the list under new ids, with
     * titles made unique, and saves them. Exercises [resolver] could not match join the library.
     */
    internal fun addParsedRoutines(parsed: List<RoutineTemplate>, resolver: ImportResolver, idPrefix: String = "rt_imp"): List<RoutineTemplate> {
        adoptCustomExercises(resolver)
        val now = System.currentTimeMillis()
        val takenTitles = routines.map { it.title.lowercase(Locale.US) }.toHashSet()
        val added = parsed.mapIndexed { index, routine ->
            var title = routine.title
            var n = 2
            while (!takenTitles.add(title.lowercase(Locale.US))) title = "${routine.title} (${n++})"
            routine.exercises.forEach { exerciseMeasureOverrides[it.exerciseId] = it.measure }
            routine.copy(id = "${idPrefix}_${now}_$index", title = title)
        }
        routines.addAll(0, added)
        added.map { it.folderName }.filter { it.isNotBlank() && it !in expandedRoutineFolders }.distinct()
            .forEach { expandedRoutineFolders.add(it) }
        persistExerciseCustomisation()
        onRoutinesChanged()
        return added
    }

    /** Replaces the routine with [updated]'s id, keeping its place in the list. */
    internal fun replaceRoutine(updated: RoutineTemplate, resolver: ImportResolver) {
        val idx = routines.indexOfFirst { it.id == updated.id }
        if (idx < 0) return
        adoptCustomExercises(resolver)
        updated.exercises.forEach { exerciseMeasureOverrides[it.exerciseId] = it.measure }
        routines[idx] = updated
        persistExerciseCustomisation()
        onRoutinesChanged()
    }

    /** Adds the routines in [text] (routine JSON from a file, the clipboard or another app). Returns how many were added. */
    fun importRoutinesFromText(text: String): Int {
        val resolver = newImportResolver()
        val parsed = runCatching { RoutineCodec.parseRoutines(text, resolver, defaultRestTimerSeconds) }
            .getOrElse {
                routinesStatusMessage = "Import failed: ${it.message ?: "not routine JSON"}"
                return 0
            }
        val added = addParsedRoutines(parsed, resolver)
        val customCount = resolver.newCustomTitles.size
        routinesStatusMessage = "Imported ${added.size} routine(s)" +
            (if (customCount > 0) " and added $customCount new exercise(s) to the library." else ".")
        return added.size
    }

    private suspend fun readTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val ctx = appContext ?: error("App is not ready")
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open the selected file")
    }

    fun importRoutinesFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching { readTextFromUri(uri) }
                .onSuccess { importRoutinesFromText(it) }
                .onFailure { routinesStatusMessage = "Import failed: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    /**
     * Imports workout history from a CSV file (one row per set). Workouts already present — the same
     * title and start time, e.g. from importing the file twice — are replaced, not duplicated.
     */
    fun importWorkoutHistoryFromUri(uri: Uri) {
        if (isImportInProgress) return
        isImportInProgress = true
        settingsStatusMessage = "Importing workouts…"
        val resolver = newImportResolver()
        val rest = defaultRestTimerSeconds
        viewModelScope.launch {
            runCatching {
                val text = readTextFromUri(uri)
                withContext(Dispatchers.Default) { WorkoutCsv.importWorkouts(text, resolver, rest) }
            }.onSuccess { result ->
                adoptCustomExercises(resolver)
                result.measuresByExerciseId.forEach { (id, measure) -> exerciseMeasureOverrides[id] = measure }
                persistExerciseCustomisation()
                val importedIds = result.sessions.map { it.id }.toHashSet()
                val replaced = workoutHistory.count { it.id in importedIds }
                val merged = (workoutHistory.filter { it.id !in importedIds } + result.sessions)
                    .sortedByDescending { it.endTimeEpochSeconds }
                workoutHistory.clear()
                workoutHistory.addAll(merged)
                workoutStorage?.let { storage ->
                    withContext(Dispatchers.IO) { runCatching { storage.saveWorkoutSessions(result.sessions) } }
                }
                settingsStatusMessage = buildString {
                    append("Imported ${result.sessions.size} workouts")
                    append(" (${result.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } }} sets)")
                    if (replaced > 0) append(", replacing $replaced already imported")
                    if (resolver.newCustomTitles.isNotEmpty()) append("; added ${resolver.newCustomTitles.size} new exercises to the library")
                    if (result.skippedRows > 0) append("; skipped ${result.skippedRows} unreadable rows")
                    append(".")
                }
            }.onFailure {
                settingsStatusMessage = "Import failed: ${it.message ?: it.javaClass.simpleName}"
            }
            isImportInProgress = false
        }
    }

    // --- Backup & Restore ---
    var backupBusy by mutableStateOf(false)
        private set
    var backupProgress by mutableStateOf<String?>(null)
        private set
    var backupMessage by mutableStateOf<String?>(null)
    /** A backup that was opened and is waiting for the user to confirm replacing what is on the phone. */
    var pendingRestore by mutableStateOf<BackupManager.Opened?>(null)
        private set
    /** True while an encrypted backup is waiting for its password. */
    var restoreNeedsPassword by mutableStateOf(false)
        private set
    private var restoreFile: File? = null

    fun backupFileName(kind: BackupKind, encrypted: Boolean): String =
        appContext?.let { BackupManager(it).fileName(kind, encrypted) } ?: "LiftTracker-backup"

    private fun runBackupJob(startMessage: String, block: suspend (Context) -> Unit) {
        val ctx = appContext ?: return
        if (backupBusy) return
        backupBusy = true
        backupProgress = startMessage
        backupMessage = null
        viewModelScope.launch {
            runCatching { block(ctx) }.onFailure {
                backupMessage = when (it) {
                    is WrongPasswordException -> it.message
                    else -> "Failed: ${it.message ?: it.javaClass.simpleName}"
                }
            }
            backupProgress = null
            backupBusy = false
        }
    }

    /** Writes a backup to the document the user picked. [password] null = unencrypted (data only). */
    fun backupToUri(kind: BackupKind, password: CharArray?, uri: Uri) = runBackupJob("Preparing backup…") { ctx ->
        // Settings are saved with apply(); make sure the file on disk is current before reading it back.
        saveSettingsToPrefs()
        val manifest = withContext(Dispatchers.IO) {
            val tmp = File.createTempFile("out", ".bak", ctx.cacheDir)
            try {
                val m = BackupManager(ctx).create(kind, tmp, password) { step ->
                    viewModelScope.launch { if (backupBusy) backupProgress = step }
                }
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                    ?: error("Could not open the selected file")
                m
            } finally {
                tmp.delete()
            }
        }
        backupMessage = "Backed up ${kind.phrase}: ${manifest.summary()}." +
            if (password != null) " The file is encrypted with your password." else ""
    }

    /** Opens a backup file picked for restore; asks for a password first when it is encrypted. */
    fun pickRestoreFile(uri: Uri) = runBackupJob("Reading backup…") { ctx ->
        if (isWorkoutActive) error("finish or discard the running workout before restoring")
        cancelRestore()
        val local = withContext(Dispatchers.IO) {
            File.createTempFile("picked", ".bak", ctx.cacheDir).also { file ->
                ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ?: error("Could not open the selected file")
            }
        }
        openRestoreFile(ctx, local)
    }

    private suspend fun openRestoreFile(ctx: Context, local: File) {
        restoreFile = local
        if (withContext(Dispatchers.IO) { com.lifttracker.app.backup.BackupCrypto.isEncrypted(local) }) {
            restoreNeedsPassword = true
        } else {
            pendingRestore = withContext(Dispatchers.IO) { BackupManager(ctx).open(local, null) }
        }
    }

    // --- WebDAV destination & automatic backup ---

    /** Snapshot of the saved WebDAV and automatic-backup settings, for the settings screen. */
    data class RemoteBackupState(
        val webDavUrl: String = "",
        val webDavUser: String = "",
        val hasWebDavPassword: Boolean = false,
        val frequency: AutoBackupFrequency = AutoBackupFrequency.OFF,
        val autoToWebDav: Boolean = false,
        val autoFolderUri: String? = null,
        val autoFolderName: String? = null,
        val keepCount: Int = 7,
        val hasAutoPassword: Boolean = false,
        val lastRunLabel: String? = null,
        val lastRunOk: Boolean = true,
    ) {
        val hasWebDav: Boolean get() = WebDavConfig.urlProblem(webDavUrl) == null
        val hasDestination: Boolean get() = (autoToWebDav && hasWebDav) || autoFolderUri != null
    }

    var remoteBackup by mutableStateOf(RemoteBackupState())
        private set
    /** Backups found in the WebDAV folder; null until the list was loaded. */
    var webDavFiles by mutableStateOf<List<RemoteBackupFile>?>(null)
        private set

    fun refreshRemoteBackupState() {
        val ctx = appContext ?: return
        val s = RemoteBackupSettings(ctx)
        val folder = s.autoFolderUri
        remoteBackup = RemoteBackupState(
            webDavUrl = s.webDavUrl,
            webDavUser = s.webDavUser,
            hasWebDavPassword = s.hasWebDavPassword,
            frequency = s.frequency,
            autoToWebDav = s.autoToWebDav,
            autoFolderUri = folder,
            autoFolderName = folder?.let { uri ->
                runCatching { androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, Uri.parse(uri))?.name }.getOrNull() ?: "Chosen folder"
            },
            keepCount = s.keepCount,
            hasAutoPassword = s.hasAutoPassword,
            lastRunLabel = s.lastRunLabel,
            lastRunOk = s.lastRunOk,
        )
    }

    /** Saves the WebDAV folder and checks it by connecting. A blank [password] keeps the saved one. */
    fun saveWebDav(url: String, user: String, password: String) {
        WebDavConfig.urlProblem(url)?.let { backupMessage = it; return }
        runBackupJob("Connecting to the WebDAV folder…") { ctx ->
            val s = RemoteBackupSettings(ctx)
            s.webDavUrl = url
            s.webDavUser = user
            if (password.isNotEmpty()) s.setWebDavPassword(password)
            refreshRemoteBackupState()
            webDavFiles = null
            val cfg = s.webDav() ?: error("the WebDAV address can't be used")
            val count = withContext(Dispatchers.IO) { WebDav.ensureFolder(cfg); WebDav.list(cfg).size }
            AutoBackup.reschedule(ctx)
            backupMessage = "Saved. Connected to the WebDAV folder — $count Lift Tracker backup(s) in it."
        }
    }

    fun removeWebDav() {
        val ctx = appContext ?: return
        RemoteBackupSettings(ctx).clearWebDav()
        webDavFiles = null
        AutoBackup.reschedule(ctx)
        refreshRemoteBackupState()
        backupMessage = "Removed the WebDAV folder from this phone. Backups already on the server are untouched."
    }

    /** Makes a backup and uploads it to the WebDAV folder. [password] null = unencrypted (data only). */
    fun backupToWebDav(kind: BackupKind, password: CharArray?) = runBackupJob("Preparing backup…") { ctx ->
        saveSettingsToPrefs()
        val cfg = RemoteBackupSettings(ctx).webDav() ?: error("set up the WebDAV folder first")
        val manager = BackupManager(ctx)
        val name = manager.fileName(kind, password != null)
        val manifest = withContext(Dispatchers.IO) {
            val tmp = File.createTempFile("out", ".bak", ctx.cacheDir)
            try {
                val m = manager.create(kind, tmp, password) { step -> viewModelScope.launch { if (backupBusy) backupProgress = step } }
                viewModelScope.launch { if (backupBusy) backupProgress = "Uploading…" }
                WebDav.ensureFolder(cfg)
                WebDav.upload(cfg, name, tmp)
                m
            } finally {
                tmp.delete()
            }
        }
        webDavFiles = null
        backupMessage = "Uploaded $name to WebDAV: ${manifest.summary()}." +
            if (password != null) " The file is encrypted with your password." else ""
    }

    fun loadWebDavFiles() = runBackupJob("Reading the WebDAV folder…") { ctx ->
        val cfg = RemoteBackupSettings(ctx).webDav() ?: error("set up the WebDAV folder first")
        webDavFiles = withContext(Dispatchers.IO) { WebDav.list(cfg) }
    }

    /** Downloads a backup from the WebDAV folder and opens it for restore (password and confirmation follow). */
    fun restoreFromWebDav(name: String) = runBackupJob("Downloading $name…") { ctx ->
        if (isWorkoutActive) error("finish or discard the running workout before restoring")
        cancelRestore()
        val cfg = RemoteBackupSettings(ctx).webDav() ?: error("set up the WebDAV folder first")
        val local = withContext(Dispatchers.IO) {
            File.createTempFile("picked", ".bak", ctx.cacheDir).also { file ->
                try { WebDav.download(cfg, name, file) } catch (e: Exception) { file.delete(); throw e }
            }
        }
        openRestoreFile(ctx, local)
    }

    /** Changes the automatic backup's settings and re-schedules it. */
    fun updateAutoBackup(
        frequency: AutoBackupFrequency = remoteBackup.frequency,
        toWebDav: Boolean = remoteBackup.autoToWebDav,
        keepCount: Int = remoteBackup.keepCount,
    ) {
        val ctx = appContext ?: return
        RemoteBackupSettings(ctx).apply {
            this.frequency = frequency
            this.autoToWebDav = toWebDav
            this.keepCount = keepCount
        }
        AutoBackup.reschedule(ctx)
        refreshRemoteBackupState()
    }

    /** Sets (or with null clears) the phone folder automatic backups are written to. */
    fun setAutoBackupFolder(uri: Uri?) {
        val ctx = appContext ?: return
        val s = RemoteBackupSettings(ctx)
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        s.autoFolderUri?.let { old -> runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) } }
        // Keeps access to the folder after the app restarts, which the background run needs.
        uri?.let { runCatching { ctx.contentResolver.takePersistableUriPermission(it, flags) } }
        s.autoFolderUri = uri?.toString()
        AutoBackup.reschedule(ctx)
        refreshRemoteBackupState()
    }

    fun setAutoBackupPassword(password: CharArray?) {
        val ctx = appContext ?: return
        runCatching { RemoteBackupSettings(ctx).setAutoPassword(password) }
            .onFailure { backupMessage = "Couldn't store the password on this device: ${it.message}" }
        refreshRemoteBackupState()
    }

    fun runAutoBackupNow() = runBackupJob("Backing up…") { ctx ->
        saveSettingsToPrefs()
        val result = withContext(Dispatchers.IO) { AutoBackup.run(ctx) }
        refreshRemoteBackupState()
        webDavFiles = null
        backupMessage = result.message
    }

    fun submitRestorePassword(password: CharArray) = runBackupJob("Decrypting…") { ctx ->
        val file = restoreFile ?: error("no backup file selected")
        // A wrong password leaves the dialog open so the user can try again.
        pendingRestore = withContext(Dispatchers.IO) { BackupManager(ctx).open(file, password) }
        restoreNeedsPassword = false
    }

    fun confirmRestore() {
        val opened = pendingRestore ?: return
        pendingRestore = null
        runBackupJob("Restoring…") { ctx ->
            try {
                withContext(Dispatchers.IO) {
                    BackupManager(ctx).restore(opened) { step -> viewModelScope.launch { if (backupBusy) backupProgress = step } }
                }
                reloadFromDisk()
                backupMessage = "Restored ${opened.manifest.kind.phrase} from ${opened.manifest.createdAt.replace('T', ' ')}" +
                    " (${opened.manifest.summary()})."
            } finally {
                opened.cleanup()
                restoreFile?.delete()
                restoreFile = null
            }
        }
    }

    fun cancelRestore() {
        pendingRestore?.cleanup()
        pendingRestore = null
        restoreNeedsPassword = false
        restoreFile?.delete()
        restoreFile = null
    }

    /** Re-reads everything a restore may have replaced: settings, routines, AI data and workout history. */
    private suspend fun reloadFromDisk() {
        val ctx = appContext ?: return
        val sp = prefs ?: return
        loadSettingsFromPrefs(sp)
        runCatching { ai.attach(AiStore(ctx)) }
        val storage = workoutStorage
        if (storage != null) {
            val sessions = withContext(Dispatchers.IO) { runCatching { storage.loadWorkoutSessions() }.getOrNull() }
            if (sessions != null) {
                workoutHistory.clear()
                workoutHistory.addAll(sessions)
            }
        }
        publishWearRoutines()
    }

    /** A file opened with or shared to the app: routine JSON or a workout history CSV. */
    fun importSharedFile(uri: Uri) {
        viewModelScope.launch {
            val head = runCatching { readTextFromUri(uri) }.getOrNull()?.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            if (head != null && (head.startsWith("{") || head.startsWith("["))) {
                selectedTab = MainTab.ROUTINES
                importRoutinesFromText(head)
            } else {
                openSettingsLayer(SettingsSection.DATA)
                importWorkoutHistoryFromUri(uri)
            }
        }
    }
}
