package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayCompletenessTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 3, 12)
    private val noonMs = today.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
    private val endOfTodayMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `the running day is only measured up to now`() {
        assertEquals(noonMs, DayCompleteness.measuredThroughMs(today, zone, noonMs))
    }

    @Test
    fun `a finished day is measured through its own end, not through now`() {
        val yesterday = today.minusDays(1)
        val endOfYesterdayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        // Syncing at noon says nothing new about yesterday: it was already complete at midnight.
        assertEquals(endOfYesterdayMs, DayCompleteness.measuredThroughMs(yesterday, zone, noonMs))
    }

    @Test
    fun `a day is complete exactly at its closing midnight`() {
        assertEquals(endOfTodayMs, DayCompleteness.measuredThroughMs(today, zone, endOfTodayMs))
    }

    @Test
    fun `an imported finished day counts as complete`() {
        val lastWeek = today.minusDays(7)
        val endOfLastWeekDayMs = lastWeek.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(endOfLastWeekDayMs, DayCompleteness.importedThroughMs(lastWeek, zone, noonMs))
    }

    @Test
    fun `an imported running day stays unknown`() {
        // The CSV has no timestamp of its own, so nothing here can vouch for today's number.
        assertEquals(DayCompleteness.UNKNOWN_MS, DayCompleteness.importedThroughMs(today, zone, noonMs))
    }

    @Test
    fun `the stamp follows the local day boundary across a DST change`() {
        // Europe/Lisbon springs forward at 01:00 on 2026-03-29: that day is 23 hours long, and
        // its end is still the local midnight that starts the 30th.
        val lisbon = ZoneId.of("Europe/Lisbon")
        val dstDay = LocalDate.of(2026, 3, 29)
        val nextMidnightMs = LocalDate.of(2026, 3, 30).atStartOfDay(lisbon).toInstant().toEpochMilli()
        val laterMs = nextMidnightMs + 60_000L
        assertEquals(nextMidnightMs, DayCompleteness.measuredThroughMs(dstDay, lisbon, laterMs))
    }
}
