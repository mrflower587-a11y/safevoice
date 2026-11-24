package com.example.data.preferences

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val recoveryHash: String? = null,
    val recoverySalt: String? = null,
    val pinFailedAttempts: Int = 0,
    val pinLockoutUntilMs: Long = 0L,
    val audioQualityKbps: Int = AudioQualityPreset.STANDARD.kbps,
    val segmentDurationMinutes: Int = 30,
    val autoUploadAfterFive: Boolean = false,
    val periodicUpload90Min: Boolean = false,
    val completedSinceLastUploadBatch: Int = 0,
    val connectedDriveEmail: String? = null,
    val connectedDriveName: String? = null,
    val selectedDriveFolderId: String? = null,
    val selectedDriveFolderName: String? = null,
    val latestUploadStatusText: String = "No uploads yet",
    val alwaysRunInBackground: Boolean = true,
    val recordingSessionActive: Boolean = false,
    val recordingSessionPaused: Boolean = false
)
