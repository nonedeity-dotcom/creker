package com.creker.screentime.core

import java.time.LocalDate

/**
 * Streak and peak statistics derived from a package's day-by-day history. A "used" day
 * is simply one with non-zero usage — there is no minimum threshold.
 */
object UsageStreaks {

    /** Consecutive used days counting back from [today]; 0 if today itself is unused. */
    fun currentStreak(history: List<DailyTotal>, today: LocalDate): Int {
        val usedDays = history.filter { it.value > 0L }.mapTo(HashSet()) { it.day }
        var streak = 0
        var day = today
        while (day in usedDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** The longest run of consecutive used days anywhere in the history. */
    fun longestStreak(history: List<DailyTotal>): Int {
        val usedDays = history.filter { it.value > 0L }.map { it.day }.sorted()
        if (usedDays.isEmpty()) return 0

        var longest = 1
        var current = 1
        for (i in 1 until usedDays.size) {
            current = if (usedDays[i] == usedDays[i - 1].plusDays(1)) current + 1 else 1
            longest = maxOf(longest, current)
        }
        return longest
    }

    /** The single highest usage recorded on any one day. */
    fun maxDayUsage(history: List<DailyTotal>): Long = history.maxOfOrNull { it.value } ?: 0L
}
