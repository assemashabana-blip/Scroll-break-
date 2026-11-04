package com.assem.scrollbreak

import android.app.Application
import com.assem.scrollbreak.service.WatchdogWorker

/**
 * Process entry point. Sets up logging and settings before any component runs, and records any
 * uncaught exception to the local error log before the system handles it, so a crash always
 * leaves a trace to export even when logcat has already rolled over.
 */
class ScrollBreakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppSettings.init(this)
        WatchdogWorker.schedule(this)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.error("UNCAUGHT on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
