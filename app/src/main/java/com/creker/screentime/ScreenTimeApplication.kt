package com.creker.screentime

import android.app.Application
import com.creker.screentime.work.UsageSyncWorker
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ScreenTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The system forgets detailed events after about a week, so the history has to
        // be mirrored locally even during stretches when the app is never opened.
        // Scheduling this isn't needed for the first frame, so it's moved off
        // Application.onCreate()'s own thread -- WorkManager's enqueue call does some
        // work on the calling thread, and onCreate() runs before the first activity
        // does, so anything synchronous here adds straight onto the app's
        // launch-to-first-frame time. GlobalScope is deliberate: this is a one-shot
        // task tied to the process itself, with no shorter-lived scope to attach to.
        GlobalScope.launch(Dispatchers.Default) { UsageSyncWorker.schedule(this@ScreenTimeApplication) }
    }
}
