package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface SafeVoiceRoute {
    @Serializable data object Splash : SafeVoiceRoute
    @Serializable data object Welcome : SafeVoiceRoute
    @Serializable data object Permissions : SafeVoiceRoute
    @Serializable data class PinSetup(val isChangingPin: Boolean = false) : SafeVoiceRoute
    @Serializable data object Home : SafeVoiceRoute
    @Serializable data object RecordingSession : SafeVoiceRoute
    @Serializable data class PinVerify(val target: String = TARGET_SETTINGS) : SafeVoiceRoute
    @Serializable data object Settings : SafeVoiceRoute
    @Serializable data object MyFiles : SafeVoiceRoute
    @Serializable data class AudioPlayer(val recordingId: Long) : SafeVoiceRoute
    @Serializable data object DriveConnection : SafeVoiceRoute
    @Serializable data object DriveFolderSelect : SafeVoiceRoute
    @Serializable data object UploadQueue : SafeVoiceRoute
    @Serializable data object UploadProgress : SafeVoiceRoute
    @Serializable data object StorageInfo : SafeVoiceRoute
    @Serializable data object PrivacyInfo : SafeVoiceRoute
    @Serializable data object PrivacyPolicy : SafeVoiceRoute
    @Serializable data object AboutHelp : SafeVoiceRoute

    companion object {
        const val TARGET_SETTINGS = "SETTINGS"
        const val TARGET_MY_FILES = "MY_FILES"
        const val TARGET_STOP_RECORDING = "STOP_RECORDING"
    }
}
