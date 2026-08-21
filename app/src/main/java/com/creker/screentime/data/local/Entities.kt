package com.creker.screentime.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Foreground time of one package on one calendar day.
 *
 * The system keeps detailed usage events for a handful of days only, so the app
 * mirrors what it reads into this table. Everything the UI shows for longer periods
 * comes from here, and nothing ever leaves the device.
 */
@Entity(tableName = "usage_day", primaryKeys = ["dayEpoch", "packageName"])
data class UsageDayEntity(
    /** Day as [java.time.LocalDate.toEpochDay] in the device time zone. */
    val dayEpoch: Long,
    val packageName: String,
    val foregroundTimeMs: Long,
    val launchCount: Int,
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
    val packageName: String,
    val foregroundTimeMs: Long,
    val launchCount: Int,
)
