package com.assem.scrollbreak.core.rules

data class NamedSignature(val name: String, val signature: Signature)

/** Detection rules for one app. Unknown JSON keys are ignored so the schema can grow. */
data class AppRule(
    val app: String,
    val packages: Set<String>,
    /** The full-screen short-video player. */
    val playerSignature: Signature,
    /** Screens that lead into the player (Shorts tab, Reels tab, ...). */
    val feedEntrySignatures: List<NamedSignature> = emptyList(),
    /** In-app chat screens, used to recognise "a friend sent this". */
    val dmSignature: Signature? = null,
    /** Nodes whose text identifies the video on screen (author handle, caption, ...). */
    val fingerprintNodes: List<FingerprintNode> = emptyList(),
    /**
     * The scrollable whose VIEW_SCROLLED events carry the current video index (from == to).
     * Matched against the event source; null when the app reports no usable index.
     */
    val scrollSignal: Leaf? = null,
) {
    fun isPlayer(screen: ScreenView) = playerSignature.evaluate(screen)

    /** Names of the feed-entry signatures that currently match. */
    fun matchingFeedEntries(screen: ScreenView): List<String> =
        feedEntrySignatures.filter { it.signature.evaluate(screen) }.map { it.name }

    fun isDm(screen: ScreenView) = dmSignature?.evaluate(screen) ?: false

    fun fingerprint(screen: ScreenView): Fingerprint = Fingerprint.compute(fingerprintNodes, screen)
}

class RuleSet(rules: List<AppRule>) {
    val rules: List<AppRule> = rules
    private val byPackage: Map<String, AppRule> =
        rules.flatMap { r -> r.packages.map { it to r } }.toMap()

    fun forPackage(pkg: String): AppRule? = byPackage[pkg]
}
