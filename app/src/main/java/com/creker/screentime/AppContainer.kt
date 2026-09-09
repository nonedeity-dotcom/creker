package com.creker.screentime

import android.content.Context
import com.creker.screentime.data.UsageRepository
import com.creker.screentime.data.local.ScreenTimeDatabase
import com.creker.screentime.data.system.AppInfoProvider
import com.creker.screentime.data.system.SystemUsageEventSource

/**
 * Hand-rolled dependency container — the graph is small enough not to need a library.
 *
 * Also the seam between the UI and the data layer: screens get [usageRepository] and
 * [appInfoProvider] and nothing else, so redrawing a screen cannot reach the database that
 * another app reads through
 * [UsageProvider][com.creker.screentime.data.provider.UsageProvider].
 *
 * That seam is a convention, not a guarantee, and it is worth being honest about which. The
 * DAOs, entities and database are `internal`, but Kotlin's `internal` is module-wide and this
 * is a single-module app: any file under `ui/` could still import them and compile. What
 * actually holds the outward-facing contract together is `UsageContractTest`, a plain JVM test
 * that pins the authority, the permissions and the column names to their literals and re-reads
 * AndroidManifest.xml. Real enforcement would need the data layer in its own Gradle module —
 * a deliberate later step, not something to assume is already in place.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database by lazy { ScreenTimeDatabase.get(appContext) }

    val appInfoProvider by lazy { AppInfoProvider(appContext) }

    val usageRepository by lazy {
        UsageRepository(
            usageDao = database.usageDao(),
            deviceUsageDao = database.deviceUsageDao(),
            syncStateDao = database.syncStateDao(),
            eventSource = SystemUsageEventSource(appContext),
        )
    }
}
