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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.preferences.AudioQualityPreset
import com.example.data.preferences.UserPreferences
import com.example.ui.components.IntentLaunchers
import com.example.ui.components.RadioChoice
import com.example.ui.components.RadioOptionDialog
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBlueOnContainer
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.theme.StatusPausedBg
import com.example.ui.theme.StatusPausedText
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusSuccessText

@Composable
fun SettingsScreen(
    preferences: UserPreferences?,
    savedFilesCount: Int,
    pendingQueueCount: Int,
    onOpenMyFiles: () -> Unit,
    onOpenDriveConnection: () -> Unit,
    onOpenUploadProgress: () -> Unit,
    onChangePin: () -> Unit,
    onSelectDriveFolder: () -> Unit,
    onToggleAutoUploadFive: (Boolean) -> Unit,
    onTogglePeriodic90Min: (Boolean) -> Unit,
    onOpenUploadQueue: () -> Unit,
    onSelectSegmentDuration: (Int) -> Unit,
    onSelectAudioQuality: (Int) -> Unit,
    onOpenStorageInfo: () -> Unit,
    onOpenPrivacyInfo: () -> Unit,
    onOpenAboutHelp: () -> Unit,
    onBack: () -> Unit,
    isBackgroundServiceRunning: Boolean = false,
    isIgnoringBatteryOptimizations: Boolean = true,
    onToggleAlwaysRunInBackground: (Boolean) -> Unit = {},
    onRefreshBatteryStatus: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshBatteryStatus()
    }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    val prefs = preferences ?: UserPreferences()
    val qualityPreset = AudioQualityPreset.fromKbps(prefs.audioQualityKbps)

    SafeVoiceScaffold(
        title = "Settings",
        onBackClick = onBack,
        backTestTag = "settings_back_button"
    ) { modifier ->
        LazyColumn(
            modifier = modifier
                .padding(horizontal = 20.dp)
                .testTag("settings_lazy_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "top_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(4.dp)) }

            // Overview & Library (Top 3 items in exact order)
            item(key = "header_overview", contentType = "header") {
                SettingsSectionHeader("Overview & Library")
            }
            item(key = "card_overview", contentType = "card") {
                SafeVoiceCard {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Filled.Folder,
                            title = "Saved Files",
                            subtitle = "$savedFilesCount encrypted recording(s)",
                            testTag = "settings_saved_files",
                            onClick = onOpenMyFiles
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.CloudQueue,
                            title = "Google Drive",
                            subtitle = prefs.connectedDriveEmail ?: "Not connected",
                            testTag = "settings_google_drive",
                            onClick = onOpenDriveConnection
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.CloudSync,
                            title = "Latest Upload Status",
                            subtitle = prefs.latestUploadStatusText,
                            testTag = "settings_latest_upload_status",
                            onClick = onOpenUploadProgress
                        )
                    }
                }
            }

            // Always-On Background Protection
            item(key = "header_always_on", contentType = "header") {
                SettingsSectionHeader("Always-On Background")
            }
            item(key = "card_always_on", contentType = "card") {
                SafeVoiceCard(
                    modifier = Modifier.testTag("settings_always_on_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Background Service Status",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = when {
                                    prefs.alwaysRunInBackground && isBackgroundServiceRunning -> StatusSuccessBg
                                    prefs.alwaysRunInBackground -> SafeBlueContainer
                                    else -> StatusPausedBg
                                }
                            ) {
                                Text(
                                    text = when {
                                        prefs.alwaysRunInBackground && isBackgroundServiceRunning -> "Running"
                                        prefs.alwaysRunInBackground -> "Auto-Standby"
                                        else -> "Off"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        prefs.alwaysRunInBackground && isBackgroundServiceRunning -> StatusSuccessText
                                        prefs.alwaysRunInBackground -> SafeBlueOnContainer
                                        else -> StatusPausedText
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        SettingsSwitchRow(
                            title = "Always-On Background Mode",
                            subtitle = "Run permanently in background with START_STICKY, boot restart & watchdog recovery",
                            checked = prefs.alwaysRunInBackground,
                            testTag = "switch_always_run_background",
                            onCheckedChange = onToggleAlwaysRunInBackground
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.BatterySaver,
                            title = "Unrestricted Battery Usage",
                            subtitle = if (isIgnoringBatteryOptimizations) {
                                "Unrestricted (Recommended for permanent background)"
                            } else {
                                "Restricted — tap to allow Unrestricted background"
                            },
                            testTag = "settings_battery_optimization",
                            onClick = { IntentLaunchers.openBatteryOptimizationSettings(context) }
                        )
                    }
                }
            }

            // Security & Drive Setup
            item(key = "header_security", contentType = "header") {
                SettingsSectionHeader("Security & Drive Setup")
            }
            item(key = "card_security", contentType = "card") {
                SafeVoiceCard {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Filled.LockReset,
                            title = "PIN Settings",
                            subtitle = "Change your 4–6 digit PIN & recovery code",
                            testTag = "settings_change_pin",
                            onClick = onChangePin
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.FolderOpen,
                            title = "Select Drive Folder",
                            subtitle = prefs.selectedDriveFolderName ?: "SafeVoice Recordings (default)",
                            testTag = "settings_select_drive_folder",
                            onClick = onSelectDriveFolder
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.Sync,
                            title = "Upload Queue",
                            subtitle = "$pendingQueueCount pending segment(s)",
                            testTag = "settings_upload_queue",
                            onClick = onOpenUploadQueue
                        )
                    }
                }
            }

            item(key = "card_upload_prefs", contentType = "card") {
                SafeVoiceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Upload Preferences",
                            style = MaterialTheme.typography.titleMedium
                        )
                        SettingsSwitchRow(
                            title = "Automatic Upload",
                            subtitle = "Flush pending queue automatically after every 5 completed segments",
                            checked = prefs.autoUploadAfterFive,
                            testTag = "switch_auto_upload_5",
                            onCheckedChange = onToggleAutoUploadFive
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsSwitchRow(
                            title = "Upload Every 90 Minutes",
                            subtitle = "Background WorkManager sync when internet is connected",
                            checked = prefs.periodicUpload90Min,
                            testTag = "switch_periodic_upload_90",
                            onCheckedChange = onTogglePeriodic90Min
                        )
                    }
                }
            }

            // Recording Preferences
            item(key = "header_recording", contentType = "header") {
                SettingsSectionHeader("Recording Preferences")
            }
            item(key = "card_recording", contentType = "card") {
                SafeVoiceCard {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Filled.Schedule,
                            title = "Recording Duration Settings",
                            subtitle = "Auto-split every ${prefs.segmentDurationMinutes} minutes",
                            testTag = "settings_segment_duration",
                            onClick = { showDurationDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.GraphicEq,
                            title = "Audio Quality Settings",
                            subtitle = "${qualityPreset.title} (${qualityPreset.kbps} kbps • ${qualityPreset.approxSizeLabel})",
                            testTag = "settings_audio_quality",
                            onClick = { showQualityDialog = true }
                        )
                    }
                }
            }

            // Device, Privacy & Support
            item(key = "header_support", contentType = "header") {
                SettingsSectionHeader("Device, Privacy & Support")
            }
            item(key = "card_support", contentType = "card") {
                SafeVoiceCard {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Filled.Notifications,
                            title = "Notification Settings",
                            subtitle = "Manage silent recording notification channel in Android Settings",
                            testTag = "settings_notification_settings",
                            onClick = { IntentLaunchers.openAppNotificationSettings(context) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.SdStorage,
                            title = "Storage Information",
                            subtitle = "Device headroom & encrypted vault footprint",
                            testTag = "settings_storage_info",
                            onClick = onOpenStorageInfo
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.PrivacyTip,
                            title = "Privacy Information",
                            subtitle = "Consent-first rules, Keystore encryption & Drive scope",
                            testTag = "settings_privacy_info",
                            onClick = onOpenPrivacyInfo
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.Filled.Policy,
                            title = "Privacy Policy",
                            subtitle = "Effective November 24, 2026 • Data use, rights & contact",
                            testTag = "settings_privacy_policy",
                            onClick = onOpenPrivacyPolicy
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsRowItem(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = "About SafeVoice & Help",
                            subtitle = "Version 1.0 • Crash recovery & battery tips",
                            testTag = "settings_about_help",
                            onClick = onOpenAboutHelp
                        )
                    }
                }
            }

            item(key = "bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showDurationDialog) {
        val durationChoices = remember {
            listOf(15, 30, 45, 60).map { mins ->
                RadioChoice(
                    value = mins,
                    title = "$mins minutes",
                    subtitle = if (mins == 30) "Recommended default balance" else "Auto-encrypt & rotate every $mins min"
                )
            }
        }
        RadioOptionDialog(
            title = "Segment Split Duration",
            options = durationChoices,
            selectedValue = prefs.segmentDurationMinutes,
            onSelect = { mins ->
                onSelectSegmentDuration(mins)
                showDurationDialog = false
            },
            onDismiss = { showDurationDialog = false }
        )
    }

    if (showQualityDialog) {
        val qualityChoices = remember {
            AudioQualityPreset.entries.map { preset ->
                RadioChoice(
                    value = preset.kbps,
                    title = "${preset.title} (${preset.kbps} kbps)",
                    subtitle = preset.approxSizeLabel
                )
            }
        }
        RadioOptionDialog(
            title = "Audio Quality Preset",
            options = qualityChoices,
            selectedValue = prefs.audioQualityKbps,
            onSelect = { kbps ->
                onSelectAudioQuality(kbps)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = SafeBluePrimary,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = SafeBluePrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
