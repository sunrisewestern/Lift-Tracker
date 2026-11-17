package com.lifttracker.app.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.lifttracker.app.domain.ExerciseMeasure
import com.lifttracker.app.domain.RoutineTemplate
import com.lifttracker.app.domain.SampleCatalog
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.storage.SQLiteWorkoutStorage
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Read-only export API for other apps.
 *
 *   content://com.lifttracker.app.export/workouts.json   all logged workouts (WorkoutExporter JSON schema)
 *   content://com.lifttracker.app.export/workouts.csv    one row per set
 *   content://com.lifttracker.app.export/workout-history.csv   one row per set, common strength-log layout (WorkoutCsv)
 *   content://com.lifttracker.app.export/routines.json   routine templates (RoutineCodec); `?id=` picks one
 *
 * Optional query parameter: `archived=false` leaves out archived workouts.
 *
 * Callers need the `com.lifttracker.app.permission.READ_EXPORT` runtime permission, or a one-off
 * URI grant (which is how the in-app "Share export" action hands a file to another app).
 * Open the URI with ContentResolver.openInputStream(); writes are rejected.
 */
class ExportProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun formatFor(uri: Uri): ExportFormat = when (uri.lastPathSegment) {
        PATH_WORKOUTS_JSON, PATH_ROUTINES_JSON -> ExportFormat.JSON
        PATH_WORKOUTS_CSV, PATH_HISTORY_CSV -> ExportFormat.CSV
        else -> throw FileNotFoundException("Unknown export: $uri")
    }

    private fun storedRoutines(ctx: Context, onlyId: String?): List<RoutineTemplate> {
        val stored = ctx.getSharedPreferences("lifttracker_settings", Context.MODE_PRIVATE).getString("routines_json", null)
        val all = if (stored == null) {
            SampleCatalog.defaultRoutines
        } else {
            runCatching {
                RoutineCodec.parseRoutines(stored, { id, title -> ResolvedExercise(id, ExerciseMeasure.infer(title)) })
            }.getOrDefault(emptyList())
        }
        return all.filter { onlyId == null || it.id == onlyId }
    }

    private fun displayName(ctx: Context, uri: Uri): String = when (uri.lastPathSegment) {
        PATH_ROUTINES_JSON -> RoutineCodec.suggestedFileName(storedRoutines(ctx, uri.getQueryParameter("id")))
        PATH_HISTORY_CSV -> "lift-tracker-workouts.csv"
        else -> WorkoutExporter.suggestedFileName(formatFor(uri))
    }

    override fun getType(uri: Uri): String? =
        runCatching { formatFor(uri).mimeType }.getOrNull()

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val format = formatFor(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns, 1)
        cursor.addRow(
            columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> context?.let { displayName(it, uri) }
                        ?: WorkoutExporter.suggestedFileName(format)
                    else -> null // size is unknown until the export is generated
                }
            }
        )
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Export is read-only")
        val format = formatFor(uri)
        val includeArchived = uri.getQueryParameter("archived") != "false"
        val ctx = context ?: throw FileNotFoundException("Provider not attached")
        val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            try {
                FileOutputStream(pipe[1].fileDescriptor).use { out ->
                    val text = when (uri.lastPathSegment) {
                        PATH_ROUTINES_JSON -> RoutineCodec.exportRoutines(storedRoutines(ctx, uri.getQueryParameter("id")))
                        PATH_HISTORY_CSV -> {
                            val unitName = ctx.getSharedPreferences("lifttracker_settings", Context.MODE_PRIVATE)
                                .getString("weight_unit", WeightUnit.KG.name)
                            val unit = WeightUnit.entries.find { it.name == unitName } ?: WeightUnit.KG
                            WorkoutCsv.exportWorkouts(
                                SQLiteWorkoutStorage(ctx).loadWorkoutSessions(), unit, includeArchived = includeArchived
                            )
                        }
                        else -> {
                            val workouts = SQLiteWorkoutStorage(ctx).loadWorkoutSessions()
                            when (format) {
                                ExportFormat.JSON -> WorkoutExporter.exportJson(workouts, includeArchived = includeArchived)
                                ExportFormat.CSV -> WorkoutExporter.exportWorkoutsCsv(workouts, includeArchived)
                            }
                        }
                    }
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // Reader closed the pipe early or the database was unavailable; nothing more to send.
            } finally {
                runCatching { pipe[1].close() }
            }
        }.start()
        return pipe[0]
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    companion object {
        const val PATH_WORKOUTS_JSON = "workouts.json"
        const val PATH_WORKOUTS_CSV = "workouts.csv"
        const val PATH_HISTORY_CSV = "workout-history.csv"
        const val PATH_ROUTINES_JSON = "routines.json"

        fun historyCsvUri(context: Context): Uri = Uri.parse("content://${authority(context)}/$PATH_HISTORY_CSV")

        /** All routines, or just one when [routineId] is given. */
        fun routinesUri(context: Context, routineId: String? = null): Uri =
            Uri.parse("content://${authority(context)}/$PATH_ROUTINES_JSON").buildUpon()
                .apply { if (routineId != null) appendQueryParameter("id", routineId) }
                .build()
        const val READ_PERMISSION_SUFFIX = ".permission.READ_EXPORT"

        fun authority(context: Context): String = "${context.packageName}.export"

        fun uriFor(context: Context, format: ExportFormat): Uri {
            val path = if (format == ExportFormat.JSON) PATH_WORKOUTS_JSON else PATH_WORKOUTS_CSV
            return Uri.parse("content://${authority(context)}/$path")
        }
    }
}
