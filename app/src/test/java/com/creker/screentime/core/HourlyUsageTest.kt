package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class HourlyUsageTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `an interval within one hour lands in a single bucket`() {
        val intervals = listOf(
            UsageInterval("com.chat", at("2026-08-20T10:05:00"), at("2026-08-20T10:15:00")),
        )

        val hourly = ForegroundSessionBuilder.toHourlyUsage(intervals, zone)

        assertEquals(24, hourly.size)
        assertEquals(TimeUnit.MINUTES.toMillis(10), hourly[10].value)
        assertEquals(0L, hourly[9].value)
        assertEquals(0L, hourly[11].value)
    }

    @Test
    fun `an interval crossing an hour boundary is split between both buckets`() {
        val intervals = listOf(
            UsageInterval("com.chat", at("2026-08-20T10:50:00"), at("2026-08-20T11:20:00")),
        )

        val hourly = ForegroundSessionBuilder.toHourlyUsage(intervals, zone)

        assertEquals(TimeUnit.MINUTES.toMillis(10), hourly[10].value)
        assertEquals(TimeUnit.MINUTES.toMillis(20), hourly[11].value)
    }

    @Test
    fun `an interval spanning several hours accounts for every millisecond`() {
        val start = at("2026-08-20T09:40:00")
        val end = at("2026-08-20T12:10:00")
        val intervals = listOf(UsageInterval("com.game", start, end))

        val hourly = ForegroundSessionBuilder.toHourlyUsage(intervals, zone)

        assertEquals(end - start, hourly.sumOf { it.value })
        assertEquals(TimeUnit.MINUTES.toMillis(20), hourly[9].value)
        assertEquals(TimeUnit.HOURS.toMillis(1), hourly[10].value)
        assertEquals(TimeUnit.HOURS.toMillis(1), hourly[11].value)
        assertEquals(TimeUnit.MINUTES.toMillis(10), hourly[12].value)
    }

    @Test
    fun `no intervals still returns 24 zeroed buckets in hour order`() {
        val hourly = ForegroundSessionBuilder.toHourlyUsage(emptyList(), zone)

        assertEquals(24, hourly.size)
        assertEquals((0..23).toList(), hourly.map { it.hour })
        assertEquals(0L, hourly.sumOf { it.value })
    }

    @Test
    fun `two intervals in the same hour accumulate`() {
        val intervals = listOf(
            UsageInterval("com.chat", at("2026-08-20T14:00:00"), at("2026-08-20T14:05:00")),
            UsageInterval("com.browser", at("2026-08-20T14:30:00"), at("2026-08-20T14:32:00")),
        )

        val hourly = ForegroundSessionBuilder.toHourlyUsage(intervals, zone)

        assertEquals(TimeUnit.MINUTES.toMillis(7), hourly[14].value)
    }
}
