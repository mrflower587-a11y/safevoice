package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.RecordingEntity
import com.example.data.preferences.UserPreferences
import com.example.drive.DriveFileItem
import com.example.ui.components.DismissibleErrorBanner
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FormatUtils
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.components.UploadStatusPill
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.theme.StatusErrorText
import com.example.ui.theme.StatusSuccessText
import com.example.ui.viewmodel.DriveUiState

@Composable
fun DriveConnectionScreen(
    preferences: UserPreferences?,
    driveState: DriveUiState,
    onConnectOrRefresh: () -> Unit,
    onAuthResult: (android.content.Intent?) -> Unit,
    onConsentLaunched: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectFolder: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        onAuthResult(result.data)
    }

    LaunchedEffect(driveState.pendingConsentIntent) {
        val pending = driveState.pendingConsentIntent ?: return@LaunchedEffect
        onConsentLaunched()
        authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }

    val isConnected = !preferences?.connectedDriveEmail.isNullOrBlank()

    SafeVoiceScaffold(
        title = "Google Drive",
        onBackClick = onBack,
        backTestTag = "drive_conn_back_button"
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (driveState.errorMessage != null) {
                DismissibleErrorBanner(
                    message = driveState.errorMessage,
                    onDismiss = onDismissError
                )
            }

            SafeVoiceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            contentDescription = "Drive connection status",
                            tint = if (isConnected) StatusSuccessText else SafeBluePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = if (isConnected) "Connected to Google Drive" else "Not Connected",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = preferences?.connectedDriveEmail ?: "Sign in with Google to enable encrypted backup",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!preferences?.connectedDriveName.isNullOrBlank()) {
                        Text(
                            text = "Account: ${preferences?.connectedDriveName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Destination Folder: ${preferences?.selectedDriveFolderName ?: "SafeVoice Recordings"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (driveState.statusMessage != null) {
                        Text(
                            text = driveState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeBluePrimary
                        )
                    }
                }
            }

            SafeVoiceCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Minimal OAuth Scope (drive.file)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "SafeVoice requests only drive.file, email, and profile scopes. It can only access files and folders created by SafeVoice and never reads your personal Drive documents.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onOpenPrivacyPolicy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("drive_privacy_policy_link")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Policy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read Safe Voice Privacy Policy")
                    }
                }
            }

            if (driveState.isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Button(
                onClick = onConnectOrRefresh,
                enabled = !driveState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("drive_connect_button")
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isConnected) "Refresh Authorization" else "Connect Google Drive")
            }

            if (isConnected) {
                OutlinedButton(
                    onClick = onSelectFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("drive_choose_folder_button")
                ) {
                    Icon(imageVector = Icons.Filled.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Destination Folder")
                }

                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusErrorText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("drive_disconnect_button")
                ) {
                    Text("Disconnect Account")
                }
            }
        }
    }
}

@Composable
fun DriveFolderSelectScreen(
    preferences: UserPreferences?,
    driveState: DriveUiState,
    onLoadFolders: () -> Unit,
    onSelectFolder: (DriveFileItem) -> Unit,
    onCreateFolder: (String) -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadFolders()
    }

    SafeVoiceScaffold(
        title = "Select Drive Folder",
        onBackClick = onBack,
        backTestTag = "folder_select_back_button",
        actions = {
            TextButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("create_folder_button")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Create folder")
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
        }
    ) { modifier ->
        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (driveState.errorMessage != null) {
                DismissibleErrorBanner(message = driveState.errorMessage, onDismiss = onDismissError)
            }

            if (driveState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (driveState.folders.isEmpty() && !driveState.isBusy) {
                EmptyStateView(
                    icon = Icons.Filled.Folder,
                    title = "No App Folders Found",
                    subtitle = "Create a new folder or connect Google Drive to list folders created by SafeVoice."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(
                        items = driveState.folders,
                        key = { it.id },
                        contentType = { "drive_folder" }
                    ) { folder ->
                        val selected = folder.id == preferences?.selectedDriveFolderId
                        SafeVoiceCard(
                            containerColor = if (selected) SafeBlueContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { onSelectFolder(folder) }
                                    .testTag("drive_folder_${folder.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = folder.name,
                                        tint = SafeBluePrimary
                                    )
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected folder",
                                        tint = StatusSuccessText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var folderName by remember { mutableStateOf("SafeVoice Recordings") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Drive Folder", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_folder_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateFolder(folderName)
                        showCreateDialog = false
                    },
                    enabled = folderName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_folder_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UploadQueueScreen(
    pendingQueue: List<RecordingEntity>,
    onUploadAll: () -> Unit,
    onUploadSingle: (Long) -> Unit,
    onBack: () -> Unit
) {
    SafeVoiceScaffold(
        title = "Upload Queue",
        onBackClick = onBack,
        backTestTag = "upload_queue_back_button"
    ) { modifier ->
        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (pendingQueue.isNotEmpty()) {
                Button(
                    onClick = onUploadAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("upload_queue_now_button")
                ) {
                    Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Queue Now (${pendingQueue.size})")
                }
            }

            if (pendingQueue.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Filled.CloudDone,
                    title = "Upload Queue Empty",
                    subtitle = "All local encrypted segments have been synced to Google Drive."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(
                        items = pendingQueue,
                        key = { it.id },
                        contentType = { "queue_item" }
                    ) { item ->
                        SafeVoiceCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    UploadStatusPill(status = item.uploadStatus)
                                }
                                Text(
                                    text = "${FormatUtils.formatBytes(item.decryptedSizeBytes)} • Retries: ${item.retryCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!item.lastError.isNullOrBlank()) {
                                    Text(
                                        text = item.lastError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusErrorText
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onUploadSingle(item.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("queue_upload_single_${item.id}")
                                ) {
                                    Text("Upload Now")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadProgressScreen(
    recordings: List<RecordingEntity>,
    latestStatusText: String,
    onUploadAllPending: () -> Unit,
    onBack: () -> Unit
) {
    SafeVoiceScaffold(
        title = "Upload Status",
        onBackClick = onBack,
        backTestTag = "upload_progress_back_button"
    ) { modifier ->
        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SafeVoiceCard(containerColor = SafeBlueContainer.copy(alpha = 0.35f)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Latest Sync Summary", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = latestStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (recordings.any { it.remoteDriveFileId == null }) {
                Button(
                    onClick = onUploadAllPending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("upload_progress_sync_now_button")
                ) {
                    Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Pending Recordings")
                }
            }

            if (recordings.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Filled.CloudUpload,
                    title = "No Recordings Tracked",
                    subtitle = "Once you complete a recording, upload progress and Drive IDs appear here."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(
                        items = recordings,
                        key = { it.id },
                        contentType = { "upload_progress_item" }
                    ) { item ->
                        SafeVoiceCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    UploadStatusPill(status = item.uploadStatus)
                                }
                                LinearProgressIndicator(
                                    progress = { (item.uploadProgress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${item.uploadProgress}%",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = item.remoteDriveFileId?.let { "Drive ID: $it" } ?: "Not uploaded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
