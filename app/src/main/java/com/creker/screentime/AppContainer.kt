package com.creker.screentime

import android.content.Context
import com.creker.screentime.data.UsageRepository
import com.creker.screentime.data.local.ScreenTimeDatabase
import com.creker.screentime.data.system.AppInfoProvider
import com.creker.screentime.data.system.SystemUsageEventSource

/** Hand-rolled dependency container — the graph is small enough not to need a library. */
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
