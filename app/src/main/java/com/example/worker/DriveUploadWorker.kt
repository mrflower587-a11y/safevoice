package com.example.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.SafeVoiceApp

class DriveUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SafeVoiceApp).container
        val dao = container.database.recordingDao()
        val prefsRepo = container.preferencesRepository
        val authManager = container.driveAuthManager
        val driveRepo = container.driveRepository

        val singleRecordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        val pending = if (singleRecordingId > 0L) {
            listOfNotNull(dao.getById(singleRecordingId)).filter { it.remoteDriveFileId == null }
        } else {
            dao.getPendingUploadsSync()
        }

        if (pending.isEmpty()) {
            return Result.success()
        }

        if (!isNetworkAvailable(applicationContext)) {
            pending.forEach { dao.markWaitingForInternet(it.id) }
            prefsRepo.setLatestUploadStatusText("Waiting for internet (${pending.size} pending)")
            return Result.retry()
        }

        val token = authManager.getSilentAccessToken()
        if (token.isNullOrBlank()) {
            pending.forEach {
                dao.markFailed(it.id, it.retryCount, "Google Drive sign-in required")
            }
            prefsRepo.setLatestUploadStatusText("Google Drive authorization needed")
            return Result.failure()
        }

        var hadTransientFailure = false
        var uploadedCount = 0

        for (recording in pending) {
            if (recording.remoteDriveFileId != null) continue
            val outcome = driveRepo.uploadRecording(token, recording)
            if (outcome.isSuccess) {
                uploadedCount++
            } else {
                hadTransientFailure = true
            }
        }

        if (uploadedCount > 0 && !hadTransientFailure) {
            prefsRepo.setLatestUploadStatusText("Synced $uploadedCount recording(s) to Drive")
        }

        return if (hadTransientFailure) Result.retry() else Result.success()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        const val KEY_RECORDING_ID = "key_recording_id"
    }
}
