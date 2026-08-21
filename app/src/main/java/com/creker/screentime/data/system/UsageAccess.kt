package com.creker.screentime.data.system

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * "Usage access" is a special app-op rather than a runtime permission: it can only be
 * toggled by the user on a system Settings screen.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            // MODE_DEFAULT means "fall back to the permission check".
            AppOpsManager.MODE_DEFAULT -> context.checkCallingOrSelfPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            else -> false
        }
    }

    /**
     * Opens the system screen where the user grants usage access. Some vendors ship
     * without the per-app deep link, hence the fallback to the plain list.
     */
    fun openSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.onSuccess { return true }
        }
        return false
    }
}
