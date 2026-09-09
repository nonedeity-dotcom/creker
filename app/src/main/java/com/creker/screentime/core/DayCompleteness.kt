package com.creker.screentime.core

import java.time.LocalDate
import java.time.ZoneId

/**
 * How far a stored day is actually measured.
 *
 * A daily total on its own cannot answer "was there really that little screen time, or did
 * nobody look?" — and creker only recomputes the running day when it is opened or when the
 * daily worker fires just after midnight, so a low number for today is usually the second
 * case. Every stored day therefore carries the moment its total is complete through, which is
 * what another app needs in order to refuse to act on a number nobody measured.
 *
 * Android-free and pure, like the rest of `core/`, so the rule is unit-testable on the JVM
 * rather than only observable on a device.
 */
object DayCompleteness {

    /** Stamp for a day whose completeness is not known — a row written before this existed. */
    const val UNKNOWN_MS: Long = 0L

    /**
     * Epoch millis through which [day]'s total is complete, as measured at [nowMs]: the end of
     * the day itself once it has passed, or [nowMs] while the day is still running.
     *
     * Deliberately not "when the row was written": for a finished day those differ by however
     * long the sync was late, and a reader checking whether a past day is complete would then
     * have to guess at that lateness.
     */
    fun measuredThroughMs(day: LocalDate, zone: ZoneId, nowMs: Long): Long =
        minOf(nowMs, endOfDayMs(day, zone))

    /**
     * Stamp for a day merged in from another device's CSV export. A day that had already ended
     * when the file was written is complete whoever measured it; the running day is not, and
     * the file carries no timestamp of its own, so it stays [UNKNOWN_MS] rather than claiming a
     * freshness this device cannot vouch for.
     */
    fun importedThroughMs(day: LocalDate, zone: ZoneId, nowMs: Long): Long {
        val endMs = endOfDayMs(day, zone)
        return if (endMs <= nowMs) endMs else UNKNOWN_MS
    }

    /** Exclusive end of [day] — local midnight that starts the next one. */
    private fun endOfDayMs(day: LocalDate, zone: ZoneId): Long =
        day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
