package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.DismissibleErrorBanner
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBlueOnContainer
import com.example.ui.theme.SafeBluePrimary
import com.example.ui.viewmodel.PinSetupUiState

@Composable
fun PinSetupScreen(
    isChangingPin: Boolean,
    setupState: PinSetupUiState,
    onCreatePin: (String, String) -> Unit,
    onDismissError: () -> Unit = {},
    onFinish: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var savedRecoveryConfirmed by remember { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }

    SafeVoiceScaffold(
        title = if (isChangingPin) "Change Security PIN" else "Create Security PIN",
        onBackClick = if (isChangingPin) onBackClick else null
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val recoveryCode = setupState.generatedRecoveryCode
            if (recoveryCode == null) {
                Text(
                    text = "Set a 4 to 6 digit PIN to lock Settings, My Files, and stopping an active recording. Starting a recording from Home stays one-tap.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (setupState.errorMessage != null) {
                    DismissibleErrorBanner(
                        message = setupState.errorMessage,
                        onDismiss = onDismissError
                    )
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("Enter 4–6 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_setup_input")
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_confirm_input")
                )

                Button(
                    onClick = { onCreatePin(pin, confirmPin) },
                    enabled = !setupState.isSaving && pin.length >= 4 && confirmPin.length >= 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pin_setup_save_button")
                ) {
                    Text(if (setupState.isSaving) "Securing PIN…" else "Save PIN & Generate Recovery Code")
                }
            } else {
                SafeVoiceCard(containerColor = SafeBlueContainer.copy(alpha = 0.35f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "One-Time Backup Recovery Code",
                            style = MaterialTheme.typography.titleLarge,
                            color = SafeBlueOnContainer
                        )
                        Text(
                            text = "Write down or store this code safely. It is shown only once and is required to reset your PIN if forgotten.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, SafeBluePrimary.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = recoveryCode,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall,
                                color = SafeBlueOnContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("recovery_code_display")
                            )
                        }

                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SafeVoice Recovery Code", recoveryCode))
                                copiedFeedback = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_recovery_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy recovery code",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (copiedFeedback) "Copied to clipboard" else "Copy Recovery Code")
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                ) {
                    Checkbox(
                        checked = savedRecoveryConfirmed,
                        onCheckedChange = { savedRecoveryConfirmed = it },
                        modifier = Modifier.testTag("recovery_saved_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I have saved my recovery code in a safe place.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = onFinish,
                    enabled = savedRecoveryConfirmed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pin_setup_finish_button")
                ) {
                    Text(if (isChangingPin) "Return to Settings" else "Enter SafeVoice")
                }
            }
        }
    }
}
