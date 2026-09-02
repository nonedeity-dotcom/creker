package com.creker.screentime.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.creker.screentime.contract.UsageContract

/**
 * Foreground time of one package on one calendar day.
 *
 * The system keeps detailed usage events for a handful of days only, so the app
 * mirrors what it reads into this table. Everything the UI shows for longer periods
 * comes from here, and nothing ever leaves the device. This table in particular is
 * never exposed through the provider — which app was used stays inside creker.
 */
@Entity(tableName = "app_usage", primaryKeys = ["package_name", "date"])
internal data class AppUsageEntity(
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
 *
 * This is the one table another app can read, so its table and column names come from
 * [UsageContract] rather than being spelled out here: renaming a column is then a change
 * to the contract file, which is where it is visible as one.
 */
@Entity(tableName = UsageContract.TABLE_DEVICE_USAGE, primaryKeys = [UsageContract.COLUMN_DATE])
internal data class DeviceUsageEntity(
    @ColumnInfo(name = UsageContract.COLUMN_DATE) val date: String,
    @ColumnInfo(name = UsageContract.COLUMN_SCREEN_MILLIS) val screenMillis: Long,
    /**
     * Epoch millis up to which [screenMillis] is complete — see [UsageContract.COLUMN_UPDATED_AT].
     * `0` for rows written before this column existed.
     */
    @ColumnInfo(name = UsageContract.COLUMN_UPDATED_AT, defaultValue = "0") val updatedAtMs: Long = 0L,
)

/** Single-row table remembering how far the event stream has been consumed. */
@Entity(tableName = "sync_state")
internal data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastSyncedAtMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

/** Aggregated result of a group-by-package query. */
internal data class PackageTotalRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "usage_millis") val usageMillis: Long,
)

/** Aggregated result of a group-by-date query, summed across every app. Also doubles as
 *  one package's own per-day row when queried without a GROUP BY across apps. */
internal data class DateTotalRow(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "usage_millis") val usageMillis: Long,
)

/**
 * Usage and launches for one package over a date range, summed into a single row.
 * Fields are nullable because SQL's SUM() over zero matching rows still returns one
 * row, with NULL in place of a sum.
 */
internal data class AppPeriodTotalRow(
    @ColumnInfo(name = "usage_millis") val usageMillis: Long?,
    @ColumnInfo(name = "launch_count") val launchCount: Int?,
)
