package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recording.StorageStats
import com.example.system.RamProfile
import com.example.ui.components.FormatUtils
import com.example.ui.components.IntentLaunchers
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusErrorText
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusSuccessText

@Composable
fun StorageInfoScreen(
    storageStats: StorageStats,
    ramProfile: RamProfile,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    val total = storageStats.totalBytes.coerceAtLeast(1L)
    val used = (total - storageStats.freeBytes).coerceAtLeast(0L)
    val usedFraction = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    SafeVoiceScaffold(
        title = "Storage Information",
        onBackClick = onBack,
        backTestTag = "storage_info_back_button"
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!storageStats.hasMinimumHeadroom) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StatusErrorBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = "Low storage warning",
                            tint = StatusErrorText
                        )
                        Text(
                            text = "Device free storage is below the 25 MB safety threshold. Free space before starting a recording.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusErrorText
                        )
                    }
                }
            }

            SafeVoiceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Device Storage", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = SafeBluePrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Free: ${FormatUtils.formatBytes(storageStats.freeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Total: ${FormatUtils.formatBytes(storageStats.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SafeVoiceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encrypted Vault Footprint", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = FormatUtils.formatBytes(storageStats.vaultBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = SafeBluePrimary
                    )
                    Text(
                        text = "Recordings are buffered in a private staging directory during capture, then chunk-encrypted into the private vault while plaintext staging files are wiped.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SafeVoiceCard(
                modifier = Modifier.testTag("storage_ram_profile_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Device Memory & 1 GB RAM Mode", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = StatusSuccessBg
                        ) {
                            Text(
                                text = if (ramProfile.isLowRamDevice) {
                                    "1 GB RAM Optimization Active"
                                } else {
                                    "Lightweight Streaming Active"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusSuccessText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Available RAM: ${FormatUtils.formatBytes(ramProfile.availableRamBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Total RAM: ${FormatUtils.formatBytes(ramProfile.totalRamBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Tuned for low-RAM stability: ${ramProfile.streamBufferBytes / 1024} KB crypto buffers, ${ramProfile.amplitudePollIntervalMs} ms UI meter refresh, TRUNCATE journal mode, and ${ramProfile.okHttpMaxIdleConnections} idle upload connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyInfoScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current

    SafeVoiceScaffold(
        title = "Privacy Information",
        onBackClick = onBack,
        backTestTag = "privacy_info_back_button"
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PrivacyPolicyCard(
                title = "1. Explicit Consent Only",
                body = "SafeVoice never records automatically or silently on startup. Audio capture starts solely when you tap Start Recording and stops when you tap Stop."
            )
            PrivacyPolicyCard(
                title = "2. Hardware-Backed Keystore Encryption",
                body = "Completed segments are encrypted in 64 KB streaming chunks using AES-256-GCM keys inside Android Keystore. Device cloud backup is disabled so keys and encrypted recordings never leak."
            )
            PrivacyPolicyCard(
                title = "3. Salted PBKDF2 PIN Lock",
                body = "Your 4–6 digit PIN and backup recovery code are stored strictly as salted PBKDF2WithHmacSHA256 hashes (10,000 iterations) with progressive brute-force lockouts."
            )
            PrivacyPolicyCard(
                title = "4. Minimal Google Drive Scope",
                body = "Drive backup uses only the drive.file scope—SafeVoice can access only the folders and audio files it creates."
            )

            Button(
                onClick = { IntentLaunchers.openAppNotificationSettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("privacy_open_notification_settings")
            ) {
                Icon(imageVector = Icons.Filled.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open App Notification Settings")
            }

            OutlinedButton(
                onClick = { IntentLaunchers.openBatteryOptimizationSettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("privacy_open_battery_settings")
            ) {
                Icon(imageVector = Icons.Filled.BatterySaver, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review Android Battery Settings")
            }

            OutlinedButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("privacy_info_open_policy_button")
            ) {
                Icon(imageVector = Icons.Filled.Policy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read Full Privacy Policy")
            }
        }
    }
}

@Composable
private fun PrivacyPolicyCard(title: String, body: String) {
    SafeVoiceCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AboutHelpScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    SafeVoiceScaffold(
        title = "About SafeVoice & Help",
        onBackClick = onBack,
        backTestTag = "about_help_back_button"
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SafeVoiceCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SafeVoice v1.0", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Lightweight, privacy-first encrypted voice recorder for Android.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PrivacyPolicyCard(
                title = "How do silent recording notifications work?",
                body = "SafeVoice creates a silent IMPORTANCE_MIN notification channel with no sound, vibration, or badge. You can also toggle notifications off in Android Notification Settings while background recording continues."
            )
            PrivacyPolicyCard(
                title = "What happens during a phone call?",
                body = "SafeVoice keeps your recording session active during calls and audio interruptions and never auto-pauses—recording pauses or stops only when you explicitly tap Pause or Stop with your PIN."
            )
            PrivacyPolicyCard(
                title = "How do crash recovery and restart resume work?",
                body = "If the phone restarts or powers off unexpectedly during an active recording, SafeVoice encrypts completed segments on startup, keeps the Always-On Background service running, and automatically resumes recording (or notifies you to resume on Android 14+)."
            )

            OutlinedButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("about_open_privacy_policy_button")
            ) {
                Icon(imageVector = Icons.Filled.Policy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Privacy Policy")
            }
        }
    }
}
