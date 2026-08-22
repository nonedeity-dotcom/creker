package com.creker.screentime.core

import java.util.concurrent.TimeUnit

/** Localised single-letter unit suffixes, e.g. "ч" / "м" / "с". */
data class DurationUnits(val hours: String, val minutes: String, val seconds: String)

/**
 * Formats a duration in one of three ways, depending on how much room the number has.
 *
 * Hours are never wrapped at 24: a month total is legitimately `127:45:10`, and
 * anything else would silently lose days.
 */
object DurationFormatter {

    /** `чч:мм:сс`, e.g. `02:15:40` — the fixed-width form for the big readout. */
    fun format(durationMs: Long): String {
        val (hours, minutes, seconds) = split(durationMs)
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /** `1ч 34м 36с`, dropping the units that would be leading zeros. */
    fun formatWithUnits(durationMs: Long, units: DurationUnits): String {
        val (hours, minutes, seconds) = split(durationMs)
        return buildString {
            if (hours > 0) append(hours).append(units.hours).append(' ')
            if (hours > 0 || minutes > 0) append(minutes).append(units.minutes).append(' ')
            append(seconds).append(units.seconds)
        }
    }

    /**
     * The shortest honest form, for labelling a chart bar where there is only room for
     * a few characters: `2ч 15м`, `41м`, `40с`. Seconds are dropped as soon as there
     * are minutes to show — at that scale they are noise, and the tooltip and the app
     * list both carry the exact figure.
     */
    fun formatCompact(durationMs: Long, units: DurationUnits): String {
        val (hours, minutes, seconds) = split(durationMs)
        return when {
            hours > 0 -> "$hours${units.hours} $minutes${units.minutes}"
            minutes > 0 -> "$minutes${units.minutes}"
            else -> "$seconds${units.seconds}"
        }
    }

    private fun split(durationMs: Long): Triple<Long, Long, Long> {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L))
        return Triple(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }
}
