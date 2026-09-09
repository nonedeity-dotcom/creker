package com.creker.screentime.ui.settings

/**
 * One row of the "which apps get this data" list: a package that has asked creker for screen
 * time (or the companion app, which is listed before it ever asks), resolved to something a
 * person can recognise.
 */
data class CallerUi(
    val packageName: String,
    /** The app's own name, or the package name when it can't be resolved. */
    val label: String,
    val isInstalled: Boolean,
    val allowed: Boolean,
    /** Epoch millis of the most recent query, or 0 if it has never asked. */
    val lastSeenMs: Long,
)
