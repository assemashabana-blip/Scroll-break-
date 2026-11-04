package com.assem.scrollbreak.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.assem.scrollbreak.AppLog

class DumpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val service = ScrollBreakService.instance
            if (service == null) {
                Toast.makeText(context, "ScrollBreak service is not running", Toast.LENGTH_LONG).show()
                return
            }
            // "Now" still waits briefly so the notification shade can close first.
            val delay = if (intent.action == DumpNotification.ACTION_DUMP_DELAYED) 5000L else 700L
            service.requestDump(delay)
        } catch (t: Throwable) {
            AppLog.error("DumpReceiver failed", t)
        }
    }
}
