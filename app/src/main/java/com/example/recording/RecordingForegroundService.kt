package com.example.recording

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.SafeVoiceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecordingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedForeground = false
    private var chronometerBaseMs = System.currentTimeMillis()

    private var hasObservedActiveSession = false

    private val recorderController: AudioRecorderController
        get() = (application as SafeVoiceApp).container.recorderController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RecordingNotificationHelper.ensureChannel(this)
        observerJob = serviceScope.launch {
            recorderController.sessionState.collectLatest { state ->
                if (state.isSessionActive) {
                    hasObservedActiveSession = true
                    if (startedForeground) {
                        val notification = RecordingNotificationHelper.buildRecordingNotification(
                            context = this@RecordingForegroundService,
                            isPaused = state.status == RecordingStatus.PAUSED,
                            segmentFileName = state.currentSegmentFileName,
                            chronometerBaseMs = chronometerBaseMs
                        )
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(RecordingNotificationHelper.NOTIFICATION_ID, notification)
                    }
                } else if (hasObservedActiveSession && startedForeground) {
                    hasObservedActiveSession = false
                    releaseWakeLock()
                    stopForegroundAndSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            if (!recorderController.sessionState.value.isSessionActive) {
                stopSelf()
                return START_NOT_STICKY
            }
            return START_STICKY
        }

        when (action) {
            ACTION_START -> {
                hasObservedActiveSession = false
                chronometerBaseMs = System.currentTimeMillis()
                promoteToForeground(isPaused = false, segmentName = "Starting…")
                acquireWakeLock()
                serviceScope.launch {
                    val started = recorderController.startRecording()
                    if (!started) {
                        releaseWakeLock()
                        stopForegroundAndSelf()
                    }
                }
            }
            ACTION_PAUSE -> {
                serviceScope.launch {
                    recorderController.pauseRecording()
                    releaseWakeLock()
                }
            }
            ACTION_RESUME -> {
                serviceScope.launch {
                    val resumed = recorderController.resumeRecording()
                    if (resumed) {
                        acquireWakeLock()
                    }
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    recorderController.stopRecording()
                    releaseWakeLock()
                    stopForegroundAndSelf()
                }
            }
        }
        return if (action == ACTION_STOP) {
            START_NOT_STICKY
        } else {
            START_STICKY
        }
    }

    private fun promoteToForeground(isPaused: Boolean, segmentName: String) {
        val notification = RecordingNotificationHelper.buildRecordingNotification(
            context = this,
            isPaused = isPaused,
            segmentFileName = segmentName,
            chronometerBaseMs = chronometerBaseMs
        )
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            RecordingNotificationHelper.NOTIFICATION_ID,
            notification,
            serviceType
        )
        startedForeground = true
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeVoice::RecorderWakeLock").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L) // 2h safety timeout
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) runCatching { it.release() }
        }
    }

    private fun stopForegroundAndSelf() {
        startedForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.recording.ACTION_START"
        const val ACTION_PAUSE = "com.example.recording.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.recording.ACTION_RESUME"
        const val ACTION_STOP = "com.example.recording.ACTION_STOP"

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                this.action = action
            }
            if (action == ACTION_START) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
