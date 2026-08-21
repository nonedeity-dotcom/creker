package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UsageStreaksTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun used(day: LocalDate, millis: Long = 60_000L) = DailyTotal(day, millis)
    private fun unused(day: LocalDate) = DailyTotal(day, 0L)

    @Test
    fun `current streak counts back from today while every day is used`() {
        val history = listOf(
            used(today),
            used(today.minusDays(1)),
            used(today.minusDays(2)),
            unused(today.minusDays(3)),
        )

        assertEquals(3, UsageStreaks.currentStreak(history, today))
    }

    @Test
    fun `current streak is zero when today itself has no usage`() {
        val history = listOf(used(today.minusDays(1)), used(today.minusDays(2)))

        assertEquals(0, UsageStreaks.currentStreak(history, today))
    }

    @Test
    fun `current streak with no history at all is zero`() {
        assertEquals(0, UsageStreaks.currentStreak(emptyList(), today))
    }

    @Test
    fun `longest streak finds the best run even if it is not the most recent one`() {
        val history = listOf(
            used(today.minusDays(10)),
            used(today.minusDays(9)),
            used(today.minusDays(8)),
            used(today.minusDays(7)),
            unused(today.minusDays(6)),
            used(today.minusDays(1)),
            used(today),
        )

        assertEquals(4, UsageStreaks.longestStreak(history))
    }

    @Test
    fun `longest streak with no used days is zero`() {
        val history = listOf(unused(today), unused(today.minusDays(1)))

        assertEquals(0, UsageStreaks.longestStreak(history))
    }

    @Test
    fun `longest streak ignores the order rows arrive in`() {
        val history = listOf(used(today), used(today.minusDays(2)), used(today.minusDays(1)))

        assertEquals(3, UsageStreaks.longestStreak(history))
    }

    @Test
    fun `max day usage picks the single highest value`() {
        val history = listOf(used(today, 90_000L), used(today.minusDays(1), 500_000L), used(today.minusDays(2), 10_000L))

        assertEquals(500_000L, UsageStreaks.maxDayUsage(history))
    }

    @Test
    fun `max day usage with no history is zero`() {
        assertEquals(0L, UsageStreaks.maxDayUsage(emptyList()))
    }
}
