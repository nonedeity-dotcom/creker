package com.creker.screentime.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * The one user-flippable switch that changes what leaves this app: whether
 * [UsageProvider][com.creker.screentime.data.provider.UsageProvider] answers other apps
 * at all.
 *
 * Deliberately plain SharedPreferences rather than DataStore. The provider is created
 * before `Application.onCreate` runs, so it cannot reach [AppContainer]
 * [com.creker.screentime.AppContainer] or anything built there — it has only a Context.
 * SharedPreferences can be read straight from that Context, synchronously, on the binder
 * thread a cross-app query arrives on; DataStore's flow would have to be blocked on.
 *
 * Sharing is on by default: the provider is the reason the app declares an outward-facing
 * contract at all, and a companion app that reads it is the normal case. Turning it off is
 * a deliberate act, and it is the *only* thing that has to be honoured on every query —
 * hence the read below happening per call rather than being cached in a field, so flipping
 * the switch takes effect on the next query instead of the next process start.
 */
object SharingSettings {

    private const val PREFS_NAME = "creker_settings"
    private const val KEY_SHARING_ENABLED = "provider_sharing_enabled"
    private const val DEFAULT_ENABLED = true

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHARING_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHARING_ENABLED, enabled).apply()
    }
}
