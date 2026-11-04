package com.assem.scrollbreak

import android.content.Context
import android.util.Log
import com.assem.scrollbreak.core.EventLog
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
        errorFile = File(dir, "errors.log")
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
