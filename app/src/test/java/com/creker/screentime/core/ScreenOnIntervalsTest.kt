package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ScreenOnIntervalsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun event(dateTime: String, type: RawUsageEventType) =
        RawUsageEvent("android", at(dateTime), type)

    private fun build(events: List<RawUsageEvent>) = ForegroundSessionBuilder.buildScreenOnIntervals(
        events = events,
        rangeStartMs = at("2026-08-20T00:00:00"),
        rangeEndMs = at("2026-08-21T00:00:00"),
        nowMs = at("2026-08-20T23:00:00"),
    )

    @Test
    fun `screen on with no keyguard at all counts in full`() {
        val intervals = build(
            listOf(
                event("2026-08-20T10:00:00", RawUsageEventType.SCREEN_ON),
                event("2026-08-20T10:05:00", RawUsageEventType.SCREEN_OFF),
            ),
        )

        assertEquals(TimeUnit.MINUTES.toMillis(5), intervals.sumOf { it.durationMs })
    }

    @Test
    fun `a normal unlock cycle excludes the locked portion`() {
        val intervals = build(
            listOf(
                event("2026-08-20T10:00:00", RawUsageEventType.SCREEN_ON),
                event("2026-08-20T10:00:00", RawUsageEventType.KEYGUARD_SHOWN),
                event("2026-08-20T10:00:05", RawUsageEventType.KEYGUARD_HIDDEN),
                event("2026-08-20T10:10:00", RawUsageEventType.SCREEN_OFF),
            ),
        )

        // 10 minutes of screen-on minus the 5 seconds the lock screen was up.
        assertEquals(TimeUnit.MINUTES.toMillis(10) - TimeUnit.SECONDS.toMillis(5), intervals.sumOf { it.durationMs })
    }

    @Test
    fun `screen timing out while still locked closes the keyguard interval too`() {
        val intervals = build(
            listOf(
                event("2026-08-20T10:00:00", RawUsageEventType.SCREEN_ON),
                event("2026-08-20T10:00:00", RawUsageEventType.KEYGUARD_SHOWN),
                // No KEYGUARD_HIDDEN: the phone times out while still on the lock screen.
                event("2026-08-20T10:01:00", RawUsageEventType.SCREEN_OFF),
            ),
        )

        assertTrue(intervals.sumOf { it.durationMs } == 0L)
    }

    @Test
    fun `an unlock without a matching screen-on session is ignored`() {
        // The keyguard was never shown, so KEYGUARD_HIDDEN has nothing to subtract.
        val intervals = build(
            listOf(
                event("2026-08-20T10:00:00", RawUsageEventType.SCREEN_ON),
                event("2026-08-20T10:00:01", RawUsageEventType.KEYGUARD_HIDDEN),
                event("2026-08-20T10:05:00", RawUsageEventType.SCREEN_OFF),
            ),
        )

        assertEquals(TimeUnit.MINUTES.toMillis(5), intervals.sumOf { it.durationMs })
    }

    @Test
    fun `two unlock cycles in one screen-on session both get excluded`() {
        val intervals = build(
            listOf(
                event("2026-08-20T10:00:00", RawUsageEventType.SCREEN_ON),
                event("2026-08-20T10:00:00", RawUsageEventType.KEYGUARD_SHOWN),
                event("2026-08-20T10:00:03", RawUsageEventType.KEYGUARD_HIDDEN),
                // The user locks and unlocks again without the screen ever going off.
                event("2026-08-20T10:04:00", RawUsageEventType.KEYGUARD_SHOWN),
                event("2026-08-20T10:04:02", RawUsageEventType.KEYGUARD_HIDDEN),
                event("2026-08-20T10:05:00", RawUsageEventType.SCREEN_OFF),
            ),
        )

        val excluded = TimeUnit.SECONDS.toMillis(3) + TimeUnit.SECONDS.toMillis(2)
        assertEquals(TimeUnit.MINUTES.toMillis(5) - excluded, intervals.sumOf { it.durationMs })
    }

    @Test
    fun `an unfinished screen-on session ends at now, not the window end`() {
        val intervals = build(listOf(event("2026-08-20T22:50:00", RawUsageEventType.SCREEN_ON)))

        assertEquals(TimeUnit.MINUTES.toMillis(10), intervals.single().durationMs)
    }

    @Test
    fun `hourly launch counts only count foreground entries inside the window`() {
        val events = listOf(
            RawUsageEvent("com.chat", at("2026-08-20T10:00:00"), RawUsageEventType.FOREGROUND),
            RawUsageEvent("com.chat", at("2026-08-20T10:30:00"), RawUsageEventType.FOREGROUND),
            RawUsageEvent("com.chat", at("2026-08-20T11:00:00"), RawUsageEventType.FOREGROUND),
            RawUsageEvent("com.chat", at("2026-08-19T23:00:00"), RawUsageEventType.FOREGROUND),
            RawUsageEvent("com.chat", at("2026-08-20T10:15:00"), RawUsageEventType.BACKGROUND),
        )

        val hourly = ForegroundSessionBuilder.toHourlyLaunches(
            events = events,
            rangeStartMs = at("2026-08-20T00:00:00"),
            rangeEndMs = at("2026-08-21T00:00:00"),
            zone = zone,
        )

        assertEquals(2L, hourly[10].value)
        assertEquals(1L, hourly[11].value)
        assertEquals(0L, hourly[9].value)
    }
}
