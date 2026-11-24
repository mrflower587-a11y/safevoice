package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class UploadScheduler(private val context: Context) {

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

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueueImmediateUpload() {
        val wm = workManager ?: return
        val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(
            UNIQUE_QUEUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueSingleRecordingUpload(recordingId: Long) {
        val wm = workManager ?: return
        val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(DriveUploadWorker.KEY_RECORDING_ID to recordingId))
            .build()
        wm.enqueueUniqueWork(
            "safevoice_upload_item_$recordingId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun configurePeriodicSync(enabled: Boolean) {
        val wm = workManager ?: return
        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            return
        }
        val periodic = PeriodicWorkRequestBuilder<DriveUploadWorker>(90L, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    companion object {
        const val UNIQUE_QUEUE_WORK_NAME = "safevoice_immediate_upload_queue"
        const val UNIQUE_PERIODIC_WORK_NAME = "safevoice_periodic_90min_sync"
    }
}
