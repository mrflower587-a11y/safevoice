package com.example.drive

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DriveUserInfo(
    @Json(name = "email") val email: String? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "mimeType") val mimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    @Json(name = "files") val files: List<DriveFileItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateFolderRequest(
    @Json(name = "name") val name: String,
    @Json(name = "mimeType") val mimeType: String = "application/vnd.google-apps.folder"
)

@JsonClass(generateAdapter = true)
data class ResumableUploadMetadata(
    @Json(name = "name") val name: String,
    @Json(name = "parents") val parents: List<String>,
    @Json(name = "mimeType") val mimeType: String = "audio/mp4"
)
