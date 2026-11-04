package com.assem.scrollbreak.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.AppSettings

/** Handles the notification actions; debug builds also export it for `adb shell am broadcast`. */
class DumpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == ACTION_SET_BLOCKING) {
                AppSettings.init(context)
                AppSettings.blockingEnabled = intent.getBooleanExtra("enabled", false)
                return
            }
            if (intent.action == ACTION_RUN_WATCHDOG) {
                WatchdogWorker.runNow(context)
                return
            }
            val service = ScrollBreakService.instance
            if (service == null) {
                Toast.makeText(context, "ScrollBreak service is not running", Toast.LENGTH_LONG).show()
                return
            }
            if (intent.action == ACTION_SHOW_OVERLAY) {
                service.showTestOverlay()
                return
            }
            if (intent.action == ACTION_INJECT_FAULT) {
                service.injectFault()
                return
            }
            // "Now" still waits briefly so the notification shade can close first.
            val delay = if (intent.action == DumpNotification.ACTION_DUMP_DELAYED) 5000L else 700L
            service.requestDump(delay)
        } catch (t: Throwable) {
            AppLog.error("DumpReceiver failed", t)
        }
    }

    companion object {
        const val ACTION_SET_BLOCKING = "com.assem.scrollbreak.SET_BLOCKING"
        const val ACTION_SHOW_OVERLAY = "com.assem.scrollbreak.SHOW_OVERLAY"
        const val ACTION_INJECT_FAULT = "com.assem.scrollbreak.INJECT_FAULT"
        const val ACTION_RUN_WATCHDOG = "com.assem.scrollbreak.RUN_WATCHDOG"
    }
}
