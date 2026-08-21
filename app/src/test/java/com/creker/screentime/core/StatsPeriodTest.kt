package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsPeriodTest {

    private val today = LocalDate.of(2026, 8, 21)
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    @Test
    fun `today covers a single day`() {
        val range = StatsPeriod.Today.resolve(today)

        assertEquals(DayRange(today, today), range)
        assertEquals(1, range.dayCount)
    }

    @Test
    fun `week covers seven rolling days including today`() {
        val range = StatsPeriod.Week.resolve(today)

        assertEquals(LocalDate.of(2026, 8, 15), range.from)
        assertEquals(today, range.to)
        assertEquals(7, range.dayCount)
    }

    @Test
    fun `month covers thirty rolling days including today`() {
        val range = StatsPeriod.Month.resolve(today)

        assertEquals(LocalDate.of(2026, 7, 23), range.from)
        assertEquals(today, range.to)
        assertEquals(30, range.dayCount)
    }

    @Test
    fun `a custom range picked backwards is normalised`() {
        val range = StatsPeriod.Custom(
            from = LocalDate.of(2026, 8, 20),
            to = LocalDate.of(2026, 8, 10),
        ).resolve(today)

        assertEquals(LocalDate.of(2026, 8, 10), range.from)
        assertEquals(LocalDate.of(2026, 8, 20), range.to)
    }

    @Test
    fun `range boundaries span from midnight to the next midnight`() {
        val range = StatsPeriod.Today.resolve(today)

        val expectedStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val expectedEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(expectedStart, range.startMillis(zone))
        assertEquals(expectedEnd, range.endMillis(zone))
        assertEquals(24 * 60 * 60 * 1000L, range.endMillis(zone) - range.startMillis(zone))
    }
}
