package com.creker.screentime.core

/** One row of the exported backup: either an app's day, or the device's screen-on day. */
data class UsageExportRow(
    val kind: String,
    val packageName: String,
    val date: String,
    val valueMillis: Long,
    val launchCount: Int,
)

/** Builds the CSV backing the "save all data" export — plain text, no external format needed. */
object UsageCsvWriter {

    private const val HEADER = "type,package_name,date,value_millis,launch_count"

    fun write(rows: List<UsageExportRow>): String {
        if (rows.isEmpty()) return HEADER
        val body = rows.joinToString("\n") {
            "${it.kind},${it.packageName},${it.date},${it.valueMillis},${it.launchCount}"
        }
        return "$HEADER\n$body"
    }
}

/**
 * Reads back the CSV [UsageCsvWriter] produces, for the "load data" import used when
 * moving to a new phone: export on the old one, then open that file here. Malformed
 * lines (wrong column count, a date or number that won't parse) are skipped rather
 * than failing the whole import, since a partially-recovered history beats none.
 */
object UsageCsvReader {

    fun parse(csv: String): List<UsageExportRow> {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val dataLines = if (lines.firstOrNull()?.startsWith("type,") == true) lines.drop(1) else lines
        return dataLines.mapNotNull { line -> parseRow(line) }
    }

    private fun parseRow(line: String): UsageExportRow? {
        val parts = line.split(",")
        if (parts.size != 5) return null
        val kind = parts[0]
        val packageName = parts[1]
        val date = parts[2]
        val valueMillis = parts[3].toLongOrNull() ?: return null
        val launchCount = parts[4].toIntOrNull() ?: return null
        if (kind != "app" && kind != "screen") return null
        if (runCatching { java.time.LocalDate.parse(date) }.isFailure) return null
        return UsageExportRow(kind = kind, packageName = packageName, date = date, valueMillis = valueMillis, launchCount = launchCount)
    }
}
