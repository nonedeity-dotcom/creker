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
        SELECT packageName,
               SUM(foregroundTimeMs) AS foregroundTimeMs,
               SUM(launchCount) AS launchCount
        FROM usage_day
        WHERE dayEpoch BETWEEN :fromDayEpoch AND :toDayEpoch
        GROUP BY packageName
        HAVING SUM(foregroundTimeMs) > 0
        ORDER BY foregroundTimeMs DESC
        """
    )
    abstract fun observeTotals(fromDayEpoch: Long, toDayEpoch: Long): Flow<List<PackageTotalRow>>

    /** Earliest day the database holds, used to warn about the missing history. */
    @Query("SELECT MIN(dayEpoch) FROM usage_day")
    abstract fun observeEarliestDay(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(rows: List<UsageDayEntity>)

    @Query("DELETE FROM usage_day WHERE dayEpoch BETWEEN :fromDayEpoch AND :toDayEpoch")
    abstract suspend fun deleteRange(fromDayEpoch: Long, toDayEpoch: Long)

    @Query("DELETE FROM usage_day WHERE dayEpoch < :dayEpoch")
    abstract suspend fun deleteBefore(dayEpoch: Long)

    /**
     * Replaces a whole span of days at once. Days are recomputed rather than
     * incremented, which keeps repeated syncs of the same window idempotent.
     */
    @Transaction
    open suspend fun replaceDays(fromDayEpoch: Long, toDayEpoch: Long, rows: List<UsageDayEntity>) {
        deleteRange(fromDayEpoch, toDayEpoch)
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
