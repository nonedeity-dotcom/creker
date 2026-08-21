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
                RawUsageEventType.KEYGUARD_SHOWN,
                RawUsageEventType.SHUTDOWN,
                -> close(event.timestampMs)

                RawUsageEventType.SCREEN_ON, RawUsageEventType.KEYGUARD_HIDDEN -> Unit
            }
        }
        close(minOf(rangeEndMs, nowMs))

        return intervals.mapNotNull { it.clipTo(rangeStartMs, rangeEndMs) }
    }

    /**
     * Splits intervals on local midnight and sums them per day and package, so that a
     * session running across midnight is credited to both days.
     */
    fun toDailyUsage(
        intervals: List<UsageInterval>,
        zone: ZoneId,
        launches: Map<Pair<LocalDate, String>, Int> = emptyMap(),
    ): List<DailyUsage> {
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

        val keys = totals.keys + launches.keys
        return keys.map { (day, pkg) ->
            DailyUsage(
                day = day,
                packageName = pkg,
                usageMillis = totals[day to pkg] ?: 0L,
                launchCount = launches[day to pkg] ?: 0,
            )
        }.sortedWith(compareBy({ it.day }, { it.packageName }))
    }

    /** Counts foreground entries per day and package — how many times each app was opened. */
    fun countLaunches(
        events: List<RawUsageEvent>,
        rangeStartMs: Long,
        rangeEndMs: Long,
        zone: ZoneId,
    ): Map<Pair<LocalDate, String>, Int> {
        val launches = mutableMapOf<Pair<LocalDate, String>, Int>()
        for (event in events) {
            if (event.type != RawUsageEventType.FOREGROUND) continue
            if (event.timestampMs < rangeStartMs || event.timestampMs >= rangeEndMs) continue
            val key = event.timestampMs.toLocalDate(zone) to event.packageName
            launches[key] = (launches[key] ?: 0) + 1
        }
        return launches
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

    /** Foreground entries per hour of a single day — the session/launch count for the chart. */
    fun toHourlyLaunches(events: List<RawUsageEvent>, rangeStartMs: Long, rangeEndMs: Long, zone: ZoneId): List<HourlyUsage> {
        val counts = LongArray(24)
        for (event in events) {
            if (event.type != RawUsageEventType.FOREGROUND) continue
            if (event.timestampMs < rangeStartMs || event.timestampMs >= rangeEndMs) continue
            counts[event.timestampMs.toLocalDateTime(zone).hour] += 1L
        }
        return counts.mapIndexed { hour, count -> HourlyUsage(hour, count) }
    }

    /**
     * Device-wide screen-on time, excluding whatever portion the lock screen itself
     * was showing. Built from two independent event pairs — SCREEN_ON/SCREEN_OFF and
     * KEYGUARD_SHOWN/KEYGUARD_HIDDEN — rather than one, because a screen can turn on
     * without ever showing a keyguard at all (no lock method set), in which case
     * there is nothing to subtract.
     */
    fun buildScreenOnIntervals(
        events: List<RawUsageEvent>,
        rangeStartMs: Long,
        rangeEndMs: Long,
        nowMs: Long,
    ): List<UsageInterval> {
        val screenOn = buildSingleStreamIntervals(
            events, RawUsageEventType.SCREEN_ON, RawUsageEventType.SCREEN_OFF, rangeStartMs, rangeEndMs, nowMs,
        )
        val keyguardShown = buildSingleStreamIntervals(
            events, RawUsageEventType.KEYGUARD_SHOWN, RawUsageEventType.KEYGUARD_HIDDEN, rangeStartMs, rangeEndMs, nowMs,
        )
        return subtractIntervals(screenOn, keyguardShown)
    }

    /**
     * Pairs a generic "opens" event type with a "closes" one into intervals. Unlike
     * [buildIntervals], this is for a single unkeyed stream — only one of it can be
     * open at a time, which is true of both screen-on and keyguard-shown.
     */
    private fun buildSingleStreamIntervals(
        events: List<RawUsageEvent>,
        opensOn: RawUsageEventType,
        closesOn: RawUsageEventType,
        rangeStartMs: Long,
        rangeEndMs: Long,
        nowMs: Long,
    ): List<UsageInterval> {
        val intervals = mutableListOf<UsageInterval>()
        var openStartMs: Long? = null
        for (event in events.sortedBy { it.timestampMs }) {
            when (event.type) {
                opensOn -> if (openStartMs == null) openStartMs = event.timestampMs
                closesOn -> {
                    val start = openStartMs ?: continue
                    if (event.timestampMs > start) intervals += UsageInterval(SINGLE_STREAM_LABEL, start, event.timestampMs)
                    openStartMs = null
                }
                else -> Unit
            }
        }
        openStartMs?.let { start ->
            val end = minOf(rangeEndMs, nowMs)
            if (end > start) intervals += UsageInterval(SINGLE_STREAM_LABEL, start, end)
        }
        return intervals.mapNotNull { it.clipTo(rangeStartMs, rangeEndMs) }
    }

    /** Removes every part of [subtrahends] from [base], keeping each remaining piece's own bounds. */
    private fun subtractIntervals(base: List<UsageInterval>, subtrahends: List<UsageInterval>): List<UsageInterval> {
        if (subtrahends.isEmpty()) return base
        val sorted = subtrahends.sortedBy { it.startMs }
        return base.flatMap { interval -> subtractFrom(interval, sorted) }
    }

    private fun subtractFrom(interval: UsageInterval, subtrahends: List<UsageInterval>): List<UsageInterval> {
        var remaining = listOf(interval)
        for (cut in subtrahends) {
            remaining = remaining.flatMap { piece ->
                val overlapStart = maxOf(piece.startMs, cut.startMs)
                val overlapEnd = minOf(piece.endMs, cut.endMs)
                if (overlapStart >= overlapEnd) {
                    listOf(piece)
                } else {
                    listOfNotNull(
                        piece.takeIf { piece.startMs < overlapStart }?.copy(endMs = overlapStart),
                        piece.takeIf { piece.endMs > overlapEnd }?.copy(startMs = overlapEnd),
                    )
                }
            }
        }
        return remaining
    }

    private fun UsageInterval.clipTo(startMs: Long, endMs: Long): UsageInterval? {
        val clippedStart = maxOf(this.startMs, startMs)
        val clippedEnd = minOf(this.endMs, endMs)
        return if (clippedEnd > clippedStart) copy(startMs = clippedStart, endMs = clippedEnd) else null
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private fun Long.toLocalDateTime(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone)

    private const val SINGLE_STREAM_LABEL = "__device__"
}
