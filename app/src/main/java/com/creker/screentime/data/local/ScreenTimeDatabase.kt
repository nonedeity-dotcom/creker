package com.creker.screentime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.creker.screentime.contract.UsageContract

@Database(
    entities = [AppUsageEntity::class, SyncStateEntity::class, DeviceUsageEntity::class],
    version = 4,
    exportSchema = true,
)
internal abstract class ScreenTimeDatabase : RoomDatabase() {

    abstract fun usageDao(): UsageDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun deviceUsageDao(): DeviceUsageDao

    companion object {
        private const val NAME = "screen_time.db"

        /**
         * Adds [UsageContract.COLUMN_UPDATED_AT] to the exposed table.
         *
         * Written out rather than left to [fallbackToDestructiveMigration]: destroying the table
         * would also destroy up to 400 days of history that the system can no longer rebuild (it
         * only remembers about a week of events), and the app has no cloud backup to fall back on
         * by design. Existing rows keep `0`, i.e. "unknown" — the next sync fills in real values
         * for the last week, and older days stay honestly marked as unmeasured.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ${UsageContract.TABLE_DEVICE_USAGE} " +
                        "ADD COLUMN ${UsageContract.COLUMN_UPDATED_AT} INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var instance: ScreenTimeDatabase? = null

        fun get(context: Context): ScreenTimeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScreenTimeDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_3_4)
                    // Still the fallback for any *other* schema jump: nothing stored here is
                    // user-authored, it is re-derived from the system on the next sync. Any
                    // change that touches device_usage deserves a real migration like the one
                    // above, though — that table is what the other app reads.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
