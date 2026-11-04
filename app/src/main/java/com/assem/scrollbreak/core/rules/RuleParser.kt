package com.assem.scrollbreak.core.rules

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

class RuleParseException(message: String) : Exception(message)

object RuleParser {
    private const val SUPPORTED_SCHEMA = 1
    private val leafKeys = setOf("viewId", "className", "text", "desc", "match", "selected", "clickable", "visible")
    private val composites = setOf("allOf", "anyOf", "not")

    fun parse(json: String): AppRule {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            throw RuleParseException("invalid JSON: ${e.message?.lineSequence()?.first()}")
        } as? JsonObject ?: throw RuleParseException("top level must be an object")

        val schema = root.int("schemaVersion", "schemaVersion")
        if (schema != SUPPORTED_SCHEMA) throw RuleParseException("unsupported schemaVersion $schema")

        val packages = root.array("packages", "packages").mapIndexed { i, e ->
            e.string("packages[$i]")
        }.toSet()
        if (packages.isEmpty()) throw RuleParseException("packages must not be empty")

        val entries = namedSignatures(root, "feedEntrySignatures")
        val sources = namedSignatures(root, "allowSources")

        val fingerprint = (root["fingerprintNodes"] as? JsonArray).orEmpty().mapIndexed { i, e ->
            val path = "fingerprintNodes[$i]"
            val obj = e as? JsonObject ?: throw RuleParseException("$path must be an object")
            FingerprintNode(
                obj.string("name", "$path.name"),
                leaf(JsonObject(obj - "name"), path),
            )
        }

        val urlPatterns = (root["urlPatterns"] as? JsonArray).orEmpty().mapIndexed { i, e ->
            val path = "urlPatterns[$i]"
            val obj = e as? JsonObject ?: throw RuleParseException("$path must be an object")
            val regex = obj.string("regex", "$path.regex")
            UrlPattern(
                obj.string("name", "$path.name"),
                try {
                    Regex(regex)
                } catch (ex: java.util.regex.PatternSyntaxException) {
                    throw RuleParseException("$path.regex is not a valid regular expression")
                },
            )
        }
        val urlBarIds = (root["urlBarIds"] as? JsonArray).orEmpty().mapIndexed { i, e -> e.string("urlBarIds[$i]") }
        if (urlPatterns.isNotEmpty() && urlBarIds.isEmpty()) {
            throw RuleParseException("urlPatterns needs urlBarIds to read the URL from")
        }
        val player = root["playerSignature"]?.let { signature(it, "playerSignature") }
        if (player == null && urlPatterns.isEmpty()) throw RuleParseException("playerSignature missing")

        return AppRule(
            app = root.string("app", "app"),
            packages = packages,
            playerSignature = player,
            feedEntrySignatures = entries,
            allowSources = sources,
            fingerprintNodes = fingerprint,
            urlBarIds = urlBarIds,
            urlPatterns = urlPatterns,
            scrollSignal = root["scrollSignal"]?.let {
                leaf(it as? JsonObject ?: throw RuleParseException("scrollSignal must be an object"), "scrollSignal")
            },
        )
    }

    private fun namedSignatures(root: JsonObject, key: String): List<NamedSignature> =
        (root[key] as? JsonArray).orEmpty().mapIndexed { i, e ->
            val obj = e as? JsonObject ?: throw RuleParseException("$key[$i] must be an object")
            NamedSignature(
                obj.string("name", "$key[$i].name"),
                signature(obj["signature"] ?: throw RuleParseException("$key[$i].signature missing"), "$key[$i].signature"),
            )
        }

    private fun signature(e: JsonElement, path: String): Signature {
        val obj = e as? JsonObject ?: throw RuleParseException("$path must be an object")
        val compositeKeys = obj.keys.intersect(composites)
        if (compositeKeys.isEmpty()) return leaf(obj, path)
        if (compositeKeys.size > 1 || obj.keys.size > 1) {
            throw RuleParseException("$path: a composite must have exactly one of allOf/anyOf/not and nothing else")
        }
        return when (val key = compositeKeys.first()) {
            "not" -> Not(signature(obj.getValue(key), "$path.not"))
            else -> {
                val items = (obj.getValue(key) as? JsonArray)
                    ?: throw RuleParseException("$path.$key must be an array")
                if (items.isEmpty()) throw RuleParseException("$path.$key must not be empty")
                val parsed = items.mapIndexed { i, item -> signature(item, "$path.$key[$i]") }
                if (key == "allOf") AllOf(parsed) else AnyOf(parsed)
            }
        }
    }

    private fun leaf(obj: JsonObject, path: String): Leaf {
        val unknown = obj.keys - leafKeys
        if (unknown.isNotEmpty()) throw RuleParseException("$path: unknown key ${unknown.first()}")
        val leaf = Leaf(
            viewId = obj.optString("viewId", path),
            className = obj.optString("className", path),
            text = obj.optString("text", path),
            desc = obj.optString("desc", path),
            match = obj.optString("match", path)?.let {
                when (it) {
                    "equals" -> MatchMode.EQUALS
                    "contains" -> MatchMode.CONTAINS
                    "startsWith" -> MatchMode.STARTS_WITH
                    else -> throw RuleParseException("$path.match must be equals, contains or startsWith")
                }
            } ?: MatchMode.CONTAINS,
            selected = obj.optBool("selected", path),
            clickable = obj.optBool("clickable", path),
            visible = obj.optBool("visible", path) ?: true,
        )
        if (leaf.viewId == null && leaf.className == null && leaf.text == null &&
            leaf.desc == null && leaf.selected == null && leaf.clickable == null
        ) throw RuleParseException("$path: leaf needs at least one of viewId, className, text, desc, selected, clickable")
        return leaf
    }

    private fun JsonObject.optString(key: String, path: String): String? {
        val v = this[key] ?: return null
        return (v as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw RuleParseException("$path.$key must be a string")
    }

    private fun JsonObject.optBool(key: String, path: String): Boolean? {
        val v = this[key] ?: return null
        return (v as? JsonPrimitive)?.booleanOrNull ?: throw RuleParseException("$path.$key must be true or false")
    }

    private fun JsonObject.string(key: String, path: String): String =
        this[key]?.string(path) ?: throw RuleParseException("$path missing")

    private fun JsonElement.string(path: String): String =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw RuleParseException("$path must be a string")

    private fun JsonObject.int(key: String, path: String): Int =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: throw RuleParseException("$path missing or not a number")

    private fun JsonObject.array(key: String, path: String): JsonArray =
        this[key] as? JsonArray ?: throw RuleParseException("$path missing or not an array")
}
