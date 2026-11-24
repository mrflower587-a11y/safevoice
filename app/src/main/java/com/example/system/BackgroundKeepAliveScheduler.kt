package com.example.system

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BackgroundKeepAliveScheduler(private val context: Context) {

    private val workManager: WorkManager?
        get() = runCatching {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(
                    context.applicationContext,
                    androidx.work.Configuration.Builder().build()
                )
            }
            WorkManager.getInstance(context.applicationContext)
        }.getOrNull()

    fun configureAlwaysOnBackground(enabled: Boolean, startServiceNow: Boolean = true) {
        val wm = workManager
        if (!enabled) {
            wm?.cancelUniqueWork(UNIQUE_KEEP_ALIVE_PERIODIC)
            wm?.cancelUniqueWork(UNIQUE_KEEP_ALIVE_RECOVERY)
            PersistentBackgroundService.stop(context)
            return
        }

        if (startServiceNow) {
            PersistentBackgroundService.ensureRunning(context)
        }

        val periodic = PeriodicWorkRequestBuilder<BackgroundWatchdogWorker>(
            15L,
            TimeUnit.MINUTES
        ).build()
        wm?.enqueueUniquePeriodicWork(
            UNIQUE_KEEP_ALIVE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    fun enqueueImmediateRecovery() {
        val wm = workManager ?: return
        val request = OneTimeWorkRequestBuilder<BackgroundWatchdogWorker>()
            .setInitialDelay(3L, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(
            UNIQUE_KEEP_ALIVE_RECOVERY,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        const val UNIQUE_KEEP_ALIVE_PERIODIC = "safevoice_background_watchdog_periodic"
        const val UNIQUE_KEEP_ALIVE_RECOVERY = "safevoice_background_watchdog_recovery"
    }
}
