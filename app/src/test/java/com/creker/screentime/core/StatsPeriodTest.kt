package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsPeriodTest {

    private val today = LocalDate.of(2026, 8, 21)
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    @Test
    fun `day covers a single day`() {
        val range = StatsPeriod.Day.resolve(today)

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
    fun `yesterday is a single day, one before today`() {
        val range = StatsPeriod.Yesterday.resolve(today)

        assertEquals(DayRange(today.minusDays(1), today.minusDays(1)), range)
        assertEquals(1, range.dayCount)
    }

    @Test
    fun `a preset selection re-resolves against today`() {
        val selection: PeriodSelection = PeriodSelection.Preset(StatsPeriod.Day)

        assertEquals(DayRange(today, today), selection.resolve(today))
        assertEquals(DayRange(today.plusDays(1), today.plusDays(1)), selection.resolve(today.plusDays(1)))
    }

    @Test
    fun `a fixed selection ignores today and always returns its own range`() {
        val fixedRange = DayRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5))
        val selection: PeriodSelection = PeriodSelection.Fixed(fixedRange)

        assertEquals(fixedRange, selection.resolve(today))
        assertEquals(fixedRange, selection.resolve(today.plusDays(30)))
    }

    @Test
    fun `range boundaries span from midnight to the next midnight`() {
        val range = StatsPeriod.Day.resolve(today)

        val expectedStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val expectedEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(expectedStart, range.startMillis(zone))
        assertEquals(expectedEnd, range.endMillis(zone))
        assertEquals(24 * 60 * 60 * 1000L, range.endMillis(zone) - range.startMillis(zone))
    }

    @Test
    fun `shiftBy moves both ends of the range by the same number of days`() {
        val range = DayRange(LocalDate.of(2026, 8, 15), today)

        assertEquals(DayRange(LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 14)), range.shiftBy(-7))
        assertEquals(DayRange(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 28)), range.shiftBy(7))
    }
}
