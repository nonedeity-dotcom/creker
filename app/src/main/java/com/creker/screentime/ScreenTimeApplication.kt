package com.creker.screentime

import android.app.Application
import com.creker.screentime.work.UsageSyncWorker

class ScreenTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The system forgets detailed events after about a week, so the history has to
        // be mirrored locally even during stretches when the app is never opened.
        UsageSyncWorker.schedule(this)
    }
}
