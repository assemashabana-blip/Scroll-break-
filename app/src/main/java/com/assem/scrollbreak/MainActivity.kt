package com.assem.scrollbreak

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.assem.scrollbreak.service.DumpNotification
import com.assem.scrollbreak.service.ScrollBreakService
import com.assem.scrollbreak.ui.DumpsScreen
import com.assem.scrollbreak.ui.EventLogScreen
import com.assem.scrollbreak.ui.HomeScreen
import com.assem.scrollbreak.ui.theme.ScrollBreakTheme

class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)
    private var blockingEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        AppSettings.init(this)
        blockingEnabled = AppSettings.blockingEnabled
        requestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            ScrollBreakTheme {
                var screen by rememberSaveable { mutableStateOf("home") }
                BackHandler(enabled = screen != "home") { screen = "home" }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    val m = Modifier.padding(padding)
                    when (screen) {
                        "log" -> EventLogScreen(m)
                        "dumps" -> DumpsScreen(m)
                        else -> HomeScreen(
                            serviceEnabled = serviceEnabled,
                            onOpenAccessibilitySettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onOpenEventLog = { screen = "log" },
                            onOpenDumps = { screen = "dumps" },
                            blockingEnabled = blockingEnabled,
                            onBlockingChange = {
                                AppSettings.blockingEnabled = it
                                blockingEnabled = it
                            },
                            modifier = m,
                        )
                    }
                }
            }
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The service posts the debug notification on connect; repost now that it may show.
            if (granted && ScrollBreakService.instance != null) DumpNotification.show(this)
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isServiceEnabled(this)
    }

    private fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, ScrollBreakService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
