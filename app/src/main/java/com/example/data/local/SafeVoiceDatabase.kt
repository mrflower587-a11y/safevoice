package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class SafeVoiceTypeConverters {
    @TypeConverter
    fun fromUploadStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus =
        runCatching { UploadStatus.valueOf(value) }.getOrDefault(UploadStatus.PENDING)
}

@Database(
    entities = [RecordingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SafeVoiceTypeConverters::class)
abstract class SafeVoiceDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        private const val DB_NAME = "safevoice_vault.db"

        fun create(context: Context, isLowRamDevice: Boolean = false): SafeVoiceDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                SafeVoiceDatabase::class.java,
                DB_NAME
            )
            if (isLowRamDevice) {
                builder.setJournalMode(JournalMode.TRUNCATE)
            }
            return builder.build()
        }
    }
}
