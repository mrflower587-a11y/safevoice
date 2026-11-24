package com.example.system

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.SafeVoiceApp
import com.example.recording.RecordingForegroundService
import com.example.recording.RecordingNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BackgroundBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = appContext as? SafeVoiceApp ?: return@launch
                app.awaitStartupRecovery()
                val prefs = app.container.preferencesRepository.getSnapshot()
                if (!prefs.onboardingCompleted) return@launch

                if (prefs.alwaysRunInBackground || prefs.recordingSessionActive) {
                    app.container.keepAliveScheduler.configureAlwaysOnBackground(
                        enabled = true,
                        startServiceNow = true
                    )
                }

                if (prefs.recordingSessionActive && !prefs.recordingSessionPaused) {
                    val hasMic = ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic && !app.container.recorderController.sessionState.value.isSessionActive) {
                        val startedInBackground =
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                runCatching {
                                    RecordingForegroundService.sendAction(
                                        appContext,
                                        RecordingForegroundService.ACTION_START
                                    )
                                    true
                                }.getOrDefault(false)
                            } else {
                                false
                            }
                        if (!startedInBackground) {
                            RecordingNotificationHelper.showResumeRecordingNotification(appContext)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
