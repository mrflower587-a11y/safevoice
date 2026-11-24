package com.example.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.preferences.UserPreferencesRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome()
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthOutcome()
    data class Error(val message: String) : DriveAuthOutcome()
}

class GoogleDriveAuthManager(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val authorizationClient by lazy {
        Identity.getAuthorizationClient(context)
    }

    private fun buildAuthRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_DRIVE_FILE),
                    Scope(SCOPE_EMAIL),
                    Scope(SCOPE_PROFILE)
                )
            )
            .build()
    }

    suspend fun requestAuthorization(): DriveAuthOutcome {
        return try {
            val result = authorizationClient.authorize(buildAuthRequest()).awaitResult()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    DriveAuthOutcome.NeedsConsent(pendingIntent)
                } else {
                    DriveAuthOutcome.Error("Google Drive consent dialog unavailable.")
                }
            } else {
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    DriveAuthOutcome.Authorized(token)
                } else {
                    DriveAuthOutcome.Error("No access token returned by Google Play Services.")
                }
            }
        } catch (t: Throwable) {
            DriveAuthOutcome.Error(t.message ?: "Unable to connect to Google Drive.")
        }
    }

    fun extractTokenFromActivityResult(data: Intent?): Result<String> {
        if (data == null) {
            return Result.failure(IllegalStateException("Authorization cancelled."))
        }
        return try {
            val authResult = authorizationClient.getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("Authorization completed without access token."))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun getSilentAccessToken(): String? {
        return try {
            val result = authorizationClient.authorize(buildAuthRequest()).awaitResult()
            if (!result.hasResolution()) {
                result.accessToken?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun disconnect() {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        preferencesRepository.setConnectedDriveAccount(email = null, name = null)
        preferencesRepository.setLatestUploadStatusText("Google Drive disconnected")
    }

    companion object {
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_EMAIL = "email"
        const val SCOPE_PROFILE = "profile"
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exc ->
        if (cont.isActive) cont.resumeWithException(exc)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
