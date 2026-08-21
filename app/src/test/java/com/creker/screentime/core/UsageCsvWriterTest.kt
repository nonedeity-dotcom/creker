package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCsvWriterTest {

    @Test
    fun `an empty export is just the header`() {
        assertEquals("type,package_name,date,value_millis,launch_count", UsageCsvWriter.write(emptyList()))
    }

    @Test
    fun `each row becomes one comma-separated line`() {
        val rows = listOf(
            UsageExportRow(kind = "app", packageName = "com.chat", date = "2026-08-20", valueMillis = 90_000L, launchCount = 3),
            UsageExportRow(kind = "screen", packageName = "", date = "2026-08-20", valueMillis = 500_000L, launchCount = 0),
        )

        val csv = UsageCsvWriter.write(rows)

        val lines = csv.lines()
        assertEquals(3, lines.size)
        assertEquals("type,package_name,date,value_millis,launch_count", lines[0])
        assertEquals("app,com.chat,2026-08-20,90000,3", lines[1])
        assertEquals("screen,,2026-08-20,500000,0", lines[2])
    }
}
