package com.creker.screentime.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Turns the raw system event stream into foreground intervals and per-day totals.
 *
 * The system only reports transitions, so the duration of a session is the distance
 * between the event that opened it and the first event that closed it. A session can
 * be closed by another app coming to the foreground, by the app itself being paused,
 * by the screen turning off, or by the end of the requested window.
 */
object ForegroundSessionBuilder {

    /**
     * @param events events covering [rangeStartMs]..[rangeEndMs], ideally with some
     *   lookback so that a session started before the window is not lost.
     * @param nowMs current wall clock; a session that is still open cannot extend
     *   past it.
     * @return intervals clipped to the requested window, in chronological order.
     */
    fun buildIntervals(
        events: List<RawUsageEvent>,
        rangeStartMs: Long,
        rangeEndMs: Long,
        nowMs: Long,
    ): List<UsageInterval> {
        val intervals = mutableListOf<UsageInterval>()
        var openPackage: String? = null
        var openStartMs = 0L

        fun close(atMs: Long) {
            val pkg = openPackage ?: return
            if (atMs > openStartMs) {
                intervals += UsageInterval(pkg, openStartMs, atMs)
            }
            openPackage = null
        }

        for (event in events.sortedBy { it.timestampMs }) {
            when (event.type) {
                RawUsageEventType.FOREGROUND -> {
                    close(event.timestampMs)
                    openPackage = event.packageName
                    openStartMs = event.timestampMs
                }

                RawUsageEventType.BACKGROUND -> {
                    if (openPackage == event.packageName) close(event.timestampMs)
                }

                RawUsageEventType.SCREEN_OFF,
                RawUsageEventType.SHUTDOWN,
                -> close(event.timestampMs)
            }
        }
        close(minOf(rangeEndMs, nowMs))

        return intervals.mapNotNull { it.clipTo(rangeStartMs, rangeEndMs) }
    }

    /**
     * Splits intervals on local midnight and sums them per day and package, so that a
     * session running across midnight is credited to both days.
     */
    fun toDailyUsage(intervals: List<UsageInterval>, zone: ZoneId): List<DailyUsage> {
        val totals = mutableMapOf<Pair<LocalDate, String>, Long>()
        for (interval in intervals) {
            var cursor = interval.startMs
            while (cursor < interval.endMs) {
                val day = cursor.toLocalDate(zone)
                val nextDayStart = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                // Guards against a pathological zone rule producing a non-advancing cursor.
                val chunkEnd = minOf(maxOf(nextDayStart, cursor + 1), interval.endMs)
                val key = day to interval.packageName
                totals[key] = (totals[key] ?: 0L) + (chunkEnd - cursor)
                cursor = chunkEnd
            }
        }

        return totals.map { (key, millis) -> DailyUsage(key.first, key.second, millis) }
            .sortedWith(compareBy({ it.day }, { it.packageName }))
    }

    /**
     * Sums intervals into 24 hour-of-day buckets, for the single-day chart. Callers
     * are expected to have already clipped the intervals to one calendar day — the
     * day itself is not needed here, only the hour each millisecond falls into.
     */
    fun toHourlyUsage(intervals: List<UsageInterval>, zone: ZoneId): List<HourlyUsage> {
        val totals = LongArray(24)
        for (interval in intervals) {
            var cursor = interval.startMs
            while (cursor < interval.endMs) {
                val zoned = Instant.ofEpochMilli(cursor).atZone(zone)
                val nextHourStart = zoned.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val chunkEnd = minOf(maxOf(nextHourStart, cursor + 1), interval.endMs)
                totals[zoned.hour] += chunkEnd - cursor
                cursor = chunkEnd
            }
        }
        return totals.mapIndexed { hour, millis -> HourlyUsage(hour, millis) }
    }

    private fun UsageInterval.clipTo(startMs: Long, endMs: Long): UsageInterval? {
        val clippedStart = maxOf(this.startMs, startMs)
        val clippedEnd = minOf(this.endMs, endMs)
        return if (clippedEnd > clippedStart) copy(startMs = clippedStart, endMs = clippedEnd) else null
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
