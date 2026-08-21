package com.creker.screentime.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.creker.screentime.ScreenTimeApplication
import com.creker.screentime.data.system.UsageAccess
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Copies the system's usage events into the local database once a day, so that the
 * week and month views keep working after the events themselves have expired.
 */
class UsageSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!UsageAccess.isGranted(applicationContext)) return Result.success()
        val container = (applicationContext as? ScreenTimeApplication)?.container ?: return Result.retry()
        return runCatching { container.usageRepository.sync() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val WORK_NAME = "usage-sync"

        /**
         * Runs daily, first firing just after the upcoming midnight so that the day
         * being stored has actually ended. WorkManager may defer a run while the
         * device is in Doze; the sync re-derives whole days, so a late run loses
         * nothing as long as it lands inside the system's event retention.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP: re-scheduling on every launch would push the run past midnight
                // for anyone who opens the app daily.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun millisUntilNextMidnight(): Long {
            val now = ZonedDateTime.now()
            val nextMidnight = LocalDate.now(now.zone).plusDays(1).atStartOfDay(now.zone)
            return java.time.Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0L)
        }
    }
}
