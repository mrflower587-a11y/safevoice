package com.example.ui.viewmodel

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drive.DriveAuthOutcome
import com.example.drive.DriveFileItem
import com.example.drive.DriveRepository
import com.example.drive.GoogleDriveAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriveUiState(
    val isBusy: Boolean = false,
    val folders: List<DriveFileItem> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val pendingConsentIntent: PendingIntent? = null
)

class DriveViewModel(
    private val authManager: GoogleDriveAuthManager,
    private val driveRepository: DriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveUiState())
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    fun connectOrRefreshDrive() {
        _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
        viewModelScope.launch {
            when (val outcome = authManager.requestAuthorization()) {
                is DriveAuthOutcome.Authorized -> {
                    syncProfile(outcome.accessToken)
                }
                is DriveAuthOutcome.NeedsConsent -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            pendingConsentIntent = outcome.pendingIntent
                        )
                    }
                }
                is DriveAuthOutcome.Error -> {
                    _uiState.update {
                        it.copy(isBusy = false, errorMessage = outcome.message)
                    }
                }
            }
        }
    }

    fun consumePendingConsentIntent() {
        _uiState.update { it.copy(pendingConsentIntent = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onAuthorizationActivityResult(data: Intent?) {
        val tokenResult = authManager.extractTokenFromActivityResult(data)
        tokenResult.onSuccess { token ->
            viewModelScope.launch { syncProfile(token) }
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = err.message ?: "Google Drive authorization was cancelled."
                )
            }
        }
    }

    fun loadFolders() {
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val token = authManager.getSilentAccessToken()
            if (token == null) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "Please connect Google Drive before loading folders."
                    )
                }
                return@launch
            }
            runCatching { driveRepository.listAppCreatedFolders(token) }
                .onSuccess { list ->
                    _uiState.update { it.copy(isBusy = false, folders = list) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isBusy = false, errorMessage = err.message ?: "Failed to load folders")
                    }
                }
        }
    }

    fun selectFolder(folder: DriveFileItem, onDone: () -> Unit) {
        viewModelScope.launch {
            driveRepository.selectFolder(folder)
            _uiState.update { it.copy(statusMessage = "Selected folder: ${folder.name}") }
            onDone()
        }
    }

    fun createAndSelectFolder(name: String, onDone: () -> Unit) {
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val token = authManager.getSilentAccessToken()
            if (token == null) {
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = "Connect Google Drive first.")
                }
                return@launch
            }
            runCatching { driveRepository.createAndSelectFolder(token, name) }
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            folders = (it.folders + created).distinctBy { f -> f.id },
                            statusMessage = "Created and selected '${created.name}'"
                        )
                    }
                    onDone()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isBusy = false, errorMessage = err.message ?: "Could not create folder")
                    }
                }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            authManager.disconnect()
            _uiState.value = DriveUiState(statusMessage = "Google Drive disconnected")
        }
    }

    private suspend fun syncProfile(accessToken: String) {
        runCatching { driveRepository.syncAccountProfile(accessToken) }
            .onSuccess { profile ->
                val folders = runCatching { driveRepository.listAppCreatedFolders(accessToken) }
                    .getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        folders = folders,
                        statusMessage = "Connected as ${profile.email ?: "Google Account"}"
                    )
                }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = err.message ?: "Failed to verify Google account profile"
                    )
                }
            }
    }
}
