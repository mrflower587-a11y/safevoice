package com.example.playback

import android.media.MediaPlayer
import com.example.data.local.RecordingEntity
import com.example.recording.StorageManager
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlaybackUiState(
    val activeRecordingId: Long? = null,
    val fileName: String = "",
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

class AudioPlaybackManager(
    private val storageManager: StorageManager,
    private val cryptoManager: KeystoreCryptoManager,
    private val pollIntervalMs: Long = 200L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var activeTempFile: File? = null
    private var prepareJob: Job? = null
    private var progressJob: Job? = null

    fun startIfNotActive(recording: RecordingEntity) {
        val current = _playbackState.value
        if (current.activeRecordingId == recording.id && (current.isPreparing || mediaPlayer != null)) {
            return
        }
        playRecording(recording)
    }

    fun playRecording(recording: RecordingEntity) {
        val current = _playbackState.value
        if (current.activeRecordingId == recording.id && mediaPlayer != null && !current.isPreparing) {
            togglePlayPause()
            return
        }

        stop()

        _playbackState.value = PlaybackUiState(
            activeRecordingId = recording.id,
            fileName = recording.fileName,
            isPreparing = true,
            durationMs = recording.durationMs
        )

        prepareJob = scope.launch {
            try {
                val encFile = File(recording.encryptedFilePath)
                if (!encFile.exists()) {
                    _playbackState.update {
                        it.copy(
                            isPreparing = false,
                            errorMessage = "Encrypted recording file not found on device."
                        )
                    }
                    return@launch
                }

                val tempFile = withContext(Dispatchers.IO) {
                    val temp = storageManager.createTempPlaybackFile(recording.id)
                    cryptoManager.decryptToFile(encFile, temp)
                    temp
                }
                ensureActive()
                activeTempFile = tempFile

                val player = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnCompletionListener {
                        stop()
                    }
                    setOnErrorListener { _, _, _ ->
                        stop()
                        _playbackState.update {
                            it.copy(errorMessage = "Unable to decode audio recording.")
                        }
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = player
                _playbackState.update {
                    it.copy(
                        isPreparing = false,
                        isPlaying = true,
                        positionMs = 0L,
                        durationMs = player.duration.toLong().coerceAtLeast(recording.durationMs)
                    )
                }
                startProgressPolling()
            } catch (t: Throwable) {
                stop()
                _playbackState.update {
                    it.copy(
                        isPreparing = false,
                        errorMessage = "Decryption or playback error: ${t.message}"
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            progressJob?.cancel()
            _playbackState.update { it.copy(isPlaying = false) }
        } else {
            player.start()
            _playbackState.update { it.copy(isPlaying = true) }
            startProgressPolling()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.toInt().coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(clamped)
        _playbackState.update { it.copy(positionMs = clamped.toLong()) }
    }

    fun stop() {
        prepareJob?.cancel()
        prepareJob = null
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        activeTempFile?.let { runCatching { it.delete() } }
        activeTempFile = null
        storageManager.wipePlaybackCache()
        _playbackState.value = PlaybackUiState()
    }

    fun onAppBackgroundOrLowMemory() {
        stop()
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _playbackState.update {
                        it.copy(positionMs = player.currentPosition.toLong())
                    }
                }
                delay(pollIntervalMs)
            }
        }
    }
}
