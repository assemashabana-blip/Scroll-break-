package com.assem.scrollbreak

import android.content.Context
import com.assem.scrollbreak.core.rules.AppRule
import com.assem.scrollbreak.core.rules.RuleParser
import com.assem.scrollbreak.core.rules.RuleSet
import java.io.File

/**
 * Loads the rules shipped in assets/rules, then lets files in filesDir/rules replace them
 * (matched by the "app" name). A broken file is logged and skipped, never fatal.
 */
object RuleRepository {
    fun overrideDir(context: Context): File = File(context.applicationContext.filesDir, "rules")

    fun load(context: Context): RuleSet {
        val byApp = LinkedHashMap<String, AppRule>()
        fun add(source: String, json: String) {
            try {
                val rule = RuleParser.parse(json)
                byApp[rule.app] = rule
            } catch (e: Exception) {
                AppLog.error("rule $source rejected: ${e.message}")
                AppLog.events.add(System.currentTimeMillis(), "-", "RULE_ERROR", source, e.message.orEmpty())
            }
        }

        val assets = context.assets
        assets.list("rules").orEmpty().filter { it.endsWith(".json") }.forEach { name ->
            add("asset:$name", assets.open("rules/$name").bufferedReader().use { it.readText() })
        }
        overrideDir(context).listFiles { f -> f.extension == "json" }.orEmpty().forEach { f ->
            add("override:${f.name}", f.readText())
        }

        AppLog.events.add(
            System.currentTimeMillis(), "-", "RULES", "loaded", byApp.keys.joinToString(", ").ifEmpty { "none" },
        )
        return RuleSet(byApp.values.toList())
    }
}
