package com.example.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.StatFs
import com.example.data.local.RecordingDao
import com.example.data.local.RecordingEntity
import com.example.data.local.UploadStatus
import com.example.security.KeystoreCryptoManager
import java.io.File

data class StorageStats(
    val freeBytes: Long,
    val totalBytes: Long,
    val vaultBytes: Long,
    val hasMinimumHeadroom: Boolean
)

class StorageManager(
    private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    val stagingDir: File
        get() = File(context.filesDir, "staging").apply { mkdirs() }

    val vaultDir: File
        get() = File(context.filesDir, "vault").apply { mkdirs() }

    val playbackCacheDir: File
        get() = File(context.cacheDir, "playback_temp").apply { mkdirs() }

    fun getFreeBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Throwable) {
            0L
        }
    }

    fun getTotalBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.totalBytes
        } catch (_: Throwable) {
            0L
        }
    }

    fun hasSufficientStorage(): Boolean = getFreeBytes() >= MIN_HEADROOM_BYTES

    fun getVaultSizeBytes(): Long {
        return vaultDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    fun getStorageStats(): StorageStats {
        val free = getFreeBytes()
        return StorageStats(
            freeBytes = free,
            totalBytes = getTotalBytes(),
            vaultBytes = getVaultSizeBytes(),
            hasMinimumHeadroom = free >= MIN_HEADROOM_BYTES
        )
    }

    fun createStagingFile(fileName: String): File {
        return File(stagingDir, fileName)
    }

    fun createVaultFile(baseM4aFileName: String): File {
        return File(vaultDir, "$baseM4aFileName.enc")
    }

    fun createTempPlaybackFile(recordingId: Long): File {
        wipePlaybackCache()
        return File(playbackCacheDir, "playback_${recordingId}_${System.currentTimeMillis()}.m4a")
    }

    fun wipePlaybackCache() {
        playbackCacheDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    /**
     * Scans [stagingDir] on startup for unfinalized segments left after unexpected termination.
     * Tiny corrupt stubs (< 1 KB) are discarded; valid segments are encrypted into [vaultDir]
     * and recorded in Room with `isRecovered = true`.
     */
    suspend fun recoverOrphanedStagingSegments(
        recordingDao: RecordingDao,
        activeStagingPaths: Set<String> = emptySet()
    ): Int {
        var recoveredCount = 0
        val orphanedFiles = stagingDir.listFiles()?.filter { it.isFile } ?: emptyList()
        for (stagingFile in orphanedFiles) {
            if (stagingFile.absolutePath in activeStagingPaths) {
                continue
            }
            if (stagingFile.length() < MIN_VALID_STUB_BYTES) {
                stagingFile.delete()
                continue
            }

            val destEncFile = createVaultFile(stagingFile.name)
            try {
                val durationMs = extractAudioDurationMs(stagingFile)
                val decryptedSize = cryptoManager.encryptFile(stagingFile, destEncFile)
                val startTime = stagingFile.lastModified().takeIf { it > 0L }
                    ?: (System.currentTimeMillis() - durationMs)
                val endTime = startTime + durationMs

                recordingDao.insert(
                    RecordingEntity(
                        fileName = stagingFile.name,
                        startTimeMs = startTime,
                        endTimeMs = endTime,
                        durationMs = durationMs,
                        fileSizeBytes = destEncFile.length(),
                        decryptedSizeBytes = decryptedSize,
                        encryptedFilePath = destEncFile.absolutePath,
                        uploadStatus = UploadStatus.PENDING,
                        isRecovered = true
                    )
                )
                stagingFile.delete()
                recoveredCount++
            } catch (_: Throwable) {
                destEncFile.delete()
                stagingFile.delete()
            }
        }
        return recoveredCount
    }

    private fun extractAudioDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            raw?.toLongOrNull()?.coerceAtLeast(1_000L) ?: 1_000L
        } catch (_: Throwable) {
            1_000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val MIN_HEADROOM_BYTES = 25L * 1024L * 1024L
        const val MIN_VALID_STUB_BYTES = 1024L
    }
}
