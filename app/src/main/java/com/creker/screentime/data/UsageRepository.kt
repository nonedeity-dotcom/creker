package com.creker.screentime.data

import com.creker.screentime.core.AppPeriodTotal
import com.creker.screentime.core.AppUsageTotal
import com.creker.screentime.core.DailyTotal
import com.creker.screentime.core.DayCompleteness
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.ForegroundSessionBuilder
import com.creker.screentime.core.HourlyUsage
import com.creker.screentime.core.UsageExportRow
import com.creker.screentime.data.local.AppUsageEntity
import com.creker.screentime.data.local.DateTotalRow
import com.creker.screentime.data.local.DeviceUsageDao
import com.creker.screentime.data.local.DeviceUsageEntity
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
class UsageRepository internal constructor(
    private val usageDao: UsageDao,
    private val deviceUsageDao: DeviceUsageDao,
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

    /** Every app's usage summed across the period, for the overview chart in usage mode. */
    fun observeDailyTotals(range: DayRange): Flow<List<DailyTotal>> =
        usageDao.observeDailyTotals(range.from.toString(), range.to.toString()).zeroFilled(range)

    /** Every app's launches summed across the period, for the overview chart in sessions mode. */
    fun observeSessionDailyTotals(range: DayRange): Flow<List<DailyTotal>> =
        usageDao.observeSessionDailyTotals(range.from.toString(), range.to.toString()).zeroFilled(range)

    /** Device-wide screen-on time, for the overview or app-detail chart in screen-time mode. */
    fun observeDeviceDailyTotals(range: DayRange): Flow<List<DailyTotal>> =
        deviceUsageDao.observeDailyTotals(range.from.toString(), range.to.toString()).zeroFilled(range)

    /** One package's own per-day usage, for its detail chart in usage mode. */
    fun observeAppDailyUsage(packageName: String, range: DayRange): Flow<List<DailyTotal>> =
        usageDao.observeAppDailyUsage(packageName, range.from.toString(), range.to.toString()).zeroFilled(range)

    /** One package's own per-day session count, for its detail chart in sessions mode. */
    fun observeAppDailySessions(packageName: String, range: DayRange): Flow<List<DailyTotal>> =
        usageDao.observeAppDailySessions(packageName, range.from.toString(), range.to.toString()).zeroFilled(range)

    /**
     * Hour-by-hour breakdown of [day]'s foreground time across every app, for the
     * overview chart's single-day usage view. Room only stores daily totals, so —
     * unlike the observe* methods above — this reads straight from the system every
     * time; that is fine since it is only ever called for today, which is already
     * read fresh on every [sync].
     */
    suspend fun hourlyBreakdown(day: LocalDate): List<HourlyUsage> = withContext(ioDispatcher) {
        val (zone, startMs, endMs) = dayWindow(day)
        val events = eventSource.queryEvents(startMs - SESSION_LOOKBACK_MS, endMs)
        val intervals = ForegroundSessionBuilder.buildIntervals(events, startMs, endMs, endMs)
        ForegroundSessionBuilder.toHourlyUsage(intervals, zone)
    }

    /** Same as [hourlyBreakdown] but restricted to one package, for its detail chart. */
    suspend fun hourlyBreakdownForApp(day: LocalDate, packageName: String): List<HourlyUsage> = withContext(ioDispatcher) {
        val (zone, startMs, endMs) = dayWindow(day)
        val events = eventSource.queryEvents(startMs - SESSION_LOOKBACK_MS, endMs)
        val intervals = ForegroundSessionBuilder.buildIntervals(events, startMs, endMs, endMs)
            .filter { it.packageName == packageName }
        ForegroundSessionBuilder.toHourlyUsage(intervals, zone)
    }

    /** Hour-by-hour launch counts across every app, for the overview chart's sessions view. */
    suspend fun hourlySessions(day: LocalDate): List<HourlyUsage> = withContext(ioDispatcher) {
        val (zone, startMs, endMs) = dayWindow(day)
        val events = eventSource.queryEvents(startMs, endMs)
        ForegroundSessionBuilder.toHourlyLaunches(events, startMs, endMs, zone)
    }

    /** Same as [hourlySessions] but restricted to one package, for its detail chart. */
    suspend fun hourlySessionsForApp(day: LocalDate, packageName: String): List<HourlyUsage> = withContext(ioDispatcher) {
        val (zone, startMs, endMs) = dayWindow(day)
        val events = eventSource.queryEvents(startMs, endMs).filter { it.packageName == packageName }
        ForegroundSessionBuilder.toHourlyLaunches(events, startMs, endMs, zone)
    }

    /**
     * Device-wide screen-on time by hour, excluding the lock screen — shared by both
     * the overview chart and every app-detail chart's screen-time view, since it is
     * not tied to any one package.
     */
    suspend fun hourlyScreenTime(day: LocalDate): List<HourlyUsage> = withContext(ioDispatcher) {
        val (zone, startMs, endMs) = dayWindow(day)
        val events = eventSource.queryEvents(startMs - SESSION_LOOKBACK_MS, endMs)
        val intervals = ForegroundSessionBuilder.buildScreenOnIntervals(events, startMs, endMs, endMs)
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

    /** Every stored row, flattened for the "save all data" export. */
    suspend fun exportRows(): List<UsageExportRow> = withContext(ioDispatcher) {
        val appRows = usageDao.getAllRows().map {
            UsageExportRow(kind = "app", packageName = it.packageName, date = it.date, valueMillis = it.usageMillis, launchCount = it.launchCount)
        }
        val deviceRows = deviceUsageDao.getAllRows().map {
            UsageExportRow(kind = "screen", packageName = "", date = it.date, valueMillis = it.screenMillis, launchCount = 0)
        }
        (appRows + deviceRows).sortedBy { it.date }
    }

    /**
     * Merges rows from a CSV [exportRows] previously produced elsewhere -- the "load
     * data" flow for moving to a new phone. Only fills in (package, date) or (date)
     * combinations this device has no row for yet; anything already recorded here
     * (this device's own, freshly synced history) is left untouched. Returns how many
     * rows were actually written, which may be fewer than [rows].size if some overlap
     * with what is already stored.
     */
    suspend fun importRows(rows: List<UsageExportRow>): Int = withContext(ioDispatcher) {
        val appEntities = rows.filter { it.kind == "app" }.map {
            AppUsageEntity(packageName = it.packageName, date = it.date, usageMillis = it.valueMillis, launchCount = it.launchCount)
        }
        val nowMs = clock.millis()
        val deviceEntities = rows.filter { it.kind == "screen" }.map {
            DeviceUsageEntity(
                date = it.date,
                screenMillis = it.valueMillis,
                // A day that had already ended when the file was written is complete, whichever
                // device measured it. Today's row is a different matter: the export could be
                // hours old and nothing in the CSV says when it was taken, so it stays "unknown"
                // rather than claiming a freshness this device cannot vouch for.
                updatedAtMs = importedThroughMs(it.date, nowMs),
            )
        }
        val appIds = if (appEntities.isNotEmpty()) usageDao.importAll(appEntities) else emptyList()
        val deviceIds = if (deviceEntities.isNotEmpty()) deviceUsageDao.importAll(deviceEntities) else emptyList()
        appIds.count { it != -1L } + deviceIds.count { it != -1L }
    }

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

        val screenOnIntervals = ForegroundSessionBuilder.buildScreenOnIntervals(
            events = events,
            rangeStartMs = windowStartMs,
            rangeEndMs = nowMs,
            nowMs = nowMs,
        )
        val screenRows = ForegroundSessionBuilder.toDailyUsage(screenOnIntervals, zone)
            .map { usage ->
                DeviceUsageEntity(
                    date = usage.day.toString(),
                    screenMillis = usage.usageMillis,
                    updatedAtMs = DayCompleteness.measuredThroughMs(usage.day, zone, nowMs),
                )
            }
        deviceUsageDao.replaceDays(firstDay.toString(), today.toString(), screenRows)
        deviceUsageDao.deleteBefore(today.minusDays(HISTORY_RETENTION_DAYS).toString())

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

    /**
     * [DayCompleteness.importedThroughMs] for a date string off a CSV row. The reader has already
     * dropped anything unparseable, so a bad date here can only mean the row was hand-edited —
     * treat it as a day of unknown completeness rather than dropping the usage it carries.
     */
    private fun importedThroughMs(date: String, nowMs: Long): Long {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return DayCompleteness.UNKNOWN_MS
        return DayCompleteness.importedThroughMs(day, clock.zone, nowMs)
    }

    /** [zone], the start of [day] in it, and the end — clamped to now if `day` is today. */
    private fun dayWindow(day: LocalDate): Triple<java.time.ZoneId, Long, Long> {
        val zone = clock.zone
        val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(clock.millis())
            .coerceAtLeast(startMs)
        return Triple(zone, startMs, endMs)
    }

    /** Zero-fills any day in [range] without a matching row, so a chart never silently skips one. */
    private fun Flow<List<DateTotalRow>>.zeroFilled(range: DayRange): Flow<List<DailyTotal>> = map { rows ->
        val byDate = rows.associate { LocalDate.parse(it.date) to it.usageMillis }
        (0 until range.dayCount).map { offset ->
            val day = range.from.plusDays(offset.toLong())
            DailyTotal(day, byDate[day] ?: 0L)
        }
    }

    companion object {
        /**
         * How far back the system keeps detailed events, conservatively — and so also
         * how much history the very first run can import. Also how far back a chart
         * can ask for an hourly (rather than daily-total) breakdown of a single day,
         * since that reads this same live event log directly.
         */
        internal const val IMPORT_WINDOW_DAYS = 7L

        /** How much local history to keep before pruning. */
        private const val HISTORY_RETENTION_DAYS = 400L

        private val SESSION_LOOKBACK_MS = TimeUnit.HOURS.toMillis(12)
    }
}
