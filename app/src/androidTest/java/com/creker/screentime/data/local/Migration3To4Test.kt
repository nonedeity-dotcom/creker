package com.creker.screentime.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creker.screentime.contract.UsageContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 3 → 4 migration, run against a database actually shaped like version 3.
 *
 * This was the one piece of creker with real data at stake and no test behind it. The
 * migration adds a column to the table the companion app reads; get it wrong and Room falls
 * back to destroying the table, taking up to 400 days of history that cannot be rebuilt —
 * the system only remembers about a week of events. The cross-app E2E cannot cover this
 * either, because there creker is installed fresh and never sees a version 3 database.
 *
 * The v3 schema is written out by hand rather than taken from an exported schema file: what
 * matters is that a database in that shape, with rows in it, survives being opened by the
 * current code. Room validates the result against its own expectation of version 4 as part
 * of opening, so a mistake in either the migration or this DDL fails the test rather than
 * passing quietly.
 */
@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-3-4-test.db"

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    /** A database exactly as version 3 left it, with a day of history already in it. */
    private fun createV3() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `app_usage` (" +
                                "`package_name` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                                "`usage_millis` INTEGER NOT NULL, `launch_count` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`package_name`, `date`))"
                        )
                        // Version 3 of this one: no updated_at yet. That column is what the
                        // migration adds.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `${UsageContract.TABLE_DEVICE_USAGE}` (" +
                                "`${UsageContract.COLUMN_DATE}` TEXT NOT NULL, " +
                                "`${UsageContract.COLUMN_SCREEN_MILLIS}` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`${UsageContract.COLUMN_DATE}`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `sync_state` (" +
                                "`id` INTEGER NOT NULL, `lastSyncedAtMs` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`id`))"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO app_usage (package_name, date, usage_millis, launch_count) " +
                    "VALUES ('com.example.old', '2026-01-05', 123456, 7)"
            )
            db.execSQL(
                "INSERT INTO ${UsageContract.TABLE_DEVICE_USAGE} " +
                    "(${UsageContract.COLUMN_DATE}, ${UsageContract.COLUMN_SCREEN_MILLIS}) " +
                    "VALUES ('2026-01-05', 987654)"
            )
        }
    }

    /** The real database, opened on that file — which is what runs the migration. */
    private fun openCurrent(): ScreenTimeDatabase =
        Room.databaseBuilder(context, ScreenTimeDatabase::class.java, dbName)
            .addMigrations(ScreenTimeDatabase.MIGRATION_3_4)
            // Deliberately absent: fallbackToDestructiveMigration. Here a failed migration
            // must fail the test, not quietly wipe the table and report success — which is
            // exactly the outcome this test exists to rule out.
            .build()

    @Test
    fun history_survives_the_upgrade() {
        createV3()
        val db = openCurrent()
        try {
            val screen = runBlocking { db.deviceUsageDao().getAllRows() }
            assertEquals("день экранного времени должен пережить миграцию", 1, screen.size)
            assertEquals(987654L, screen.first().screenMillis)

            val apps = runBlocking { db.usageDao().getAllRows() }
            assertEquals("строки по приложениям тоже", 1, apps.size)
            assertEquals("com.example.old", apps.first().packageName)
            assertEquals(123456L, apps.first().usageMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun the_new_column_arrives_as_unknown() {
        createV3()
        val db = openCurrent()
        try {
            db.openHelper.readableDatabase.query(
                "SELECT ${UsageContract.COLUMN_UPDATED_AT} FROM ${UsageContract.TABLE_DEVICE_USAGE}"
            ).use { cursor ->
                assertNotNull(cursor)
                cursor.moveToFirst()
                // 0 means "we don't know how far this day was measured" — honest for a row
                // written before the column existed, and what the freshness rule in the
                // companion app reads as "cannot vouch for this".
                assertEquals(0L, cursor.getLong(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun a_fresh_row_still_writes_after_the_upgrade() {
        createV3()
        val db = openCurrent()
        try {
            runBlocking {
                db.deviceUsageDao().insertAll(
                    listOf(DeviceUsageEntity(date = "2026-01-06", screenMillis = 42L, updatedAtMs = 111L))
                )
            }
            val rows = runBlocking { db.deviceUsageDao().getAllRows() }
            assertEquals(2, rows.size)
            assertEquals(111L, rows.first { it.date == "2026-01-06" }.updatedAtMs)
            assertEquals(0L, rows.first { it.date == "2026-01-05" }.updatedAtMs)
        } finally {
            db.close()
        }
    }
}
