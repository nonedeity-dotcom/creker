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
    /** Number of times the app was brought to the foreground that day. */
    @ColumnInfo(name = "launch_count") val launchCount: Int,
)

/**
 * Device-wide screen-on time for one calendar day, excluding whatever portion the
 * lock screen itself was showing. Not tied to any package.
 */
@Entity(tableName = "device_usage", primaryKeys = ["date"])
data class DeviceUsageEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "screen_millis") val screenMillis: Long,
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

/** Aggregated result of a group-by-package query. */
data class PackageTotalRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "usage_millis") val usageMillis: Long,
)

/** Aggregated result of a group-by-date query, summed across every app. Also doubles as
 *  one package's own per-day row when queried without a GROUP BY across apps. */
data class DateTotalRow(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "usage_millis") val usageMillis: Long,
)

/**
 * Usage and launches for one package over a date range, summed into a single row.
 * Fields are nullable because SQL's SUM() over zero matching rows still returns one
 * row, with NULL in place of a sum.
 */
data class AppPeriodTotalRow(
    @ColumnInfo(name = "usage_millis") val usageMillis: Long?,
    @ColumnInfo(name = "launch_count") val launchCount: Int?,
)
