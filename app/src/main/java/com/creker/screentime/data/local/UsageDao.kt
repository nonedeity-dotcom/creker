package com.creker.screentime.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsageDao {

    @Query(
        """
        SELECT package_name, SUM(usage_millis) AS usage_millis
        FROM app_usage
        WHERE date BETWEEN :fromDate AND :toDate
        GROUP BY package_name
        HAVING SUM(usage_millis) > 0
        ORDER BY usage_millis DESC
        """
    )
    abstract fun observeTotals(fromDate: String, toDate: String): Flow<List<PackageTotalRow>>

    /** Per-day totals across every app, for the multi-day chart. Days with no usage are absent. */
    @Query(
        """
        SELECT date, SUM(usage_millis) AS usage_millis
        FROM app_usage
        WHERE date BETWEEN :fromDate AND :toDate
        GROUP BY date
        """
    )
    abstract fun observeDailyTotals(fromDate: String, toDate: String): Flow<List<DateTotalRow>>

    /** Per-day session (launch) counts across every app, for the multi-day chart in sessions mode. */
    @Query(
        """
        SELECT date, SUM(launch_count) AS usage_millis
        FROM app_usage
        WHERE date BETWEEN :fromDate AND :toDate
        GROUP BY date
        """
    )
    abstract fun observeSessionDailyTotals(fromDate: String, toDate: String): Flow<List<DateTotalRow>>

    /** One package's full per-day history (all locally retained days), oldest first. */
    @Query("SELECT date, usage_millis FROM app_usage WHERE package_name = :packageName ORDER BY date ASC")
    abstract fun observeAppHistory(packageName: String): Flow<List<DateTotalRow>>

    /** One package's per-day usage over a date range, for its detail chart in usage mode. */
    @Query("SELECT date, usage_millis FROM app_usage WHERE package_name = :packageName AND date BETWEEN :fromDate AND :toDate")
    abstract fun observeAppDailyUsage(packageName: String, fromDate: String, toDate: String): Flow<List<DateTotalRow>>

    /** One package's per-day session count over a date range, for its detail chart in sessions mode. */
    @Query(
        """
        SELECT date, launch_count AS usage_millis
        FROM app_usage
        WHERE package_name = :packageName AND date BETWEEN :fromDate AND :toDate
        """
    )
    abstract fun observeAppDailySessions(packageName: String, fromDate: String, toDate: String): Flow<List<DateTotalRow>>

    /** One package's usage and launch count summed over a date range. */
    @Query(
        """
        SELECT SUM(usage_millis) AS usage_millis, SUM(launch_count) AS launch_count
        FROM app_usage
        WHERE package_name = :packageName AND date BETWEEN :fromDate AND :toDate
        """
    )
    abstract fun observeAppPeriodTotal(packageName: String, fromDate: String, toDate: String): Flow<AppPeriodTotalRow>

    /** Earliest day the database holds, used to explain a still-short history. */
    @Query("SELECT MIN(date) FROM app_usage")
    abstract fun observeEarliestDate(): Flow<String?>

    /** Every stored row, for the "save all data" export. */
    @Query("SELECT * FROM app_usage ORDER BY date ASC, package_name ASC")
    abstract suspend fun getAllRows(): List<AppUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(rows: List<AppUsageEntity>)

    @Query("DELETE FROM app_usage WHERE date BETWEEN :fromDate AND :toDate")
    abstract suspend fun deleteRange(fromDate: String, toDate: String)

    @Query("DELETE FROM app_usage WHERE date < :date")
    abstract suspend fun deleteBefore(date: String)

    /**
     * Replaces a whole span of days at once. Days are recomputed rather than
     * incremented, which keeps repeated syncs of the same window idempotent.
     */
    @Transaction
    open suspend fun replaceDays(fromDate: String, toDate: String, rows: List<AppUsageEntity>) {
        deleteRange(fromDate, toDate)
        if (rows.isNotEmpty()) insertAll(rows)
    }
}

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun get(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: SyncStateEntity)
}

@Dao
abstract class DeviceUsageDao {

    /** Per-day device-wide screen-on time, for the multi-day chart in screen-time mode. */
    @Query("SELECT date, screen_millis AS usage_millis FROM device_usage WHERE date BETWEEN :fromDate AND :toDate")
    abstract fun observeDailyTotals(fromDate: String, toDate: String): Flow<List<DateTotalRow>>

    /** Every stored row, for the "save all data" export. */
    @Query("SELECT * FROM device_usage ORDER BY date ASC")
    abstract suspend fun getAllRows(): List<DeviceUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(rows: List<DeviceUsageEntity>)

    @Query("DELETE FROM device_usage WHERE date BETWEEN :fromDate AND :toDate")
    abstract suspend fun deleteRange(fromDate: String, toDate: String)

    @Query("DELETE FROM device_usage WHERE date < :date")
    abstract suspend fun deleteBefore(date: String)

    @Transaction
    open suspend fun replaceDays(fromDate: String, toDate: String, rows: List<DeviceUsageEntity>) {
        deleteRange(fromDate, toDate)
        if (rows.isNotEmpty()) insertAll(rows)
    }
}
