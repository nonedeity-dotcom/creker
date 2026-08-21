package com.creker.screentime.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.creker.screentime.ScreenTimeApplication
import com.creker.screentime.data.system.UsageAccess
import java.util.concurrent.TimeUnit

/**
 * Copies the system's usage events into the local database in the background, so that
 * the week and month views keep working after the events themselves have expired.
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

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
