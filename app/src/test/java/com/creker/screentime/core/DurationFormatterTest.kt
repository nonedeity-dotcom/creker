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
    fun `treats negative durations as zero`() {
        assertEquals("00:00:00", DurationFormatter.format(-5_000L))
    }
}
