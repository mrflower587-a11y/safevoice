package com.example.system

import android.app.ActivityManager
import android.content.Context

data class RamProfile(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowRamDevice: Boolean,
    val isMemoryConstrainedNow: Boolean,
    val streamBufferBytes: Int,
    val amplitudePollIntervalMs: Long,
    val okHttpMaxIdleConnections: Int
)

class LowRamOptimizer(private val context: Context) {

    fun getRamProfile(): RamProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem.coerceAtLeast(0L)
        val availRam = memInfo.availMem.coerceAtLeast(0L)
        val systemLowRam = am?.isLowRamDevice == true
        // Devices with 1 GB - 1.5 GB physical RAM report totalMem <= ~1.5 GB
        val oneGbClassDevice = systemLowRam || (totalRam in 1L..ONE_GB_CLASS_MAX_BYTES)

        return RamProfile(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            isLowRamDevice = oneGbClassDevice,
            isMemoryConstrainedNow = memInfo.lowMemory,
            streamBufferBytes = if (oneGbClassDevice) LOW_RAM_STREAM_BUFFER else STANDARD_STREAM_BUFFER,
            amplitudePollIntervalMs = if (oneGbClassDevice) LOW_RAM_POLL_MS else STANDARD_POLL_MS,
            okHttpMaxIdleConnections = if (oneGbClassDevice) 1 else 2
        )
    }

    companion object {
        private const val ONE_GB_CLASS_MAX_BYTES = 1_610_612_736L // 1.5 GiB ceiling covers 1 GB RAM phones
        private const val LOW_RAM_STREAM_BUFFER = 16 * 1024
        private const val STANDARD_STREAM_BUFFER = 32 * 1024
        private const val LOW_RAM_POLL_MS = 300L
        private const val STANDARD_POLL_MS = 150L
    }
}
