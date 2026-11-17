package com.lifttracker.app.health

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutMechanics
import com.lifttracker.app.domain.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

enum class MeasurementType(
    val writeKey: String,
    val readKey: String,
    val unit: String,
    val recordClass: KClass<out Record>
) {
    WEIGHT("write_weight", "read_weight", "kg", WeightRecord::class),
    BODY_FAT("write_bodyFat", "read_bodyFat", "%", BodyFatRecord::class)
}

data class HealthMeasurement(
    val type: MeasurementType,
    val value: Double,
    val time: Instant,
    val recordId: String,
    val originPackage: String
)

data class WorkoutWriteResult(
    val wroteSession: Boolean,
    val wroteCalories: Boolean,
    val heartRateSamplesWritten: Int
)

data class MeasurementChanges(
    val measurements: List<HealthMeasurement>,
    /** Health Connect record ids deleted since the last import. */
    val deletedRecordIds: List<String>,
    /** True when the stored token had expired and a full re-read of recent data was done instead. */
    val tokenWasReset: Boolean
)

/**
 * Real Health Connect bridge: availability, permissions, workout export
 * (ExerciseSessionRecord + TotalCaloriesBurnedRecord + HeartRateRecord), body measurements,
 * and incremental measurement import through change tokens.
 */
class HealthConnectManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("lifttracker_health_connect", Context.MODE_PRIVATE)

    val sdkStatus: Int
        get() = HealthConnectClient.getSdkStatus(appContext, WorkoutMechanics.HEALTH_CONNECT_PROVIDER_PACKAGE)

    val isAvailable: Boolean
        get() = sdkStatus == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(appContext, WorkoutMechanics.HEALTH_CONNECT_PROVIDER_PACKAGE)
    }

    suspend fun grantedPermissions(): Set<String> {
        if (!isAvailable) return emptySet()
        return client.permissionController.getGrantedPermissions()
    }

    /** Intent that installs/updates the Health Connect provider (Android 13 and below). */
    fun providerInstallIntent(): Intent {
        val pkg = WorkoutMechanics.HEALTH_CONNECT_PROVIDER_PACKAGE
        return Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse("market://details?id=$pkg&url=healthconnect%3A%2F%2Fonboarding")
            putExtra("overlay", true)
            putExtra("callerId", appContext.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Intent that opens Health Connect's own settings so the user can review or revoke access. */
    fun manageDataIntent(): Intent {
        val action = if (Build.VERSION.SDK_INT >= 34) {
            "android.health.connect.action.HEALTH_HOME_SETTINGS"
        } else {
            HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
        }
        return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Writes [session] to Health Connect. Records carry a clientRecordId derived from the session id,
     * so syncing the same workout again updates the existing records instead of duplicating them.
     * Only record types whose write permission is granted are written.
     */
    suspend fun writeWorkout(session: WorkoutSession): WorkoutWriteResult {
        val granted = grantedPermissions()
        check(PERMISSION_WRITE_EXERCISE in granted) { "Exercise write permission is not granted" }

        val start = Instant.ofEpochSecond(session.startTimeEpochSeconds)
        val end = Instant.ofEpochSecond(session.endTimeEpochSeconds).let {
            if (it.isAfter(start)) it else start.plusSeconds(60)
        }
        val zone = ZoneId.systemDefault()
        val startOffset = zone.rules.getOffset(start)
        val endOffset = zone.rules.getOffset(end)
        val version = System.currentTimeMillis()

        val records = mutableListOf<Record>()
        records += ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = startOffset,
            endTime = end,
            endZoneOffset = endOffset,
            metadata = Metadata.manualEntry("workout_${session.id}", version),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = session.title.ifBlank { "Strength training" },
            notes = buildNotes(session)
        )

        val writeCalories = PERMISSION_WRITE_CALORIES in granted && session.caloriesBurnedKcal > 0.0
        if (writeCalories) {
            records += TotalCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = startOffset,
                endTime = end,
                endZoneOffset = endOffset,
                energy = Energy.kilocalories(session.caloriesBurnedKcal),
                metadata = Metadata.manualEntry("calories_${session.id}", version)
            )
        }

        // Heart rate is only exported when real samples were recorded (streamed from the watch).
        val samples = session.heartRateSamples
            .map { Instant.ofEpochMilli(it.heartRateTimestampMs) to it.heartRate.toLong() }
            .filter { (time, bpm) -> bpm in 1..300 && !time.isBefore(start) && !time.isAfter(end) }
            .sortedBy { it.first }
            .distinctBy { it.first }
        val writeHeartRate = PERMISSION_WRITE_HEART_RATE in granted && samples.isNotEmpty()
        if (writeHeartRate) {
            records += HeartRateRecord(
                startTime = start,
                startZoneOffset = startOffset,
                endTime = end,
                endZoneOffset = endOffset,
                samples = samples.map { (time, bpm) -> HeartRateRecord.Sample(time, bpm) },
                metadata = Metadata.manualEntry("heartrate_${session.id}", version)
            )
        }

        client.insertRecords(records)
        return WorkoutWriteResult(
            wroteSession = true,
            wroteCalories = writeCalories,
            heartRateSamplesWritten = if (writeHeartRate) samples.size else 0
        )
    }

    private fun buildNotes(session: WorkoutSession): String {
        val lines = session.exercises.mapNotNull { ex ->
            val done = ex.sets.filter { it.completed }
            if (done.isEmpty()) return@mapNotNull null
            val sets = done.joinToString(", ") { s -> WorkoutMechanics.formatSetSummary(s, ex.measure, WeightUnit.KG) }
            "${ex.exerciseTitle}: $sets"
        }
        return (listOf(session.notes).filter { it.isNotBlank() } + lines).joinToString("\n")
    }

    /**
     * Writes today's weight (kg) or body fat (%). This app's own entry for today is replaced via a
     * per-day clientRecordId; Health Connect never lets an app delete other apps' records.
     */
    suspend fun writeMeasurement(type: MeasurementType, value: Double, time: Instant = Instant.now()) {
        val permission = HealthPermission.getWritePermission(type.recordClass)
        check(permission in grantedPermissions()) { "${type.writeKey} permission is not granted" }
        val zone = ZoneId.systemDefault()
        val offset = zone.rules.getOffset(time)
        val day = LocalDate.ofInstant(time, zone)
        val metadata = Metadata.manualEntry("${type.writeKey}_$day", System.currentTimeMillis())
        val record: Record = when (type) {
            MeasurementType.WEIGHT -> {
                require(value > 0.0 && value < 1000.0) { "Weight must be between 0 and 1000 kg" }
                WeightRecord(time = time, zoneOffset = offset, weight = Mass.kilograms(value), metadata = metadata)
            }
            MeasurementType.BODY_FAT -> {
                require(value in 0.0..100.0) { "Body fat must be between 0 and 100 %" }
                BodyFatRecord(time = time, zoneOffset = offset, percentage = Percentage(value), metadata = metadata)
            }
        }
        client.insertRecords(listOf(record))
    }

    /** Reads measurements of the last [days] days, newest first. Types without read permission are skipped. */
    suspend fun readRecentMeasurements(days: Long = 90): List<HealthMeasurement> {
        val granted = grantedPermissions()
        val range = TimeRangeFilter.after(Instant.now().minus(days, ChronoUnit.DAYS))
        val result = mutableListOf<HealthMeasurement>()
        if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
            client.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records
                .mapTo(result) { it.toMeasurement() }
        }
        if (HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
            client.readRecords(ReadRecordsRequest(BodyFatRecord::class, range)).records
                .mapTo(result) { it.toMeasurement() }
        }
        return result.sortedByDescending { it.time }
    }

    fun storedChangeToken(type: MeasurementType): String? = prefs.getString(tokenKey(type), null)

    /**
     * Incremental import: returns weight / body-fat records upserted since the last call and advances
     * the stored change tokens. The first call (or an expired token) falls back to a recent full read.
     */
    suspend fun fetchNewMeasurements(): MeasurementChanges {
        val granted = grantedPermissions()
        val out = mutableListOf<HealthMeasurement>()
        val deleted = mutableListOf<String>()
        var reset = false
        for (type in MeasurementType.entries) {
            if (HealthPermission.getReadPermission(type.recordClass) !in granted) continue
            var token = storedChangeToken(type)
            if (token == null) {
                reset = true
                out += readRecentMeasurements().filter { it.type == type }
                token = client.getChangesToken(ChangesTokenRequest(setOf(type.recordClass)))
            } else {
                var expired = false
                var hasMore = true
                while (hasMore) {
                    val response = client.getChanges(token!!)
                    if (response.changesTokenExpired) {
                        expired = true
                        break
                    }
                    response.changes.forEach { change ->
                        if (change is DeletionChange) {
                            deleted += change.recordId
                        } else if (change is UpsertionChange) {
                            when (val record = change.record) {
                                is WeightRecord -> out += record.toMeasurement()
                                is BodyFatRecord -> out += record.toMeasurement()
                                else -> Unit
                            }
                        }
                    }
                    token = response.nextChangesToken
                    hasMore = response.hasMore
                }
                if (expired) {
                    reset = true
                    out += readRecentMeasurements().filter { it.type == type }
                    token = client.getChangesToken(ChangesTokenRequest(setOf(type.recordClass)))
                }
            }
            prefs.edit().putString(tokenKey(type), token).apply()
        }
        return MeasurementChanges(out.sortedByDescending { it.time }, deleted, reset)
    }

    private fun tokenKey(type: MeasurementType) = "change_token_${type.readKey}"

    private fun WeightRecord.toMeasurement() = HealthMeasurement(
        MeasurementType.WEIGHT, weight.inKilograms, time, metadata.id, metadata.dataOrigin.packageName
    )

    private fun BodyFatRecord.toMeasurement() = HealthMeasurement(
        MeasurementType.BODY_FAT, percentage.value, time, metadata.id, metadata.dataOrigin.packageName
    )

    companion object {
        val PERMISSION_WRITE_EXERCISE = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        val PERMISSION_WRITE_CALORIES = HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
        val PERMISSION_WRITE_HEART_RATE = HealthPermission.getWritePermission(HeartRateRecord::class)

        /** The seven permissions declared in the manifest. */
        val ALL_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            PERMISSION_WRITE_EXERCISE,
            PERMISSION_WRITE_CALORIES,
            PERMISSION_WRITE_HEART_RATE
        )
    }
}
