package com.example.drive

import com.example.data.local.RecordingDao
import com.example.data.local.RecordingEntity
import com.example.data.local.UploadStatus
import com.example.data.preferences.UserPreferencesRepository
import com.example.security.KeystoreCryptoManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class DriveRepository(
    private val recordingDao: RecordingDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val cryptoManager: KeystoreCryptoManager,
    maxIdleConnections: Int = 1
) {
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(maxIdleConnections.coerceAtLeast(1), 30, TimeUnit.SECONDS))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val driveApi: DriveApiService = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(DriveApiService::class.java)

    suspend fun syncAccountProfile(accessToken: String): DriveUserInfo = withContext(Dispatchers.IO) {
        val userInfo = driveApi.getUserInfo("Bearer $accessToken")
        preferencesRepository.setConnectedDriveAccount(
            email = userInfo.email ?: "Connected Google Account",
            name = userInfo.name
        )
        ensureDestinationFolder(accessToken)
        userInfo
    }

    suspend fun listAppCreatedFolders(accessToken: String): List<DriveFileItem> = withContext(Dispatchers.IO) {
        driveApi.listFolders("Bearer $accessToken").files
    }

    suspend fun createAndSelectFolder(accessToken: String, folderName: String): DriveFileItem =
        withContext(Dispatchers.IO) {
            val trimmed = folderName.trim().ifEmpty { DEFAULT_FOLDER_NAME }
            val created = driveApi.createFolder(
                authorization = "Bearer $accessToken",
                body = CreateFolderRequest(name = trimmed)
            )
            preferencesRepository.setSelectedDriveFolder(created.id, created.name)
            created
        }

    suspend fun selectFolder(folder: DriveFileItem) {
        preferencesRepository.setSelectedDriveFolder(folder.id, folder.name)
    }

    suspend fun ensureDestinationFolder(accessToken: String): DriveFileItem = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.getSnapshot()
        val existingFolders = runCatching { listAppCreatedFolders(accessToken) }.getOrDefault(emptyList())

        val selectedId = prefs.selectedDriveFolderId
        if (!selectedId.isNullOrBlank()) {
            val matched = existingFolders.firstOrNull { it.id == selectedId }
            if (matched != null) return@withContext matched
        }

        val defaultMatch = existingFolders.firstOrNull { it.name == DEFAULT_FOLDER_NAME }
        if (defaultMatch != null) {
            preferencesRepository.setSelectedDriveFolder(defaultMatch.id, defaultMatch.name)
            return@withContext defaultMatch
        }

        createAndSelectFolder(accessToken, DEFAULT_FOLDER_NAME)
    }

    /**
     * Streams decrypted chunks from [recording] directly into a Google Drive v3 resumable
     * upload session without buffering the whole file in RAM.
     */
    suspend fun uploadRecording(
        accessToken: String,
        recording: RecordingEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!recording.remoteDriveFileId.isNullOrBlank()) {
            return@withContext Result.success(recording.remoteDriveFileId)
        }

        val encryptedFile = File(recording.encryptedFilePath)
        if (!encryptedFile.exists()) {
            val msg = "Encrypted file missing on disk"
            recordingDao.markFailed(recording.id, recording.retryCount + 1, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        return@withContext try {
            val folder = ensureDestinationFolder(accessToken)
            recordingDao.updateUploadProgress(recording.id, UploadStatus.UPLOADING, 1)

            val sessionUri = initiateResumableSession(
                accessToken = accessToken,
                fileName = recording.fileName,
                folderId = folder.id,
                decryptedSizeBytes = recording.decryptedSizeBytes
            )

            val driveFileId = streamDecryptedBodyToSession(
                sessionUri = sessionUri,
                encryptedFile = encryptedFile,
                recording = recording
            )

            recordingDao.markUploaded(
                id = recording.id,
                remoteDriveFileId = driveFileId,
                driveFolderId = folder.id
            )
            preferencesRepository.setLatestUploadStatusText("Uploaded ${recording.fileName}")
            Result.success(driveFileId)
        } catch (t: Throwable) {
            val message = t.message ?: "Upload failed"
            recordingDao.markFailed(recording.id, recording.retryCount + 1, message)
            preferencesRepository.setLatestUploadStatusText("Upload failed: $message")
            Result.failure(t)
        }
    }

    private fun initiateResumableSession(
        accessToken: String,
        fileName: String,
        folderId: String,
        decryptedSizeBytes: Long
    ): String {
        val metadata = ResumableUploadMetadata(
            name = fileName,
            parents = listOf(folderId),
            mimeType = AUDIO_MP4_MIME
        )
        val json = moshi.adapter(ResumableUploadMetadata::class.java).toJson(metadata)
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("X-Upload-Content-Type", AUDIO_MP4_MIME)
            .addHeader("X-Upload-Content-Length", decryptedSizeBytes.toString())
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Resumable init HTTP ${response.code}")
            }
            return response.header("Location")
                ?: throw IllegalStateException("Missing Drive resumable Location header")
        }
    }

    private suspend fun streamDecryptedBodyToSession(
        sessionUri: String,
        encryptedFile: File,
        recording: RecordingEntity
    ): String {
        val totalSize = recording.decryptedSizeBytes.coerceAtLeast(1L)
        var lastReportedPercent = 1

        val streamingBody = object : RequestBody() {
            override fun contentType(): MediaType = AUDIO_MP4_MEDIA_TYPE

            override fun contentLength(): Long = recording.decryptedSizeBytes

            override fun writeTo(sink: BufferedSink) {
                val outStream = sink.outputStream()
                cryptoManager.decryptToStream(encryptedFile, outStream) { writtenBytes ->
                    val pct = ((writtenBytes * 100L) / totalSize).toInt().coerceIn(1, 99)
                    if (pct >= lastReportedPercent + 10 || pct == 99) {
                        lastReportedPercent = pct
                        progressScope.launch {
                            recordingDao.updateUploadProgress(
                                id = recording.id,
                                status = UploadStatus.UPLOADING,
                                progress = pct
                            )
                        }
                    }
                }
            }
        }

        val uploadReq = Request.Builder()
            .url(sessionUri)
            .put(streamingBody)
            .build()

        okHttpClient.newCall(uploadReq).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Upload PUT HTTP ${response.code}")
            }
            val uploaded = moshi.adapter(DriveFileItem::class.java).fromJson(bodyString)
            return uploaded?.id?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Missing uploaded Drive file ID")
        }
    }

    companion object {
        const val DEFAULT_FOLDER_NAME = "SafeVoice Recordings"
        private const val AUDIO_MP4_MIME = "audio/mp4"
        private val AUDIO_MP4_MEDIA_TYPE = AUDIO_MP4_MIME.toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
