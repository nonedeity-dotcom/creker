package com.creker.screentime.data.system

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/** Display name, icon and install date of an installed package. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isInstalled: Boolean,
    val installedAtMs: Long?,
)

/**
 * Resolves package names to labels and icons, caching the result: the same handful of
 * packages is re-rendered on every period switch and decoding icons is not cheap.
 *
 * Must be called off the main thread the first time a package is seen.
 */
class AppInfoProvider(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
    private val cache = ConcurrentHashMap<String, AppInfo>()

    fun get(packageName: String): AppInfo = cache.getOrPut(packageName) { load(packageName) }

    private fun load(packageName: String): AppInfo {
        val applicationInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()

        if (applicationInfo == null) {
            // Uninstalled since the usage was recorded, or hidden by package visibility.
            return AppInfo(packageName, packageName, icon = null, isInstalled = false, installedAtMs = null)
        }

        val label = runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrNull().orEmpty().ifBlank { packageName }

        val icon = runCatching {
            packageManager.getApplicationIcon(applicationInfo)
                .toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
                .asImageBitmap()
        }.getOrNull()

        val installedAtMs = runCatching {
            packageManager.getPackageInfo(packageName, 0).firstInstallTime
        }.getOrNull()

        return AppInfo(packageName, label, icon, isInstalled = true, installedAtMs = installedAtMs)
    }

    private companion object {
        const val ICON_SIZE_PX = 128
    }
}
