package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.DismissibleErrorBanner
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusErrorText
import com.example.ui.viewmodel.PinVerifyUiState

@Composable
fun PinVerifyScreen(
    verifyState: PinVerifyUiState,
    destinationLabel: String,
    onVerifyPin: (String) -> Unit,
    onOpenForgotPin: () -> Unit,
    onCloseForgotPin: () -> Unit,
    onVerifyRecoveryCode: (String) -> Unit,
    onResetPinAfterRecovery: (String, String) -> Unit,
    onBack: () -> Unit,
    onDismissRecoveryError: () -> Unit = {}
) {
    var pinInput by remember { mutableStateOf("") }
    val isStopRecording = destinationLabel == "Stop Recording"

    SafeVoiceScaffold(
        title = if (isStopRecording) "Stop Recording" else "Unlock $destinationLabel",
        onBackClick = onBack,
        backTestTag = "pin_verify_back_button"
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SafeVoiceCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = SafeBlueContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "PIN lock",
                            tint = SafeBluePrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Text(
                        text = if (isStopRecording) "Verify PIN to Stop Recording" else "Enter Security PIN",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isStopRecording) {
                            "Enter your 4 to 6 digit SafeVoice PIN to stop and encrypt the active recording."
                        } else {
                            "Enter your 4 to 6 digit SafeVoice PIN to access $destinationLabel."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (verifyState.remainingLockoutSeconds > 0) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = StatusErrorBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_lockout_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = "Lockout timer",
                                    tint = StatusErrorText
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Too many failed attempts. Try again in ${verifyState.remainingLockoutSeconds}s.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StatusErrorText,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (verifyState.errorMessage != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = StatusErrorBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_verify_error")
                        ) {
                            Text(
                                text = verifyState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusErrorText,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("4–6 digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        enabled = verifyState.remainingLockoutSeconds == 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_verify_input")
                    )

                    Button(
                        onClick = { onVerifyPin(pinInput) },
                        enabled = !verifyState.isVerifying &&
                            verifyState.remainingLockoutSeconds == 0 &&
                            pinInput.length >= 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pin_verify_submit_button")
                    ) {
                        val submitLabel = when {
                            verifyState.isVerifying -> "Verifying…"
                            isStopRecording -> "Verify & Stop Recording"
                            else -> "Unlock"
                        }
                        Text(submitLabel)
                    }

                    TextButton(
                        onClick = onOpenForgotPin,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("forgot_pin_button")
                    ) {
                        Text("Forgot PIN?")
                    }
                }
            }
        }
    }

    if (verifyState.showForgotPinDialog) {
        ForgotPinRecoveryDialog(
            verifyState = verifyState,
            onVerifyCode = onVerifyRecoveryCode,
            onResetPin = onResetPinAfterRecovery,
            onDismissError = onDismissRecoveryError,
            onDismiss = onCloseForgotPin
        )
    }
}

@Composable
private fun ForgotPinRecoveryDialog(
    verifyState: PinVerifyUiState,
    onVerifyCode: (String) -> Unit,
    onResetPin: (String, String) -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit
) {
    var recoveryInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (verifyState.recoveryVerifiedForReset) "Set New PIN" else "Recover PIN",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (verifyState.recoveryError != null) {
                    DismissibleErrorBanner(
                        message = verifyState.recoveryError,
                        onDismiss = onDismissError
                    )
                }

                if (!verifyState.recoveryVerifiedForReset) {
                    Text(
                        text = "Enter the 12-character backup recovery code (XXXX-XXXX-XXXX) saved during PIN setup.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it.uppercase() },
                        label = { Text("Backup Recovery Code") },
                        placeholder = { Text("XXXX-XXXX-XXXX") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_code_input")
                    )
                } else {
                    Text(
                        text = "Recovery code verified. Choose a new 4 to 6 digit PIN.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_pin_input")
                    )
                    OutlinedTextField(
                        value = confirmNewPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmNewPin = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_pin_confirm_input")
                    )
                }
            }
        },
        confirmButton = {
            if (!verifyState.recoveryVerifiedForReset) {
                Button(
                    onClick = { onVerifyCode(recoveryInput) },
                    modifier = Modifier.testTag("verify_recovery_button")
                ) {
                    Text("Verify Code")
                }
            } else {
                Button(
                    onClick = { onResetPin(newPin, confirmNewPin) },
                    modifier = Modifier.testTag("reset_pin_submit_button")
                ) {
                    Text("Reset PIN")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("recovery_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
