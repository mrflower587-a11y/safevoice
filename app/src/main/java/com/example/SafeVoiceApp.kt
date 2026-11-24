package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.recording.RecordingNotificationHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeVoiceApp : Application(), ComponentCallbacks2 {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startupRecoveryDone = CompletableDeferred<Unit>()

    suspend fun awaitStartupRecovery() {
        startupRecoveryDone.await()
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RecordingNotificationHelper.ensureChannel(this)
        RecordingNotificationHelper.ensureStandbyChannel(this)
        container.storageManager.wipePlaybackCache()

        appScope.launch {
            try {
                val recovered = container.storageManager.recoverOrphanedStagingSegments(
                    recordingDao = container.database.recordingDao(),
                    activeStagingPaths = container.recorderController.getActiveStagingPaths()
                )
                val prefs = container.preferencesRepository.getSnapshot()
                if (prefs.periodicUpload90Min) {
                    container.uploadScheduler.configurePeriodicSync(true)
                }
                if (prefs.onboardingCompleted && (prefs.alwaysRunInBackground || prefs.recordingSessionActive)) {
                    container.keepAliveScheduler.configureAlwaysOnBackground(
                        enabled = true,
                        startServiceNow = true
                    )
                }
                if (recovered > 0) {
                    container.preferencesRepository.setLatestUploadStatusText(
                        "Recovered $recovered segment(s) after unexpected stop"
                    )
                }
            } finally {
                startupRecoveryDone.complete(Unit)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND ||
            level == TRIM_MEMORY_RUNNING_LOW ||
            level == TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            container.playbackManager.onAppBackgroundOrLowMemory()
            container.storageManager.wipePlaybackCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        container.playbackManager.onAppBackgroundOrLowMemory()
        container.storageManager.wipePlaybackCache()
    }
}
