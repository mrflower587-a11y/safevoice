package com.example.drive

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface DriveApiService {

    @GET("https://www.googleapis.com/oauth2/v3/userinfo")
    suspend fun getUserInfo(
        @Header("Authorization") authorization: String
    ): DriveUserInfo

    @GET("drive/v3/files")
    suspend fun listFolders(
        @Header("Authorization") authorization: String,
        @Query("q") query: String = "mimeType = 'application/vnd.google-apps.folder' and trashed = false",
        @Query("spaces") spaces: String = "drive",
        @Query("fields") fields: String = "files(id,name,mimeType)",
        @Query("orderBy") orderBy: String = "name"
    ): DriveFileListResponse

    @POST("drive/v3/files")
    suspend fun createFolder(
        @Header("Authorization") authorization: String,
        @Body body: CreateFolderRequest,
        @Query("fields") fields: String = "id,name,mimeType"
    ): DriveFileItem
}
