package com.example

import android.content.Context
import com.example.data.local.SafeVoiceDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.drive.DriveRepository
import com.example.drive.GoogleDriveAuthManager
import com.example.playback.AudioPlaybackManager
import com.example.recording.AudioRecorderController
import com.example.recording.StorageManager
import com.example.security.KeystoreCryptoManager
import com.example.security.PinSecurityManager
import com.example.system.BackgroundKeepAliveScheduler
import com.example.system.LowRamOptimizer
import com.example.worker.UploadScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val lowRamOptimizer: LowRamOptimizer = LowRamOptimizer(appContext)
    private val initialRamProfile = lowRamOptimizer.getRamProfile()

    val database: SafeVoiceDatabase = SafeVoiceDatabase.create(
        context = appContext,
        isLowRamDevice = initialRamProfile.isLowRamDevice
    )
    val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(appContext)
    val cryptoManager: KeystoreCryptoManager = KeystoreCryptoManager()
    val pinSecurityManager: PinSecurityManager = PinSecurityManager()
    val storageManager: StorageManager = StorageManager(appContext, cryptoManager)
    val uploadScheduler: UploadScheduler = UploadScheduler(appContext)
    val keepAliveScheduler: BackgroundKeepAliveScheduler = BackgroundKeepAliveScheduler(appContext)

    val driveAuthManager: GoogleDriveAuthManager = GoogleDriveAuthManager(
        context = appContext,
        preferencesRepository = preferencesRepository
    )

    val driveRepository: DriveRepository = DriveRepository(
        recordingDao = database.recordingDao(),
        preferencesRepository = preferencesRepository,
        cryptoManager = cryptoManager,
        maxIdleConnections = initialRamProfile.okHttpMaxIdleConnections
    )

    val recorderController: AudioRecorderController = AudioRecorderController(
        context = appContext,
        storageManager = storageManager,
        cryptoManager = cryptoManager,
        recordingDao = database.recordingDao(),
        preferencesRepository = preferencesRepository,
        uploadScheduler = uploadScheduler,
        pollIntervalMs = initialRamProfile.amplitudePollIntervalMs
    )

    val playbackManager: AudioPlaybackManager = AudioPlaybackManager(
        storageManager = storageManager,
        cryptoManager = cryptoManager,
        pollIntervalMs = initialRamProfile.amplitudePollIntervalMs
    )
}
