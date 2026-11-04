package com.assem.scrollbreak

import android.content.Context
import android.util.Log
import com.assem.scrollbreak.core.EventLog
import com.assem.scrollbreak.core.LatencyStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Process-wide logging: live event log (memory) and rotating error log (file). */
object AppLog {
    private const val TAG = "ScrollBreak"
    private const val MAX_FILE_BYTES = 256 * 1024L

    val events = EventLog(500)

    private val _serviceConnected = MutableStateFlow(false)
    /** True while the accessibility service is bound in this process. */
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    @Volatile private var errorFile: File? = null

    fun init(context: Context) {
        val dir = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        if (errorFile == null) {
            errorFile = File(dir, "errors.log")
            loadLatencyHistory(dir)
        }
    }

    /** Reaction time of recent blocks, shown in the app. Rebuilt from blocks.csv on start. */
    val latency = LatencyStats()

    /** Appends one block to logs/blocks.csv (epoch ms, app, reason, latency ms) for stats. Never throws. */
    @Synchronized
    fun recordBlock(app: String, reason: String, latencyMs: Long) {
        latency.add(latencyMs)
        try {
            val dir = errorFile?.parentFile ?: return
            File(dir, "blocks.csv").appendText("${System.currentTimeMillis()},$app,$reason,$latencyMs\n")
        } catch (_: Throwable) {
        }
    }

    private fun loadLatencyHistory(dir: File) {
        try {
            val csv = File(dir, "blocks.csv")
            if (!csv.exists()) return
            csv.readLines().takeLast(200).forEach { line ->
                line.split(',').getOrNull(3)?.toLongOrNull()?.let { latency.add(it) }
            }
        } catch (_: Throwable) {
        }
    }

    /** Never throws: the service must not crash because logging failed. */
    @Synchronized
    fun error(message: String, t: Throwable? = null) {
        Log.e(TAG, message, t)
        try {
            val file = errorFile ?: return
            if (file.length() > MAX_FILE_BYTES) {
                val old = File(file.parentFile, "errors.1.log")
                old.delete()
                file.renameTo(old)
            }
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$ts $message${t?.let { "\n" + Log.getStackTraceString(it) } ?: ""}\n")
        } catch (_: Throwable) {
        }
    }
}
