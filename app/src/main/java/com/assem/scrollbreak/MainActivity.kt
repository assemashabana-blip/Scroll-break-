package com.assem.scrollbreak

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/** How deep a page is: Home 0, Settings 1, the tools opened from Settings 2. Decides the slide direction. */
private fun depth(screen: String) = when (screen) {
    "home" -> 0
    "settings" -> 1
    else -> 2
}

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
        setContent {
            // Null follows the phone; the Settings toggle stores an explicit choice.
            var darkPref by remember { mutableStateOf(AppSettings.darkMode) }
            val dark = darkPref ?: isSystemInDarkTheme()
            // Status and navigation bar icons follow the app's theme, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            ScrollBreakTheme(darkTheme = dark) {
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
                // Home's data lives up here, outside the page animation: it is read off the main
                // thread, and the last value stays on screen while a fresh one loads.
                val setupLeft = remember(tick) { Setup.remaining(this@MainActivity) }
                val stats by produceState(BlockStats.Summary.EMPTY, tick, latency.count) {
                    value = withContext(Dispatchers.IO) {
                        BlockStats.summarize(AppLog.readBlockLines(), System.currentTimeMillis())
                    }
                }
                val usageAccess = remember(tick) { AppUsage.hasAccess(this@MainActivity) }
                val usage by produceState<AppUsage.Result?>(null, tick, usageAccess) {
                    value = withContext(Dispatchers.IO) { AppUsage.summarize(this@MainActivity) }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    // Home stays composed underneath the other pages, so going back to it only slides
                    // the page away; rebuilding Home (charts, cards, tiles) on every return dropped
                    // frames. It fades and drifts left while covered, drawn from layer values only.
                    val onHome = screen == "home"
                    val shown by animateFloatAsState(if (onHome) 1f else 0f, tween(320), label = "home")
                    Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .graphicsLayer {
                                alpha = shown
                                translationX = -(1f - shown) * size.width / 5
                            }
                            // Hidden from TalkBack while another page covers it.
                            .then(if (onHome) Modifier else Modifier.clearAndSetSemantics {}),
                    ) {
                    HomeScreen(
                        state = HomeState(
                            serviceEnabled = serviceEnabled,
                            setupRemaining = setupLeft,
                            blockingEnabled = blockingEnabled,
                            stats = stats,
                            usageAccess = usageAccess,
                            usage = usage,
                            apps = loaded.filter { it.rule.purpose == Purpose.SHORT_VIDEO }
                                .map { it.rule.app to AppSettings.isAppEnabled(it.rule.app) },
                        ),
                        actions = HomeActions(
                            openAccessibilitySettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            openSetup = { navigate("setup") },
                            openUsageAccess = { startActivity(AppUsage.accessIntent(this@MainActivity)) },
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
                        modifier = Modifier,
                    )
                    }

                    // The other pages. The title bar lives inside each page, so it slides with it.
                    // Going deeper slides the new page in from the right; going back slides it out.
                    val m = Modifier
                    AnimatedContent(
                        targetState = screen.takeIf { it != "home" },
                        transitionSpec = {
                            val deeper = depth(targetState ?: "home") > depth(initialState ?: "home")
                            val dir = if (deeper) 1 else -1
                            (slideInHorizontally(tween(320)) { dir * it / 5 } + fadeIn(tween(320)))
                                .togetherWith(slideOutHorizontally(tween(320)) { -dir * it / 5 } + fadeOut(tween(200)))
                        },
                        label = "screen",
                    ) { target ->
                    if (target != null) {
                    // Opaque, and it takes the touches, so Home underneath never gets them.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(padding)) {
                    SubScreenBar(titles[target] ?: "ScrollBreak", onBack = goBack)
                    Box(Modifier.weight(1f)) {
                    when (target) {
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
                            setupLeft = setupLeft,
                            onNavigate = navigate,
                            dark = dark,
                            followsPhone = darkPref == null,
                            onDarkChange = { darkPref = it; AppSettings.darkMode = it },
                            onFollowPhone = { darkPref = null; AppSettings.darkMode = null },
                            showDeveloperTools = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
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
                        else -> Unit
                    }
                    }
                    }
                    }
                    }
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
