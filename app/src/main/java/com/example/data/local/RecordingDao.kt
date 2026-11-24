package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY startTimeMs DESC")
    fun observeAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT COUNT(*) FROM recordings")
    fun observeSavedCount(): Flow<Int>

    @Query(
        "SELECT * FROM recordings " +
            "WHERE remoteDriveFileId IS NULL AND uploadStatus != 'UPLOADED' " +
            "ORDER BY startTimeMs ASC"
    )
    fun observePendingQueue(): Flow<List<RecordingEntity>>

    @Query(
        "SELECT COUNT(*) FROM recordings " +
            "WHERE remoteDriveFileId IS NULL AND uploadStatus != 'UPLOADED'"
    )
    fun observePendingCount(): Flow<Int>

    @Query(
        "SELECT * FROM recordings " +
            "WHERE remoteDriveFileId IS NULL AND uploadStatus != 'UPLOADED' " +
            "ORDER BY startTimeMs ASC"
    )
    suspend fun getPendingUploadsSync(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: Long): Flow<RecordingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Query(
        "UPDATE recordings SET uploadStatus = :status, uploadProgress = :progress, lastError = :lastError " +
            "WHERE id = :id AND remoteDriveFileId IS NULL"
    )
    suspend fun updateUploadProgress(
        id: Long,
        status: UploadStatus,
        progress: Int,
        lastError: String? = null
    )

    @Query(
        "UPDATE recordings SET uploadStatus = 'WAITING_FOR_INTERNET', lastError = :reason " +
            "WHERE id = :id AND remoteDriveFileId IS NULL"
    )
    suspend fun markWaitingForInternet(id: Long, reason: String? = "Waiting for internet connection")

    @Query(
        "UPDATE recordings SET uploadStatus = 'FAILED', retryCount = :retryCount, lastError = :lastError, uploadProgress = 0 " +
            "WHERE id = :id AND remoteDriveFileId IS NULL"
    )
    suspend fun markFailed(id: Long, retryCount: Int, lastError: String)

    @Query(
        "UPDATE recordings SET uploadStatus = 'UPLOADED', uploadProgress = 100, " +
            "remoteDriveFileId = :remoteDriveFileId, driveFolderId = :driveFolderId, lastError = NULL " +
            "WHERE id = :id AND remoteDriveFileId IS NULL"
    )
    suspend fun markUploaded(
        id: Long,
        remoteDriveFileId: String,
        driveFolderId: String?
    ): Int

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
