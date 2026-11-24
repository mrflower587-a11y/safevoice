package com.example.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.SafeVoiceApp
import com.example.recording.RecordingNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PersistentBackgroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RecordingNotificationHelper.ensureStandbyChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _isRunningFlow.value = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForeground()
        _isRunningFlow.value = true
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val app = application as? SafeVoiceApp ?: return
        serviceScope.launch {
            val prefs = runCatching { app.container.preferencesRepository.getSnapshot() }.getOrNull()
            if (prefs?.onboardingCompleted == true && prefs.alwaysRunInBackground) {
                scheduleSoftRestart(applicationContext)
                app.container.keepAliveScheduler.enqueueImmediateRecovery()
            }
        }
    }

    override fun onDestroy() {
        _isRunningFlow.value = false
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val notification = RecordingNotificationHelper.buildStandbyNotification(this)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            RecordingNotificationHelper.STANDBY_NOTIFICATION_ID,
            notification,
            serviceType
        )
    }

    companion object {
        const val ACTION_START = "com.example.system.ACTION_START_STANDBY"
        const val ACTION_STOP = "com.example.system.ACTION_STOP_STANDBY"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        fun ensureRunning(context: Context): Boolean {
            val appContext = context.applicationContext
            val intent = Intent(appContext, PersistentBackgroundService::class.java).apply {
                action = ACTION_START
            }
            return runCatching {
                ContextCompat.startForegroundService(appContext, intent)
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            _isRunningFlow.value = false
            runCatching {
                val intent = Intent(appContext, PersistentBackgroundService::class.java).apply {
                    action = ACTION_STOP
                }
                appContext.stopService(intent)
            }
        }

        private fun scheduleSoftRestart(context: Context) {
            runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    pm?.isIgnoringBatteryOptimizations(context.packageName) != true
                ) {
                    return
                }
                val restartIntent = Intent(context, PersistentBackgroundService::class.java).apply {
                    action = ACTION_START
                }
                val pendingIntent = PendingIntent.getForegroundService(
                    context,
                    4002,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 2_000L,
                    pendingIntent
                )
            }
        }
    }
}
