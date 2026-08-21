package com.creker.screentime.data.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService
import com.creker.screentime.core.RawUsageEvent
import com.creker.screentime.core.RawUsageEventType

/**
 * Reads the raw event stream from [UsageStatsManager] and maps it onto the
 * Android-free model used by the aggregation.
 */
class SystemUsageEventSource(private val context: Context) {

    fun queryEvents(startMs: Long, endMs: Long): List<RawUsageEvent> {
        val manager = context.getSystemService<UsageStatsManager>() ?: return emptyList()
        val events = runCatching { manager.queryEvents(startMs, endMs) }.getOrNull() ?: return emptyList()

        val result = mutableListOf<RawUsageEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType.toRawType() ?: continue
            val packageName = event.packageName ?: continue
            result += RawUsageEvent(packageName, event.timeStamp, type)
        }
        result.sortBy { it.timestampMs }
        return result
    }

    private fun Int.toRawType(): RawUsageEventType? = when (this) {
        // ACTIVITY_RESUMED (API 29+) shares its value with the older MOVE_TO_FOREGROUND.
        UsageEvents.Event.MOVE_TO_FOREGROUND -> RawUsageEventType.FOREGROUND
        // Same for ACTIVITY_PAUSED and MOVE_TO_BACKGROUND.
        UsageEvents.Event.MOVE_TO_BACKGROUND -> RawUsageEventType.BACKGROUND
        SCREEN_INTERACTIVE -> RawUsageEventType.SCREEN_ON
        SCREEN_NON_INTERACTIVE -> RawUsageEventType.SCREEN_OFF
        KEYGUARD_SHOWN -> RawUsageEventType.KEYGUARD_SHOWN
        KEYGUARD_HIDDEN -> RawUsageEventType.KEYGUARD_HIDDEN
        DEVICE_SHUTDOWN -> RawUsageEventType.SHUTDOWN
        else -> null
    }

    private companion object {
        /**
         * Declared as literals because the matching constants were added in later API
         * levels than the app's minimum; the numeric values are part of the platform
         * contract and never change.
         */
        const val SCREEN_INTERACTIVE = 15 // UsageEvents.Event.SCREEN_INTERACTIVE, API 28
        const val SCREEN_NON_INTERACTIVE = 16 // UsageEvents.Event.SCREEN_NON_INTERACTIVE, API 28
        const val KEYGUARD_SHOWN = 17 // UsageEvents.Event.KEYGUARD_SHOWN, API 28
        const val KEYGUARD_HIDDEN = 18 // UsageEvents.Event.KEYGUARD_HIDDEN, API 28
        const val DEVICE_SHUTDOWN = 26 // UsageEvents.Event.DEVICE_SHUTDOWN, API 30
    }
}
