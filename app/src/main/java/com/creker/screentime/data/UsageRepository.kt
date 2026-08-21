package com.creker.screentime.data

import com.creker.screentime.core.AppPeriodTotal
import com.creker.screentime.core.AppUsageTotal
import com.creker.screentime.core.DailyTotal
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.ForegroundSessionBuilder
import com.creker.screentime.core.HourlyUsage
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
     * Per-day totals for the multi-day chart (week / month / a longer custom range).
     * Days with no stored usage are zero-filled so the chart never silently skips one.
     */
    fun observeDailyTotals(range: DayRange): Flow<List<DailyTotal>> =
        usageDao.observeDailyTotals(range.from.toString(), range.to.toString()).map { rows ->
            val byDate = rows.associate { LocalDate.parse(it.date) to it.usageMillis }
            (0 until range.dayCount).map { offset ->
                val day = range.from.plusDays(offset.toLong())
                DailyTotal(day, byDate[day] ?: 0L)
            }
        }

    /**
     * Hour-by-hour breakdown of [day]'s foreground time, for the single-day chart.
     * Room only stores daily totals, so — unlike [observeDailyTotals] — this reads
     * straight from the system every time; that is fine since it is only ever called
     * for today, which is already read fresh on every [sync].
     */
    suspend fun hourlyBreakdown(day: LocalDate): List<HourlyUsage> = withContext(ioDispatcher) {
        val zone = clock.zone
        val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(clock.millis())
            .coerceAtLeast(startMs)

        val events = eventSource.queryEvents(startMs - SESSION_LOOKBACK_MS, endMs)
        val intervals = ForegroundSessionBuilder.buildIntervals(events, startMs, endMs, endMs)
        ForegroundSessionBuilder.toHourlyUsage(intervals, zone)
    }

    /**
     * One package's full per-day history — every locally retained day, not just a
     * period — for the current/longest streak and single-day-max stats on its detail
     * screen, which need to look further back than any one period covers.
     */
    fun observeAppHistory(packageName: String): Flow<List<DailyTotal>> =
        usageDao.observeAppHistory(packageName).map { rows ->
            rows.map { DailyTotal(LocalDate.parse(it.date), it.usageMillis) }
        }

    /** One package's usage and launch count summed over [range]. */
    fun observeAppPeriodTotal(packageName: String, range: DayRange): Flow<AppPeriodTotal> =
        usageDao.observeAppPeriodTotal(packageName, range.from.toString(), range.to.toString())
            .map { row -> AppPeriodTotal(usageMillis = row.usageMillis ?: 0L, launchCount = row.launchCount ?: 0) }

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
        val launches = ForegroundSessionBuilder.countLaunches(events, windowStartMs, nowMs, zone)
        val rows = ForegroundSessionBuilder.toDailyUsage(intervals, zone, launches)
            .map { usage ->
                AppUsageEntity(
                    packageName = usage.packageName,
                    date = usage.day.toString(),
                    usageMillis = usage.usageMillis,
                    launchCount = usage.launchCount,
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
