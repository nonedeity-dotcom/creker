package com.creker.screentime.contract

/**
 * Everything creker promises to other apps on this device, in one place.
 *
 * A second app (no-burnout) reads daily screen time and per-app time through [UsageProvider]
 * [com.creker.screentime.data.provider.UsageProvider] and identifies the columns by name.
 * That app fails *silently* on a broken contract: it is written so that a missing creker, a
 * missing permission and an empty result all render as "nothing to show", which is a normal
 * state rather than an error. So a renamed column does not crash anything, does not log
 * anything, and does not look different on screen — it just quietly stops working.
 *
 * Hence this file. The names below are not descriptive constants; they *are* the contract:
 *
 * - Room takes its table and column names from here (see
 *   [DeviceUsageEntity][com.creker.screentime.data.local.DeviceUsageEntity] and the cursor query
 *   in [DeviceUsageDao][com.creker.screentime.data.local.DeviceUsageDao]), so renaming a column
 *   in the database means editing this file — the compiler will not let the two drift apart.
 * - `UsageContractTest` pins every value below to its literal and re-reads AndroidManifest.xml,
 *   which cannot reference Kotlin constants. Changing a value here fails that test on the JVM,
 *   before anything reaches a device.
 *
 * If a test in this repository points you at this file: the fix is not to update the test.
 * Any change to these values has to land in the consuming app in the same go.
 */
object UsageContract {

    /** Provider authority. Must match `android:authorities` in AndroidManifest.xml. */
    const val AUTHORITY: String = "com.creker.screentime.provider"

    /** Daily device-wide totals. */
    const val PATH_DEVICE_USAGE: String = "device_usage"

    /**
     * Per-app foreground time, opened deliberately and late.
     *
     * This path did not exist, and its absence was the point: which apps a person opens is the
     * most personal thing creker holds, and creker had no reason to hand it to anybody. What
     * changed is that the reading app is no longer a different app in any meaningful sense —
     * the two are being merged into one, and this path is how the history crosses over before
     * creker is uninstalled. It is still guarded by [READ_PERMISSION] and still answers only
     * callers the user allowed in creker's own settings; nothing about who may ask has been
     * loosened, only what an already-trusted caller may see.
     */
    const val PATH_APP_USAGE: String = "app_usage"

    /** `content://com.creker.screentime.provider/device_usage` — the URI the other app queries. */
    const val CONTENT_URI_STRING: String = "content://$AUTHORITY/$PATH_DEVICE_USAGE"

    /** `content://com.creker.screentime.provider/app_usage`. */
    const val CONTENT_URI_APP_USAGE_STRING: String = "content://$AUTHORITY/$PATH_APP_USAGE"

    /**
     * Held by the reading app. `protectionLevel="normal"`, so it is granted at install time with
     * no dialog. Must match the `<permission>` and `android:readPermission` in AndroidManifest.xml.
     */
    const val READ_PERMISSION: String = "com.creker.screentime.permission.READ_USAGE"

    /**
     * Guards the write half of the provider. `protectionLevel="signature"`, so no third-party app
     * can hold it — this is a lock, not an entry point. The provider *also* refuses every write in
     * code (see [UsageProvider][com.creker.screentime.data.provider.UsageProvider]); the manifest
     * is configuration and configuration can be lost in a merge, the code check cannot.
     */
    const val WRITE_PERMISSION: String = "com.creker.screentime.permission.WRITE_USAGE"

    /** Room table behind the provider. Read-only from the outside. */
    const val TABLE_DEVICE_USAGE: String = "device_usage"

    /** Room table behind [PATH_APP_USAGE]. Read-only from the outside, same as the other. */
    const val TABLE_APP_USAGE: String = "app_usage"

    /** Calendar day in the device's own time zone, ISO `yyyy-MM-dd`. Cursor column and Room column. */
    const val COLUMN_DATE: String = "date"

    /** Device-wide screen-on time for that day in milliseconds, lock screen excluded. */
    const val COLUMN_SCREEN_MILLIS: String = "screen_millis"

    /**
     * Epoch millis up to which [COLUMN_SCREEN_MILLIS] for that day is complete — *not* the moment
     * the row was written.
     *
     * For a day that has ended this is that day's local midnight boundary, meaning "the whole day
     * is accounted for". For today it is the moment of the last sync, so a reader can tell how
     * stale the running total is. `0` means unknown: the row predates this column.
     *
     * Why the distinction matters: creker only recomputes today's total when it is opened or when
     * the daily worker runs just after midnight. Without this column a caller cannot tell "the
     * screen was off all morning" from "nobody measured the morning", and both look like a
     * comfortably low number. A reader that wants to act on today should require
     * `now - updated_at` to be small; a reader that wants to know whether a past day is complete
     * should require `updated_at` to have reached that day's end.
     */
    const val COLUMN_UPDATED_AT: String = "updated_at"

    /** Package whose foreground time this row counts. Cursor column and Room column. */
    const val COLUMN_PACKAGE_NAME: String = "package_name"

    /** Foreground time of that package on that day, in milliseconds. */
    const val COLUMN_USAGE_MILLIS: String = "usage_millis"

    /** How many times that package was brought to the foreground that day. */
    const val COLUMN_LAUNCH_COUNT: String = "launch_count"

    /**
     * Human-readable name of the package, resolved at query time — not a stored column.
     *
     * The caller cannot resolve it itself: on Android 11 and later an app sees only packages it
     * declared, and a screen-time list of raw package names is not something a person can read.
     * Falls back to the package name for anything uninstalled since.
     */
    const val COLUMN_APP_LABEL: String = "app_label"

    /** Both query arguments use this pattern, inclusive on both ends. */
    const val DATE_PATTERN: String = "yyyy-MM-dd"

    /**
     * `query()` reads its range from `selectionArgs`, not from `selection`: the caller passes
     * `arrayOf(fromDate, toDate)` and the provider ignores `selection`, `sortOrder` and
     * `projection` entirely — which is also why SQL injection has no way in.
     */
    const val ARG_FROM_DATE: Int = 0
    const val ARG_TO_DATE: Int = 1
}
