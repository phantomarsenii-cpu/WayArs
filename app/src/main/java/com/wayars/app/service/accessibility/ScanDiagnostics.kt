package com.wayars.app.service.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DiagnosticEntry(
    val packageName: String,
    val timestampMillis: Long,
    val matchedSupportedApp: Boolean,
    val windowFound: Boolean,
    val textsCollected: Int,
    /** Whatever the parser could pull out, even if incomplete — e.g. "earnings=null km=3.1 min=null". */
    val parsedSummary: String?,
    /**
     * The raw strings scraped from the screen for this event, capped so the
     * diagnostics list can't blow up. Only meant for on-device debugging of
     * unsupported/misparsed app layouts (e.g. Stuart) — lets us see exactly
     * what ScreenTextParser had to work with instead of guessing blind.
     */
    val rawTexts: List<String> = emptyList()
)

/**
 * TEMPORARY diagnostic tool. Records exactly ONE entry per accessibility
 * event, with the full outcome already known (not two separate before/after
 * calls) — an earlier version recorded a cheap "0 texts" entry immediately
 * and a real one after text collection, but the dedup logic (meant to stop
 * the list filling up with 50 copies of the same app while scrolling)
 * matched on package name alone and silently ate the second, actually
 * useful entry every time, since it shared a package name with the one
 * right above it. Every real reading was hidden behind a fake "0".
 */
object ScanDiagnostics {
    private const val MAX_ENTRIES = 20

    private val _recentPackages = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val recentPackages: StateFlow<List<DiagnosticEntry>> = _recentPackages

    /**
     * While true, [record] is a no-op. Lets the Diagnostics screen freeze
     * the list on demand — entries scroll off the capped list fast enough
     * (even after fixing the debounce bug that made it worse) that reading
     * one before it's evicted was still a race against real time.
     */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused

    fun setPaused(value: Boolean) {
        _paused.value = value
    }

    /** Raw text lists are capped per-entry too — only need enough to spot the pattern. */
    private const val MAX_RAW_TEXTS_PER_ENTRY = 40

    fun record(
        packageName: String,
        matchedSupportedApp: Boolean,
        windowFound: Boolean = false,
        textsCollected: Int = 0,
        parsedSummary: String? = null,
        rawTexts: List<String> = emptyList()
    ) {
        // Written unconditionally, independent of [_paused] below — [_paused]
        // only ever froze the (now-removed) live on-screen list; the file
        // must keep capturing every single event for the whole Active
        // session no matter what, since it's the only place these are still
        // readable after the fact.
        run {
            val line = buildString {
                append(packageName)
                append(" | matched=").append(matchedSupportedApp)
                append(" | window=").append(windowFound)
                append(" | texts=").append(textsCollected)
                if (parsedSummary != null) append(" | ").append(parsedSummary)
            }
            ScanLogFile.append(line)
            if (rawTexts.isNotEmpty()) {
                ScanLogFile.append("  raw: " + rawTexts.joinToString(" | "))
            }
        }

        if (_paused.value) return
        val entry = DiagnosticEntry(
            packageName = packageName,
            timestampMillis = System.currentTimeMillis(),
            matchedSupportedApp = matchedSupportedApp,
            windowFound = windowFound,
            textsCollected = textsCollected,
            parsedSummary = parsedSummary,
            rawTexts = rawTexts.take(MAX_RAW_TEXTS_PER_ENTRY)
        )
        _recentPackages.value = (listOf(entry) + _recentPackages.value).take(MAX_ENTRIES)
    }

    fun clear() {
        _recentPackages.value = emptyList()
    }
}
