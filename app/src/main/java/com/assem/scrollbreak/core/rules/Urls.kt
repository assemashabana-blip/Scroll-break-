package com.assem.scrollbreak.core.rules

/** A named URL shape that identifies a short-video page, matched against [Urls.normalize] output. */
data class UrlPattern(val name: String, val regex: Regex)

object Urls {
    private val urlLike = Regex("""^[a-z0-9-]+(\.[a-z0-9-]+)+(:[0-9]+)?([/?#].*)?$""", RegexOption.IGNORE_CASE)

    /**
     * Canonical "host/path" form of a browser URL-bar text: no scheme, host lowercased, no query,
     * fragment or trailing slash. Null when the text is not a URL (a search box, a hint, a query).
     */
    fun normalize(raw: String?): String? {
        val text = raw?.trim() ?: return null
        val noScheme = text.removePrefix("https://").removePrefix("http://").removePrefix("HTTPS://").removePrefix("HTTP://")
        if (noScheme.any { it.isWhitespace() } || !urlLike.matches(noScheme)) return null
        val end = noScheme.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) noScheme.length else it }
        val withoutQuery = noScheme.substring(0, end)
        val slash = withoutQuery.indexOf('/')
        val host = (if (slash < 0) withoutQuery else withoutQuery.substring(0, slash)).lowercase()
        val path = if (slash < 0) "" else withoutQuery.substring(slash)
        return (host + path).trimEnd('/')
    }

    fun firstMatch(patterns: List<UrlPattern>, url: String): UrlPattern? =
        patterns.firstOrNull { it.regex.containsMatchIn(url) }
}

/**
 * Remembers the last real URL seen per browser. Some browsers hide the URL bar while you scroll;
 * then there is no text to read, and the page you are on has not changed, so the last URL stands.
 */
class UrlTracker {
    private val cache = HashMap<String, String>()

    /**
     * [raw] is the URL bar's text, or null when the bar is absent or hidden.
     * Returns the URL to evaluate, or null when there is no page URL (e.g. the omnibox is a search box).
     */
    @Synchronized
    fun observe(pkg: String, raw: String?): String? {
        if (raw == null) return cache[pkg]
        val url = Urls.normalize(raw)
        if (url == null) {
            cache.remove(pkg)
            return null
        }
        cache[pkg] = url
        return url
    }

    @Synchronized
    fun forget(pkg: String) {
        cache.remove(pkg)
    }
}
