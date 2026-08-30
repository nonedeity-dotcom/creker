package com.creker.screentime.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.creker.screentime.data.local.ScreenTimeDatabase

/**
 * Read-only window into [device_usage][com.creker.screentime.data.local.DeviceUsageEntity]
 * for other apps on the same device — this is how a habit/reminder app can ask "how much
 * screen time was there on day X" without creker itself gaining any network permission,
 * notification, or background-launch capability. It only ever answers a query the other
 * app makes while it is itself in the foreground; creker never initiates anything.
 *
 * Query contract for `content://com.creker.screentime.provider/device_usage`:
 * - `selectionArgs[0]` = from-date, `selectionArgs[1]` = to-date, both `yyyy-MM-dd`
 *   (inclusive range, same convention as the rest of the app).
 * - Returned cursor columns: `date` (TEXT), `screen_millis` (INTEGER) — one row per day
 *   that has data; days with no synced data are simply absent, same as the Room table.
 * - Requires the caller to hold `com.creker.screentime.permission.READ_USAGE`
 *   (declared by this app, `protectionLevel="normal"` — granted automatically to any app
 *   that lists it, no runtime prompt, but at least requires knowing the exact name rather
 *   than being wide open to every installed app).
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
        if (MATCHER.match(uri) != DEVICE_USAGE) return null
        val context = context ?: return null
        val fromDate = selectionArgs?.getOrNull(0) ?: return null
        val toDate = selectionArgs?.getOrNull(1) ?: return null

        val dao = ScreenTimeDatabase.get(context).deviceUsageDao()
        return dao.queryDailyTotalsCursor(fromDate, toDate)
    }

    // Read-only — everything else is a deliberate no-op.
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.creker.screentime.provider"
        const val READ_PERMISSION = "com.creker.screentime.permission.READ_USAGE"

        private const val DEVICE_USAGE = 1
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "device_usage", DEVICE_USAGE)
        }
    }
}
