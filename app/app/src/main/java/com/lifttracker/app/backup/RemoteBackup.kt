package com.lifttracker.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifttracker.app.ai.KeyCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** A WebDAV folder (Nextcloud, a NAS, …). [url] is the folder backups are kept in. */
data class WebDavConfig(val url: String, val user: String, val password: String) {
    /** Folder URL with exactly one trailing slash. */
    val folderUrl: String get() = url.trim().trimEnd('/') + "/"

    companion object {
        /** An error message for a folder address that can't be used, or null when it is fine. */
        fun urlProblem(url: String): String? {
            val u = url.trim()
            return when {
                !u.startsWith("https://") && !u.startsWith("http://") -> "The WebDAV address must start with https:// (or http:// on your own network)."
                u.substringAfter("://").isBlank() -> "Enter the address of the WebDAV folder."
                else -> null
            }
        }
    }
}

/** A backup file found in the WebDAV folder. */
data class RemoteBackupFile(val name: String, val sizeBytes: Long, val modified: String)

/** The few WebDAV calls backups need: check / create the folder, list, upload, download, delete. */
object WebDav {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()
    private val XML = "application/xml; charset=utf-8".toMediaType()
    private val BINARY = "application/octet-stream".toMediaType()
    private const val PROPFIND_BODY =
        """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""

    private fun request(cfg: WebDavConfig, url: String) = Request.Builder().url(url).apply {
        if (cfg.user.isNotBlank()) header("Authorization", Credentials.basic(cfg.user, cfg.password, Charsets.UTF_8))
    }

    private fun fileUrl(cfg: WebDavConfig, name: String) = cfg.folderUrl + Uri.encode(name)

    private fun failure(code: Int, what: String): IOException = IOException(
        when (code) {
            401, 403 -> "The WebDAV server refused the user name or password (HTTP $code)."
            404 -> "$what: not found on the WebDAV server (HTTP 404). Check the folder address."
            409 -> "$what: the parent folder doesn't exist on the server (HTTP 409)."
            507 -> "The WebDAV server is out of space (HTTP 507)."
            else -> "$what failed: the WebDAV server answered HTTP $code."
        },
    )

    private fun propfind(cfg: WebDavConfig, depth: String): Pair<Int, String> {
        val req = request(cfg, cfg.folderUrl).header("Depth", depth).method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).build()
        return http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
    }

    /** Makes sure the folder exists (creating it when only the last part is missing). Throws with a readable message. */
    fun ensureFolder(cfg: WebDavConfig) {
        val (code, _) = propfind(cfg, "0")
        if (code == 207 || code == 200) return
        if (code != 404) throw failure(code, "Opening the folder")
        http.newCall(request(cfg, cfg.folderUrl).method("MKCOL", null).build()).execute().use { resp ->
            // 405: it exists after all.
            if (!resp.isSuccessful && resp.code != 405) throw failure(resp.code, "Creating the folder")
        }
    }

    /** Lift Tracker backups in the folder, newest first. */
    fun list(cfg: WebDavConfig): List<RemoteBackupFile> {
        val (code, body) = propfind(cfg, "1")
        if (code != 207 && code != 200) throw failure(code, "Listing the folder")
        return parseListing(body).filter { it.name.startsWith("LiftTracker-") }.sortedByDescending { backupStamp(it.name) }
    }

    fun upload(cfg: WebDavConfig, name: String, file: File) {
        http.newCall(request(cfg, fileUrl(cfg, name)).put(file.asRequestBody(BINARY)).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw failure(resp.code, "Uploading $name")
        }
    }

    fun download(cfg: WebDavConfig, name: String, dest: File) {
        http.newCall(request(cfg, fileUrl(cfg, name)).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw failure(resp.code, "Downloading $name")
            val body = resp.body ?: throw IOException("Downloading $name: the server sent nothing.")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    fun delete(cfg: WebDavConfig, name: String) {
        http.newCall(request(cfg, fileUrl(cfg, name)).delete().build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) throw failure(resp.code, "Deleting $name")
        }
    }

    /** "20261116-1705" from "LiftTracker-auto-data-20261116-1705.ltbackup"; names sort by it whatever their kind. */
    internal fun backupStamp(name: String): String = Regex("""\d{8}-\d{4,6}""").find(name)?.value ?: name

    /** The files (not folders) of a PROPFIND multistatus answer. Pure, so it is unit-tested. */
    internal fun parseListing(xml: String): List<RemoteBackupFile> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // No DTDs or external entities from a server's answer.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { isExpandEntityReferences = false }
        }
        val doc = runCatching { factory.newDocumentBuilder().parse(xml.byteInputStream()) }
            .getOrElse { throw IOException("The server's answer isn't a WebDAV listing. Check the folder address.") }
        val responses = doc.getElementsByTagNameNS("*", "response")
        return (0 until responses.length).mapNotNull { i ->
            val r = responses.item(i) as? org.w3c.dom.Element ?: return@mapNotNull null
            fun text(tag: String) = r.getElementsByTagNameNS("*", tag).item(0)?.textContent?.trim().orEmpty()
            if (r.getElementsByTagNameNS("*", "collection").length > 0) return@mapNotNull null
            val href = text("href")
            if (href.isEmpty() || href.endsWith("/")) return@mapNotNull null
            val name = runCatching { URLDecoder.decode(href.substringAfterLast('/').replace("+", "%2B"), "UTF-8") }.getOrNull()
                ?: return@mapNotNull null
            RemoteBackupFile(name, text("getcontentlength").toLongOrNull() ?: 0L, text("getlastmodified"))
        }
    }
}

enum class AutoBackupFrequency(val label: String, val days: Long) {
    OFF("Off", 0), DAILY("Every day", 1), WEEKLY("Every week", 7)
}

/**
 * Where backups go besides a hand-picked file, and the automatic backup's schedule. Kept in its
 * own preferences file: it holds the WebDAV password and the backup password (both encrypted with
 * the Android Keystore), is tied to this phone, and is never part of a backup.
 */
class RemoteBackupSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lifttracker_backup_remote", Context.MODE_PRIVATE)

    var webDavUrl: String
        get() = prefs.getString("webdav_url", "").orEmpty()
        set(v) = prefs.edit().putString("webdav_url", v.trim()).apply()
    var webDavUser: String
        get() = prefs.getString("webdav_user", "").orEmpty()
        set(v) = prefs.edit().putString("webdav_user", v.trim()).apply()
    val hasWebDavPassword: Boolean get() = !prefs.getString("webdav_pass", "").isNullOrEmpty()
    fun setWebDavPassword(plain: String) = prefs.edit().putString("webdav_pass", KeyCipher.encrypt(plain)).apply()

    /** The saved WebDAV folder, or null when none is set up. */
    fun webDav(): WebDavConfig? {
        if (WebDavConfig.urlProblem(webDavUrl) != null) return null
        val pass = runCatching { KeyCipher.decrypt(prefs.getString("webdav_pass", "").orEmpty()) }.getOrDefault("")
        return WebDavConfig(webDavUrl, webDavUser, pass)
    }

    fun clearWebDav() = prefs.edit().remove("webdav_url").remove("webdav_user").remove("webdav_pass").putBoolean("auto_webdav", false).apply()

    var frequency: AutoBackupFrequency
        get() = AutoBackupFrequency.entries.find { it.name == prefs.getString("auto_frequency", null) } ?: AutoBackupFrequency.OFF
        set(v) = prefs.edit().putString("auto_frequency", v.name).apply()
    var autoToWebDav: Boolean
        get() = prefs.getBoolean("auto_webdav", false)
        set(v) = prefs.edit().putBoolean("auto_webdav", v).apply()
    /** Tree URI of a folder on the phone (Storage Access Framework), or null. */
    var autoFolderUri: String?
        get() = prefs.getString("auto_folder", null)
        set(v) = prefs.edit().apply { if (v == null) remove("auto_folder") else putString("auto_folder", v) }.apply()
    /** How many automatic backups of each kind are kept per destination. */
    var keepCount: Int
        get() = prefs.getInt("auto_keep", 7).coerceIn(1, 60)
        set(v) = prefs.edit().putInt("auto_keep", v.coerceIn(1, 60)).apply()

    val hasAutoPassword: Boolean get() = !prefs.getString("auto_pass", "").isNullOrEmpty()
    /** Password automatic backups are encrypted with; null removes it (plain data backups only). */
    fun setAutoPassword(plain: CharArray?) = prefs.edit().apply {
        if (plain == null || plain.isEmpty()) remove("auto_pass") else putString("auto_pass", KeyCipher.encrypt(String(plain)))
    }.apply()
    fun autoPassword(): CharArray? =
        runCatching { KeyCipher.decrypt(prefs.getString("auto_pass", "").orEmpty()) }.getOrDefault("").takeIf { it.isNotEmpty() }?.toCharArray()

    var lastRunLabel: String?
        get() = prefs.getString("auto_last_run", null)
        set(v) = prefs.edit().putString("auto_last_run", v).apply()
    var lastRunOk: Boolean
        get() = prefs.getBoolean("auto_last_ok", true)
        set(v) = prefs.edit().putBoolean("auto_last_ok", v).apply()
}

/** The automatic backup: what one run does, and its schedule in WorkManager. */
object AutoBackup {
    private const val WORK_NAME = "lifttracker_auto_backup"
    private const val AUTO_TAG = "LiftTracker-auto-"

    class RunResult(val ok: Boolean, val message: String)

    /**
     * Backs up now to every destination that is switched on. Without a backup password it writes
     * the data backup as a plain zip; with one, the data backup is encrypted and a settings backup
     * (API keys) is written too. Older automatic backups beyond the keep count are deleted.
     */
    fun run(context: Context): RunResult {
        val settings = RemoteBackupSettings(context)
        val webDav = settings.webDav().takeIf { settings.autoToWebDav }
        val folder = settings.autoFolderUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it)) }.getOrNull() }
        val result = if (webDav == null && folder == null) {
            RunResult(false, "No destination is switched on.")
        } else {
            val password = settings.autoPassword()
            val kinds = if (password != null) listOf(BackupKind.DATA, BackupKind.CONFIG) else listOf(BackupKind.DATA)
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            val done = mutableListOf<String>()
            val failed = mutableListOf<String>()
            val manager = BackupManager(context)
            runCatching { webDav?.let { WebDav.ensureFolder(it) } }.onFailure { failed += "WebDAV: ${it.message}" }
            kinds.forEach { kind ->
                val name = "$AUTO_TAG${kind.fileTag}-$stamp" + if (password != null) ".ltbackup" else ".zip"
                val tmp = File.createTempFile("auto", ".bak", context.cacheDir)
                try {
                    manager.create(kind, tmp, password)
                    if (webDav != null && failed.none { it.startsWith("WebDAV") }) {
                        runCatching {
                            WebDav.upload(webDav, name, tmp)
                            prune(WebDav.list(webDav).map { it.name }, kind, settings.keepCount) { WebDav.delete(webDav, it) }
                        }.onSuccess { done += "WebDAV" }.onFailure { failed += "WebDAV: ${it.message}" }
                    }
                    if (folder != null) {
                        runCatching {
                            if (!folder.canWrite()) throw IOException("the folder can't be written to any more; choose it again")
                            val doc = folder.createFile("application/octet-stream", name) ?: throw IOException("couldn't create $name")
                            context.contentResolver.openOutputStream(doc.uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                                ?: throw IOException("couldn't write $name")
                            val files = folder.listFiles().filter { it.isFile }
                            prune(files.mapNotNull { it.name }, kind, settings.keepCount) { old -> files.find { it.name == old }?.delete() }
                        }.onSuccess { done += "phone folder" }.onFailure { failed += "Phone folder: ${it.message}" }
                    }
                } catch (e: Exception) {
                    failed += "${kind.label}: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    tmp.delete()
                }
            }
            val what = if (kinds.size == 2) "data and settings" else "data"
            when {
                failed.isEmpty() -> RunResult(true, "Backed up $what to ${done.distinct().joinToString(" and ")}.")
                done.isEmpty() -> RunResult(false, failed.distinct().joinToString(" "))
                else -> RunResult(false, "Backed up $what to ${done.distinct().joinToString(" and ")}, but: ${failed.distinct().joinToString(" ")}")
            }
        }
        settings.lastRunOk = result.ok
        settings.lastRunLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " — " + result.message
        return result
    }

    /** Names of automatic backups of [kind] to delete so that only the newest [keep] stay. Pure. */
    internal fun toPrune(names: List<String>, kind: BackupKind, keep: Int): List<String> =
        names.filter { it.startsWith("$AUTO_TAG${kind.fileTag}-") }.distinct().sortedByDescending { WebDav.backupStamp(it) }.drop(keep)

    private fun prune(names: List<String>, kind: BackupKind, keep: Int, delete: (String) -> Unit) {
        // Tidying up must never make a finished backup count as failed.
        toPrune(names, kind, keep).forEach { runCatching { delete(it) } }
    }

    /** Makes WorkManager's schedule match the saved settings. Call after every change to them. */
    fun reschedule(context: Context) {
        val settings = RemoteBackupSettings(context)
        val manager = WorkManager.getInstance(context.applicationContext)
        val toWebDav = settings.autoToWebDav && settings.webDav() != null
        if (settings.frequency == AutoBackupFrequency.OFF || (!toWebDav && settings.autoFolderUri == null)) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (toWebDav) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(settings.frequency.days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** Runs the automatic backup in the background, also when the app isn't open. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = runCatching { AutoBackup.run(applicationContext) }.getOrElse { AutoBackup.RunResult(false, it.message.orEmpty()) }
        // A server that is down for a moment gets a few more tries before the next scheduled run.
        if (result.ok || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
