package com.example.recording

enum class RecordingStatus {
    READY,
    RECORDING,
    PAUSED
}

data class RecordingSessionState(
    val status: RecordingStatus = RecordingStatus.READY,
    val totalElapsedMs: Long = 0L,
    val currentSegmentElapsedMs: Long = 0L,
    val segmentDurationMs: Long = 30L * 60_000L,
    val currentSegmentFileName: String = "",
    val completedSegmentsCount: Int = 0,
    val normalizedAmplitude: Float = 0f,
    val autoPausedReason: String? = null,
    val errorMessage: String? = null,
    val sessionStartTimeMs: Long = 0L
) {
    val isSessionActive: Boolean
        get() = status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED

    val segmentProgress: Float
        get() = if (segmentDurationMs <= 0L) 0f
        else (currentSegmentElapsedMs.toFloat() / segmentDurationMs.toFloat()).coerceIn(0f, 1f)

    val countdownToNextSplitMs: Long
        get() = (segmentDurationMs - currentSegmentElapsedMs).coerceAtLeast(0L)
}
