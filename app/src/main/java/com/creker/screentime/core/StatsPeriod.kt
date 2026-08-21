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

/** The period selector at the top of the screen. */
sealed interface StatsPeriod {

    /** The current day. */
    data object Day : StatsPeriod

    /** Rolling seven days, today included. */
    data object Week : StatsPeriod

    /** Rolling thirty days, today included. */
    data object Month : StatsPeriod

    data class Custom(val from: LocalDate, val to: LocalDate) : StatsPeriod

    fun resolve(today: LocalDate): DayRange = when (this) {
        Day -> DayRange(today, today)
        Week -> DayRange(today.minusDays(WEEK_DAYS - 1L), today)
        Month -> DayRange(today.minusDays(MONTH_DAYS - 1L), today)
        // A user can pick the dates in any order; normalise instead of showing nothing.
        is Custom -> if (from.isAfter(to)) DayRange(to, from) else DayRange(from, to)
    }

    companion object {
        const val WEEK_DAYS = 7
        const val MONTH_DAYS = 30
    }
}
