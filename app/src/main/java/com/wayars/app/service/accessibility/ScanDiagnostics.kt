package com.wayars.app.service.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DiagnosticEntry(
    val packageName: String,
    val timestampMillis: Long,
    val matchedSupportedApp: Boolean,
    val textsCollected: Int
)

/**
 * TEMPORARY diagnostic tool — not for end users, for figuring out the exact
 * real package name of Bolt/Wolt on this specific device/build, since guessed
 * package names have repeatedly turned out wrong and there's no PC/adb handy
 * to check with `dumpsys window`. Records every foreground-app package the
 * accessibility service sees (regardless of whether it's on the supported
 * list) into a small rolling in-memory list — nothing is written to disk,
 * nothing leaves the device. Once the real Bolt/Wolt package names are
 * confirmed from this screen, the XML allow-list gets the correct names and
 * this can go back to only logging supported-app events.
 */
object ScanDiagnostics {
    private const val MAX_ENTRIES = 15

    private val _recentPackages = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val recentPackages: StateFlow<List<DiagnosticEntry>> = _recentPackages

    fun record(packageName: String, matchedSupportedApp: Boolean, textsCollected: Int) {
        val entry = DiagnosticEntry(
            packageName = packageName,
            timestampMillis = System.currentTimeMillis(),
            matchedSupportedApp = matchedSupportedApp,
            textsCollected = textsCollected
        )
        val current = _recentPackages.value
        // Skip if it's identical to the very last entry (avoid the list
        // filling up with 50 copies of the same app while you're just
        // scrolling around inside it).
        if (current.firstOrNull()?.packageName == packageName) return
        _recentPackages.value = (listOf(entry) + current).take(MAX_ENTRIES)
    }

    fun clear() {
        _recentPackages.value = emptyList()
    }
}
