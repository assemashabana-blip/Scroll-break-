package com.assem.scrollbreak.core

/** Decides what the periodic watchdog should do about the "protection is off" alert. */
object Watchdog {
    enum class Decision { ALERT, CLEAR, NONE }

    /**
     * @param serviceEnabled the accessibility service is switched on in system settings
     * @param alertShown the "protection is off" notification is currently posted
     */
    fun decide(serviceEnabled: Boolean, alertShown: Boolean): Decision = when {
        !serviceEnabled && !alertShown -> Decision.ALERT
        serviceEnabled && alertShown -> Decision.CLEAR
        else -> Decision.NONE
    }
}

/**
 * Where to find the "autostart" / "background activity" screen on phones whose maker kills
 * background services aggressively. These are the vendors' own activities and change between
 * versions, so each is only a candidate to try; the caller falls back to the app's info page.
 */
object OemAutostart {
    data class Target(val pkg: String, val cls: String)

    /** Candidate screens for [manufacturer], most specific first; empty when no special handling is needed. */
    fun candidates(manufacturer: String): List<Target> = when (manufacturer.trim().lowercase()) {
        "xiaomi", "redmi", "poco" -> listOf(
            Target("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        "samsung" -> listOf(
            Target("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            Target("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        "oppo", "realme", "oneplus" -> listOf(
            Target("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Target("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            Target("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
        )
        "vivo", "iqoo" -> listOf(
            Target("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Target("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        "huawei", "honor" -> listOf(
            Target("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Target("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        "asus" -> listOf(
            Target("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        )
        else -> emptyList()
    }

    /** True for vendors known to need a manual autostart / background exemption. */
    fun needsManualStep(manufacturer: String): Boolean = candidates(manufacturer).isNotEmpty()
}
