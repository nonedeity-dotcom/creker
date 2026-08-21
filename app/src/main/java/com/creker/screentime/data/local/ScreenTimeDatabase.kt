package com.creker.screentime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppUsageEntity::class, SyncStateEntity::class, DeviceUsageEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ScreenTimeDatabase : RoomDatabase() {

    abstract fun usageDao(): UsageDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun deviceUsageDao(): DeviceUsageDao

    companion object {
        private const val NAME = "screen_time.db"

        @Volatile
        private var instance: ScreenTimeDatabase? = null

        fun get(context: Context): ScreenTimeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScreenTimeDatabase::class.java,
                    NAME,
                )
                    // Nothing stored here is user-authored — it is re-derived from the
                    // system on the next sync — so a schema bump just rebuilds the
                    // table instead of carrying a migration for data that regenerates
                    // itself anyway (aside from history older than the sync window).
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
