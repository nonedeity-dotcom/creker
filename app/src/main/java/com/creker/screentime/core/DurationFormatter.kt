package com.creker.screentime.core

import java.util.concurrent.TimeUnit

/** Localised unit suffixes, e.g. "ч" / "мин" / "сек". */
data class DurationLabels(
    val hours: String,
    val minutes: String,
    val seconds: String,
)

/**
 * Formats a duration as hours : minutes : seconds, e.g. "2ч 15мин 40сек".
 *
 * Units that would be leading zeros are dropped ("15мин 40сек", "40сек") so short
 * entries stay readable, but seconds are always shown — the whole point is that the
 * numbers are precise down to a second rather than rounded to minutes.
 */
object DurationFormatter {

    fun formatHms(durationMs: Long, labels: DurationLabels): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L))
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return buildString {
            if (hours > 0) {
                append(hours).append(labels.hours).append(' ')
            }
            if (hours > 0 || minutes > 0) {
                append(minutes).append(labels.minutes).append(' ')
            }
            append(seconds).append(labels.seconds)
        }
    }
}
