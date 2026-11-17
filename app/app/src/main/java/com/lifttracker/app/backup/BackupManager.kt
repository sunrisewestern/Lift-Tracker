package com.lifttracker.app.backup

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.lifttracker.app.ai.AiStore
import com.lifttracker.app.ai.KeyCipher
import com.lifttracker.app.domain.CustomExerciseCodec
import com.lifttracker.app.domain.ExerciseDefinition
import com.lifttracker.app.notify.RestTimerAlerts
import com.lifttracker.app.storage.SQLiteWorkoutStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class BackupKind(val fileTag: String, val label: String, /** [label] as written mid-sentence. */ val phrase: String) {
    CONFIG("config", "Settings, API keys & your exercises", "settings, API keys & your exercises"),
    DATA("data", "Workouts, routines & AI history", "workouts, routines & AI history"),
}

/** What a backup holds, read from its manifest.json. */
data class BackupManifest(
    val kind: BackupKind,
    val createdAt: String,
    val device: String,
    val appVersion: String,
    val counts: Map<String, Int>,
) {
    fun summary(): String = counts.entries.joinToString(", ") { (k, v) -> "$v $k" }

    fun toJson(): String = JSONObject()
        .put("app", APP_TAG)
        .put("kind", kind.name)
        .put("createdAt", createdAt)
        .put("device", device)
        .put("appVersion", appVersion)
        .put("counts", JSONObject().apply { counts.forEach { (k, v) -> put(k, v) } })
        .toString()

    companion object {
        const val APP_TAG = "LiftTracker"

        fun fromJson(text: String): BackupManifest {
            val o = JSONObject(text)
            if (o.optString("app") != APP_TAG) throw IOException("This isn't a Lift Tracker backup")
            val counts = LinkedHashMap<String, Int>()
            o.optJSONObject("counts")?.let { c -> c.keys().forEach { counts[it] = c.optInt(it) } }
            return BackupManifest(
                kind = BackupKind.entries.find { it.name == o.optString("kind") }
                    ?: throw IOException("Unknown backup type \"${o.optString("kind")}\""),
                createdAt = o.optString("createdAt"),
                device = o.optString("device"),
                appVersion = o.optString("appVersion"),
                counts = counts,
            )
        }
    }
}

/**
 * Builds and restores backup files. A backup is a zip with `manifest.json` plus:
 *  - config: `config.json` – app preferences (units, rest timer, theme, notification choices), the
 *    AI providers and picture model with their API keys in plain text, and the user's own exercises. Because of the keys this file is always
 *    password-encrypted; on restore the keys are re-encrypted with the new phone's keystore.
 *  - data: `workouts.jsonl` (one logged workout per line), `data.json` (routines, custom exercises,
 *    how each exercise is tracked, body measurements, AI chats and analyses).
 *  - both: `custom_exercises/<file>` – the pictures of the user's own exercises.
 * The zip is optionally encrypted with [BackupCrypto] (required for config).
 *
 * Restoring replaces what is on the phone with what is in the file, except that a settings backup
 * only adds its custom exercises to the ones already there.
 */
class BackupManager(private val context: Context) {

    private val storage = SQLiteWorkoutStorage(context)
    private val settings: SharedPreferences get() = context.getSharedPreferences(RestTimerAlerts.PREFS, Context.MODE_PRIVATE)
    private val ai: SharedPreferences get() = context.getSharedPreferences(AiStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun fileName(kind: BackupKind, encrypted: Boolean): String =
        "LiftTracker-${kind.fileTag}-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}" +
            if (encrypted) ".ltbackup" else ".zip"

    // ---- Create -------------------------------------------------------------------------------

    /** Writes a backup to [out]. Returns its manifest. [password] null = unencrypted (data only). */
    fun create(kind: BackupKind, out: File, password: CharArray?, progress: (String) -> Unit = {}): BackupManifest {
        require(kind == BackupKind.DATA || password != null) { "Settings backups need a password – they contain your API keys." }
        val zip = File.createTempFile("backup", ".zip", context.cacheDir)
        try {
            lateinit var manifest: BackupManifest
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                val counts = when (kind) {
                    BackupKind.CONFIG -> writeConfig(z)
                    BackupKind.DATA -> writeData(z, progress)
                }
                manifest = BackupManifest(
                    kind = kind,
                    createdAt = LocalDateTime.now().withNano(0).toString(),
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
                    counts = counts,
                )
                // Written last because the counts are only known now; ZipFile reads entries in any order.
                z.putNextEntry(ZipEntry(MANIFEST)); z.write(manifest.toJson().toByteArray()); z.closeEntry()
            }
            if (password != null) {
                progress("Encrypting…")
                BackupCrypto.encrypt(zip, out, password)
            } else {
                zip.copyTo(out, overwrite = true)
            }
            return manifest
        } finally {
            zip.delete()
        }
    }

