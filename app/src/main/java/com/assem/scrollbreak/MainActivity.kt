package com.assem.scrollbreak

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.assem.scrollbreak.service.ScrollBreakService
import com.assem.scrollbreak.ui.EventLogScreen
import com.assem.scrollbreak.ui.HomeScreen
import com.assem.scrollbreak.ui.theme.ScrollBreakTheme

class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        enableEdgeToEdge()
        setContent {
            ScrollBreakTheme {
                var screen by rememberSaveable { mutableStateOf("home") }
                BackHandler(enabled = screen != "home") { screen = "home" }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    val m = Modifier.padding(padding)
                    when (screen) {
                        "log" -> EventLogScreen(m)
                        else -> HomeScreen(
                            serviceEnabled = serviceEnabled,
                            onOpenAccessibilitySettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onOpenEventLog = { screen = "log" },
                            modifier = m,
                        )
                    }
                }
            }
        }
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
