package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.AboutHelpScreen
import com.example.ui.screens.AudioPlayerScreen
import com.example.ui.screens.DriveConnectionScreen
import com.example.ui.screens.DriveFolderSelectScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyFilesScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PinSetupScreen
import com.example.ui.screens.PinVerifyScreen
import com.example.ui.screens.PrivacyInfoScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.RecordingSessionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StorageInfoScreen
import com.example.ui.screens.UploadProgressScreen
import com.example.ui.screens.UploadQueueScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.viewmodel.SafeVoiceViewModel

@Composable
fun SafeVoiceNavHost(
    viewModel: SafeVoiceViewModel,
    navController: NavHostController = rememberNavController(),
    stopRecordingPinRequested: Boolean = false,
    onStopPinRequestConsumed: () -> Unit = {},
    autoResumeRecordingRequested: Boolean = false,
    onAutoResumeConsumed: () -> Unit = {}
) {
    BackgroundAndIntentEffects(
        viewModel = viewModel,
        navController = navController,
        stopRecordingPinRequested = stopRecordingPinRequested,
        onStopPinRequestConsumed = onStopPinRequestConsumed,
        autoResumeRecordingRequested = autoResumeRecordingRequested,
        onAutoResumeConsumed = onAutoResumeConsumed
    )

    NavHost(
        navController = navController,
        startDestination = SafeVoiceRoute.Splash
    ) {
        composable<SafeVoiceRoute.Splash> {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            SplashScreen(
                preferences = preferences,
                onNavigateToWelcome = {
                    navController.navigate(SafeVoiceRoute.Welcome) {
                        popUpTo(SafeVoiceRoute.Splash) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(SafeVoiceRoute.Home) {
                        popUpTo(SafeVoiceRoute.Splash) { inclusive = true }
                    }
                }
            )
        }

        composable<SafeVoiceRoute.Welcome> {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(SafeVoiceRoute.Permissions)
                },
                onOpenPrivacyPolicy = {
                    navController.navigate(SafeVoiceRoute.PrivacyPolicy)
                }
            )
        }

        composable<SafeVoiceRoute.Permissions> {
            PermissionsScreen(
                onContinue = {
                    viewModel.pinViewModel.resetSetupState()
                    navController.navigate(SafeVoiceRoute.PinSetup(isChangingPin = false))
                }
            )
        }

        composable<SafeVoiceRoute.PinSetup> { backStackEntry ->
            val route = backStackEntry.toRoute<SafeVoiceRoute.PinSetup>()
            val pinSetupState by viewModel.pinViewModel.setupState.collectAsStateWithLifecycle()
            PinSetupScreen(
                isChangingPin = route.isChangingPin,
                setupState = pinSetupState,
                onCreatePin = { pin, confirm ->
                    viewModel.pinViewModel.createPinAndRecovery(
                        pin = pin,
                        confirmPin = confirm,
                        markOnboardingComplete = !route.isChangingPin
                    )
                },
                onDismissError = { viewModel.pinViewModel.dismissSetupError() },
                onFinish = {
                    viewModel.pinViewModel.resetSetupState()
                    if (route.isChangingPin) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(SafeVoiceRoute.Home) {
                            popUpTo(SafeVoiceRoute.Welcome) { inclusive = true }
                        }
                    }
                },
                onBackClick = {
                    viewModel.pinViewModel.resetSetupState()
                    navController.popBackStack()
                }
            )
        }

        composable<SafeVoiceRoute.Home> {
            val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            HomeScreen(
                recordingState = recordingState,
                preferences = preferences,
                onStartRecording = { viewModel.startRecordingService() },
                onStopRecording = {
                    if (preferences?.hasPinConfigured == true) {
                        navController.navigate(SafeVoiceRoute.PinVerify(SafeVoiceRoute.TARGET_STOP_RECORDING))
                    } else {
                        viewModel.stopRecordingService()
                    }
                },
                onViewActiveSession = { navController.navigate(SafeVoiceRoute.RecordingSession) },
                onOpenSettings = {
                    navController.navigate(SafeVoiceRoute.PinVerify(SafeVoiceRoute.TARGET_SETTINGS))
                },
                onDismissError = { viewModel.dismissRecorderError() },
                onOpenPrivacyPolicy = {
                    navController.navigate(SafeVoiceRoute.PrivacyPolicy)
                }
            )
        }

        composable<SafeVoiceRoute.RecordingSession> {
            val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            RecordingSessionScreen(
                recordingState = recordingState,
                onPause = { viewModel.pauseRecordingService() },
                onResume = { viewModel.resumeRecordingService() },
                onStop = {
                    if (preferences?.hasPinConfigured == true) {
                        navController.navigate(SafeVoiceRoute.PinVerify(SafeVoiceRoute.TARGET_STOP_RECORDING))
                    } else {
                        viewModel.stopRecordingService()
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
                onDismissError = { viewModel.dismissRecorderError() }
            )
        }

        composable<SafeVoiceRoute.PinVerify> { backStackEntry ->
            val route = backStackEntry.toRoute<SafeVoiceRoute.PinVerify>()
            val pinVerifyState by viewModel.pinViewModel.verifyState.collectAsStateWithLifecycle()
            LaunchedEffect(route.target) {
                viewModel.pinViewModel.clearVerifyTransientState()
            }
            val targetLabel = when (route.target) {
                SafeVoiceRoute.TARGET_STOP_RECORDING -> "Stop Recording"
                SafeVoiceRoute.TARGET_MY_FILES -> "My Files"
                else -> "Settings"
            }
            val onUnlocked: () -> Unit = {
                if (route.target == SafeVoiceRoute.TARGET_STOP_RECORDING) {
                    viewModel.stopRecordingService()
                    navController.navigate(SafeVoiceRoute.Home) {
                        popUpTo<SafeVoiceRoute.Home> { inclusive = false }
                        launchSingleTop = true
                    }
                } else {
                    val dest: SafeVoiceRoute = if (route.target == SafeVoiceRoute.TARGET_MY_FILES) {
                        SafeVoiceRoute.MyFiles
                    } else {
                        SafeVoiceRoute.Settings
                    }
                    navController.navigate(dest) {
                        popUpTo<SafeVoiceRoute.PinVerify> { inclusive = true }
                    }
                }
            }

            PinVerifyScreen(
                verifyState = pinVerifyState,
                destinationLabel = targetLabel,
                onVerifyPin = { pin -> viewModel.pinViewModel.verifyPin(pin, onUnlocked) },
                onOpenForgotPin = { viewModel.pinViewModel.openForgotPinDialog() },
                onCloseForgotPin = { viewModel.pinViewModel.closeForgotPinDialog() },
                onVerifyRecoveryCode = { code -> viewModel.pinViewModel.verifyRecoveryCode(code) },
                onResetPinAfterRecovery = { newPin, confirmPin ->
                    viewModel.pinViewModel.resetPinAfterRecovery(newPin, confirmPin, onUnlocked)
                },
                onBack = {
                    viewModel.pinViewModel.clearVerifyTransientState()
                    navController.popBackStack()
                },
                onDismissRecoveryError = { viewModel.pinViewModel.dismissRecoveryError() }
            )
        }

        composable<SafeVoiceRoute.Settings> {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val savedFilesCount by viewModel.savedFilesCount.collectAsStateWithLifecycle()
            val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
            val isBackgroundServiceRunning by viewModel.isBackgroundServiceRunning.collectAsStateWithLifecycle()
            val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
            SettingsScreen(
                preferences = preferences,
                savedFilesCount = savedFilesCount,
                pendingQueueCount = pendingCount,
                onOpenMyFiles = { navController.navigate(SafeVoiceRoute.MyFiles) },
                onOpenDriveConnection = { navController.navigate(SafeVoiceRoute.DriveConnection) },
                onOpenUploadProgress = { navController.navigate(SafeVoiceRoute.UploadProgress) },
                onChangePin = {
                    viewModel.pinViewModel.resetSetupState()
                    navController.navigate(SafeVoiceRoute.PinSetup(isChangingPin = true))
                },
                onSelectDriveFolder = { navController.navigate(SafeVoiceRoute.DriveFolderSelect) },
                onToggleAutoUploadFive = { viewModel.setAutoUploadAfterFive(it) },
                onTogglePeriodic90Min = { viewModel.setPeriodicUpload90Min(it) },
                onOpenUploadQueue = { navController.navigate(SafeVoiceRoute.UploadQueue) },
                onSelectSegmentDuration = { viewModel.setSegmentDurationMinutes(it) },
                onSelectAudioQuality = { viewModel.setAudioQualityKbps(it) },
                onOpenStorageInfo = { navController.navigate(SafeVoiceRoute.StorageInfo) },
                onOpenPrivacyInfo = { navController.navigate(SafeVoiceRoute.PrivacyInfo) },
                onOpenAboutHelp = { navController.navigate(SafeVoiceRoute.AboutHelp) },
                onBack = { navController.popBackStack() },
                isBackgroundServiceRunning = isBackgroundServiceRunning,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                onToggleAlwaysRunInBackground = { viewModel.setAlwaysRunInBackground(it) },
                onRefreshBatteryStatus = { viewModel.refreshBatteryStatus() },
                onOpenPrivacyPolicy = { navController.navigate(SafeVoiceRoute.PrivacyPolicy) }
            )
        }

        composable<SafeVoiceRoute.MyFiles> {
            val allRecordings by viewModel.allRecordings.collectAsStateWithLifecycle()
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            MyFilesScreen(
                recordings = allRecordings,
                playbackState = playbackState,
                onPlayOrToggle = { viewModel.playOrToggleRecording(it) },
                onUploadNow = { viewModel.enqueueSingleUpload(it.id) },
                onOpenPlayer = { navController.navigate(SafeVoiceRoute.AudioPlayer(it.id)) },
                onDeleteRecording = { viewModel.deleteRecording(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SafeVoiceRoute.AudioPlayer> { backStackEntry ->
            val route = backStackEntry.toRoute<SafeVoiceRoute.AudioPlayer>()
            val recording by viewModel.observeRecordingById(route.recordingId)
                .collectAsStateWithLifecycle(initialValue = null)
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            AudioPlayerScreen(
                recording = recording,
                playbackState = playbackState,
                onPlayRecording = { viewModel.startPlaybackIfNotActive(it) },
                onTogglePlayPause = { viewModel.togglePlayback() },
                onSeekTo = { viewModel.seekPlayback(it) },
                onStopPlayback = { viewModel.stopPlayback() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SafeVoiceRoute.DriveConnection> {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val driveState by viewModel.driveViewModel.uiState.collectAsStateWithLifecycle()
            DriveConnectionScreen(
                preferences = preferences,
                driveState = driveState,
                onConnectOrRefresh = { viewModel.driveViewModel.connectOrRefreshDrive() },
                onAuthResult = { viewModel.driveViewModel.onAuthorizationActivityResult(it) },
                onConsentLaunched = { viewModel.driveViewModel.consumePendingConsentIntent() },
                onDisconnect = { viewModel.driveViewModel.disconnect() },
                onSelectFolder = { navController.navigate(SafeVoiceRoute.DriveFolderSelect) },
                onBack = { navController.popBackStack() },
                onDismissError = { viewModel.driveViewModel.dismissError() },
                onOpenPrivacyPolicy = { navController.navigate(SafeVoiceRoute.PrivacyPolicy) }
            )
        }

        composable<SafeVoiceRoute.DriveFolderSelect> {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val driveState by viewModel.driveViewModel.uiState.collectAsStateWithLifecycle()
            DriveFolderSelectScreen(
                preferences = preferences,
                driveState = driveState,
                onLoadFolders = { viewModel.driveViewModel.loadFolders() },
                onSelectFolder = { folder ->
                    viewModel.driveViewModel.selectFolder(folder) {
                        navController.popBackStack()
                    }
                },
                onCreateFolder = { name ->
                    viewModel.driveViewModel.createAndSelectFolder(name) {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
                onDismissError = { viewModel.driveViewModel.dismissError() }
            )
        }

        composable<SafeVoiceRoute.UploadQueue> {
            val pendingQueue by viewModel.pendingQueue.collectAsStateWithLifecycle()
            UploadQueueScreen(
                pendingQueue = pendingQueue,
                onUploadAll = { viewModel.enqueueAllPendingUploads() },
                onUploadSingle = { id -> viewModel.enqueueSingleUpload(id) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SafeVoiceRoute.UploadProgress> {
            val allRecordings by viewModel.allRecordings.collectAsStateWithLifecycle()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            UploadProgressScreen(
                recordings = allRecordings,
                latestStatusText = preferences?.latestUploadStatusText ?: "No uploads yet",
                onUploadAllPending = { viewModel.enqueueAllPendingUploads() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SafeVoiceRoute.StorageInfo> {
            val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
            val ramProfile by viewModel.ramProfile.collectAsStateWithLifecycle()
            StorageInfoScreen(
                storageStats = storageStats,
                ramProfile = ramProfile,
                onRefresh = { viewModel.refreshStorageStats() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SafeVoiceRoute.PrivacyInfo> {
            PrivacyInfoScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacyPolicy = { navController.navigate(SafeVoiceRoute.PrivacyPolicy) }
            )
        }

        composable<SafeVoiceRoute.PrivacyPolicy> {
            PrivacyPolicyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<SafeVoiceRoute.AboutHelp> {
            AboutHelpScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacyPolicy = { navController.navigate(SafeVoiceRoute.PrivacyPolicy) }
            )
        }
    }
}

@Composable
private fun BackgroundAndIntentEffects(
    viewModel: SafeVoiceViewModel,
    navController: NavHostController,
    stopRecordingPinRequested: Boolean,
    onStopPinRequestConsumed: () -> Unit,
    autoResumeRecordingRequested: Boolean,
    onAutoResumeConsumed: () -> Unit
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    var autoResumeTriggeredOnLaunch by remember { mutableStateOf(false) }

    LaunchedEffect(preferences?.onboardingCompleted, preferences?.alwaysRunInBackground) {
        val currentPrefs = preferences ?: return@LaunchedEffect
        if (currentPrefs.onboardingCompleted && currentPrefs.alwaysRunInBackground) {
            viewModel.ensureAlwaysOnBackgroundActive()
        }
    }

    LaunchedEffect(
        autoResumeRecordingRequested,
        preferences?.onboardingCompleted,
        preferences?.recordingSessionActive,
        preferences?.recordingSessionPaused
    ) {
        val currentPrefs = preferences ?: return@LaunchedEffect
        if (autoResumeRecordingRequested) {
            onAutoResumeConsumed()
        }
        val shouldResume = currentPrefs.onboardingCompleted &&
            (autoResumeRecordingRequested || (currentPrefs.recordingSessionActive && !currentPrefs.recordingSessionPaused))
        if (shouldResume && !autoResumeTriggeredOnLaunch && !recordingState.isSessionActive) {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                autoResumeTriggeredOnLaunch = true
                viewModel.startRecordingService()
            }
        }
    }

    LaunchedEffect(stopRecordingPinRequested, recordingState.isSessionActive, preferences?.hasPinConfigured) {
        if (stopRecordingPinRequested) {
            val currentPrefs = preferences ?: return@LaunchedEffect
            onStopPinRequestConsumed()
            if (recordingState.isSessionActive) {
                if (currentPrefs.hasPinConfigured) {
                    navController.navigate(SafeVoiceRoute.PinVerify(SafeVoiceRoute.TARGET_STOP_RECORDING)) {
                        launchSingleTop = true
                    }
                } else {
                    viewModel.stopRecordingService()
                }
            }
        }
    }
}
