package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RecordingEntity
import com.example.playback.PlaybackUiState
import com.example.ui.components.DismissibleErrorBanner
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FormatUtils
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.components.StatusPill
import com.example.ui.components.UploadStatusPill
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBlueOnContainer
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.theme.StatusErrorText
import com.example.ui.theme.StatusPausedBg
import com.example.ui.theme.StatusPausedText
import com.example.ui.theme.TabularTimerStyle

@Composable
fun MyFilesScreen(
    recordings: List<RecordingEntity>,
    playbackState: PlaybackUiState,
    onPlayOrToggle: (RecordingEntity) -> Unit,
    onUploadNow: (RecordingEntity) -> Unit,
    onOpenPlayer: (RecordingEntity) -> Unit,
    onDeleteRecording: (RecordingEntity) -> Unit,
    onBack: () -> Unit
) {
    var detailTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingEntity?>(null) }

    SafeVoiceScaffold(
        title = "My Files",
        onBackClick = onBack,
        backTestTag = "my_files_back_button"
    ) { modifier ->
        if (recordings.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.Lock,
                title = "No Encrypted Recordings Yet",
                subtitle = "Recordings you capture are encrypted in 64 KB AES-GCM chunks and appear here.",
                modifier = modifier
            )
        } else {
            LazyColumn(
                modifier = modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "top_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(4.dp)) }

                items(
                    items = recordings,
                    key = { it.id },
                    contentType = { "recording_item" }
                ) { recording ->
                    val isCurrent = playbackState.activeRecordingId == recording.id
                    val isPlayingCurrent = isCurrent && playbackState.isPlaying

                    SafeVoiceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPlayer(recording) }
                                    .testTag("recording_card_${recording.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = recording.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    UploadStatusPill(status = recording.uploadStatus)
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = FormatUtils.formatTimestamp(recording.startTimeMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (recording.isRecovered) {
                                        StatusPill(
                                            text = "Recovered",
                                            textColor = StatusPausedText,
                                            backgroundColor = StatusPausedBg
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        text = "Duration: ${FormatUtils.formatDuration(recording.durationMs)}",
                                        style = TabularTimerStyle.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Encrypted: ${FormatUtils.formatBytes(recording.fileSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onPlayOrToggle(recording) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("play_button_${recording.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingCurrent) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isPlayingCurrent) "Pause" else if (isCurrent) "Resume" else "Play")
                                }

                                Row {
                                    if (recording.remoteDriveFileId == null) {
                                        IconButton(
                                            onClick = { onUploadNow(recording) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("upload_now_button_${recording.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CloudUpload,
                                                contentDescription = "Upload recording now",
                                                tint = SafeBluePrimary
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { detailTarget = recording },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("details_button_${recording.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "Metadata details"
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleteTarget = recording },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_button_${recording.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete recording",
                                            tint = StatusErrorText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    detailTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text("Recording Metadata", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetadataLine("File", item.fileName)
                    MetadataLine("Started", FormatUtils.formatTimestamp(item.startTimeMs))
                    MetadataLine("Duration", FormatUtils.formatDuration(item.durationMs))
                    MetadataLine("Encrypted Size", FormatUtils.formatBytes(item.fileSizeBytes))
                    MetadataLine("Plaintext Size", FormatUtils.formatBytes(item.decryptedSizeBytes))
                    MetadataLine("Upload Status", item.uploadStatus.name)
                    MetadataLine("Drive File ID", item.remoteDriveFileId ?: "Not uploaded yet")
                    if (item.isRecovered) {
                        MetadataLine("Recovery", "Recovered from staging after unexpected stop")
                    }
                    if (!item.lastError.isNullOrBlank()) {
                        MetadataLine("Last Error", item.lastError)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailTarget = null }) {
                    Text("Close")
                }
            }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Encrypted Recording?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text("This permanently deletes '${item.fileName}' from your local encrypted vault.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecording(item)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorText),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AudioPlayerScreen(
    recording: RecordingEntity?,
    playbackState: PlaybackUiState,
    onPlayRecording: (RecordingEntity) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onStopPlayback: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(recording?.id) {
        if (recording != null && playbackState.activeRecordingId != recording.id) {
            onPlayRecording(recording)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopPlayback()
        }
    }

    SafeVoiceScaffold(
        title = "Audio Player",
        onBackClick = onBack,
        backTestTag = "player_back_button"
    ) { modifier ->
        if (recording == null) {
            EmptyStateView(
                icon = Icons.Filled.GraphicEq,
                title = "Recording Unavailable",
                subtitle = "The selected recording could not be loaded.",
                modifier = modifier
            )
        } else {
            val isActive = playbackState.activeRecordingId == recording.id
            val durationMs = if (isActive && playbackState.durationMs > 0L) {
                playbackState.durationMs
            } else {
                recording.durationMs
            }.coerceAtLeast(1L)
            val positionMs = if (isActive) playbackState.positionMs.coerceIn(0L, durationMs) else 0L

            Column(
                modifier = modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (playbackState.errorMessage != null) {
                    DismissibleErrorBanner(
                        message = playbackState.errorMessage,
                        onDismiss = onStopPlayback
                    )
                }

                SafeVoiceCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = SafeBlueContainer
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Decrypted on demand",
                                tint = SafeBluePrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }

                        Text(
                            text = recording.fileName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Decrypted into volatile temp playback cache; wiped automatically on stop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isActive && playbackState.isPreparing) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }

                        Slider(
                            value = positionMs.toFloat(),
                            onValueChange = { onSeekTo(it.toLong()) },
                            valueRange = 0f..durationMs.toFloat(),
                            enabled = isActive && !playbackState.isPreparing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_seek_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = FormatUtils.formatDuration(positionMs),
                                style = TabularTimerStyle.copy(fontSize = 14.sp),
                                modifier = Modifier.testTag("player_elapsed_time")
                            )
                            Text(
                                text = FormatUtils.formatDuration(durationMs),
                                style = TabularTimerStyle.copy(fontSize = 14.sp),
                                modifier = Modifier.testTag("player_total_time")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isActive) onTogglePlayPause() else onPlayRecording(recording)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("player_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isActive && playbackState.isPlaying) Icons.Filled.Pause
                                    else Icons.Filled.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when {
                                        isActive && playbackState.isPlaying -> "Pause"
                                        isActive -> "Resume"
                                        else -> "Play"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = onStopPlayback,
                                enabled = isActive,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("player_stop_button")
                            ) {
                                Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop & Wipe")
                            }
                        }
                    }
                }

                SafeVoiceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Encryption & Drive Status", style = MaterialTheme.typography.titleMedium)
                        MetadataLine("Cipher", "Android Keystore AES-256-GCM (64 KB chunks)")
                        MetadataLine("Encrypted Vault Size", FormatUtils.formatBytes(recording.fileSizeBytes))
                        MetadataLine("Upload Status", recording.uploadStatus.name)
                        MetadataLine("Google Drive ID", recording.remoteDriveFileId ?: "Local vault only")
                    }
                }
            }
        }
    }
}
