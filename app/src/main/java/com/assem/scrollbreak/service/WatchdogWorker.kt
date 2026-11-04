package com.assem.scrollbreak.service

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.core.Watchdog
import java.util.concurrent.TimeUnit

/**
 * Safety net for the case the service gets turned off (by the user, an OEM "optimiser" or an
 * update) while the app is not running: checks system settings and raises or clears the alert.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        try {
            val ctx = applicationContext
            val enabled = ServiceStatus.isEnabled(ctx)
            val shown = ProtectionAlert.isShown(ctx)
            val decision = Watchdog.decide(enabled, shown)
            Log.i("ScrollBreak", "WATCHDOG enabled=$enabled alertShown=$shown -> $decision")
            when (decision) {
                Watchdog.Decision.ALERT -> ProtectionAlert.show(ctx)
                Watchdog.Decision.CLEAR -> ProtectionAlert.cancel(ctx)
                Watchdog.Decision.NONE -> Unit
            }
        } catch (t: Throwable) {
            AppLog.error("watchdog failed", t)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "scrollbreak-watchdog"

        /** WorkManager's periodic minimum is 15 minutes; the work survives reboots on its own. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        /** Runs one check now (debug aid and "check again" button). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WatchdogWorker>().build())
        }
    }
}
