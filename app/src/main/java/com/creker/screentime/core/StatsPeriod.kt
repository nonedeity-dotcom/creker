package com.creker.screentime.core

import java.time.LocalDate
import java.time.ZoneId

/** Inclusive range of calendar days the statistics are shown for. */
data class DayRange(val from: LocalDate, val to: LocalDate) {

    val dayCount: Int get() = (to.toEpochDay() - from.toEpochDay()).toInt() + 1

    fun startMillis(zone: ZoneId): Long = from.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive end: midnight of the day after [to]. */
    fun endMillis(zone: ZoneId): Long = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Shifts both ends of the range by the same number of days — steps through history a window at a time. */
fun DayRange.shiftBy(days: Int): DayRange = DayRange(from.plusDays(days.toLong()), to.plusDays(days.toLong()))

/** A named, auto-rolling period — resolves relative to "today" every time it is shown. */
sealed interface StatsPeriod {

    data object Day : StatsPeriod
    data object Yesterday : StatsPeriod

    /** Rolling seven days, today included. */
    data object Week : StatsPeriod

    /** Rolling thirty days, today included. */
    data object Month : StatsPeriod

    fun resolve(today: LocalDate): DayRange = when (this) {
        Day -> DayRange(today, today)
        Yesterday -> DayRange(today.minusDays(1), today.minusDays(1))
        Week -> DayRange(today.minusDays(WEEK_DAYS - 1L), today)
        Month -> DayRange(today.minusDays(MONTH_DAYS - 1L), today)
    }

    companion object {
        const val WEEK_DAYS = 7
        const val MONTH_DAYS = 30
    }
}

/**
 * What a screen's period picker is currently showing: either a named preset that
 * re-resolves against "today" every time (so it keeps up if the app is left open
 * across midnight), or a specific range — from stepping forward/back, or a custom
 * pick — that stays exactly where it was put.
 */
sealed interface PeriodSelection {
    data class Preset(val period: StatsPeriod) : PeriodSelection
    data class Fixed(val range: DayRange) : PeriodSelection
}

fun PeriodSelection.resolve(today: LocalDate): DayRange = when (this) {
    is PeriodSelection.Preset -> period.resolve(today)
    is PeriodSelection.Fixed -> range
}
