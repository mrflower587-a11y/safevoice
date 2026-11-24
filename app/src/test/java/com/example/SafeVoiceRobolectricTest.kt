package com.example

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RecordingEntity
import com.example.data.local.SafeVoiceDatabase
import com.example.data.local.UploadStatus
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.recording.RecordingNotificationHelper
import com.example.recording.RecordingSessionState
import com.example.recording.RecordingStatus
import com.example.recording.StorageManager
import com.example.recording.StorageStats
import com.example.security.KeystoreCryptoManager
import com.example.security.SecretKeyProvider
import com.example.system.LowRamOptimizer
import com.example.system.PersistentBackgroundService
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PinVerifyScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StorageInfoScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.SafeVoiceTheme
import com.example.ui.viewmodel.PinVerifyUiState
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafeVoiceRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SafeVoiceDatabase

    private class TestKeyProvider : SecretKeyProvider {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getOrCreateKey(): SecretKey = key
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SafeVoiceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `room dao prevents duplicate uploads via remoteDriveFileId check`() = runBlocking {
        val dao = db.recordingDao()
        val id1 = dao.insert(
            RecordingEntity(
                fileName = "SafeVoice_2026-11-23_10-00-00.m4a",
                startTimeMs = 1_000L,
                endTimeMs = 5_000L,
                durationMs = 4_000L,
                fileSizeBytes = 2048L,
                decryptedSizeBytes = 2000L,
                encryptedFilePath = "/tmp/a.enc",
                uploadStatus = UploadStatus.PENDING
            )
        )
        val id2 = dao.insert(
            RecordingEntity(
                fileName = "SafeVoice_2026-11-23_10-30-00.m4a",
                startTimeMs = 2_000L,
                endTimeMs = 6_000L,
                durationMs = 4_000L,
                fileSizeBytes = 2048L,
                decryptedSizeBytes = 2000L,
                encryptedFilePath = "/tmp/b.enc",
                uploadStatus = UploadStatus.PENDING
            )
        )

        assertEquals(2, dao.getPendingUploadsSync().size)

        val updatedRows = dao.markUploaded(id1, "drive_file_abc", "folder_123")
        assertEquals(1, updatedRows)

        // Second markUploaded is idempotent and returns 0 rows
        val duplicateRows = dao.markUploaded(id1, "drive_file_other", "folder_123")
        assertEquals(0, duplicateRows)
        assertEquals("drive_file_abc", dao.getById(id1)?.remoteDriveFileId)

        val remainingPending = dao.getPendingUploadsSync()
        assertEquals(1, remainingPending.size)
        assertEquals(id2, remainingPending.first().id)
    }

    @Test
    fun `staging crash recovery removes tiny corrupt stubs and recovers valid segments`() = runBlocking {
        val crypto = KeystoreCryptoManager(TestKeyProvider())
        val storage = StorageManager(context, crypto)
        storage.stagingDir.listFiles()?.forEach { it.delete() }
        storage.vaultDir.listFiles()?.forEach { it.delete() }

        val tinyStub = File(storage.stagingDir, "SafeVoice_stub.m4a").apply {
            writeBytes(ByteArray(512) { 1 })
        }
        val validSegment = File(storage.stagingDir, "SafeVoice_2026-11-23_09-00-00.m4a").apply {
            writeBytes(ByteArray(4096) { 7 })
        }

        val recovered = storage.recoverOrphanedStagingSegments(db.recordingDao())
        assertEquals(1, recovered)
        assertFalse(tinyStub.exists())
        assertFalse(validSegment.exists())

        val saved = db.recordingDao().getPendingUploadsSync()
        assertEquals(1, saved.size)
        assertTrue(saved.first().isRecovered)
        assertEquals("SafeVoice_2026-11-23_09-00-00.m4a", saved.first().fileName)
        assertTrue(File(saved.first().encryptedFilePath).exists())
    }

    @Test
    fun `staging crash recovery skips active staging paths while recording`() = runBlocking {
        val crypto = KeystoreCryptoManager(TestKeyProvider())
        val storage = StorageManager(context, crypto)
        storage.stagingDir.listFiles()?.forEach { it.delete() }
        storage.vaultDir.listFiles()?.forEach { it.delete() }

        val activeStub = File(storage.stagingDir, "SafeVoice_active.m4a").apply {
            writeBytes(ByteArray(256) { 2 })
        }
        val orphanSegment = File(storage.stagingDir, "SafeVoice_2026-11-23_09-05-00.m4a").apply {
            writeBytes(ByteArray(4096) { 5 })
        }

        val recovered = storage.recoverOrphanedStagingSegments(
            recordingDao = db.recordingDao(),
            activeStagingPaths = setOf(activeStub.absolutePath)
        )
        assertEquals(1, recovered)
        assertTrue(activeStub.exists())
        assertFalse(orphanSegment.exists())
    }

    @Test
    fun `recording notification channel is silent and IMPORTANCE_MIN`() {
        RecordingNotificationHelper.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(RecordingNotificationHelper.CHANNEL_ID)

        assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)
        assertFalse(channel.canShowBadge())
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.shouldShowLights())
    }

    @Test
    fun `welcome screen shows SafeVoice branding and Get Started`() {
        var started = false
        composeRule.setContent {
            SafeVoiceTheme {
                WelcomeScreen(onGetStarted = { started = true })
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SafeVoice").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_get_started_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(started)
    }

    @Test
    fun `home screen focuses on recording controls without library or drive summary cards`() {
        composeRule.setContent {
            SafeVoiceTheme {
                HomeScreen(
                    recordingState = RecordingSessionState(),
                    preferences = UserPreferences(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onViewActiveSession = {},
                    onOpenSettings = {},
                    onDismissError = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("start_recording_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_consent_card").assertDoesNotExist()
        composeRule.onNodeWithTag("home_background_status_card").assertDoesNotExist()
        composeRule.onNodeWithText("Consent-First Protection").assertDoesNotExist()
        composeRule.onNodeWithText("Always-On Background").assertDoesNotExist()
        composeRule.onNodeWithText("Saved Files").assertDoesNotExist()
        composeRule.onNodeWithText("Latest Upload Status").assertDoesNotExist()
    }

    @Test
    fun `settings screen orders Saved Files, Google Drive, and Latest Upload Status at top`() {
        composeRule.setContent {
            SafeVoiceTheme {
                SettingsScreen(
                    preferences = UserPreferences(connectedDriveEmail = "user@example.com"),
                    savedFilesCount = 3,
                    pendingQueueCount = 1,
                    onOpenMyFiles = {},
                    onOpenDriveConnection = {},
                    onOpenUploadProgress = {},
                    onChangePin = {},
                    onSelectDriveFolder = {},
                    onToggleAutoUploadFive = {},
                    onTogglePeriodic90Min = {},
                    onOpenUploadQueue = {},
                    onSelectSegmentDuration = {},
                    onSelectAudioQuality = {},
                    onOpenStorageInfo = {},
                    onOpenPrivacyInfo = {},
                    onOpenAboutHelp = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        val savedFilesTop = composeRule.onNodeWithTag("settings_saved_files")
            .fetchSemanticsNode().boundsInRoot.top
        val driveTop = composeRule.onNodeWithTag("settings_google_drive")
            .fetchSemanticsNode().boundsInRoot.top
        val uploadStatusTop = composeRule.onNodeWithTag("settings_latest_upload_status")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("Saved Files must precede Google Drive", savedFilesTop < driveTop)
        assertTrue("Google Drive must precede Latest Upload Status", driveTop < uploadStatusTop)
        composeRule.onNodeWithTag("settings_lazy_list")
            .performScrollToNode(hasTestTag("settings_notification_settings"))
        composeRule.onNodeWithTag("settings_notification_settings").assertIsDisplayed()
    }

    @Test
    fun `pin verify screen displays error, lockout banner, and forgot pin dialog`() {
        var forgotOpened = false
        composeRule.setContent {
            SafeVoiceTheme {
                PinVerifyScreen(
                    verifyState = PinVerifyUiState(
                        errorMessage = "Incorrect PIN",
                        remainingLockoutSeconds = 15,
                        showForgotPinDialog = true
                    ),
                    destinationLabel = "Settings",
                    onVerifyPin = {},
                    onOpenForgotPin = { forgotOpened = true },
                    onCloseForgotPin = {},
                    onVerifyRecoveryCode = {},
                    onResetPinAfterRecovery = { _, _ -> },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pin_verify_error").assertIsDisplayed()
        composeRule.onNodeWithTag("pin_lockout_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("recovery_code_input").assertIsDisplayed()
    }

    @Test
    fun `pin verify screen renders stop recording prompt when target is Stop Recording`() {
        composeRule.setContent {
            SafeVoiceTheme {
                PinVerifyScreen(
                    verifyState = PinVerifyUiState(),
                    destinationLabel = "Stop Recording",
                    onVerifyPin = {},
                    onOpenForgotPin = {},
                    onCloseForgotPin = {},
                    onVerifyRecoveryCode = {},
                    onResetPinAfterRecovery = { _, _ -> },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Verify PIN to Stop Recording").assertIsDisplayed()
        composeRule.onNodeWithText("Verify & Stop Recording").assertIsDisplayed()
    }

    @Test
    fun `home screen requires PIN action callback when stopping an active recording`() {
        var stopRequested = false
        composeRule.setContent {
            SafeVoiceTheme {
                HomeScreen(
                    recordingState = RecordingSessionState(
                        status = RecordingStatus.RECORDING,
                        currentSegmentFileName = "SafeVoice_2026-11-23_11-00-00.m4a"
                    ),
                    preferences = UserPreferences(hasPinConfigured = true),
                    onStartRecording = {},
                    onStopRecording = { stopRequested = true },
                    onViewActiveSession = {},
                    onOpenSettings = {},
                    onDismissError = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_stop_recording_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(stopRequested)
    }

    @Test
    fun `recording notification stop action routes to MainActivity PIN prompt`() {
        val notification = RecordingNotificationHelper.buildRecordingNotification(
            context = context,
            isPaused = false,
            segmentFileName = "SafeVoice_2026-11-23_11-00-00.m4a",
            chronometerBaseMs = 1_000L
        )
        assertEquals(1, notification.actions.size)
        val stopAction = notification.actions[0]
        val shadowPendingIntent = Shadows.shadowOf(stopAction.actionIntent)
        val targetIntent = shadowPendingIntent.savedIntent
        assertEquals(MainActivity::class.java.name, targetIntent.component?.className)
        assertTrue(targetIntent.getBooleanExtra(MainActivity.EXTRA_REQUIRE_PIN_TO_STOP, false))
    }

    @Test
    fun `storage info screen displays 1 GB RAM optimization mode card`() {
        val profile = LowRamOptimizer(context).getRamProfile().copy(
            totalRamBytes = 1_073_741_824L,
            availableRamBytes = 314_572_800L,
            isLowRamDevice = true,
            streamBufferBytes = 16 * 1024,
            amplitudePollIntervalMs = 300L,
            okHttpMaxIdleConnections = 1
        )
        composeRule.setContent {
            SafeVoiceTheme {
                StorageInfoScreen(
                    storageStats = StorageStats(
                        freeBytes = 2_000_000_000L,
                        totalBytes = 8_000_000_000L,
                        vaultBytes = 1_024_000L,
                        hasMinimumHeadroom = true
                    ),
                    ramProfile = profile,
                    onRefresh = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("storage_ram_profile_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("1 GB RAM Optimization Active").assertIsDisplayed()
    }

    @Test
    fun `standby notification channel is silent and IMPORTANCE_MIN`() {
        RecordingNotificationHelper.ensureStandbyChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(RecordingNotificationHelper.STANDBY_CHANNEL_ID)

        assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)
        assertFalse(channel.canShowBadge())
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun `persistent background service runs START_STICKY and stops cleanly on ACTION_STOP`() {
        val controller = Robolectric.buildService(PersistentBackgroundService::class.java).create()
        val service = controller.get()

        val startResult = service.onStartCommand(
            Intent(context, PersistentBackgroundService::class.java).apply {
                action = PersistentBackgroundService.ACTION_START
            },
            0,
            1
        )
        assertEquals(Service.START_STICKY, startResult)
        assertTrue(PersistentBackgroundService.isRunningFlow.value)

        val stopResult = service.onStartCommand(
            Intent(context, PersistentBackgroundService::class.java).apply {
                action = PersistentBackgroundService.ACTION_STOP
            },
            0,
            2
        )
        assertEquals(Service.START_NOT_STICKY, stopResult)
        assertFalse(PersistentBackgroundService.isRunningFlow.value)
    }

    @Test
    fun `settings screen displays Always-On Background status card and battery optimization row`() {
        composeRule.setContent {
            SafeVoiceTheme {
                SettingsScreen(
                    preferences = UserPreferences(alwaysRunInBackground = true),
                    savedFilesCount = 0,
                    pendingQueueCount = 0,
                    onOpenMyFiles = {},
                    onOpenDriveConnection = {},
                    onOpenUploadProgress = {},
                    onChangePin = {},
                    onSelectDriveFolder = {},
                    onToggleAutoUploadFive = {},
                    onTogglePeriodic90Min = {},
                    onOpenUploadQueue = {},
                    onSelectSegmentDuration = {},
                    onSelectAudioQuality = {},
                    onOpenStorageInfo = {},
                    onOpenPrivacyInfo = {},
                    onOpenAboutHelp = {},
                    onBack = {},
                    isBackgroundServiceRunning = true,
                    isIgnoringBatteryOptimizations = false
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_list")
            .performScrollToNode(hasTestTag("settings_always_on_card"))
        composeRule.onNodeWithTag("settings_always_on_card").assertIsDisplayed()
        composeRule.onNodeWithText("Always-On Background").assertIsDisplayed()
        composeRule.onNodeWithText("Running").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_battery_optimization").assertIsDisplayed()
        composeRule.onNodeWithText("Restricted — tap to allow Unrestricted background")
            .assertIsDisplayed()
    }

    @Test
    fun `settings screen shows Always-On Background Mode switch and toggles callback`() {
        var toggledTo: Boolean? = null
        composeRule.setContent {
            SafeVoiceTheme {
                SettingsScreen(
                    preferences = UserPreferences(alwaysRunInBackground = true),
                    savedFilesCount = 0,
                    pendingQueueCount = 0,
                    onOpenMyFiles = {},
                    onOpenDriveConnection = {},
                    onOpenUploadProgress = {},
                    onChangePin = {},
                    onSelectDriveFolder = {},
                    onToggleAutoUploadFive = {},
                    onTogglePeriodic90Min = {},
                    onOpenUploadQueue = {},
                    onSelectSegmentDuration = {},
                    onSelectAudioQuality = {},
                    onOpenStorageInfo = {},
                    onOpenPrivacyInfo = {},
                    onOpenAboutHelp = {},
                    onBack = {},
                    isIgnoringBatteryOptimizations = false,
                    onToggleAlwaysRunInBackground = { toggledTo = it }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_list")
            .performScrollToNode(hasTestTag("switch_always_run_background"))
        composeRule.onNodeWithTag("switch_always_run_background")
            .assertIsDisplayed()
            .performClick()
        assertEquals(false, toggledTo)
    }

    @Test
    fun `preferences repository persists recordingSessionActive across restart`() = runBlocking {
        val repo = UserPreferencesRepository(context)
        repo.setRecordingSessionState(active = true, paused = false)
        var snapshot = repo.getSnapshot()
        assertTrue(snapshot.recordingSessionActive)
        assertFalse(snapshot.recordingSessionPaused)

        repo.setRecordingSessionState(active = true, paused = true)
        snapshot = repo.getSnapshot()
        assertTrue(snapshot.recordingSessionActive)
        assertTrue(snapshot.recordingSessionPaused)

        repo.setRecordingSessionState(active = false, paused = false)
        snapshot = repo.getSnapshot()
        assertFalse(snapshot.recordingSessionActive)
        assertFalse(snapshot.recordingSessionPaused)
    }

    @Test
    fun `resume notification targets MainActivity with EXTRA_AUTO_RESUME_RECORDING`() {
        RecordingNotificationHelper.showResumeRecordingNotification(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).allNotifications
            .firstOrNull {
                it.extras.getString("android.title") ==
                    context.getString(R.string.notification_resume_title)
            }
        assertTrue("Expected resume recording notification to be posted", posted != null)
        val targetIntent = Shadows.shadowOf(posted!!.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, targetIntent.component?.className)
        assertTrue(targetIntent.getBooleanExtra(MainActivity.EXTRA_AUTO_RESUME_RECORDING, false))
    }

    @Test
    fun `privacy policy screen displays policy text without personal email`() {
        var backClicked = false
        composeRule.setContent {
            SafeVoiceTheme {
                PrivacyPolicyScreen(onBack = { backClicked = true })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Privacy Policy for Safe Voice").assertIsDisplayed()
        composeRule.onNodeWithText("Effective Date: November 24, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("1. Information We Collect").assertIsDisplayed()

        composeRule.onNodeWithTag("privacy_policy_list")
            .performScrollToNode(hasText("4. Data Security and Retention"))
        composeRule.onNodeWithText("4. Data Security and Retention").assertIsDisplayed()

        composeRule.onNodeWithTag("privacy_policy_list")
            .performScrollToNode(hasTestTag("privacy_policy_contact_card"))
        composeRule.onNodeWithText("8. Contact Us").assertIsDisplayed()
        composeRule.onNodeWithText("App Name: Safe Voice").assertIsDisplayed()
        composeRule.onNodeWithText("gmail.com", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("privacy_policy_contact_email_button").assertDoesNotExist()

        composeRule.onNodeWithTag("privacy_policy_back_button").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `privacy policy links are easily accessible on Welcome, Home, and Settings screens`() {
        var welcomeClicked = false
        composeRule.setContent {
            SafeVoiceTheme {
                WelcomeScreen(
                    onGetStarted = {},
                    onOpenPrivacyPolicy = { welcomeClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("welcome_privacy_policy_link")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(welcomeClicked)
    }

    @Test
    fun `home and settings privacy policy links trigger navigation callbacks`() {
        var homeClicked = false
        composeRule.setContent {
            SafeVoiceTheme {
                HomeScreen(
                    recordingState = RecordingSessionState(),
                    preferences = UserPreferences(),
                    onStartRecording = {},
                    onStopRecording = {},
                    onViewActiveSession = {},
                    onOpenSettings = {},
                    onDismissError = {},
                    onOpenPrivacyPolicy = { homeClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_privacy_policy_link")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(homeClicked)
    }

    @Test
    fun `settings privacy policy row triggers callback`() {
        var settingsPolicyClicked = false
        composeRule.setContent {
            SafeVoiceTheme {
                SettingsScreen(
                    preferences = UserPreferences(),
                    savedFilesCount = 0,
                    pendingQueueCount = 0,
                    onOpenMyFiles = {},
                    onOpenDriveConnection = {},
                    onOpenUploadProgress = {},
                    onChangePin = {},
                    onSelectDriveFolder = {},
                    onToggleAutoUploadFive = {},
                    onTogglePeriodic90Min = {},
                    onOpenUploadQueue = {},
                    onSelectSegmentDuration = {},
                    onSelectAudioQuality = {},
                    onOpenStorageInfo = {},
                    onOpenPrivacyInfo = {},
                    onOpenAboutHelp = {},
                    onBack = {},
                    onOpenPrivacyPolicy = { settingsPolicyClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_list")
            .performScrollToNode(hasTestTag("settings_privacy_policy"))
        composeRule.onNodeWithTag("settings_privacy_policy")
            .assertIsDisplayed()
            .performClick()
        assertTrue(settingsPolicyClicked)
    }
}
