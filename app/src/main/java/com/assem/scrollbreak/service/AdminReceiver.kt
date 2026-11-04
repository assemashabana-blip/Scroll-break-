package com.assem.scrollbreak.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Optional "uninstall friction". An active device admin cannot be uninstalled until the admin is
 * deactivated first, which adds a deliberate extra step. It requests no policies.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets ScrollBreak be uninstalled. If you are doing it to scroll, wait and think first."

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        fun isActive(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        fun enableIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Makes ScrollBreak harder to uninstall: you must switch this off first.",
                )
    }
}
