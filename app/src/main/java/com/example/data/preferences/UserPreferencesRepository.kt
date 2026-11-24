package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.safeVoiceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "safevoice_prefs"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val RECOVERY_HASH = stringPreferencesKey("recovery_hash")
        val RECOVERY_SALT = stringPreferencesKey("recovery_salt")
        val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val PIN_LOCKOUT_UNTIL_MS = longPreferencesKey("pin_lockout_until_ms")
        val AUDIO_QUALITY_KBPS = intPreferencesKey("audio_quality_kbps")
        val SEGMENT_DURATION_MINUTES = intPreferencesKey("segment_duration_minutes")
        val AUTO_UPLOAD_AFTER_FIVE = booleanPreferencesKey("auto_upload_after_five")
        val PERIODIC_UPLOAD_90_MIN = booleanPreferencesKey("periodic_upload_90_min")
        val COMPLETED_SINCE_LAST_BATCH = intPreferencesKey("completed_since_last_batch")
        val CONNECTED_DRIVE_EMAIL = stringPreferencesKey("connected_drive_email")
        val CONNECTED_DRIVE_NAME = stringPreferencesKey("connected_drive_name")
        val SELECTED_DRIVE_FOLDER_ID = stringPreferencesKey("selected_drive_folder_id")
        val SELECTED_DRIVE_FOLDER_NAME = stringPreferencesKey("selected_drive_folder_name")
        val LATEST_UPLOAD_STATUS_TEXT = stringPreferencesKey("latest_upload_status_text")
        val ALWAYS_RUN_IN_BACKGROUND = booleanPreferencesKey("always_run_in_background")
        val RECORDING_SESSION_ACTIVE = booleanPreferencesKey("recording_session_active")
        val RECORDING_SESSION_PAUSED = booleanPreferencesKey("recording_session_paused")
    }

    val preferencesFlow: Flow<UserPreferences> = context.safeVoiceDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val pinHash = prefs[Keys.PIN_HASH]
            val pinSalt = prefs[Keys.PIN_SALT]
            UserPreferences(
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                hasPinConfigured = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank(),
                pinHash = pinHash,
                pinSalt = pinSalt,
                recoveryHash = prefs[Keys.RECOVERY_HASH],
                recoverySalt = prefs[Keys.RECOVERY_SALT],
                pinFailedAttempts = prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0,
                pinLockoutUntilMs = prefs[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L,
                audioQualityKbps = prefs[Keys.AUDIO_QUALITY_KBPS] ?: AudioQualityPreset.STANDARD.kbps,
                segmentDurationMinutes = prefs[Keys.SEGMENT_DURATION_MINUTES] ?: 30,
                autoUploadAfterFive = prefs[Keys.AUTO_UPLOAD_AFTER_FIVE] ?: false,
                periodicUpload90Min = prefs[Keys.PERIODIC_UPLOAD_90_MIN] ?: false,
                completedSinceLastUploadBatch = prefs[Keys.COMPLETED_SINCE_LAST_BATCH] ?: 0,
                connectedDriveEmail = prefs[Keys.CONNECTED_DRIVE_EMAIL],
                connectedDriveName = prefs[Keys.CONNECTED_DRIVE_NAME],
                selectedDriveFolderId = prefs[Keys.SELECTED_DRIVE_FOLDER_ID],
                selectedDriveFolderName = prefs[Keys.SELECTED_DRIVE_FOLDER_NAME],
                latestUploadStatusText = prefs[Keys.LATEST_UPLOAD_STATUS_TEXT] ?: "No uploads yet",
                alwaysRunInBackground = prefs[Keys.ALWAYS_RUN_IN_BACKGROUND] ?: true,
                recordingSessionActive = prefs[Keys.RECORDING_SESSION_ACTIVE] ?: false,
                recordingSessionPaused = prefs[Keys.RECORDING_SESSION_PAUSED] ?: false
            )
        }

    suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    suspend fun setPinAndRecovery(
        pinHash: String,
        pinSalt: String,
        recoveryHash: String,
        recoverySalt: String,
        markOnboardingComplete: Boolean
    ) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = pinHash
            prefs[Keys.PIN_SALT] = pinSalt
            prefs[Keys.RECOVERY_HASH] = recoveryHash
            prefs[Keys.RECOVERY_SALT] = recoverySalt
            prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
            prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = 0L
            if (markOnboardingComplete) {
                prefs[Keys.ONBOARDING_COMPLETED] = true
            }
        }
    }

    suspend fun updatePinOnly(pinHash: String, pinSalt: String) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = pinHash
            prefs[Keys.PIN_SALT] = pinSalt
            prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
            prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun recordPinFailure(failedAttempts: Int, lockoutUntilMs: Long) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.PIN_FAILED_ATTEMPTS] = failedAttempts
            prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = lockoutUntilMs
        }
    }

    suspend fun resetPinFailures() {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
            prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun setAudioQualityKbps(kbps: Int) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.AUDIO_QUALITY_KBPS] = kbps
        }
    }

    suspend fun setSegmentDurationMinutes(minutes: Int) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.SEGMENT_DURATION_MINUTES] = minutes
        }
    }

    suspend fun setAutoUploadAfterFive(enabled: Boolean) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.AUTO_UPLOAD_AFTER_FIVE] = enabled
        }
    }

    suspend fun setPeriodicUpload90Min(enabled: Boolean) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.PERIODIC_UPLOAD_90_MIN] = enabled
        }
    }

    suspend fun incrementCompletedBatchAndCheckFlush(autoUploadEnabled: Boolean): Boolean {
        var shouldTrigger = false
        context.safeVoiceDataStore.edit { prefs ->
            val next = (prefs[Keys.COMPLETED_SINCE_LAST_BATCH] ?: 0) + 1
            if (autoUploadEnabled && next >= 5) {
                prefs[Keys.COMPLETED_SINCE_LAST_BATCH] = 0
                shouldTrigger = true
            } else {
                prefs[Keys.COMPLETED_SINCE_LAST_BATCH] = next
            }
        }
        return shouldTrigger
    }

    suspend fun resetCompletedBatchCounter() {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.COMPLETED_SINCE_LAST_BATCH] = 0
        }
    }

    suspend fun setConnectedDriveAccount(email: String?, name: String?) {
        context.safeVoiceDataStore.edit { prefs ->
            if (email == null) {
                prefs.remove(Keys.CONNECTED_DRIVE_EMAIL)
                prefs.remove(Keys.CONNECTED_DRIVE_NAME)
                prefs.remove(Keys.SELECTED_DRIVE_FOLDER_ID)
                prefs.remove(Keys.SELECTED_DRIVE_FOLDER_NAME)
            } else {
                prefs[Keys.CONNECTED_DRIVE_EMAIL] = email
                if (name != null) {
                    prefs[Keys.CONNECTED_DRIVE_NAME] = name
                } else {
                    prefs.remove(Keys.CONNECTED_DRIVE_NAME)
                }
            }
        }
    }

    suspend fun setSelectedDriveFolder(folderId: String?, folderName: String?) {
        context.safeVoiceDataStore.edit { prefs ->
            if (folderId == null) {
                prefs.remove(Keys.SELECTED_DRIVE_FOLDER_ID)
                prefs.remove(Keys.SELECTED_DRIVE_FOLDER_NAME)
            } else {
                prefs[Keys.SELECTED_DRIVE_FOLDER_ID] = folderId
                prefs[Keys.SELECTED_DRIVE_FOLDER_NAME] = folderName ?: "SafeVoice Recordings"
            }
        }
    }

    suspend fun setLatestUploadStatusText(statusText: String) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.LATEST_UPLOAD_STATUS_TEXT] = statusText
        }
    }

    suspend fun setAlwaysRunInBackground(enabled: Boolean) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.ALWAYS_RUN_IN_BACKGROUND] = enabled
        }
    }

    suspend fun setRecordingSessionState(active: Boolean, paused: Boolean = false) {
        context.safeVoiceDataStore.edit { prefs ->
            prefs[Keys.RECORDING_SESSION_ACTIVE] = active
            prefs[Keys.RECORDING_SESSION_PAUSED] = paused
        }
    }
}
