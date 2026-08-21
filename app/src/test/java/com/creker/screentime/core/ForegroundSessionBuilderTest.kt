package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ForegroundSessionBuilderTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun event(pkg: String, dateTime: String, type: RawUsageEventType) =
        RawUsageEvent(pkg, at(dateTime), type)

    @Test
    fun `pairs foreground and background events into an interval`() {
        val events = listOf(
            event("com.chat", "2026-08-20T10:00:00", RawUsageEventType.FOREGROUND),
            event("com.chat", "2026-08-20T10:05:30", RawUsageEventType.BACKGROUND),
        )

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            nowMs = at("2026-08-20T23:00:00"),
        )

        assertEquals(1, intervals.size)
        assertEquals("com.chat", intervals[0].packageName)
        assertEquals(TimeUnit.MINUTES.toMillis(5) + TimeUnit.SECONDS.toMillis(30), intervals[0].durationMs)
    }

    @Test
    fun `switching apps closes the previous session`() {
        val events = listOf(
            event("com.chat", "2026-08-20T10:00:00", RawUsageEventType.FOREGROUND),
            event("com.browser", "2026-08-20T10:10:00", RawUsageEventType.FOREGROUND),
            event("com.browser", "2026-08-20T10:12:00", RawUsageEventType.BACKGROUND),
        )

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            nowMs = at("2026-08-20T23:00:00"),
        )

        assertEquals(2, intervals.size)
        assertEquals(TimeUnit.MINUTES.toMillis(10), intervals[0].durationMs)
        assertEquals(TimeUnit.MINUTES.toMillis(2), intervals[1].durationMs)
    }

    @Test
    fun `screen off ends a session that was never paused`() {
        val events = listOf(
            event("com.reader", "2026-08-20T10:00:00", RawUsageEventType.FOREGROUND),
            event("android", "2026-08-20T10:03:00", RawUsageEventType.SCREEN_OFF),
        )

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            nowMs = at("2026-08-20T23:00:00"),
        )

        assertEquals(1, intervals.size)
        assertEquals(TimeUnit.MINUTES.toMillis(3), intervals[0].durationMs)
        assertEquals("com.reader", intervals[0].packageName)
    }

    @Test
    fun `an unfinished session ends at the current moment, not at the window end`() {
        val events = listOf(
            event("com.game", "2026-08-20T22:50:00", RawUsageEventType.FOREGROUND),
        )

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            nowMs = at("2026-08-20T23:00:00"),
        )

        assertEquals(TimeUnit.MINUTES.toMillis(10), intervals.single().durationMs)
    }

    @Test
    fun `a session started before the window is clipped to it`() {
        val events = listOf(
            event("com.game", "2026-08-19T23:40:00", RawUsageEventType.FOREGROUND),
            event("com.game", "2026-08-20T00:20:00", RawUsageEventType.BACKGROUND),
        )

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            nowMs = at("2026-08-20T23:00:00"),
        )

        assertEquals(TimeUnit.MINUTES.toMillis(20), intervals.single().durationMs)
    }

    @Test
    fun `usage crossing midnight is credited to both days`() {
        val intervals = listOf(
            UsageInterval("com.game", at("2026-08-19T23:40:00"), at("2026-08-20T00:20:00")),
        )

        val daily = ForegroundSessionBuilder.toDailyUsage(intervals, zone)

        assertEquals(2, daily.size)
        assertEquals(LocalDate.of(2026, 8, 19), daily[0].day)
        assertEquals(TimeUnit.MINUTES.toMillis(20), daily[0].foregroundTimeMs)
        assertEquals(LocalDate.of(2026, 8, 20), daily[1].day)
        assertEquals(TimeUnit.MINUTES.toMillis(20), daily[1].foregroundTimeMs)
    }

    @Test
    fun `daily totals sum every session of the same app`() {
        val intervals = listOf(
            UsageInterval("com.chat", at("2026-08-20T10:00:00"), at("2026-08-20T10:05:00")),
            UsageInterval("com.chat", at("2026-08-20T14:00:00"), at("2026-08-20T14:02:30")),
            UsageInterval("com.browser", at("2026-08-20T15:00:00"), at("2026-08-20T15:01:00")),
        )

        val daily = ForegroundSessionBuilder.toDailyUsage(intervals, zone)

        val chat = daily.single { it.packageName == "com.chat" }
        assertEquals(TimeUnit.MINUTES.toMillis(7) + TimeUnit.SECONDS.toMillis(30), chat.foregroundTimeMs)
        assertEquals(LocalDate.of(2026, 8, 20), chat.day)
    }

    @Test
    fun `launches are counted per day and package`() {
        val events = listOf(
            event("com.chat", "2026-08-20T10:00:00", RawUsageEventType.FOREGROUND),
            event("com.chat", "2026-08-20T11:00:00", RawUsageEventType.FOREGROUND),
            event("com.chat", "2026-08-20T11:30:00", RawUsageEventType.BACKGROUND),
        )

        val launches = ForegroundSessionBuilder.countLaunches(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            zone = zone,
        )

        assertEquals(2, launches[LocalDate.of(2026, 8, 20) to "com.chat"])
    }

    @Test
    fun `events outside the window do not count as launches`() {
        val events = listOf(
            event("com.chat", "2026-08-19T23:00:00", RawUsageEventType.FOREGROUND),
        )

        val launches = ForegroundSessionBuilder.countLaunches(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            zone = zone,
        )

        assertTrue(launches.isEmpty())
    }

    @Test
    fun `unordered events are still paired correctly`() {
        val events = listOf(
            event("com.chat", "2026-08-20T10:05:00", RawUsageEventType.BACKGROUND),
            event("com.chat", "2026-08-20T10:00:00", RawUsageEventType.FOREGROUND),
        )

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            nowMs = at("2026-08-20T23:00:00"),
        )

        assertEquals(TimeUnit.MINUTES.toMillis(5), intervals.single().durationMs)
    }

    @Test
    fun `a background event for another app does not close the current session`() {
        val events = listOf(
            event("com.chat", "2026-08-20T10:00:00", RawUsageEventType.FOREGROUND),
            event("com.other", "2026-08-20T10:01:00", RawUsageEventType.BACKGROUND),
            event("com.chat", "2026-08-20T10:04:00", RawUsageEventType.BACKGROUND),
        )

        val intervals = ForegroundSessionBuilder.buildIntervals(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            nowMs = at("2026-08-20T23:00:00"),
        )

        assertEquals(TimeUnit.MINUTES.toMillis(4), intervals.single().durationMs)
    }
}
