package com.example.ui.viewmodel

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.RecordingEntity
import com.example.data.preferences.UserPreferences
import com.example.playback.PlaybackUiState
import com.example.recording.RecordingForegroundService
import com.example.recording.RecordingSessionState
import com.example.recording.StorageStats
import com.example.system.PersistentBackgroundService
import com.example.system.RamProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SafeVoiceViewModel(
    private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    val preferences: StateFlow<UserPreferences?> = container.preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val recordingState: StateFlow<RecordingSessionState> = container.recorderController.sessionState

    val playbackState: StateFlow<PlaybackUiState> = container.playbackManager.playbackState

    val allRecordings: StateFlow<List<RecordingEntity>> = container.database.recordingDao()
        .observeAllRecordings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val savedFilesCount: StateFlow<Int> = container.database.recordingDao()
        .observeSavedCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val pendingQueue: StateFlow<List<RecordingEntity>> = container.database.recordingDao()
        .observePendingQueue()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val pendingCount: StateFlow<Int> = container.database.recordingDao()
        .observePendingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    private val _storageStats = MutableStateFlow(container.storageManager.getStorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _ramProfile = MutableStateFlow(container.lowRamOptimizer.getRamProfile())
    val ramProfile: StateFlow<RamProfile> = _ramProfile.asStateFlow()

    val isBackgroundServiceRunning: StateFlow<Boolean> = PersistentBackgroundService.isRunningFlow

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(checkIgnoringBatteryOptimizations())
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    val pinViewModel = PinViewModel(
        preferencesRepository = container.preferencesRepository,
        pinSecurityManager = container.pinSecurityManager
    )

    val driveViewModel = DriveViewModel(
        authManager = container.driveAuthManager,
        driveRepository = container.driveRepository
    )

    private fun checkIgnoringBatteryOptimizations(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return runCatching { pm?.isIgnoringBatteryOptimizations(appContext.packageName) ?: false }
            .getOrDefault(false)
    }

    fun refreshBatteryStatus() {
        _isIgnoringBatteryOptimizations.value = checkIgnoringBatteryOptimizations()
    }

    fun ensureAlwaysOnBackgroundActive() {
        container.keepAliveScheduler.configureAlwaysOnBackground(
            enabled = true,
            startServiceNow = true
        )
    }

    fun setAlwaysRunInBackground(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setAlwaysRunInBackground(enabled)
            container.keepAliveScheduler.configureAlwaysOnBackground(
                enabled = enabled,
                startServiceNow = enabled
            )
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageStats.value = container.storageManager.getStorageStats()
            _ramProfile.value = container.lowRamOptimizer.getRamProfile()
            _isIgnoringBatteryOptimizations.value = checkIgnoringBatteryOptimizations()
        }
    }

    fun startRecordingService() {
        RecordingForegroundService.sendAction(appContext, RecordingForegroundService.ACTION_START)
    }

    fun pauseRecordingService() {
        RecordingForegroundService.sendAction(appContext, RecordingForegroundService.ACTION_PAUSE)
    }

    fun resumeRecordingService() {
        RecordingForegroundService.sendAction(appContext, RecordingForegroundService.ACTION_RESUME)
    }

    fun stopRecordingService() {
        RecordingForegroundService.sendAction(appContext, RecordingForegroundService.ACTION_STOP)
    }

    fun dismissRecorderError() {
        container.recorderController.dismissError()
    }

    fun setAudioQualityKbps(kbps: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setAudioQualityKbps(kbps)
        }
    }

    fun setSegmentDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setSegmentDurationMinutes(minutes)
        }
    }

    fun setAutoUploadAfterFive(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setAutoUploadAfterFive(enabled)
        }
    }

    fun setPeriodicUpload90Min(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setPeriodicUpload90Min(enabled)
            container.uploadScheduler.configurePeriodicSync(enabled)
        }
    }

    fun enqueueAllPendingUploads() {
        container.uploadScheduler.enqueueImmediateUpload()
    }

    fun enqueueSingleUpload(recordingId: Long) {
        container.uploadScheduler.enqueueSingleRecordingUpload(recordingId)
    }

    fun observeRecordingById(id: Long) = container.database.recordingDao().observeById(id)

    fun playOrToggleRecording(recording: RecordingEntity) {
        container.playbackManager.playRecording(recording)
    }

    fun startPlaybackIfNotActive(recording: RecordingEntity) {
        container.playbackManager.startIfNotActive(recording)
    }

    fun togglePlayback() {
        container.playbackManager.togglePlayPause()
    }

    fun seekPlayback(ms: Long) {
        container.playbackManager.seekTo(ms)
    }

    fun stopPlayback() {
        container.playbackManager.stop()
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (container.playbackManager.playbackState.value.activeRecordingId == recording.id) {
                container.playbackManager.stop()
            }
            runCatching { File(recording.encryptedFilePath).delete() }
            container.database.recordingDao().deleteById(recording.id)
            refreshStorageStats()
        }
    }
}
