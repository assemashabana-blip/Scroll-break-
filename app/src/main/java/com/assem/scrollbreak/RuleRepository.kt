package com.assem.scrollbreak

import android.content.Context
import android.net.Uri
import com.assem.scrollbreak.core.rules.AppRule
import com.assem.scrollbreak.core.rules.RuleParser
import com.assem.scrollbreak.core.rules.RuleSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class RuleSource { BUILT_IN, IMPORTED }

/** A parsed rule together with where it came from and its original JSON (for export). */
data class LoadedRule(val rule: AppRule, val source: RuleSource, val fileName: String, val json: String)

/** The rules in force, shared by the service and the UI. Reloaded after an import or reset. */
object RuleStore {
    private val _loaded = MutableStateFlow<List<LoadedRule>>(emptyList())
    private val _errors = MutableStateFlow<List<String>>(emptyList())
    @Volatile private var ruleSet = RuleSet(emptyList())

    val loaded: StateFlow<List<LoadedRule>> = _loaded.asStateFlow()
    val errors: StateFlow<List<String>> = _errors.asStateFlow()
    val current: RuleSet get() = ruleSet

    fun reload(context: Context) {
        val result = RuleRepository.load(context)
        _loaded.value = result.rules
        _errors.value = result.errors
        ruleSet = RuleSet(result.rules.map { it.rule })
    }
}

/**
 * Loads the rules shipped in assets/rules, then lets files in filesDir/rules replace them
 * (matched by the "app" name). A broken file is logged and skipped, never fatal.
 */
object RuleRepository {
    class LoadResult(val rules: List<LoadedRule>, val errors: List<String>)

    fun overrideDir(context: Context): File = File(context.applicationContext.filesDir, "rules")

    fun load(context: Context): LoadResult {
        val byApp = LinkedHashMap<String, LoadedRule>()
        val errors = ArrayList<String>()
        fun add(source: RuleSource, fileName: String, json: String) {
            try {
                val rule = RuleParser.parse(json)
                byApp[rule.app] = LoadedRule(rule, source, fileName, json)
            } catch (e: Exception) {
                val message = "$fileName: ${e.message}"
                errors += message
                AppLog.error("rule rejected: $message")
                AppLog.events.add(System.currentTimeMillis(), "-", "RULE_ERROR", fileName, e.message.orEmpty())
            }
        }

        val assets = context.assets
        assets.list("rules").orEmpty().filter { it.endsWith(".json") }.sorted().forEach { name ->
            add(RuleSource.BUILT_IN, name, assets.open("rules/$name").bufferedReader().use { it.readText() })
        }
        overrideDir(context).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.forEach { f ->
            add(RuleSource.IMPORTED, f.name, f.readText())
        }

        AppLog.events.add(
            System.currentTimeMillis(), "-", "RULES", "loaded", byApp.keys.joinToString(", ").ifEmpty { "none" },
        )
        return LoadResult(byApp.values.toList(), errors)
    }

    /** Validates [json] and, only if it parses, stores it as an override and reloads. Returns an error message or null. */
    fun import(context: Context, json: String): String? {
        val rule = try {
            RuleParser.parse(json)
        } catch (e: Exception) {
            return e.message ?: "invalid rule"
        }
        val safeName = rule.app.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "rule" }
        val dir = overrideDir(context).apply { mkdirs() }
        File(dir, "$safeName.json").writeText(json)
        RuleStore.reload(context)
        return null
    }

    fun importUri(context: Context, uri: Uri): String? = try {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return "could not read the file"
        import(context, text)
    } catch (e: Exception) {
        e.message ?: "could not read the file"
    }

    /** Removes any imported override for [app], so the built-in rule applies again. */
    fun reset(context: Context, app: String) {
        overrideDir(context).listFiles { f -> f.extension == "json" }.orEmpty().forEach { f ->
            val parsedApp = try { RuleParser.parse(f.readText()).app } catch (_: Exception) { null }
            if (parsedApp == app || parsedApp == null) f.delete()
        }
        RuleStore.reload(context)
    }

    /** Writes a rule's JSON to a file that can be shared. */
    fun exportFile(context: Context, loaded: LoadedRule): File {
        val dir = File(context.applicationContext.filesDir, "exports").apply { mkdirs() }
        return File(dir, "${loaded.rule.app}.json").also { it.writeText(loaded.json) }
    }
}
