package com.creker.screentime.core

import java.util.concurrent.TimeUnit

/**
 * Formats a duration as `чч:мм:сс`, e.g. `02:15:40`.
 *
 * Hours are not wrapped at 24: a month total is legitimately `127:45:10`, and
 * anything else would silently lose days.
 */
object DurationFormatter {

    fun format(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L))
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
