package com.creker.screentime

import android.content.ContentResolver
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creker.screentime.contract.UsageContract
import com.creker.screentime.data.local.DeviceUsageEntity
import com.creker.screentime.data.local.ScreenTimeDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * CI-only door for putting a known screen-time value into `device_usage` — an emulator has
 * no real usage history for [UsageProvider][com.creker.screentime.data.provider.UsageProvider]
 * to serve, so without this there is nothing for another app (e.g. no-burnout, in the
 * cross-app CI check that installs both) to actually read.
 *
 * This lives only in `app/src/androidTest`: it is compiled into the separate instrumentation
 * test APK, never into the debug or release APK a person installs on their own phone. Running
 * it (`./gradlew connectedDebugAndroidTest ...`) requires an already-installed `app-debug`
 * build as its target — it cannot run standalone.
 *
 * Also immediately re-reads what it wrote through the real ContentProvider URI, so a wiring
 * mistake in the provider itself fails right here with a clear assertion, instead of silently
 * as "no data" several steps later on the no-burnout side of the CI job.
 */
@RunWith(AndroidJUnit4::class)
class SeedTestDataTest {

    @Test
    fun seedTodayScreenTimeAndVerifyProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.now() // the app's own convention: local calendar day, yyyy-MM-dd
        val testMillis = 5_400_000L // 1h30m: a plausible value, comfortably under any sane daily limit
        // Seeded as measured just now, the way a real sync of the running day stamps it — a stale
        // stamp is exactly what a reader is supposed to refuse to act on, so it would make the
        // cross-app check pass for the wrong reason.
        val measuredThroughMs = System.currentTimeMillis()

        runBlocking {
            ScreenTimeDatabase.get(context).deviceUsageDao().insertAll(
                listOf(
                    DeviceUsageEntity(
                        date = today.toString(),
                        screenMillis = testMillis,
                        updatedAtMs = measuredThroughMs,
                    ),
                ),
            )
        }

        val resolver: ContentResolver = context.contentResolver
        val uri = Uri.parse(UsageContract.CONTENT_URI_STRING)
        val cursor = resolver.query(uri, null, null, arrayOf(today.toString(), today.toString()), null)
            ?: throw AssertionError("UsageProvider returned a null cursor for $uri")
        cursor.use {
            assertTrue("expected at least one row for $today", it.moveToFirst())
            assertEquals(
                testMillis,
                it.getLong(it.getColumnIndexOrThrow(UsageContract.COLUMN_SCREEN_MILLIS)),
            )
            assertEquals(
                today.toString(),
                it.getString(it.getColumnIndexOrThrow(UsageContract.COLUMN_DATE)),
            )
            assertEquals(
                measuredThroughMs,
                it.getLong(it.getColumnIndexOrThrow(UsageContract.COLUMN_UPDATED_AT)),
            )
        }
    }
}