    private fun writeConfig(z: ZipOutputStream): Map<String, Int> {
        val providers = JSONArray(ai.getString("providers", null) ?: "[]")
        for (i in 0 until providers.length()) {
            val p = providers.getJSONObject(i)
            p.put("apiKey", runCatching { KeyCipher.decrypt(p.optString("encryptedKey")) }.getOrDefault(""))
            p.remove("encryptedKey")
        }
        val prefs = dumpPrefs(settings) { it !in DATA_KEYS && it !in DEVICE_KEYS }
        val root = JSONObject()
            .put("prefs", prefs)
            .put("providers", providers)
            .put("activeProvider", ai.getString("active_provider", null) ?: JSONObject.NULL)
        // The picture model, with its key in plain text like the providers'.
        runCatching { JSONObject(ai.getString("image_model", null) ?: "") }.getOrNull()?.let { m ->
            m.put("apiKey", runCatching { KeyCipher.decrypt(m.optString("encryptedKey")) }.getOrDefault(""))
            m.remove("encryptedKey")
            root.put("imageModel", m)
        }
        // The user's own exercises travel with the settings too (and with the data backup).
        val customJson = settings.getString(CUSTOM_EXERCISES_KEY, null)
        customJson?.let { root.put(CUSTOM_EXERCISES_KEY, it) }
        val custom = CustomExerciseCodec.decode(customJson)
        // …with how each of them is tracked (weight & reps, duration, …).
        runCatching { JSONObject(settings.getString("exercise_measures", null) ?: "{}") }.getOrNull()?.let { all ->
            val mine = JSONObject()
            custom.forEach { e -> if (all.has(e.id)) mine.put(e.id, all.optString(e.id)) }
            root.put("customExerciseMeasures", mine)
        }
        z.putNextEntry(ZipEntry(CONFIG_JSON)); z.write(root.toString().toByteArray()); z.closeEntry()
        writeCustomImages(z, custom)
        return linkedMapOf("settings" to prefs.length(), "AI providers" to providers.length(), "custom exercises" to custom.size)
    }

    private val imageDir: File get() = File(context.filesDir, CustomExerciseCodec.IMAGE_DIR)

    private fun writeCustomImages(z: ZipOutputStream, custom: List<ExerciseDefinition>) {
        custom.mapNotNull { CustomExerciseCodec.imageFileName(it) }.distinct().forEach { name ->
            val file = File(imageDir, name)
            if (!file.isFile) return@forEach
            z.putNextEntry(ZipEntry(IMAGES_PREFIX + name)); file.inputStream().use { it.copyTo(z) }; z.closeEntry()
        }
    }

