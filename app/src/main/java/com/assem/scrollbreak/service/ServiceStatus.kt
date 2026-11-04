package com.assem.scrollbreak.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ServiceStatus {
    /** True when ScrollBreak's accessibility service is switched on in system settings. */
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, ScrollBreakService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
