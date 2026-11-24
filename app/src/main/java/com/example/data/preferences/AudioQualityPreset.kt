package com.example.data.preferences

enum class AudioQualityPreset(
    val kbps: Int,
    val bitRateBps: Int,
    val title: String,
    val approxSizeLabel: String
) {
    COMPACT(
        kbps = 32,
        bitRateBps = 32_000,
        title = "Compact Voice",
        approxSizeLabel = "~7 MB / 30 min"
    ),
    STANDARD(
        kbps = 64,
        bitRateBps = 64_000,
        title = "Standard Voice",
        approxSizeLabel = "~14 MB / 30 min"
    ),
    HIGH(
        kbps = 128,
        bitRateBps = 128_000,
        title = "High Clarity",
        approxSizeLabel = "~28 MB / 30 min"
    );

    companion object {
        fun fromKbps(kbps: Int): AudioQualityPreset {
            return entries.firstOrNull { it.kbps == kbps } ?: STANDARD
        }
    }
}
