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
