package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
import com.example.security.PinSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PinSetupUiState(
    val generatedRecoveryCode: String? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)

data class PinVerifyUiState(
    val errorMessage: String? = null,
    val remainingLockoutSeconds: Int = 0,
    val showForgotPinDialog: Boolean = false,
    val recoveryError: String? = null,
    val recoveryVerifiedForReset: Boolean = false,
    val isVerifying: Boolean = false
)

class PinViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val pinSecurityManager: PinSecurityManager
) : ViewModel() {

    private val _setupState = MutableStateFlow(PinSetupUiState())
    val setupState: StateFlow<PinSetupUiState> = _setupState.asStateFlow()

    private val _verifyState = MutableStateFlow(PinVerifyUiState())
    val verifyState: StateFlow<PinVerifyUiState> = _verifyState.asStateFlow()

    private var lockoutTimerJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                updateRemainingLockout(prefs.pinLockoutUntilMs)
            }
        }
    }

    fun resetSetupState() {
        _setupState.value = PinSetupUiState()
    }

    fun clearVerifyTransientState() {
        _verifyState.update {
            it.copy(
                errorMessage = null,
                showForgotPinDialog = false,
                recoveryError = null,
                recoveryVerifiedForReset = false,
                isVerifying = false
            )
        }
    }

    fun dismissSetupError() {
        _setupState.update { it.copy(errorMessage = null) }
    }

    fun dismissRecoveryError() {
        _verifyState.update { it.copy(recoveryError = null) }
    }

    fun createPinAndRecovery(pin: String, confirmPin: String, markOnboardingComplete: Boolean) {
        if (!pinSecurityManager.isValidPinFormat(pin)) {
            _setupState.update { it.copy(errorMessage = "PIN must be 4 to 6 numeric digits.") }
            return
        }
        if (pin != confirmPin) {
            _setupState.update { it.copy(errorMessage = "PIN entries do not match.") }
            return
        }

        _setupState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val recoveryCode = pinSecurityManager.generateBackupRecoveryCode()
            val (pinHashed, recoveryHashed) = withContext(Dispatchers.Default) {
                pinSecurityManager.hashPin(pin) to pinSecurityManager.hashRecoveryCode(recoveryCode)
            }
            preferencesRepository.setPinAndRecovery(
                pinHash = pinHashed.hashBase64,
                pinSalt = pinHashed.saltBase64,
                recoveryHash = recoveryHashed.hashBase64,
                recoverySalt = recoveryHashed.saltBase64,
                markOnboardingComplete = markOnboardingComplete
            )
            _setupState.update {
                it.copy(
                    isSaving = false,
                    generatedRecoveryCode = recoveryCode,
                    errorMessage = null
                )
            }
        }
    }

    fun verifyPin(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val prefs = preferencesRepository.getSnapshot()
            val now = System.currentTimeMillis()
            if (prefs.pinLockoutUntilMs > now) {
                updateRemainingLockout(prefs.pinLockoutUntilMs)
                return@launch
            }
            if (!pinSecurityManager.isValidPinFormat(pin)) {
                _verifyState.update { it.copy(errorMessage = "Enter a 4 to 6 digit PIN.") }
                return@launch
            }

            _verifyState.update { it.copy(isVerifying = true, errorMessage = null) }
            val valid = withContext(Dispatchers.Default) {
                pinSecurityManager.verifyPin(pin, prefs.pinHash, prefs.pinSalt)
            }

            if (valid) {
                preferencesRepository.resetPinFailures()
                _verifyState.value = PinVerifyUiState()
                onSuccess()
            } else {
                val nextAttempts = prefs.pinFailedAttempts + 1
                val lockoutMs = pinSecurityManager.computeLockoutDurationMs(nextAttempts)
                val lockoutUntil = if (lockoutMs > 0L) System.currentTimeMillis() + lockoutMs else 0L
                preferencesRepository.recordPinFailure(nextAttempts, lockoutUntil)
                updateRemainingLockout(lockoutUntil)
                val errorText = if (lockoutMs > 0L) {
                    "Incorrect PIN. Locked for ${lockoutMs / 1000L}s after $nextAttempts failed attempts."
                } else {
                    "Incorrect PIN ($nextAttempts of 3 before temporary lockout)."
                }
                _verifyState.update {
                    it.copy(isVerifying = false, errorMessage = errorText)
                }
            }
        }
    }

    fun openForgotPinDialog() {
        _verifyState.update {
            it.copy(
                showForgotPinDialog = true,
                recoveryError = null,
                recoveryVerifiedForReset = false
            )
        }
    }

    fun closeForgotPinDialog() {
        _verifyState.update {
            it.copy(
                showForgotPinDialog = false,
                recoveryError = null,
                recoveryVerifiedForReset = false
            )
        }
    }

    fun verifyRecoveryCode(inputCode: String) {
        viewModelScope.launch {
            val prefs = preferencesRepository.getSnapshot()
            val valid = withContext(Dispatchers.Default) {
                pinSecurityManager.verifyRecoveryCode(
                    inputCode = inputCode,
                    storedHashBase64 = prefs.recoveryHash,
                    storedSaltBase64 = prefs.recoverySalt
                )
            }
            if (valid) {
                _verifyState.update {
                    it.copy(recoveryVerifiedForReset = true, recoveryError = null)
                }
            } else {
                _verifyState.update {
                    it.copy(recoveryError = "Recovery code did not match. Check the 12-character code.")
                }
            }
        }
    }

    fun resetPinAfterRecovery(newPin: String, confirmPin: String, onSuccess: () -> Unit) {
        if (!pinSecurityManager.isValidPinFormat(newPin)) {
            _verifyState.update { it.copy(recoveryError = "PIN must be 4 to 6 digits.") }
            return
        }
        if (newPin != confirmPin) {
            _verifyState.update { it.copy(recoveryError = "New PIN entries do not match.") }
            return
        }
        viewModelScope.launch {
            val hashed = withContext(Dispatchers.Default) {
                pinSecurityManager.hashPin(newPin)
            }
            preferencesRepository.updatePinOnly(hashed.hashBase64, hashed.saltBase64)
            _verifyState.value = PinVerifyUiState()
            onSuccess()
        }
    }

    private fun updateRemainingLockout(lockoutUntilMs: Long) {
        lockoutTimerJob?.cancel()
        val now = System.currentTimeMillis()
        if (lockoutUntilMs <= now) {
            _verifyState.update { it.copy(remainingLockoutSeconds = 0) }
            return
        }
        lockoutTimerJob = viewModelScope.launch {
            while (isActive) {
                val remaining = ((lockoutUntilMs - System.currentTimeMillis() + 999L) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
                _verifyState.update { it.copy(remainingLockoutSeconds = remaining) }
                if (remaining <= 0) break
                delay(500L)
            }
        }
    }
}
