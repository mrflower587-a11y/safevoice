package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val decryptedSizeBytes: Long,
    val encryptedFilePath: String,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val uploadProgress: Int = 0,
    val remoteDriveFileId: String? = null,
    val driveFolderId: String? = null,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val isRecovered: Boolean = false
)
