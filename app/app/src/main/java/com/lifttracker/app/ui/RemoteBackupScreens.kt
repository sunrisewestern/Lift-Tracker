package com.lifttracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifttracker.app.backup.AutoBackupFrequency
import com.lifttracker.app.backup.BackupKind
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import java.util.Locale

@Composable
private fun BackupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun sizeLabel(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Settings → Backup & Restore: the WebDAV folder, uploading to it and restoring from it. */
@Composable
internal fun WebDavBackupCard(viewModel: LiftTrackerViewModel) {
    val state = viewModel.remoteBackup
    val ready = !viewModel.backupBusy
    var editing by remember { mutableStateOf(false) }
    var url by remember(state.webDavUrl) { mutableStateOf(state.webDavUrl) }
    var user by remember(state.webDavUser) { mutableStateOf(state.webDavUser) }
    var password by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf<BackupKind?>(null) }
    val plainText = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    BackupCard {
        if (!state.hasWebDav || editing) {
            Text(
                "Keep backups in a folder on your own cloud or NAS (Nextcloud, ownCloud, Synology, any WebDAV server).",
                color = Muted, fontSize = 12.sp,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Folder address") },
                placeholder = { Text("https://cloud.example.com/remote.php/dav/files/me/LiftTracker") },
                singleLine = true,
                keyboardOptions = plainText.copy(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("User name") },
                singleLine = true,
                keyboardOptions = plainText,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password or app password") },
                placeholder = { if (state.hasWebDavPassword) Text("Saved — leave empty to keep it") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = plainText.copy(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (url.trim().startsWith("http://")) {
                Text("http:// sends your password and backups unencrypted. Use it only on your own network.", color = ErrorRed, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.saveWebDav(url, user, password)
                        password = ""
                        editing = false
                    },
                    enabled = ready && url.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save & connect", fontWeight = FontWeight.Bold) }
                if (editing) OutlinedButton(onClick = { editing = false; password = "" }, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
            Text("The password is encrypted with the Android Keystore, stays on this phone and is never part of a backup.", color = Muted, fontSize = 11.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(state.webDavUrl, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (state.webDavUser.isBlank()) "No user name" else "Signed in as ${state.webDavUser}", color = Muted, fontSize = 11.sp)
                }
                Chip("Edit") { editing = true }
            }
            HorizontalDivider(color = CardHigh)
            Text("Back up to WebDAV now", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { asking = BackupKind.DATA }, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text("Workouts & data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { asking = BackupKind.CONFIG }, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text("Settings & keys", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = CardHigh)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Restore from WebDAV", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Chip(if (viewModel.webDavFiles == null) "Show backups" else "Refresh", enabled = ready) { viewModel.loadWebDavFiles() }
            }
            viewModel.webDavFiles?.let { files ->
                if (files.isEmpty()) Text("No Lift Tracker backups in this folder yet.", color = Muted, fontSize = 12.sp)
                files.take(30).forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = ready) { viewModel.restoreFromWebDav(f.name) }
                            .padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = OnSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(
                                    if (f.name.contains("-config-")) "Settings" else "Data",
                                    if (f.name.contains("-auto-")) "automatic" else "manual",
                                    sizeLabel(f.sizeBytes),
                                ).filter { it.isNotEmpty() }.joinToString(" · "),
                                color = Muted, fontSize = 11.sp,
                            )
                        }
                        Text("Restore", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (files.size > 30) Text("Showing the newest 30 of ${files.size}.", color = Muted, fontSize = 11.sp)
            }
            TextButton(
                onClick = {
                    viewModel.requestConfirmation(
                        title = "Remove WebDAV folder?",
                        message = "The address and password are removed from this phone and automatic backups stop going there. Backups on the server stay.",
                        confirmLabel = "Remove",
                    ) { viewModel.removeWebDav() }
                },
            ) { Text("Remove WebDAV folder", color = ErrorRed, fontSize = 12.sp) }
        }
    }

    asking?.let { kind ->
        BackupPasswordDialog(
            title = "Upload ${kind.phrase}",
            message = if (kind == BackupKind.CONFIG) {
                "This backup holds your API keys, so it is encrypted. You need this password to restore it — it cannot be recovered."
            } else {
                "Add a password to encrypt the file before it leaves the phone, or leave both fields empty for a plain zip."
            },
            passwordRequired = kind == BackupKind.CONFIG,
            confirmTwice = true,
            confirmLabel = "Upload",
            onDismiss = { asking = null },
            onConfirm = { pw ->
                asking = null
                viewModel.backupToWebDav(kind, pw)
            },
        )
    }
}

/** Settings → Backup & Restore: the schedule, destinations and password of the automatic backup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutoBackupCard(viewModel: LiftTrackerViewModel) {
    val state = viewModel.remoteBackup
    val ready = !viewModel.backupBusy
    var askingPassword by remember { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setAutoBackupFolder(uri)
    }

    BackupCard {
        Text("How often", color = Muted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AutoBackupFrequency.entries.forEach { f ->
                Chip(f.label, selected = state.frequency == f) { viewModel.updateAutoBackup(frequency = f) }
            }
        }
        Text(
            text = when {
                state.frequency == AutoBackupFrequency.OFF -> "Automatic backups are off."
                !state.hasDestination -> "Choose at least one destination below — nothing is backed up until you do."
                else -> "Runs in the background about ${if (state.frequency == AutoBackupFrequency.DAILY) "once a day" else "once a week"}, " +
                    "also when the app is closed. Android picks the exact time and waits while the battery is low" +
                    (if (state.autoToWebDav && state.hasWebDav) " or the phone is offline." else ".")
            },
            color = if (state.frequency != AutoBackupFrequency.OFF && !state.hasDestination) ErrorRed else Muted,
            fontSize = 12.sp,
        )

        HorizontalDivider(color = CardHigh)
        Text("Where to", color = Muted, fontSize = 12.sp)
        SettingToggleRow(
            title = "WebDAV folder",
            subtitle = if (state.hasWebDav) state.webDavUrl else "Set up the WebDAV folder above first",
            checked = state.autoToWebDav && state.hasWebDav,
            onCheckedChange = { if (state.hasWebDav) viewModel.updateAutoBackup(toWebDav = it) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Folder on this phone", color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(state.autoFolderName ?: "Not chosen. Pick one that a sync app or the cloud keeps a copy of.", color = Muted, fontSize = 12.sp)
            }
            if (state.autoFolderUri != null) Chip("Stop") { viewModel.setAutoBackupFolder(null) }
            Chip(if (state.autoFolderUri == null) "Choose" else "Change") { pickFolder.launch(null) }
        }

        HorizontalDivider(color = CardHigh)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Backup password", color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (state.hasAutoPassword) {
                        "Set. Workouts & data and settings & API keys are both backed up, encrypted with it."
                    } else {
                        "Not set. Only workouts & data are backed up, as a plain zip. Settings with API keys need a password."
                    },
                    color = Muted, fontSize = 12.sp,
                )
            }
            if (state.hasAutoPassword) Chip("Remove") { viewModel.setAutoBackupPassword(null) }
            Chip(if (state.hasAutoPassword) "Change" else "Set") { askingPassword = true }
        }

        HorizontalDivider(color = CardHigh)
        Text("Keep the newest", color = Muted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 7, 14, 30).forEach { n ->
                Chip("$n", selected = state.keepCount == n) { viewModel.updateAutoBackup(keepCount = n) }
            }
        }
        Text("Older automatic backups are deleted from each destination. Backups you make by hand are never deleted.", color = Muted, fontSize = 11.sp)

        HorizontalDivider(color = CardHigh)
        state.lastRunLabel?.let {
            Text("Last run: $it", color = if (state.lastRunOk) Muted else ErrorRed, fontSize = 12.sp)
        }
        Button(onClick = { viewModel.runAutoBackupNow() }, enabled = ready && state.hasDestination, modifier = Modifier.fillMaxWidth()) {
            Text("Back up now", fontWeight = FontWeight.Bold)
        }
    }

    if (askingPassword) {
        BackupPasswordDialog(
            title = "Password for automatic backups",
            message = "Automatic backups are encrypted with this password. It is stored on this phone (encrypted with the Android Keystore) " +
                "so backups can run unattended. You need it to restore — write it down; it cannot be recovered.",
            passwordRequired = true,
            confirmTwice = true,
            confirmLabel = "Save",
            onDismiss = { askingPassword = false },
            onConfirm = { pw ->
                askingPassword = false
                pw?.let { viewModel.setAutoBackupPassword(it) }
            },
        )
    }
}
