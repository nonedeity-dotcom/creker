package com.creker.screentime.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The guard on the cross-app contract.
 *
 * The app that reads creker's screen time (no-burnout) is written so that a missing creker, a
 * missing permission and an empty result all look the same: nothing on screen, nothing in the
 * log. A renamed column therefore does not fail loudly anywhere — it just stops working, and
 * looks exactly like "creker isn't installed". Nothing else in this repository would catch that:
 * Room takes its names from [UsageContract] now, so a rename compiles; the manifest is XML the
 * compiler never reads; and the instrumented check needs an emulator with both apps on it.
 *
 * So these assertions are deliberately dumb literals rather than references to the constants.
 * If one fails, the corresponding change has to land in nonedeity-dotcom/demo_klic in the same
 * go — updating the expected value here on its own is how the integration breaks silently.
 */
class UsageContractTest {

    @Test
    fun `contract values are the ones the other app is built against`() {
        assertEquals("com.creker.screentime.provider", UsageContract.AUTHORITY)
        assertEquals("device_usage", UsageContract.PATH_DEVICE_USAGE)
        assertEquals(
            "content://com.creker.screentime.provider/device_usage",
            UsageContract.CONTENT_URI_STRING,
        )
        assertEquals("com.creker.screentime.permission.READ_USAGE", UsageContract.READ_PERMISSION)
        assertEquals("date", UsageContract.COLUMN_DATE)
        assertEquals("screen_millis", UsageContract.COLUMN_SCREEN_MILLIS)
        assertEquals("updated_at", UsageContract.COLUMN_UPDATED_AT)
        assertEquals("yyyy-MM-dd", UsageContract.DATE_PATTERN)
        assertEquals(0, UsageContract.ARG_FROM_DATE)
        assertEquals(1, UsageContract.ARG_TO_DATE)
    }

    @Test
    fun `dates are stored and queried in the advertised format`() {
        val day = LocalDate.of(2026, 1, 5)
        val formatted = DateTimeFormatter.ofPattern(UsageContract.DATE_PATTERN).format(day)
        assertEquals("2026-01-05", formatted)
        // The repository writes LocalDate.toString() straight into the column, so the two have
        // to agree — including the zero padding a caller will send in selectionArgs.
        assertEquals(day.toString(), formatted)
    }

    @Test
    fun `the exposed table is the only one the contract names`() {
        // app_usage holds package names — which apps a person uses. It is deliberately not part
        // of the contract, and the provider exposes no path to it.
        assertEquals("device_usage", UsageContract.TABLE_DEVICE_USAGE)
    }

    @Test
    fun `manifest declares the same authority and permissions as the contract`() {
        val manifest = manifestText()

        assertTrue(
            "AndroidManifest.xml must declare the provider under ${UsageContract.AUTHORITY}",
            manifest.contains("android:authorities=\"${UsageContract.AUTHORITY}\""),
        )
        assertTrue(
            "the provider must stay guarded by ${UsageContract.READ_PERMISSION}",
            manifest.contains("android:readPermission=\"${UsageContract.READ_PERMISSION}\""),
        )
        assertTrue(
            "the provider must stay guarded by ${UsageContract.WRITE_PERMISSION}",
            manifest.contains("android:writePermission=\"${UsageContract.WRITE_PERMISSION}\""),
        )
        assertTrue(
            "${UsageContract.READ_PERMISSION} must be declared, and stay install-time (normal)",
            manifest.declaresPermission(UsageContract.READ_PERMISSION, "normal"),
        )
        assertTrue(
            "${UsageContract.WRITE_PERMISSION} must be declared, and stay unobtainable (signature)",
            manifest.declaresPermission(UsageContract.WRITE_PERMISSION, "signature"),
        )
    }

    @Test
    fun `the app asks for no network access`() {
        // Not about the provider, but the same kind of silent regression: creker is offline by
        // construction, and the only thing standing between it and the network is the absence of
        // this line. A dependency that adds it through manifest merging would not be obvious.
        assertTrue(
            "creker must never request INTERNET",
            !manifestText().contains("android.permission.INTERNET"),
        )
    }

    private fun String.declaresPermission(name: String, protectionLevel: String): Boolean {
        val declaration = substringAfter("android:name=\"$name\"", missingDelimiterValue = "")
            .substringBefore("/>")
        return declaration.contains("android:protectionLevel=\"$protectionLevel\"")
    }

    private fun manifestText(): String {
        // Gradle runs unit tests with the module directory as the working directory; the fallback
        // keeps the test runnable from the repository root too.
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        val manifest = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("AndroidManifest.xml not found from ${File("").absolutePath}")
        return manifest.readText()
    }
}
