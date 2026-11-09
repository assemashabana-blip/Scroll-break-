package com.assem.scrollbreak

import com.assem.scrollbreak.core.BlockStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.TimeZone

/**
 * Time spent today on a limited app's website, in a browser. Android's usage history counts the
 * browser, not the site, so the service measures this itself and it is added to the app's own time
 * when its daily limit is checked. Only today is kept; a new day starts from zero.
 */
object WebUsageStore {
    private var day = 0L
    private val ms = HashMap<String, Long>()

    @Synchronized
    fun load(json: String?) {
        ms.clear()
        day = 0L
        if (json.isNullOrBlank()) return
        try {
            val root = Json.parseToJsonElement(json).jsonObject
            day = root["day"]?.jsonPrimitive?.long ?: 0L
            root["ms"]?.jsonObject?.forEach { (pkg, v) -> ms[pkg] = v.jsonPrimitive.long }
        } catch (_: Exception) {
            ms.clear()
            day = 0L
        }
    }

    @Synchronized
    fun todayMs(pkg: String, now: Long = System.currentTimeMillis()): Long {
        rollOver(now)
        return ms[pkg] ?: 0L
    }

    @Synchronized
    fun add(pkg: String, deltaMs: Long, now: Long = System.currentTimeMillis()) {
        if (deltaMs <= 0) return
        rollOver(now)
        ms[pkg] = (ms[pkg] ?: 0L) + deltaMs
        AppSettings.webUsageJson = buildJsonObject {
            put("day", day)
            put("ms", buildJsonObject { ms.forEach { (k, v) -> put(k, v) } })
        }.toString()
    }

    /** A new calendar day forgets yesterday. */
    private fun rollOver(now: Long) {
        val today = BlockStats.startOfDay(now, TimeZone.getDefault(), 0)
        if (day != today) {
            day = today
            ms.clear()
        }
    }
}
