package com.example.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.example.R
import com.example.data.local.RecordingDao
import com.example.data.local.RecordingEntity
import com.example.data.local.UploadStatus
import com.example.data.preferences.AudioQualityPreset
import com.example.data.preferences.UserPreferencesRepository
import com.example.security.KeystoreCryptoManager
import com.example.worker.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderController(
    private val context: Context,
    private val storageManager: StorageManager,
    private val cryptoManager: KeystoreCryptoManager,
    private val recordingDao: RecordingDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val uploadScheduler: UploadScheduler,
    private val pollIntervalMs: Long = 200L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _sessionState = MutableStateFlow(RecordingSessionState())
    val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentStagingFile: File? = null
    private var nextStagingFile: File? = null
    private var currentSegmentStartTimeMs: Long = 0L
    private var currentSegmentActiveMs: Long = 0L
    private var lastTickMs: Long = 0L
    private var activeBitRateBps: Int = AudioQualityPreset.STANDARD.bitRateBps
    private var activeSegmentDurationMs: Long = 30L * 60_000L
    private var nextOutputPrepared: Boolean = false

    private var tickerJob: Job? = null
    private var recoveryRetryJob: Job? = null

    suspend fun startRecording(): Boolean = mutex.withLock {
        if (_sessionState.value.isSessionActive) return true

        if (!storageManager.hasSufficientStorage()) {
            _sessionState.update {
                it.copy(errorMessage = context.getString(R.string.low_storage_error))
            }
            return false
        }

        val prefs = preferencesRepository.getSnapshot()
        val preset = AudioQualityPreset.fromKbps(prefs.audioQualityKbps)
        activeBitRateBps = preset.bitRateBps
        activeSegmentDurationMs = prefs.segmentDurationMinutes.coerceIn(1, 120) * 60_000L

        val now = System.currentTimeMillis()
        val initialFile = storageManager.createStagingFile(generateFileName(now))
        val queuedNextFile = storageManager.createStagingFile(generateFileName(now + 1_000L))

        return try {
            recoveryRetryJob?.cancel()
            startMediaRecorder(initialFile, queuedNextFile)
            currentStagingFile = initialFile
            currentSegmentStartTimeMs = now
            currentSegmentActiveMs = 0L
            lastTickMs = now

            RecordingNotificationHelper.cancelResumeNotification(context)
            preferencesRepository.setRecordingSessionState(active = true, paused = false)

            _sessionState.value = RecordingSessionState(
                status = RecordingStatus.RECORDING,
                totalElapsedMs = 0L,
                currentSegmentElapsedMs = 0L,
                segmentDurationMs = activeSegmentDurationMs,
                currentSegmentFileName = initialFile.name,
                completedSegmentsCount = 0,
                normalizedAmplitude = 0f,
                autoPausedReason = null,
                errorMessage = null,
                sessionStartTimeMs = now
            )
            startTicker()
            true
        } catch (t: Throwable) {
            releaseRecorderQuietly()
            initialFile.delete()
            queuedNextFile.delete()
            _sessionState.update {
                it.copy(
                    status = RecordingStatus.READY,
                    errorMessage = "Unable to start microphone recorder: ${t.message ?: "Hardware busy"}"
                )
            }
            false
        }
    }

    suspend fun pauseRecording() = mutex.withLock {
        val state = _sessionState.value
        if (state.status != RecordingStatus.RECORDING) return@withLock
        try {
            recoveryRetryJob?.cancel()
            mediaRecorder?.pause()
            val now = System.currentTimeMillis()
            val delta = (now - lastTickMs).coerceAtLeast(0L)
            currentSegmentActiveMs += delta
            lastTickMs = now
            tickerJob?.cancel()
            preferencesRepository.setRecordingSessionState(active = true, paused = true)
            _sessionState.update {
                it.copy(
                    status = RecordingStatus.PAUSED,
                    totalElapsedMs = it.totalElapsedMs + delta,
                    currentSegmentElapsedMs = currentSegmentActiveMs,
                    normalizedAmplitude = 0f,
                    autoPausedReason = null
                )
            }
        } catch (t: Throwable) {
            _sessionState.update { it.copy(errorMessage = "Pause failed: ${t.message}") }
        }
    }

    suspend fun resumeRecording(): Boolean = mutex.withLock {
        val state = _sessionState.value
        if (state.status != RecordingStatus.PAUSED) return false

        if (!storageManager.hasSufficientStorage()) {
            _sessionState.update {
                it.copy(errorMessage = context.getString(R.string.low_storage_error))
            }
            return false
        }

        return try {
            recoveryRetryJob?.cancel()
            if (mediaRecorder == null) {
                val now = System.currentTimeMillis()
                val newFile = storageManager.createStagingFile(generateFileName(now))
                val queuedNext = storageManager.createStagingFile(generateFileName(now + 1_000L))
                try {
                    startMediaRecorder(newFile, queuedNext)
                    currentStagingFile = newFile
                    currentSegmentStartTimeMs = now
                    currentSegmentActiveMs = 0L
                } catch (inner: Throwable) {
                    newFile.delete()
                    queuedNext.delete()
                    throw inner
                }
            } else {
                mediaRecorder?.resume()
            }
            lastTickMs = System.currentTimeMillis()
            preferencesRepository.setRecordingSessionState(active = true, paused = false)
            _sessionState.update {
                it.copy(
                    status = RecordingStatus.RECORDING,
                    currentSegmentFileName = currentStagingFile?.name ?: it.currentSegmentFileName,
                    autoPausedReason = null,
                    errorMessage = null
                )
            }
            startTicker()
            true
        } catch (t: Throwable) {
            _sessionState.update { it.copy(errorMessage = "Resume failed: ${t.message}") }
            false
        }
    }

    suspend fun stopRecording() = mutex.withLock {
        val state = _sessionState.value
        if (!state.isSessionActive && mediaRecorder == null) {
            preferencesRepository.setRecordingSessionState(active = false, paused = false)
            RecordingNotificationHelper.cancelResumeNotification(context)
            return
        }

        recoveryRetryJob?.cancel()
        tickerJob?.cancel()
        val now = System.currentTimeMillis()
        val delta = if (state.status == RecordingStatus.RECORDING) {
            (now - lastTickMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val finalDuration = (currentSegmentActiveMs + delta).coerceAtLeast(500L)
        val fileToFinalize = currentStagingFile
        val unusedNext = nextStagingFile
        val segStart = currentSegmentStartTimeMs

        releaseRecorderQuietly()

        currentStagingFile = null
        nextStagingFile = null
        nextOutputPrepared = false

        if (unusedNext != null && unusedNext.exists()) {
            if (unusedNext.length() < StorageManager.MIN_VALID_STUB_BYTES) {
                unusedNext.delete()
            } else {
                enqueueSegmentFinalization(
                    stagingFile = unusedNext,
                    startTimeMs = now,
                    endTimeMs = now + 1_000L,
                    durationMs = 1_000L
                )
            }
        }

        if (fileToFinalize != null) {
            enqueueSegmentFinalization(
                stagingFile = fileToFinalize,
                startTimeMs = segStart,
                endTimeMs = segStart + finalDuration,
                durationMs = finalDuration
            )
        }

        preferencesRepository.setRecordingSessionState(active = false, paused = false)
        RecordingNotificationHelper.cancelResumeNotification(context)

        _sessionState.value = RecordingSessionState(
            status = RecordingStatus.READY,
            segmentDurationMs = activeSegmentDurationMs
        )
    }

    fun dismissError() {
        _sessionState.update { it.copy(errorMessage = null) }
    }

    fun getActiveStagingPaths(): Set<String> {
        return setOfNotNull(currentStagingFile?.absolutePath, nextStagingFile?.absolutePath)
    }

    private fun startMediaRecorder(outputFile: File, preparedNextFile: File) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        val maxFileSizeBytes = ((activeBitRateBps / 8L) * (activeSegmentDurationMs / 1000L) * 2L)
            .coerceIn(16L * 1024L * 1024L, 256L * 1024L * 1024L)

        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(activeBitRateBps)
                setOutputFile(outputFile.absolutePath)
                setMaxDuration(activeSegmentDurationMs.toInt().coerceAtLeast(60_000))
                setMaxFileSize(maxFileSizeBytes)
                setOnInfoListener { _, what, _ ->
                    when (what) {
                        MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> {
                            scope.launch { handleGaplessNextFileStarted() }
                        }
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> {
                            scope.launch { handleMaxDurationFallback() }
                        }
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                            scope.launch { handleMaxFileSizeFallback() }
                        }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    scope.launch {
                        handleTransientRecorderErrorLocked()
                    }
                }
                prepare()
                start()
            }
        } catch (t: Throwable) {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            throw t
        }

        mediaRecorder = recorder
        nextOutputPrepared = try {
            recorder.setNextOutputFile(preparedNextFile)
            nextStagingFile = preparedNextFile
            true
        } catch (_: Throwable) {
            nextStagingFile = null
            preparedNextFile.delete()
            false
        }
    }

    private suspend fun handleGaplessNextFileStarted() = mutex.withLock {
        val completedFile = currentStagingFile
        val promotedFile = nextStagingFile ?: return
        val now = System.currentTimeMillis()
        val segDuration = currentSegmentActiveMs.coerceAtLeast(activeSegmentDurationMs)

        if (completedFile != null) {
            enqueueSegmentFinalization(
                stagingFile = completedFile,
                startTimeMs = currentSegmentStartTimeMs,
                endTimeMs = currentSegmentStartTimeMs + segDuration,
                durationMs = segDuration
            )
        }

        currentStagingFile = promotedFile
        currentSegmentStartTimeMs = now
        currentSegmentActiveMs = 0L
        lastTickMs = now

        val upcomingFile = storageManager.createStagingFile(generateFileName(now + 1_000L))
        nextOutputPrepared = try {
            mediaRecorder?.setNextOutputFile(upcomingFile)
            nextStagingFile = upcomingFile
            true
        } catch (_: Throwable) {
            upcomingFile.delete()
            nextStagingFile = null
            false
        }

        _sessionState.update {
            it.copy(
                currentSegmentFileName = promotedFile.name,
                currentSegmentElapsedMs = 0L,
                completedSegmentsCount = it.completedSegmentsCount + 1
            )
        }
    }

    private suspend fun handleMaxDurationFallback() = mutex.withLock {
        if (!_sessionState.value.isSessionActive) return
        // Ignore stale callback if gapless rollover just promoted a fresh segment.
        if (currentSegmentActiveMs < activeSegmentDurationMs / 2L) return
        performManualSegmentRolloverLocked()
    }

    private suspend fun handleMaxFileSizeFallback() = mutex.withLock {
        if (!_sessionState.value.isSessionActive) return
        if (nextOutputPrepared) return
        performManualSegmentRolloverLocked()
    }

    private suspend fun performManualSegmentRolloverLocked() {
        val oldFile = currentStagingFile ?: return
        val now = System.currentTimeMillis()
        val duration = currentSegmentActiveMs.coerceAtLeast(1_000L)
        val wasPaused = _sessionState.value.status == RecordingStatus.PAUSED
        val staleNext = nextStagingFile

        releaseRecorderQuietly()
        nextStagingFile = null
        nextOutputPrepared = false

        if (staleNext != null && staleNext.exists()) {
            if (staleNext.length() < StorageManager.MIN_VALID_STUB_BYTES) {
                staleNext.delete()
            } else {
                enqueueSegmentFinalization(
                    stagingFile = staleNext,
                    startTimeMs = now,
                    endTimeMs = now + 1_000L,
                    durationMs = 1_000L
                )
            }
        }

        enqueueSegmentFinalization(
            stagingFile = oldFile,
            startTimeMs = currentSegmentStartTimeMs,
            endTimeMs = currentSegmentStartTimeMs + duration,
            durationMs = duration
        )

        if (!storageManager.hasSufficientStorage()) {
            preferencesRepository.setRecordingSessionState(active = false, paused = false)
            _sessionState.value = RecordingSessionState(
                status = RecordingStatus.READY,
                errorMessage = context.getString(R.string.low_storage_error)
            )
            return
        }

        val newFile = storageManager.createStagingFile(generateFileName(now))
        val queuedNext = storageManager.createStagingFile(generateFileName(now + 1_000L))
        try {
            startMediaRecorder(newFile, queuedNext)
            if (wasPaused) {
                mediaRecorder?.pause()
            }
            currentStagingFile = newFile
            currentSegmentStartTimeMs = now
            currentSegmentActiveMs = 0L
            lastTickMs = now
            _sessionState.update {
                it.copy(
                    currentSegmentFileName = newFile.name,
                    currentSegmentElapsedMs = 0L,
                    completedSegmentsCount = it.completedSegmentsCount + 1
                )
            }
        } catch (t: Throwable) {
            newFile.delete()
            queuedNext.delete()
            currentStagingFile = null
            nextStagingFile = null
            nextOutputPrepared = false
            startTransientRecoveryLoop()
        }
    }

    private suspend fun handleTransientRecorderErrorLocked() = mutex.withLock {
        val state = _sessionState.value
        if (state.status != RecordingStatus.RECORDING) return@withLock

        val now = System.currentTimeMillis()
        val oldFile = currentStagingFile
        val staleNext = nextStagingFile
        val duration = currentSegmentActiveMs.coerceAtLeast(500L)

        releaseRecorderQuietly()
        currentStagingFile = null
        nextStagingFile = null
        nextOutputPrepared = false

        if (staleNext != null && staleNext.exists() && staleNext.length() < StorageManager.MIN_VALID_STUB_BYTES) {
            staleNext.delete()
        }
        if (oldFile != null) {
            enqueueSegmentFinalization(
                stagingFile = oldFile,
                startTimeMs = currentSegmentStartTimeMs,
                endTimeMs = currentSegmentStartTimeMs + duration,
                durationMs = duration
            )
            _sessionState.update {
                it.copy(
                    currentSegmentFileName = "Reconnecting mic…",
                    currentSegmentElapsedMs = 0L,
                    completedSegmentsCount = it.completedSegmentsCount + 1,
                    normalizedAmplitude = 0f
                )
            }
        }
        currentSegmentActiveMs = 0L
        startTransientRecoveryLoop()
    }

    private fun startTransientRecoveryLoop() {
        if (recoveryRetryJob?.isActive == true) return
        recoveryRetryJob = scope.launch {
            while (isActive) {
                delay(2_000L)
                var recovered = false
                mutex.withLock {
                    if (_sessionState.value.status != RecordingStatus.RECORDING) {
                        return@launch
                    }
                    if (!storageManager.hasSufficientStorage()) {
                        preferencesRepository.setRecordingSessionState(active = false, paused = false)
                        _sessionState.value = RecordingSessionState(
                            status = RecordingStatus.READY,
                            errorMessage = context.getString(R.string.low_storage_error)
                        )
                        return@launch
                    }
                    if (mediaRecorder != null) {
                        recovered = true
                        return@withLock
                    }
                    val now = System.currentTimeMillis()
                    val newFile = storageManager.createStagingFile(generateFileName(now))
                    val queuedNext = storageManager.createStagingFile(generateFileName(now + 1_000L))
                    try {
                        startMediaRecorder(newFile, queuedNext)
                        currentStagingFile = newFile
                        currentSegmentStartTimeMs = now
                        currentSegmentActiveMs = 0L
                        lastTickMs = now
                        _sessionState.update {
                            it.copy(
                                currentSegmentFileName = newFile.name,
                                currentSegmentElapsedMs = 0L,
                                errorMessage = null
                            )
                        }
                        recovered = true
                    } catch (_: Throwable) {
                        newFile.delete()
                        queuedNext.delete()
                    }
                }
                if (recovered) break
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                var triggerFallbackRollover = false
                mutex.withLock {
                    val state = _sessionState.value
                    if (state.status == RecordingStatus.RECORDING) {
                        val now = System.currentTimeMillis()
                        val delta = (now - lastTickMs).coerceAtLeast(0L)
                        lastTickMs = now
                        currentSegmentActiveMs += delta

                        val rawAmp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                        val normalized = (rawAmp / 32_767f).coerceIn(0f, 1f)

                        _sessionState.update {
                            it.copy(
                                totalElapsedMs = it.totalElapsedMs + delta,
                                currentSegmentElapsedMs = currentSegmentActiveMs,
                                normalizedAmplitude = normalized
                            )
                        }

                        val rolloverThresholdMs = if (nextOutputPrepared) {
                            activeSegmentDurationMs + 3_000L
                        } else {
                            activeSegmentDurationMs
                        }
                        if (currentSegmentActiveMs >= rolloverThresholdMs) {
                            triggerFallbackRollover = true
                        }
                    }
                }
                if (triggerFallbackRollover) {
                    mutex.withLock {
                        val rolloverThresholdMs = if (nextOutputPrepared) {
                            activeSegmentDurationMs + 3_000L
                        } else {
                            activeSegmentDurationMs
                        }
                        if (_sessionState.value.status == RecordingStatus.RECORDING &&
                            currentSegmentActiveMs >= rolloverThresholdMs
                        ) {
                            performManualSegmentRolloverLocked()
                        }
                    }
                }
            }
        }
    }

    private fun enqueueSegmentFinalization(
        stagingFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        durationMs: Long
    ) {
        scope.launch(Dispatchers.IO) {
            if (!stagingFile.exists() || stagingFile.length() < StorageManager.MIN_VALID_STUB_BYTES) {
                stagingFile.delete()
                return@launch
            }
            val destEncFile = storageManager.createVaultFile(stagingFile.name)
            try {
                val decryptedSize = cryptoManager.encryptFile(stagingFile, destEncFile)
                recordingDao.insert(
                    RecordingEntity(
                        fileName = stagingFile.name,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        durationMs = durationMs,
                        fileSizeBytes = destEncFile.length(),
                        decryptedSizeBytes = decryptedSize,
                        encryptedFilePath = destEncFile.absolutePath,
                        uploadStatus = UploadStatus.PENDING
                    )
                )
                stagingFile.delete()

                val prefs = preferencesRepository.getSnapshot()
                val shouldFlush = preferencesRepository.incrementCompletedBatchAndCheckFlush(
                    autoUploadEnabled = prefs.autoUploadAfterFive
                )
                if (shouldFlush) {
                    uploadScheduler.enqueueImmediateUpload()
                }
            } catch (t: Throwable) {
                destEncFile.delete()
                _sessionState.update {
                    it.copy(errorMessage = "Encryption failed for ${stagingFile.name}: ${t.message}")
                }
            }
        }
    }

    private fun releaseRecorderQuietly() {
        mediaRecorder?.let { recorder ->
            runCatching { recorder.stop() }
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        mediaRecorder = null
    }

    private fun generateFileName(timestampMs: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val stem = "SafeVoice_${formatter.format(Date(timestampMs))}"
        var candidate = "$stem.m4a"
        var suffix = 2
        while (
            storageManager.createStagingFile(candidate).exists() ||
            storageManager.createVaultFile(candidate).exists()
        ) {
            candidate = "${stem}_${suffix}.m4a"
            suffix++
        }
        return candidate
    }
}
