package com.creker.screentime.ui.chart

import com.creker.screentime.core.HourlyUsage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where today's chart stops.
 *
 * A day chart drawn all the way to midnight is mostly a picture of hours that have not
 * happened yet — at nine in the morning, five sixths of it. Empty space to the right of the
 * last bar says "nothing was used then", which is a different claim from "then hasn't
 * arrived". A finished day keeps all 24.
 */
class HourlyChartPointsTest {

    private fun buckets(): List<HourlyUsage> = (0..23).map { HourlyUsage(hour = it, value = it * 1_000L) }

    @Test
    fun `a finished day keeps every hour`() {
        assertEquals(24, buckets().toHourlyChartPoints().size)
        assertEquals(24, buckets().toHourlyChartPoints(throughHour = null).size)
    }

    @Test
    fun `today stops at the hour it is now, inclusive`() {
        val points = buckets().toHourlyChartPoints(throughHour = 8)

        assertEquals("девять столбцов: с нулевого часа по восьмой включительно", 9, points.size)
        assertEquals("0", points.first().label)
        assertEquals("8", points.last().label)
    }

    @Test
    fun `the first hour of the day is one bar, not none`() {
        val points = buckets().toHourlyChartPoints(throughHour = 0)

        assertEquals(1, points.size)
        assertEquals("00:00–01:00", points.first().detailLabel)
    }

    @Test
    fun `the last hour of the day is the whole axis`() {
        assertEquals(24, buckets().toHourlyChartPoints(throughHour = 23).size)
    }

    @Test
    fun `values and hour ranges survive the trim`() {
        val points = buckets().toHourlyChartPoints(throughHour = 3)

        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), points.map { it.value })
        assertEquals("03:00–04:00", points.last().detailLabel)
    }
}
