package com.assem.scrollbreak

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.assem.scrollbreak.core.OemAutostart
import com.assem.scrollbreak.service.ServiceStatus

enum class StepState { DONE, TODO, MANUAL }

/** One line of the onboarding checklist. [MANUAL] means Android gives no way to read the state. */
data class SetupStep(
    val id: String,
    val title: String,
    val help: String,
    val state: StepState,
    val actionLabel: String,
    val action: () -> Unit,
    /** For manual steps: lets the user tick the step off once they have done it. */
    val markDone: (() -> Unit)? = null,
)

/** Builds the checklist that gets a sideloaded install working and keeps it alive. */
object Setup {

    fun steps(context: Context): List<SetupStep> = buildList {
        val serviceOn = ServiceStatus.isEnabled(context)

        add(
            SetupStep(
                "service", "Turn on the ScrollBreak accessibility service",
                "Accessibility settings > Installed apps > ScrollBreak > On. This is what lets the app see which video is showing.",
                if (serviceOn) StepState.DONE else StepState.TODO,
                "Open Accessibility settings",
                { open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            ),
        )

        if (Build.VERSION.SDK_INT >= 33) {
            // A service that is already on proves the restriction is lifted; otherwise ask AppOps.
            val restricted = !serviceOn && restrictedSettingsBlocked(context)
            add(
                SetupStep(
                    "restricted", "Allow restricted settings (sideloaded apps)",
                    "If the accessibility switch is greyed out: App info > ⋮ (top right) > Allow restricted settings, then go back and turn the service on.",
                    when {
                        serviceOn -> StepState.DONE
                        restricted -> StepState.TODO
                        else -> StepState.MANUAL
                    },
                    "Open App info",
                    { open(context, appDetails(context)) },
                ),
            )
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            add(
                SetupStep(
                    "notifications", "Allow notifications",
                    "Needed for the \"ScrollBreak is OFF\" alert and the Dump screen action.",
                    if (granted) StepState.DONE else StepState.TODO,
                    "Open notification settings",
                    {
                        open(
                            context,
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ),
            )
        }

        val power = context.getSystemService(PowerManager::class.java)
        add(
            SetupStep(
                "battery", "Exempt ScrollBreak from battery optimisation",
                "Stops Android from putting the app to sleep in the background.",
                if (power.isIgnoringBatteryOptimizations(context.packageName)) StepState.DONE else StepState.TODO,
                "Allow",
                {
                    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                    if (!open(context, request)) open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
            ),
        )

        if (OemAutostart.needsManualStep(Build.MANUFACTURER)) {
            add(
                SetupStep(
                    "oem", "Allow autostart / background activity (${Build.MANUFACTURER})",
                    "${Build.MANUFACTURER} phones close background apps aggressively. Find ScrollBreak in the list that opens and allow it to start and run in the background, with no restrictions.",
                    if (AppSettings.oemAutostartDone) StepState.DONE else StepState.MANUAL,
                    "Open ${Build.MANUFACTURER} settings",
                    { openOemAutostart(context) },
                    markDone = { AppSettings.oemAutostartDone = true },
                ),
            )
        }
    }

    /** How many steps still need attention (manual ones count until the user ticks them off). */
    fun remaining(steps: List<SetupStep>): Int = steps.count { it.state != StepState.DONE }

    fun remaining(context: Context): Int = remaining(steps(context))

    private fun restrictedSettingsBlocked(context: Context): Boolean = try {
        val ops = context.getSystemService(AppOpsManager::class.java)
        // MODE_ERRORED is how Android reports "restricted" for this op on sideloaded installs.
        ops.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ERRORED
    } catch (_: Throwable) {
        false
    }

    private fun appDetails(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"),
    )

    /** Tries each known vendor screen, then falls back to the app's info page. */
    private fun openOemAutostart(context: Context) {
        for (target in OemAutostart.candidates(Build.MANUFACTURER)) {
            if (open(context, Intent().setComponent(ComponentName(target.pkg, target.cls)))) return
        }
        open(context, appDetails(context))
    }

    /** Starts [intent] from outside an activity; false when nothing handles it. */
    private fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }
}
