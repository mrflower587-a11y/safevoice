package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.SafeVoiceApp
import com.example.recording.RecordingForegroundService
import com.example.recording.RecordingNotificationHelper

class BackgroundWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SafeVoiceApp ?: return Result.success()
        app.awaitStartupRecovery()
        val prefs = app.container.preferencesRepository.getSnapshot()
        if (prefs.onboardingCompleted && (prefs.alwaysRunInBackground || prefs.recordingSessionActive)) {
            if (!PersistentBackgroundService.isRunningFlow.value) {
                PersistentBackgroundService.ensureRunning(applicationContext)
            }
            if (prefs.recordingSessionActive &&
                !prefs.recordingSessionPaused &&
                !app.container.recorderController.sessionState.value.isSessionActive
            ) {
                val hasMic = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    val started = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        runCatching {
                            RecordingForegroundService.sendAction(
                                applicationContext,
                                RecordingForegroundService.ACTION_START
                            )
                            true
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                    if (!started) {
                        RecordingNotificationHelper.showResumeRecordingNotification(applicationContext)
                    }
                }
            }
        } else {
            PersistentBackgroundService.stop(applicationContext)
        }
        return Result.success()
    }
}
