package com.assem.scrollbreak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.assem.scrollbreak.MainActivity
import com.assem.scrollbreak.R

/** Persistent notification with "Dump now" / "Dump in 5 s" actions. */
object DumpNotification {
    const val ACTION_DUMP_NOW = "com.assem.scrollbreak.DUMP_NOW"
    const val ACTION_DUMP_DELAYED = "com.assem.scrollbreak.DUMP_DELAYED"
    private const val CHANNEL_ID = "debug_tools"
    private const val ID = 1001

    fun show(context: Context, status: String = "Open the screen to capture, then tap an action") {
        // Without POST_NOTIFICATIONS (Android 13+) notify() is a silent no-op.
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Debug tools", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scrollbreak)
            .setContentTitle("ScrollBreak")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, "Dump now", action(context, ACTION_DUMP_NOW))
            .addAction(0, "Dump in 5 s", action(context, ACTION_DUMP_DELAYED))
            .build()
        nm.notify(ID, n)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private fun action(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, DumpReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
