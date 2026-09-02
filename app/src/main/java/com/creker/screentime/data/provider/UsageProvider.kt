package com.creker.screentime.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.creker.screentime.contract.UsageContract
import com.creker.screentime.data.local.ScreenTimeDatabase

/**
 * Read-only window into [device_usage][com.creker.screentime.data.local.DeviceUsageEntity]
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
        if (MATCHER.match(uri) != DEVICE_USAGE) return null
        val context = context ?: return null
        val fromDate = selectionArgs?.getOrNull(UsageContract.ARG_FROM_DATE) ?: return null
        val toDate = selectionArgs?.getOrNull(UsageContract.ARG_TO_DATE) ?: return null

        val dao = ScreenTimeDatabase.get(context).deviceUsageDao()
        return dao.queryDailyTotalsCursor(fromDate, toDate)
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
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(UsageContract.AUTHORITY, UsageContract.PATH_DEVICE_USAGE, DEVICE_USAGE)
        }
    }
}
