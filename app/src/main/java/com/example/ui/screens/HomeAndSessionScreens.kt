package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.preferences.UserPreferences
import com.example.recording.RecordingSessionState
import com.example.recording.RecordingStatus
import com.example.ui.components.DismissibleErrorBanner
import com.example.ui.components.FormatUtils
import com.example.ui.components.RecordingStatusPill
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBlueOnContainer
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.theme.StatusPausedBg
import com.example.ui.theme.StatusPausedText
import com.example.ui.theme.StatusRecordingText
import com.example.ui.theme.TabularTimerStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recordingState: RecordingSessionState,
    preferences: UserPreferences?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onViewActiveSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartRecording()
            onViewActiveSession()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SafeBluePrimary,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_safevoice_mark),
                                    contentDescription = "SafeVoice badge",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = "SafeVoice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Open PIN-protected Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                SafeVoiceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recording Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RecordingStatusPill(
                                status = recordingState.status,
                                modifier = Modifier.testTag("home_status_pill")
                            )
                        }

                        Text(
                            text = FormatUtils.formatDuration(recordingState.totalElapsedMs),
                            style = TabularTimerStyle.copy(fontSize = 44.sp, lineHeight = 52.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("home_session_timer")
                        )

                        val summaryText = if (recordingState.isSessionActive) {
                            "Active segment: ${recordingState.currentSegmentFileName}"
                        } else {
                            val splitMin = preferences?.segmentDurationMinutes ?: 30
                            val kbps = preferences?.audioQualityKbps ?: 64
                            "Auto-split every ${splitMin}m • $kbps kbps AAC • Keystore Encrypted"
                        }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (recordingState.errorMessage != null) {
                            DismissibleErrorBanner(
                                message = recordingState.errorMessage,
                                onDismiss = onDismissError
                            )
                        }
                    }
                }

                if (!recordingState.isSessionActive) {
                    Button(
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                onStartRecording()
                                onViewActiveSession()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("start_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Start Recording", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = onViewActiveSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("view_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Recording Active — View Session", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedButton(
                        onClick = onStopRecording,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRecordingText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("home_stop_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Recording (PIN Required)", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .minimumInteractiveComponentSize()
                        .testTag("home_privacy_policy_link")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Policy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Privacy Policy", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun RecordingSessionScreen(
    recordingState: RecordingSessionState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit = {}
) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = recordingState.normalizedAmplitude,
        label = "micAmplitude"
    )

    SafeVoiceScaffold(
        title = "Recording Session",
        onBackClick = onBack,
        backTestTag = "session_back_button"
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (recordingState.errorMessage != null) {
                DismissibleErrorBanner(
                    message = recordingState.errorMessage,
                    onDismiss = onDismissError
                )
            }
            if (recordingState.autoPausedReason != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StatusPausedBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("interruption_notice_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = "Auto-paused warning",
                            tint = StatusPausedText
                        )
                        Text(
                            text = recordingState.autoPausedReason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusPausedText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            SafeVoiceCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RecordingStatusPill(status = recordingState.status)

                    Text(
                        text = FormatUtils.formatDuration(recordingState.totalElapsedMs),
                        style = TabularTimerStyle.copy(fontSize = 48.sp, lineHeight = 56.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("session_total_duration")
                    )

                    Text(
                        text = "Live Microphone Input",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    MicAmplitudeMeter(
                        amplitude = animatedAmplitude,
                        isPaused = recordingState.status != RecordingStatus.RECORDING
                    )
                }
            }

            SafeVoiceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Segment", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SafeBlueContainer
                        ) {
                            Text(
                                text = "${recordingState.completedSegmentsCount} completed",
                                style = MaterialTheme.typography.labelMedium,
                                color = SafeBlueOnContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = recordingState.currentSegmentFileName.ifEmpty { "Preparing segment…" },
                        style = TabularTimerStyle.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LinearProgressIndicator(
                        progress = { recordingState.segmentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = SafeBluePrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Segment: ${FormatUtils.formatDuration(recordingState.currentSegmentElapsedMs)}",
                            style = TabularTimerStyle.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Next auto-split in ${FormatUtils.formatDuration(recordingState.countdownToNextSplitMs)}",
                            style = TabularTimerStyle.copy(fontSize = 12.sp),
                            color = SafeBluePrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isPaused = recordingState.status == RecordingStatus.PAUSED
                OutlinedButton(
                    onClick = { if (isPaused) onResume() else onPause() },
                    enabled = recordingState.isSessionActive,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("session_pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPaused) "Resume" else "Pause")
                }

                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRecordingText),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("session_stop_button")
                ) {
                    Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop & Encrypt")
                }
            }
        }
    }
}

@Composable
private fun MicAmplitudeMeter(amplitude: Float, isPaused: Boolean) {
    val barColor = if (isPaused) StatusPausedText else SafeBluePrimary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor)
    ) {
        val barCount = 24
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        for (i in 0 until barCount) {
            val threshold = (i + 1) / barCount.toFloat()
            val active = !isPaused && amplitude >= threshold * 0.85f
            val hFraction = if (active) 0.35f + 0.65f * ((i % 4 + 1) / 4f) else 0.2f
            val barHeight = size.height * hFraction
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = if (active) barColor else barColor.copy(alpha = 0.2f),
                topLeft = Offset(i * (barWidth + gap), top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
