package com.creker.screentime.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DailyTotal
import com.creker.screentime.core.DurationFormatter
import com.creker.screentime.core.DurationUnits
import com.creker.screentime.core.HourlyUsage
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One point on a chart: an hour of a single day, or a day of a longer period. */
data class ChartPoint(
    /** Short axis label, e.g. "14" or "Пн". Hours are unpadded: 0, 1 … 23. */
    val label: String,
    /** Fuller label for the tap tooltip, e.g. "14:00–15:00" or a full date. */
    val detailLabel: String,
    val value: Long,
)

private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

/** Maps hourly buckets (always exactly 24, hour 0..23) onto chart points. */
fun List<HourlyUsage>.toHourlyChartPoints(): List<ChartPoint> =
    map {
        val nextHour = (it.hour + 1) % 24
        ChartPoint(
            label = it.hour.toString(),
            detailLabel = "%02d:00–%02d:00".format(it.hour, nextHour),
            value = it.value,
        )
    }

/**
 * Maps daily totals onto chart points. [useWeekdayLabels] picks short weekday names
 * ("Пн", "Вт"...) when there are few enough points to show one per day; otherwise a
 * "d MMM" date is used, since it stays informative even when only a handful of axis
 * labels get shown.
 */
fun List<DailyTotal>.toDailyChartPoints(useWeekdayLabels: Boolean): List<ChartPoint> =
    map { daily ->
        val label = if (useWeekdayLabels) {
            daily.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).replaceFirstChar { it.uppercase() }
        } else {
            daily.day.format(MONTH_DAY_FORMATTER)
        }
        ChartPoint(label = label, detailLabel = daily.day.format(FULL_DATE_FORMATTER), value = daily.value)
    }

/** Formats a chart/headline value the way [this] metric calls for — a duration, or a plain count. */
fun ChartMetric.formatValue(value: Long): String = when (this) {
    ChartMetric.USAGE, ChartMetric.SCREEN_TIME -> DurationFormatter.format(value)
    ChartMetric.SESSIONS -> value.toString()
}

/**
 * The short form used to label a bar, where `01:34:36` would never fit: `1ч 34м`,
 * `41м`, or a plain count for sessions.
 */
fun ChartMetric.formatCompact(value: Long, units: DurationUnits): String = when (this) {
    ChartMetric.USAGE, ChartMetric.SCREEN_TIME -> DurationFormatter.formatCompact(value, units)
    ChartMetric.SESSIONS -> value.toString()
}

/** The headline label shown above a chart's total, for whichever metric is selected. */
val ChartMetric.headlineLabelRes: Int
    get() = when (this) {
        ChartMetric.USAGE -> R.string.total_screen_time
        ChartMetric.SESSIONS -> R.string.total_sessions_label
        ChartMetric.SCREEN_TIME -> R.string.total_device_screen_time_label
    }

/** The short chip label for the metric picker. */
val ChartMetric.chipLabelRes: Int
    get() = when (this) {
        ChartMetric.USAGE -> R.string.metric_usage
        ChartMetric.SESSIONS -> R.string.metric_sessions
        ChartMetric.SCREEN_TIME -> R.string.metric_screen_time
    }

/** The unit suffixes for short durations, read once and reused for every label. */
@Composable
fun rememberDurationUnits(): DurationUnits {
    val hours = stringResource(R.string.unit_hours)
    val minutes = stringResource(R.string.unit_minutes)
    val seconds = stringResource(R.string.unit_seconds)
    return remember(hours, minutes, seconds) { DurationUnits(hours, minutes, seconds) }
}
