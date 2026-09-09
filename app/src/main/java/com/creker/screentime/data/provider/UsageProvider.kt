package com.creker.screentime.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.creker.screentime.contract.UsageContract
import com.creker.screentime.data.local.ScreenTimeDatabase
import com.creker.screentime.data.settings.CallerAccess

/**
 * Read-only window into [device_usage][com.creker.screentime.data.local.DeviceUsageEntity] and
 * [app_usage][com.creker.screentime.data.local.AppUsageEntity]
 * for other apps on the same device — this is how a habit/reminder app can ask "how much
 * screen time was there on day X" without creker itself gaining any network permission,
 * notification, or background-launch capability. It only ever answers a query the other
 * app makes while it is itself in the foreground; creker never initiates anything.
 *
 * The contract this serves lives in [UsageContract], which is also where Room takes the table
 * and column names from — the names below are the same objects, not copies of them.
 *
 * Query contract for `content://com.creker.screentime.provider/device_usage`:
 * - `selectionArgs[0]` = from-date, `selectionArgs[1]` = to-date, both `yyyy-MM-dd`
 *   (inclusive range, same convention as the rest of the app). `selection`, `sortOrder` and
 *   `projection` are ignored entirely.
 * - Returned cursor columns: `date` (TEXT), `screen_millis` (INTEGER), `updated_at` (INTEGER,
 *   epoch millis up to which that day's total is complete — see
 *   [UsageContract.COLUMN_UPDATED_AT]) — one row per day that has data; days with no synced
 *   data are simply absent, same as the Room table.
 * Query contract for `content://com.creker.screentime.provider/app_usage`: the same range
 * arguments, returning `date`, `package_name`, `usage_millis`, `launch_count` and `app_label`
 * — one row per app per day it was used, biggest first within each day. This path is newer
 * than the other and deliberately so: which apps a person opens was the one thing creker kept
 * to itself, and it is opened now only because the reading app is being merged into this one
 * and the history has to cross over first. Same permission, same allow-list, same refusals.
 *
 * - Answers only apps the user has allowed in creker's settings (see [CallerAccess]); an
 *   app nobody has allowed gets a null cursor, which every caller already has to handle as
 *   "this device has no creker data", so being refused degrades to the same quiet no-op as
 *   creker not being installed.
 * - Requires the caller to hold [UsageContract.READ_PERMISSION] (declared by this app,
 *   `protectionLevel="normal"` — granted automatically to any app that lists it, no runtime
 *   prompt, but at least requires knowing the exact name rather than being wide open to every
 *   installed app).
 */
class UsageProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val match = MATCHER.match(uri)
        if (match != DEVICE_USAGE && match != APP_USAGE) return null
        val context = context ?: return null
        // The binder identity of the caller, which an app cannot forge — unlike anything it
        // could pass in as an argument. Null means the system could not attribute the call to
        // a package at all (a shell, say); there is nothing to ask the user about, so refuse.
        val caller = callingPackage ?: return null
        // Recorded before the decision, refusals included: an app that keeps asking and keeps
        // being turned away is exactly what the settings list should show.
        CallerAccess.recordQuery(context, caller, System.currentTimeMillis())
        // Read per query rather than cached, so flipping a switch takes effect on the next
        // query instead of the next process start.
        if (!CallerAccess.isAllowed(context, caller)) return null
        val fromDate = selectionArgs?.getOrNull(UsageContract.ARG_FROM_DATE) ?: return null
        val toDate = selectionArgs?.getOrNull(UsageContract.ARG_TO_DATE) ?: return null

        val db = ScreenTimeDatabase.get(context)
        return when (match) {
            DEVICE_USAGE -> db.deviceUsageDao().queryDailyTotalsCursor(fromDate, toDate)
            else -> withLabels(context, db.usageDao().queryAppUsageCursor(fromDate, toDate))
        }
    }

    /**
     * Adds the human-readable app name to each per-app row.
     *
     * The caller cannot work it out for itself: since Android 11 an app sees only the packages
     * it declared up front, and a screen-time list reading `com.google.android.youtube` is not
     * something a person can use. creker already holds the permission to see them all, so it is
     * the one place where the name is cheap.
     *
     * Copied into a [MatrixCursor] rather than joined in SQL because the name lives in the
     * package manager, not in the database. The row count is days × apps — tens, not thousands —
     * and labels are resolved once per package rather than once per row.
     */
    private fun withLabels(context: Context, source: Cursor): Cursor {
        val out = MatrixCursor(
            arrayOf(
                UsageContract.COLUMN_DATE,
                UsageContract.COLUMN_PACKAGE_NAME,
                UsageContract.COLUMN_USAGE_MILLIS,
                UsageContract.COLUMN_LAUNCH_COUNT,
                UsageContract.COLUMN_APP_LABEL,
            )
        )
        val packageManager = context.packageManager
        val labels = HashMap<String, String>()
        source.use { cursor ->
            val dateIdx = cursor.getColumnIndex(UsageContract.COLUMN_DATE)
            val packageIdx = cursor.getColumnIndex(UsageContract.COLUMN_PACKAGE_NAME)
            val millisIdx = cursor.getColumnIndex(UsageContract.COLUMN_USAGE_MILLIS)
            val launchIdx = cursor.getColumnIndex(UsageContract.COLUMN_LAUNCH_COUNT)
            if (dateIdx < 0 || packageIdx < 0 || millisIdx < 0 || launchIdx < 0) return out
            while (cursor.moveToNext()) {
                val packageName = cursor.getString(packageIdx)
                val label = labels.getOrPut(packageName) {
                    runCatching {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0)
                        ).toString()
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                        // Uninstalled since the usage was recorded, or hidden from us: the
                        // package name is a worse name than a real one and a better one than
                        // a blank.
                        ?: packageName
                }
                out.addRow(
                    arrayOf(
                        cursor.getString(dateIdx),
                        packageName,
                        cursor.getLong(millisIdx),
                        cursor.getInt(launchIdx),
                        label,
                    )
                )
            }
        }
        return out
    }

    /*
     * Writing is refused twice over, on purpose.
     *
     * The manifest guards this provider with android:writePermission (signature-level, so no
     * third-party app can hold it), and every write entry point below also refuses
     * unconditionally, ignoring its arguments. Either check alone would do the job today; two
     * are here because they fail differently. A manifest attribute is configuration: it can be
     * dropped in a merge, overwritten by a manifest-merger rule or lost in a build variant, and
     * nothing about that failure is visible — the provider would simply start accepting writes.
     * The refusal below is code, it travels with the class, and it is what actually guarantees
     * that nothing outside this app can change a stored day.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun getType(uri: Uri): String? = null

    companion object {
        const val AUTHORITY: String = UsageContract.AUTHORITY
        const val READ_PERMISSION: String = UsageContract.READ_PERMISSION

        private const val DEVICE_USAGE = 1
        private const val APP_USAGE = 2
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(UsageContract.AUTHORITY, UsageContract.PATH_DEVICE_USAGE, DEVICE_USAGE)
            addURI(UsageContract.AUTHORITY, UsageContract.PATH_APP_USAGE, APP_USAGE)
        }
    }
}
