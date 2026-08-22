package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DurationFormatterTest {

    @Test
    fun `formats as hh mm ss`() {
        val duration = TimeUnit.HOURS.toMillis(2) +
            TimeUnit.MINUTES.toMillis(15) +
            TimeUnit.SECONDS.toMillis(40)

        assertEquals("02:15:40", DurationFormatter.format(duration))
    }

    @Test
    fun `pads every component to two digits`() {
        val duration = TimeUnit.HOURS.toMillis(1) +
            TimeUnit.MINUTES.toMillis(2) +
            TimeUnit.SECONDS.toMillis(3)

        assertEquals("01:02:03", DurationFormatter.format(duration))
    }

    @Test
    fun `keeps zeroed components visible`() {
        assertEquals("00:00:40", DurationFormatter.format(TimeUnit.SECONDS.toMillis(40)))
        assertEquals("00:15:00", DurationFormatter.format(TimeUnit.MINUTES.toMillis(15)))
    }

    @Test
    fun `does not wrap hours at a day`() {
        val duration = TimeUnit.HOURS.toMillis(127) +
            TimeUnit.MINUTES.toMillis(45) +
            TimeUnit.SECONDS.toMillis(10)

        assertEquals("127:45:10", DurationFormatter.format(duration))
    }

    @Test
    fun `truncates sub-second remainders instead of rounding up`() {
        assertEquals("00:00:01", DurationFormatter.format(1_999L))
    }

    @Test
    fun `spells out units, dropping leading zero ones`() {
        val u = DurationUnits("ч", "м", "с")
        val full = TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(34) + TimeUnit.SECONDS.toMillis(36)

        assertEquals("1ч 34м 36с", DurationFormatter.formatWithUnits(full, u))
        assertEquals("34м 36с", DurationFormatter.formatWithUnits(full - TimeUnit.HOURS.toMillis(1), u))
        assertEquals("36с", DurationFormatter.formatWithUnits(TimeUnit.SECONDS.toMillis(36), u))
    }

    @Test
    fun `compact form drops seconds once there are minutes`() {
        val u = DurationUnits("ч", "м", "с")

        assertEquals(
            "2ч 15м",
            DurationFormatter.formatCompact(
                TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(15) + TimeUnit.SECONDS.toMillis(40), u,
            ),
        )
        assertEquals("41м", DurationFormatter.formatCompact(TimeUnit.MINUTES.toMillis(41) + 500, u))
        assertEquals("48с", DurationFormatter.formatCompact(TimeUnit.SECONDS.toMillis(48), u))
        assertEquals("0с", DurationFormatter.formatCompact(0L, u))
    }

    @Test
    fun `treats negative durations as zero`() {
        assertEquals("00:00:00", DurationFormatter.format(-5_000L))
    }
}
