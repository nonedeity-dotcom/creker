package com.creker.screentime.core

import java.time.LocalDate

/**
 * A single event as reported by the system, reduced to what the aggregation cares
 * about. Keeping this Android-free makes the session logic unit-testable on the JVM.
 */
data class RawUsageEvent(
    val packageName: String,
    val timestampMs: Long,
    val type: RawUsageEventType,
)

enum class RawUsageEventType {
    /** The activity of [RawUsageEvent.packageName] came to the foreground. */
    FOREGROUND,

    /** The activity of [RawUsageEvent.packageName] left the foreground. */
    BACKGROUND,

    /**
     * The screen turned off or the keyguard appeared. Not every device pauses the
     * foreground activity in that case, so this has to end the session explicitly —
     * otherwise a phone left on a table would collect hours of "usage".
     */
    SCREEN_OFF,

    /** The device shut down; anything still open ended at that moment. */
    SHUTDOWN,
}

/** A continuous stretch of time during which [packageName] was in the foreground. */
data class UsageInterval(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

/** Foreground time and launch count of one package on one calendar day. */
data class DailyUsage(
    val day: LocalDate,
    val packageName: String,
    val usageMillis: Long,
    val launchCount: Int,
)

/** Total usage of one package over the selected period. */
data class AppUsageTotal(
    val packageName: String,
    val usageMillis: Long,
)

/** Total usage summed across every app for one calendar day — one point on the chart. */
data class DailyTotal(
    val day: LocalDate,
    val usageMillis: Long,
)

/** Total usage summed across every app for one hour of a single day, `hour` in 0..23. */
data class HourlyUsage(
    val hour: Int,
    val usageMillis: Long,
)

/** One package's usage and launch count summed over a date range. */
data class AppPeriodTotal(
    val usageMillis: Long,
    val launchCount: Int,
)
