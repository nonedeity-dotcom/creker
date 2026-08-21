package com.creker.screentime.data

import com.creker.screentime.core.AppUsageTotal
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.ForegroundSessionBuilder
import com.creker.screentime.data.local.AppUsageEntity
import com.creker.screentime.data.local.SyncStateDao
import com.creker.screentime.data.local.SyncStateEntity
import com.creker.screentime.data.local.UsageDao
import com.creker.screentime.data.system.SystemUsageEventSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Owns the local screen-time history.
 *
 * Reading happens from Room, so periods longer than the system's event retention keep
 * working; writing happens by re-deriving whole days from [SystemUsageEventSource],
 * which makes repeated syncs of the same window idempotent.
 */
class UsageRepository(
    private val usageDao: UsageDao,
    private val syncStateDao: SyncStateDao,
    private val eventSource: SystemUsageEventSource,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun today(): LocalDate = LocalDate.now(clock)

    fun observeTotals(range: DayRange): Flow<List<AppUsageTotal>> =
        usageDao.observeTotals(range.from.toString(), range.to.toString())
            .map { rows -> rows.map { AppUsageTotal(it.packageName, it.usageMillis) } }

    /** Earliest day the local history covers, or `null` while it is still empty. */
    fun observeEarliestDay(): Flow<LocalDate?> =
        usageDao.observeEarliestDate().map { date -> date?.let(LocalDate::parse) }

    /**
     * Pulls everything the system still remembers into the local database.
     *
     * On a first run this imports the whole retention window, which is the last seven
     * days. Afterwards only days that can still be fully re-derived are touched: older
     * rows were written by earlier syncs and the system no longer has the events to
     * rebuild them.
     */
    suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        val zone = clock.zone
        val nowMs = clock.millis()
        val today = LocalDate.now(clock)

        val lastSyncedAtMs = syncStateDao.get()?.lastSyncedAtMs ?: 0L
        val earliestRebuildableDay = today.minusDays(IMPORT_WINDOW_DAYS)
        val lastSyncedDay = if (lastSyncedAtMs > 0L) {
            Instant.ofEpochMilli(lastSyncedAtMs).atZone(zone).toLocalDate()
        } else {
            earliestRebuildableDay
        }
        val firstDay = maxOf(lastSyncedDay, earliestRebuildableDay)

        val windowStartMs = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        // A session may have started before the window; look back so it is not lost.
        val queryStartMs = windowStartMs - SESSION_LOOKBACK_MS
        val events = eventSource.queryEvents(queryStartMs, nowMs)

        if (events.isEmpty()) {
            // Either nothing happened or access was revoked — do not wipe stored days.
            return@withContext SyncResult(firstDay, today, daysWritten = 0, eventCount = 0)
        }

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = windowStartMs,
            rangeEndMs = nowMs,
            nowMs = nowMs,
        )
        val rows = ForegroundSessionBuilder.toDailyUsage(intervals, zone)
            .map { usage ->
                AppUsageEntity(
                    packageName = usage.packageName,
                    date = usage.day.toString(),
                    usageMillis = usage.usageMillis,
                )
            }

        usageDao.replaceDays(firstDay.toString(), today.toString(), rows)
        usageDao.deleteBefore(today.minusDays(HISTORY_RETENTION_DAYS).toString())
        syncStateDao.put(SyncStateEntity(lastSyncedAtMs = nowMs))

        SyncResult(
            from = firstDay,
            to = today,
            daysWritten = rows.map { it.date }.distinct().size,
            eventCount = events.size,
        )
    }

    data class SyncResult(
        val from: LocalDate,
        val to: LocalDate,
        val daysWritten: Int,
        val eventCount: Int,
    )

    private companion object {
        /**
         * How far back the system keeps detailed events, conservatively — and so also
         * how much history the very first run can import.
         */
        const val IMPORT_WINDOW_DAYS = 7L

        /** How much local history to keep before pruning. */
        const val HISTORY_RETENTION_DAYS = 400L

        val SESSION_LOOKBACK_MS = TimeUnit.HOURS.toMillis(12)
    }
}
