package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DurationFormatterTest {

    private val labels = DurationLabels(hours = "ч", minutes = "мин", seconds = "сек")

    @Test
    fun `formats hours minutes and seconds`() {
        val duration = TimeUnit.HOURS.toMillis(2) +
            TimeUnit.MINUTES.toMillis(15) +
            TimeUnit.SECONDS.toMillis(40)

        assertEquals("2ч 15мин 40сек", DurationFormatter.formatHms(duration, labels))
    }

    @Test
    fun `drops the hours when there are none`() {
        val duration = TimeUnit.MINUTES.toMillis(15) + TimeUnit.SECONDS.toMillis(40)

        assertEquals("15мин 40сек", DurationFormatter.formatHms(duration, labels))
    }

    @Test
    fun `keeps seconds only for very short usage`() {
        assertEquals("40сек", DurationFormatter.formatHms(TimeUnit.SECONDS.toMillis(40), labels))
    }

    @Test
    fun `keeps zero minutes visible when hours are present`() {
        val duration = TimeUnit.HOURS.toMillis(3) + TimeUnit.SECONDS.toMillis(5)

        assertEquals("3ч 0мин 5сек", DurationFormatter.formatHms(duration, labels))
    }

    @Test
    fun `truncates sub-second remainders instead of rounding up`() {
        assertEquals("1сек", DurationFormatter.formatHms(1_999L, labels))
    }

    @Test
    fun `treats negative durations as zero`() {
        assertEquals("0сек", DurationFormatter.formatHms(-5_000L, labels))
    }

    @Test
    fun `formats long durations without capping the hours`() {
        assertEquals("30ч 0мин 0сек", DurationFormatter.formatHms(TimeUnit.HOURS.toMillis(30), labels))
    }
}
