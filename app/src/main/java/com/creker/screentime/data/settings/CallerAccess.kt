package com.creker.screentime.data.settings

import android.content.Context
import android.content.SharedPreferences

/** One app that has asked creker for screen time, and whether it is allowed to have it. */
data class CallerRecord(
    val packageName: String,
    val allowed: Boolean,
    /** Epoch millis of the most recent query, or 0 if this app has never asked. */
    val lastSeenMs: Long,
)

/**
 * Who may read [UsageProvider][com.creker.screentime.data.provider.UsageProvider], decided per
 * calling app rather than by one global switch.
 *
 * The manifest permission (`READ_USAGE`, `protectionLevel="normal"`) is granted to anything that
 * declares it, with no prompt — it keeps out apps that don't know the name, and nothing else. So
 * the actual decision is made here, against the *binder* identity of the caller
 * (`ContentProvider.getCallingPackage()`), which an app cannot forge the way it could a string it
 * passes in.
 *
 * Unknown apps are refused. [NO_BURNOUT_PACKAGE] is the one exception and is allowed by default:
 * it is the companion this contract exists for, and having to go and enable it by hand before it
 * ever works would make a fresh install look broken. That default is only a default — an explicit
 * entry, written the moment the user flips its switch, wins.
 *
 * Deliberately plain SharedPreferences rather than DataStore or Room. The provider is created
 * before `Application.onCreate` runs, so it has a Context and nothing else, and it has to answer
 * synchronously on the binder thread a cross-app query arrives on.
 */
object CallerAccess {

    /** no-burnout's applicationId — the habit tracker this provider was built for. */
    const val NO_BURNOUT_PACKAGE = "com.yourcompany.noburnout"

    private const val PREFS_NAME = "creker_settings"
    private const val KEY_PREFIX = "caller."
    private const val ALLOWED_SUFFIX = ".allowed"
    private const val LAST_SEEN_SUFFIX = ".last"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun allowedKey(packageName: String) = "$KEY_PREFIX$packageName$ALLOWED_SUFFIX"

    private fun lastSeenKey(packageName: String) = "$KEY_PREFIX$packageName$LAST_SEEN_SUFFIX"

    private fun defaultAllowed(packageName: String) = packageName == NO_BURNOUT_PACKAGE

    fun isAllowed(context: Context, packageName: String): Boolean =
        prefs(context).getBoolean(allowedKey(packageName), defaultAllowed(packageName))

    /**
     * Notes that [packageName] asked, so the settings list can show apps the user has never heard
     * of rather than only the ones they already know about. Called for refused callers too — an
     * app that keeps asking and keeps being refused is exactly what someone would want to see.
     *
     * Writing the decision here also freezes the default into an explicit entry on first contact,
     * which is what keeps a later change to [defaultAllowed] from silently re-opening an app the
     * user had already seen listed.
     */
    fun recordQuery(context: Context, packageName: String, nowMs: Long) {
        val prefs = prefs(context)
        prefs.edit()
            .putBoolean(allowedKey(packageName), isAllowed(context, packageName))
            .putLong(lastSeenKey(packageName), nowMs)
            .apply()
    }

    fun setAllowed(context: Context, packageName: String, allowed: Boolean) {
        prefs(context).edit().putBoolean(allowedKey(packageName), allowed).apply()
    }

    /**
     * Every app that has ever asked, most recent first, with [NO_BURNOUT_PACKAGE] always present
     * so the list explains itself before anything has queried at all.
     */
    fun records(context: Context): List<CallerRecord> {
        val prefs = prefs(context)
        val packages = prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) && it.endsWith(ALLOWED_SUFFIX) }
            .map { it.removePrefix(KEY_PREFIX).removeSuffix(ALLOWED_SUFFIX) }
            .toMutableSet()
        packages += NO_BURNOUT_PACKAGE

        return packages
            .map {
                CallerRecord(
                    packageName = it,
                    allowed = isAllowed(context, it),
                    lastSeenMs = prefs.getLong(lastSeenKey(it), 0L),
                )
            }
            .sortedWith(compareByDescending<CallerRecord> { it.lastSeenMs }.thenBy { it.packageName })
    }
}
