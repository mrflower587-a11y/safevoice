package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.preferences.UserPreferences
import com.example.ui.components.IntentLaunchers
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusSuccessText

@Composable
fun SplashScreen(
    preferences: UserPreferences?,
    onNavigateToWelcome: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    LaunchedEffect(preferences) {
        if (preferences != null) {
            if (preferences.onboardingCompleted && preferences.hasPinConfigured) {
                onNavigateToHome()
            } else {
                onNavigateToWelcome()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SafeBluePrimary,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_safevoice_mark),
                        contentDescription = "SafeVoice logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Text(
                text = "SafeVoice",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            CircularProgressIndicator(
                color = SafeBluePrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_privacy_hero),
                contentDescription = "SafeVoice privacy shield illustration",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SafeVoice",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Private, encrypted voice recording built on explicit consent.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            WelcomeFeatureRow(
                icon = Icons.Filled.Mic,
                title = "Consent-First Recording",
                subtitle = "Recording starts only when you tap Start Recording, with a silent ongoing status indicator."
            )
            WelcomeFeatureRow(
                icon = Icons.Filled.Lock,
                title = "Hardware-Backed AES-256-GCM",
                subtitle = "Every audio segment is encrypted in 64 KB chunks using Android Keystore before storage."
            )
            WelcomeFeatureRow(
                icon = Icons.Filled.CloudDone,
                title = "Minimal-Scope Google Drive Sync",
                subtitle = "Uses only drive.file access for folders SafeVoice creates—never reads your other Drive files."
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_get_started_button")
            ) {
                Text("Get Started", style = MaterialTheme.typography.labelLarge)
            }

            TextButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .minimumInteractiveComponentSize()
                    .testTag("welcome_privacy_policy_link")
            ) {
                Icon(
                    imageVector = Icons.Filled.Policy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Read Privacy Policy", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    SafeVoiceCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SafeBlueContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = SafeBluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    SafeVoiceScaffold(title = "Permissions") { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SafeVoice requests only the permissions strictly required for intentional voice recording.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionCard(
                icon = Icons.Filled.Mic,
                title = "Microphone (Required)",
                description = "Captures audio strictly when you press Start Recording.",
                granted = micGranted,
                allowTestTag = "allow_mic_button",
                onAllowClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettingsClick = { IntentLaunchers.openAppDetailsSettings(context) }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    icon = Icons.Filled.Notifications,
                    title = "Silent Recording Status (Recommended)",
                    description = "Displays a silent, low-priority ongoing bar while recording in the background.",
                    granted = notifGranted,
                    allowTestTag = "allow_notifications_button",
                    onAllowClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onOpenSettingsClick = { IntentLaunchers.openAppNotificationSettings(context) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onContinue,
                enabled = micGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("permissions_continue_button")
            ) {
                Text(if (micGranted) "Continue to PIN Setup" else "Grant Microphone to Continue")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    allowTestTag: String,
    onAllowClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    SafeVoiceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (granted) StatusSuccessBg else SafeBlueContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (granted) Icons.Filled.CheckCircle else icon,
                            contentDescription = title,
                            tint = if (granted) StatusSuccessText else SafeBluePrimary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (granted) "Granted" else "Not granted yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (granted) StatusSuccessText else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onAllowClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(allowTestTag)
                    ) {
                        Text("Allow")
                    }
                    OutlinedButton(
                        onClick = onOpenSettingsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_app_settings_button")
                    ) {
                        Text("Open Settings")
                    }
                }
            }
        }
    }
}