    /** Copies the backup's exercise pictures onto the phone. [replace] first removes the pictures that are there. */
    private fun restoreCustomImages(z: ZipFile, replace: Boolean) {
        val dir = imageDir
        if (replace) dir.listFiles()?.forEach { it.delete() }
        z.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(IMAGES_PREFIX) }.forEach { entry ->
            // Bare file names only: an entry like "custom_exercises/../x" must not escape the folder.
            val name = entry.name.removePrefix(IMAGES_PREFIX)
            if (name.isBlank() || name.contains('/') || name.contains('\\') || name.startsWith(".")) return@forEach
            dir.mkdirs()
            z.getInputStream(entry).use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
        }
    }

    private fun writeData(z: ZipOutputStream, progress: (String) -> Unit): Map<String, Int> {
        val rows = storage.fetchWorkouts()
        z.putNextEntry(ZipEntry(WORKOUTS))
        rows.forEachIndexed { index, row ->
            val line = JSONObject().put("id", row.id).put("end_time", row.endTime).put("json", row.json)
            z.write(line.toString().toByteArray()); z.write('\n'.code)
            if (index % 100 == 0) progress("Backing up workouts… ${index + 1}")
        }
        z.closeEntry()
        val data = JSONObject()
        DATA_KEYS.forEach { key -> settings.getString(key, null)?.let { data.put(key, it) } }
        val chats = ai.getString("chats", null)
        val analyses = ai.getString("analyses", null)
        chats?.let { data.put("ai_chats", it) }
        analyses?.let { data.put("ai_analyses", it) }
        z.putNextEntry(ZipEntry(DATA_JSON)); z.write(data.toString().toByteArray()); z.closeEntry()
        val custom = CustomExerciseCodec.decode(settings.getString(CUSTOM_EXERCISES_KEY, null))
        writeCustomImages(z, custom)
        fun count(json: String?, arrayKey: String? = null): Int = runCatching {
            when {
                json == null -> 0
                arrayKey != null -> JSONObject(json).optJSONArray(arrayKey)?.length() ?: 0
                else -> JSONArray(json).length()
            }
        }.getOrDefault(0)
        return linkedMapOf(
            "workouts" to rows.size,
            "routines" to count(settings.getString("routines_json", null), "routines"),
            "custom exercises" to maxOf(custom.size, count(settings.getString("custom_exercise_titles", null))),
            "AI chats" to count(chats),
            "AI analyses" to count(analyses),
        )
    }

    // ---- Restore ------------------------------------------------------------------------------

    /** Decrypts (if needed) into a plain zip and reads its manifest. Caller calls [Opened.cleanup]. */
    fun open(file: File, password: CharArray?): Opened {
        val zip = if (BackupCrypto.isEncrypted(file)) {
            password ?: throw WrongPasswordException()
            File.createTempFile("restore", ".zip", context.cacheDir).also {
                try { BackupCrypto.decrypt(file, it, password) } catch (e: Exception) { it.delete(); throw e }
            }
        } else file
        val manifest = try {
            ZipFile(zip).use { z ->
                val e = z.getEntry(MANIFEST) ?: throw IOException("This isn't a Lift Tracker backup (no manifest)")
                BackupManifest.fromJson(z.getInputStream(e).reader().readText())
            }
        } catch (e: ZipException) {
            if (zip != file) zip.delete()
            throw IOException("This isn't a Lift Tracker backup file")
        } catch (e: org.json.JSONException) {
            if (zip != file) zip.delete()
            throw IOException("This backup's manifest can't be read")
        }
        return Opened(zip, manifest, temporary = zip != file)
    }

    data class Opened(val zip: File, val manifest: BackupManifest, val temporary: Boolean) {
        fun cleanup() { if (temporary) zip.delete() }
    }

    /** Replaces the current settings or data with the backup's. */
    fun restore(opened: Opened, progress: (String) -> Unit = {}) {
        ZipFile(opened.zip).use { z ->
            when (opened.manifest.kind) {
                BackupKind.CONFIG -> restoreConfig(z)
                BackupKind.DATA -> restoreData(z, progress)
            }
        }
    }

    private fun restoreConfig(z: ZipFile) {
        val root = JSONObject(
            z.getInputStream(z.getEntry(CONFIG_JSON) ?: throw IOException("config.json missing")).reader().readText()
        )
        val providers = root.optJSONArray("providers") ?: JSONArray()
        for (i in 0 until providers.length()) {
            val p = providers.getJSONObject(i)
            // Re-encrypt with this phone's keystore.
            p.put("encryptedKey", KeyCipher.encrypt(p.optString("apiKey")))
            p.remove("apiKey")
        }
        ai.edit()
            .putString("providers", providers.toString())
            .apply { if (root.isNull("activeProvider")) remove("active_provider") else putString("active_provider", root.optString("activeProvider")) }
            .apply {
                val m = root.optJSONObject("imageModel")
                if (m == null) remove("image_model") else {
                    m.put("encryptedKey", KeyCipher.encrypt(m.optString("apiKey")))
                    m.remove("apiKey")
                    putString("image_model", m.toString())
                }
            }
            .commit()
        // Custom exercises from a settings backup are added to the ones on the phone (same id: the
        // backup's version wins); none are removed, since workouts and routines may use them.
        if (root.has(CUSTOM_EXERCISES_KEY)) {
            val incoming = CustomExerciseCodec.decode(root.optString(CUSTOM_EXERCISES_KEY))
            val ids = incoming.map { it.id }.toHashSet()
            val merged = CustomExerciseCodec.decode(settings.getString(CUSTOM_EXERCISES_KEY, null)).filter { it.id !in ids } + incoming
            restoreCustomImages(z, replace = false)
            val measures = runCatching { JSONObject(settings.getString("exercise_measures", null) ?: "{}") }.getOrDefault(JSONObject())
            root.optJSONObject("customExerciseMeasures")?.let { m -> m.keys().forEach { id -> if (id in ids) measures.put(id, m.optString(id)) } }
            settings.edit()
                .putString(CUSTOM_EXERCISES_KEY, CustomExerciseCodec.encode(merged))
                .putString("exercise_measures", measures.toString())
                .commit()
        }
        root.optJSONObject("prefs")?.let { values ->
            val edit = settings.edit()
            // Settings are replaced as a whole; data and device-specific keys in the same file stay.
            settings.all.keys.filter { it !in DATA_KEYS && it !in DEVICE_KEYS }.forEach { edit.remove(it) }
            loadPrefs(edit, values) { it !in DATA_KEYS && it !in DEVICE_KEYS }
            edit.commit()
        }
    }

    private fun restoreData(z: ZipFile, progress: (String) -> Unit) {
        // Check everything is present and readable before deleting anything.
        val workoutsEntry = z.getEntry(WORKOUTS) ?: throw IOException("Backup is missing $WORKOUTS")
        val data = JSONObject(
            z.getInputStream(z.getEntry(DATA_JSON) ?: throw IOException("Backup is missing $DATA_JSON")).reader().readText()
        )
        val rows = ArrayList<SQLiteWorkoutStorage.StoredWorkoutRow>()
        BufferedReader(InputStreamReader(z.getInputStream(workoutsEntry), Charsets.UTF_8)).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (line.isBlank()) continue
                val o = JSONObject(line)
                rows += SQLiteWorkoutStorage.StoredWorkoutRow(o.getString("id"), o.getLong("end_time"), o.getString("json"))
            }
        }
        progress("Restoring ${rows.size} workouts…")
        storage.clearWorkouts()
        rows.chunked(200).forEach { storage.storeWorkouts(it) }
        val edit = settings.edit()
        DATA_KEYS.forEach { key -> if (data.has(key)) edit.putString(key, data.getString(key)) else edit.remove(key) }
        // The sample workouts must not come back on top of a restored (possibly empty) history.
        edit.putBoolean("seeded_default_workouts", true)
        edit.commit()
        restoreCustomImages(z, replace = true)
        ai.edit()
            .apply { if (data.has("ai_chats")) putString("chats", data.getString("ai_chats")) else remove("chats") }
            .apply { if (data.has("ai_analyses")) putString("analyses", data.getString("ai_analyses")) else remove("analyses") }
            .commit()
    }

    // ---- SharedPreferences <-> JSON -----------------------------------------------------------

    private fun dumpPrefs(prefs: SharedPreferences, include: (String) -> Boolean): JSONObject = JSONObject().apply {
        prefs.all.forEach { (key, value) ->
            if (!include(key)) return@forEach
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is Boolean -> entry.put("t", "b").put("v", value)
                is Set<*> -> entry.put("t", "set").put("v", JSONArray(value.map { it.toString() }))
                else -> return@forEach
            }
            put(key, entry)
        }
    }

    private fun loadPrefs(edit: SharedPreferences.Editor, values: JSONObject, include: (String) -> Boolean) {
        values.keys().forEach { key ->
            if (!include(key)) return@forEach
            val o = values.optJSONObject(key) ?: return@forEach
            when (o.optString("t")) {
                "s" -> edit.putString(key, o.optString("v"))
                "i" -> edit.putInt(key, o.optInt("v"))
                "l" -> edit.putLong(key, o.optLong("v"))
                "f" -> edit.putFloat(key, o.optDouble("v").toFloat())
                "b" -> edit.putBoolean(key, o.optBoolean("v"))
                "set" -> o.optJSONArray("v")?.let { a -> edit.putStringSet(key, (0 until a.length()).map { a.optString(it) }.toSet()) }
            }
        }
    }

    companion object {
        private const val MANIFEST = "manifest.json"
        private const val CONFIG_JSON = "config.json"
        private const val WORKOUTS = "workouts.jsonl"
        private const val DATA_JSON = "data.json"

        const val CUSTOM_EXERCISES_KEY = "custom_exercises_json"
        private const val IMAGES_PREFIX = "custom_exercises/"

        /** Keys of the settings file that are user data, not settings; they go in the data backup. */
        val DATA_KEYS = listOf("routines_json", "custom_exercise_titles", CUSTOM_EXERCISES_KEY, "exercise_measures", "body_measurements")

        /** Tied to this phone (a ringtone that may not exist elsewhere, sync bookkeeping); never backed up. */
        val DEVICE_KEYS = setOf(RestTimerAlerts.KEY_SOUND_URI, "hc_last_measurement_sync", "seeded_default_workouts")
    }
}
