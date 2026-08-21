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

    /** The screen turned on. Opens a device-wide "screen on" interval. */
    SCREEN_ON,

    /**
     * The screen turned off. Closes any open app session (not every device pauses
     * the foreground activity on its own — a phone left on a table would otherwise
     * collect hours of "usage") and the device-wide "screen on" interval.
     */
    SCREEN_OFF,

    /**
     * The lock screen appeared over whatever was running. Also closes any open app
     * session, and opens a "keyguard shown" interval that gets subtracted from
     * screen-on time, since a locked screen is not time actually spent using the
     * phone.
     */
    KEYGUARD_SHOWN,

    /** The lock screen was dismissed (device unlocked). Closes a "keyguard shown" interval. */
    KEYGUARD_HIDDEN,

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

/**
 * One point on a chart: a calendar day's worth of whatever metric is currently
 * selected — usage time, session count, or screen time — never more than one of
 * those at once, hence the metric-neutral field name.
 */
data class DailyTotal(
    val day: LocalDate,
    val value: Long,
)

/** One hour (0..23) of a single day's worth of whatever chart metric is selected. */
data class HourlyUsage(
    val hour: Int,
    val value: Long,
)

/** One package's usage and launch count summed over a date range. */
data class AppPeriodTotal(
    val usageMillis: Long,
    val launchCount: Int,
)

/** Which quantity a chart or detail screen is currently showing. */
enum class ChartMetric {
    /** Time each app (or all apps) spent in the foreground. */
    USAGE,

    /** How many times each app (or all apps) was opened. */
    SESSIONS,

    /** Device-wide screen-on time, excluding whatever the lock screen itself showed. */
    SCREEN_TIME,
}
