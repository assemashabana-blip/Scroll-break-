package com.assem.scrollbreak

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.assem.scrollbreak.core.BlockStats
import com.assem.scrollbreak.core.rules.Purpose
import com.assem.scrollbreak.service.AdminReceiver
import com.assem.scrollbreak.service.DumpNotification
import com.assem.scrollbreak.service.ScrollBreakService
import com.assem.scrollbreak.service.ServiceStatus
import com.assem.scrollbreak.ui.DumpsScreen
import com.assem.scrollbreak.ui.EventLogScreen
import com.assem.scrollbreak.ui.HealthScreen
import com.assem.scrollbreak.ui.HomeActions
import com.assem.scrollbreak.ui.HomeScreen
import com.assem.scrollbreak.ui.HomeState
import com.assem.scrollbreak.ui.RulesScreen
import com.assem.scrollbreak.ui.SettingsScreen
import com.assem.scrollbreak.ui.SetupScreen
import com.assem.scrollbreak.ui.SubScreenBar
import com.assem.scrollbreak.ui.theme.ScrollBreakTheme
import java.io.File

private val titles = mapOf(
    "settings" to "Settings",
    "rules" to "Rules",
    "health" to "Detection health",
    "log" to "Event log",
    "dumps" to "Screen dumps",
    "setup" to "Setup",
)

class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)
    private var blockingEnabled by mutableStateOf(false)

    /** Bumped whenever something outside the app may have changed (resume, a setup action). */
    private var refreshTick by mutableIntStateOf(0)

    private val importRule =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val error = RuleRepository.importUri(this, uri)
            Toast.makeText(
                this, if (error == null) "Rule imported" else "Not imported: $error", Toast.LENGTH_LONG,
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        AppSettings.init(this)
        blockingEnabled = AppSettings.blockingEnabled
        requestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            ScrollBreakTheme {
                // First run on a fresh install lands on the checklist, not on a dead-looking Home.
                var screen by rememberSaveable {
                    mutableStateOf(if (Setup.remaining(this@MainActivity) > 0) "setup" else "home")
                }
                // Where Back goes: Settings opens from Home, its tools open from Settings.
                var back by rememberSaveable { mutableStateOf("home") }
                val goBack = { screen = back; back = "home" }
                val navigate = { to: String -> back = screen; screen = to }
                BackHandler(enabled = screen != "home") { goBack() }
                val tick = refreshTick
                val latency by AppLog.latency.summary.collectAsState()
                val loaded by RuleStore.loaded.collectAsState()
                val errors by RuleStore.errors.collectAsState()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (screen != "home") {
                            SubScreenBar(titles[screen] ?: "ScrollBreak", onBack = goBack)
                        }
                    },
                ) { padding ->
                    val m = Modifier.padding(padding)
                    when (screen) {
                        "log" -> EventLogScreen(m)
                        "dumps" -> DumpsScreen(m)
                        "setup" -> SetupScreen(
                            steps = Setup.steps(this@MainActivity).also { tick.hashCode() },
                            onChanged = { refreshTick++ },
                            modifier = m,
                        )
                        "settings" -> SettingsScreen(
                            adminActive = AdminReceiver.isActive(this@MainActivity).also { tick.hashCode() },
                            onEnableAdmin = { startActivity(AdminReceiver.enableIntent(this@MainActivity)) },
                            onOpenAdminSettings = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                            setupLeft = Setup.remaining(this@MainActivity).also { tick.hashCode() },
                            onNavigate = navigate,
                            modifier = m,
                        )
                        "rules" -> RulesScreen(
                            loaded = loaded,
                            errors = errors,
                            onImport = { importRule.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            onExport = { shareFiles(listOf(RuleRepository.exportFile(this@MainActivity, it)), "Export rule") },
                            onReset = { RuleRepository.reset(this@MainActivity, it.rule.app) },
                            modifier = m,
                        )
                        "health" -> HealthScreen(
                            loaded = loaded,
                            onExportLogs = { shareFiles(AppLog.logFiles(), "Export logs") },
                            modifier = m,
                        )
                        else -> {
                            val stats = remember(tick, latency.count) {
                                BlockStats.summarize(AppLog.readBlockLines(), System.currentTimeMillis())
                            }
                            HomeScreen(
                                state = HomeState(
                                    serviceEnabled = serviceEnabled,
                                    setupRemaining = Setup.remaining(this@MainActivity).also { tick.hashCode() },
                                    blockingEnabled = blockingEnabled,
                                    latency = latency,
                                    stats = stats,
                                    apps = loaded.filter { it.rule.purpose == Purpose.SHORT_VIDEO }
                                        .map { it.rule.app to AppSettings.isAppEnabled(it.rule.app) },
                                ),
                                actions = HomeActions(
                                    openAccessibilitySettings = {
                                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    openSetup = { navigate("setup") },
                                    setBlocking = {
                                        AppSettings.blockingEnabled = it
                                        blockingEnabled = it
                                    },
                                    setAppEnabled = { app, on ->
                                        AppSettings.setAppEnabled(app, on)
                                        refreshTick++
                                    },
                                    navigate = navigate,
                                ),
                                modifier = m,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Hands files to the share sheet through the FileProvider, so no storage permission is needed. */
    private fun shareFiles(files: List<File>, title: String) {
        if (files.isEmpty()) {
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uris = ArrayList(files.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, title))
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The service posts the debug notification on connect; repost now that it may show.
            if (granted && ScrollBreakService.instance != null) DumpNotification.show(this)
            refreshTick++
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = ServiceStatus.isEnabled(this)
        refreshTick++
    }
}
