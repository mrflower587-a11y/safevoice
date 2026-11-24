package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.SafeVoiceNavHost
import com.example.ui.theme.SafeVoiceTheme
import com.example.ui.viewmodel.SafeVoiceViewModel

class MainActivity : ComponentActivity() {

    private val stopPinRequested = mutableStateOf(false)
    private val autoResumeRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleActionExtras(intent)

        val app = application as SafeVoiceApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SafeVoiceViewModel(app.applicationContext, app.container) as T
            }
        }

        setContent {
            SafeVoiceTheme {
                val vm: SafeVoiceViewModel = viewModel(factory = factory)
                SafeVoiceNavHost(
                    viewModel = vm,
                    stopRecordingPinRequested = stopPinRequested.value,
                    onStopPinRequestConsumed = { stopPinRequested.value = false },
                    autoResumeRecordingRequested = autoResumeRequested.value,
                    onAutoResumeConsumed = { autoResumeRequested.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleActionExtras(intent)
    }

    private fun handleActionExtras(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUIRE_PIN_TO_STOP, false) == true) {
            stopPinRequested.value = true
            intent.removeExtra(EXTRA_REQUIRE_PIN_TO_STOP)
        }
        if (intent?.getBooleanExtra(EXTRA_AUTO_RESUME_RECORDING, false) == true) {
            autoResumeRequested.value = true
            intent.removeExtra(EXTRA_AUTO_RESUME_RECORDING)
        }
    }

    companion object {
        const val EXTRA_REQUIRE_PIN_TO_STOP = "extra_require_pin_to_stop"
        const val EXTRA_AUTO_RESUME_RECORDING = "extra_auto_resume_recording"
    }
}
