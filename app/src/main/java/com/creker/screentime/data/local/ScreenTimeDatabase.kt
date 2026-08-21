package com.creker.screentime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppUsageEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ScreenTimeDatabase : RoomDatabase() {

    abstract fun usageDao(): UsageDao

    abstract fun syncStateDao(): SyncStateDao

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
                ).build().also { instance = it }
            }
    }
}
