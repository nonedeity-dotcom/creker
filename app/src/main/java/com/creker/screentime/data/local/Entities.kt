package com.creker.screentime.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Foreground time of one package on one calendar day.
 *
 * The system keeps detailed usage events for a handful of days only, so the app
 * mirrors what it reads into this table. Everything the UI shows for longer periods
 * comes from here, and nothing ever leaves the device.
 */
@Entity(tableName = "app_usage", primaryKeys = ["package_name", "date"])
data class AppUsageEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    /** Calendar day in the device time zone, ISO `yyyy-MM-dd`. */
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "usage_millis") val usageMillis: Long,
)

/** Single-row table remembering how far the event stream has been consumed. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastSyncedAtMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

/** Aggregated result of a group-by query. */
data class PackageTotalRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "usage_millis") val usageMillis: Long,
)
