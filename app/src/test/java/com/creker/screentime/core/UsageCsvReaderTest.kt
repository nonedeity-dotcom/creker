package com.creker.screentime.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCsvReaderTest {

    @Test
    fun `round-trips what UsageCsvWriter wrote`() {
        val rows = listOf(
            UsageExportRow(kind = "app", packageName = "com.chat", date = "2026-08-20", valueMillis = 90_000L, launchCount = 3),
            UsageExportRow(kind = "screen", packageName = "", date = "2026-08-20", valueMillis = 500_000L, launchCount = 0),
        )

        val parsed = UsageCsvReader.parse(UsageCsvWriter.write(rows))

        assertEquals(rows, parsed)
    }

    @Test
    fun `an empty export round-trips to an empty list`() {
        assertEquals(emptyList<UsageExportRow>(), UsageCsvReader.parse(UsageCsvWriter.write(emptyList())))
    }

    @Test
    fun `works without the header line too`() {
        val csv = "app,com.chat,2026-08-20,90000,3"

        val parsed = UsageCsvReader.parse(csv)

        assertEquals(listOf(UsageExportRow("app", "com.chat", "2026-08-20", 90_000L, 3)), parsed)
    }

    @Test
    fun `skips malformed lines instead of failing the whole import`() {
        val csv = """
            type,package_name,date,value_millis,launch_count
            app,com.chat,2026-08-20,90000,3
            app,com.broken,not-a-date,90000,3
            app,com.broken,2026-08-20,not-a-number,3
            garbage,too,few,columns
            app,com.chat,2026-08-21,60000,2
        """.trimIndent()

        val parsed = UsageCsvReader.parse(csv)

        assertEquals(
            listOf(
                UsageExportRow("app", "com.chat", "2026-08-20", 90_000L, 3),
                UsageExportRow("app", "com.chat", "2026-08-21", 60_000L, 2),
            ),
            parsed,
        )
    }
}
